/*
 * Copyright 2012 The Stanford MobiSocial Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mobisocial.musubi.service;


import gnu.trove.list.linked.TLongLinkedList;
import gnu.trove.map.hash.TLongLongHashMap;
import gnu.trove.procedure.TLongProcedure;
import gnu.trove.set.hash.TLongHashSet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import mobisocial.crypto.IBHashedIdentity;
import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.musubi.App;
import mobisocial.musubi.model.MEncodedMessage;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.helpers.DeviceManager;
import mobisocial.musubi.model.helpers.EncodedMessageManager;
import mobisocial.musubi.model.helpers.FeedManager;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.protocol.Message;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.provider.MusubiContentProvider.Provided;

import org.codehaus.jackson.map.ObjectMapper;

import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Process;
import android.util.Base64;
import android.util.Log;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConfirmListener;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownSignalException;

import de.undercouch.bson4jackson.BsonFactory;

//TODO:XXX
//amqp is not quite perfect so this implementation delivers the routing
//properties we want but not the security properties.
public class AMQPService extends Service {
	public static boolean DBG = false;
    public static final String TAG = AMQPService.class.getName();
    static final String AMQP_QUEUE_GLOBAL_PREFIX = "msb";
    static final String AMQP_SERVICE = "bumblebee.musubi.us";

    ConnectionFactory mConnectionFactory;
    Connection mConnection;
    Channel mIncomingChannel;
    Channel mOutgoingChannel;
    Channel mGroupProbeChannel;
    //we need to do operations on the connection object in background thread
    //because some of them are blocking.
    HandlerThread mThread;
    IdentitiesManager mIdentitiesManager;
    DeviceManager mDeviceManager;
    EncodedMessageManager mEncodedMessageManager;
    SQLiteOpenHelper mDatabaseSource;
    //always run the message sender when the service first boots
    TLongHashSet mMessageWaitingForAck;
    TLongLongHashMap mMessageWaitingForAckByTag;
    Long mNeedAMessageBy;
    Handler mAMQPHandler;
    ObjectMapper mMapper;
    
    HashSet<String> mDeclaredGroups; 
    boolean mConnectionReady = false;
    final static long MIN_DELAY = 10 * 1000;
    final static long MAX_DELAY = 30 * 60 * 1000;
    long mFailureDelay = MIN_DELAY;
    enum FailedOperationType {
    	FailedNone,
    	FailedConnect,
    	FailedPublish,
    	FailedReceive,
    }
    FailedOperationType mFailedOperation = FailedOperationType.FailedConnect;
   
    
    public AMQPService() { 
    	super(); 
    }
    //we create one directly that
    AMQPService(SQLiteOpenHelper databaseSource) {
    	mDatabaseSource = databaseSource;
    	initializeAMQP();
    }
    
    public boolean isConnected() {
    	return mConnectionReady;
    }
    
    class SendableMessageAvailableHandler extends ContentObserver {
    	final Handler mHandler;
    	public SendableMessageAvailableHandler(Handler h) {
			super(h);
			mHandler = h;
		}
    	@Override
    	public void onChange(boolean selfChange) {
			sendMessages();
    	}
    }
    class DropConnectionAndReconnectHandler extends ContentObserver {
    	final Handler mHandler;
    	public DropConnectionAndReconnectHandler(Handler handler) {
			super(handler);
			mHandler = handler;
		}
    	@Override
    	public void onChange(boolean selfChange) {
    		closeConnection(FailedOperationType.FailedNone);
			initiateConnection();
    	}
    }
	class ResetBackOffAndReconnectIfNotConnected extends ContentObserver {
    	final Handler mHandler;
		public ResetBackOffAndReconnectIfNotConnected(Handler handler) {
			super(handler);
			mHandler = handler;
		}
		@Override
		public void onChange(boolean selfChange) {
			mFailureDelay = MIN_DELAY;
			if(!mConnectionReady) {
				initiateConnection();
			}
		}
	}

    @Override
    public void onCreate() {
		mDatabaseSource = App.getDatabaseSource(this);

		initializeAMQP();
		
		ContentResolver resolver = getContentResolver();
        resolver.registerContentObserver(MusubiService.PREPARED_ENCODED, false, 
        		new SendableMessageAvailableHandler(mAMQPHandler));
        resolver.registerContentObserver(MusubiService.OWNED_IDENTITY_AVAILABLE, false, 
        		new DropConnectionAndReconnectHandler(mAMQPHandler));
        resolver.registerContentObserver(MusubiService.NETWORK_CHANGED, false, 
        		new ResetBackOffAndReconnectIfNotConnected(mAMQPHandler));
        resolver.registerContentObserver(MusubiService.USER_ACTIVITY_RESUME, false, 
        		new ResetBackOffAndReconnectIfNotConnected(mAMQPHandler));

        Log.w(TAG, "service is now running");
    }
	private void initializeAMQP() {
		mIdentitiesManager = new IdentitiesManager(mDatabaseSource);
		mDeviceManager = new DeviceManager(mDatabaseSource);
    	mEncodedMessageManager = new EncodedMessageManager(mDatabaseSource);
    	
		mConnectionFactory = new ConnectionFactory();
		mConnectionFactory.setHost(AMQP_SERVICE);
		mConnectionFactory.setConnectionTimeout(30 * 1000);
		mConnectionFactory.setRequestedHeartbeat(30);

		mThread = new HandlerThread("AMQP");
		mThread.setPriority(Thread.MIN_PRIORITY);
		mThread.start();
		
		mAMQPHandler = new Handler(mThread.getLooper());

        //start the connection
		mAMQPHandler.post(new Runnable() {
			@Override
			public void run() {
				initiateConnection();
			}
		});
	}
	
	String encodeAMQPname(String prefix, byte[] key) {
		if (AMQP_QUEUE_GLOBAL_PREFIX != null) {
			return new StringBuilder().append(AMQP_QUEUE_GLOBAL_PREFIX)
					.append(prefix).append(Base64.encodeToString(key, Base64.URL_SAFE))
					.toString();
		}
		return prefix + Base64.encodeToString(key, Base64.URL_SAFE);
	}

    void closeConnection(FailedOperationType failure) {
    	assert(mThread.getThreadId() == Process.myTid());
    	if(mConnection != null) {
			Log.i(TAG, "closing connection");
			mConnectionReady = false;
			mMessageWaitingForAck = null;
			mMessageWaitingForAckByTag = null;
			mDeclaredGroups = null;
			try {
				mConnection.abort();
			} catch(Throwable t) {
				//never fail on a close
			}
			mOutgoingChannel = null;
			mIncomingChannel = null;
			mGroupProbeChannel = null;
			mConnection = null;
		}
    	if(failure != FailedOperationType.FailedNone) {
    		mFailedOperation = failure;
			mFailureDelay = Math.min(mFailureDelay * 2, MAX_DELAY);
    		Log.i(TAG, mFailedOperation.toString() + " retry delay now " + mFailureDelay + "ms");
    		mAMQPHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					initiateConnection();
				}
			}, mFailureDelay);
    	}
    }
    void sendMessages() {
    	if(!mConnectionReady) {
    		//we should always be trying to reconnect already
    		//so that we can receive new messages
    		return;
    	}
    	TLongLinkedList potentiallUnsent = mEncodedMessageManager.getUnsentOutboundIdsNotPending();

    	potentiallUnsent.forEach(new TLongProcedure() {
			@Override
			public boolean execute(long id) {
				if(mMessageWaitingForAck.contains(id))
					return true;

				try {
					byte[] encodedBytes = mEncodedMessageManager.lookupEncodedDataById(id);

			        byte[] group_exchange_name_bytes;
			        //TODO: load this from an in memory cache of recently encoded messages
			        //TODO: major performance gain on sending
			        IBHashedIdentity[] hid_for_queue;
					try {
					    Message m = getObjectMapper().readValue(encodedBytes, Message.class);
					    MIdentity[] ids = new MIdentity[m.r.length];
					    hid_for_queue = new IBHashedIdentity[m.r.length];
					    for(int i = 0; i < ids.length; ++i) {
					    	hid_for_queue[i] = new IBHashedIdentity(m.r[i].i).at(0);
					    	ids[i] = new MIdentity();
					    	ids[i].principalHash_ = hid_for_queue[i].hashed_;
					    	ids[i].type_ = Authority.values()[hid_for_queue[i].authority_.ordinal()];
					    }
					    group_exchange_name_bytes = FeedManager.computeFixedIdentifier(ids);
					} catch (IOException e) {
						Log.e(TAG, "failed to compute group exchange name");
						return true;
					}
					
					String group_exchange_name = encodeAMQPname("ibetgroup-", group_exchange_name_bytes);
					if(!mDeclaredGroups.contains(group_exchange_name)) {
						if(DBG) Log.v(TAG, "exchangeDeclare " + group_exchange_name);
						mOutgoingChannel.exchangeDeclare(group_exchange_name, "fanout", false);
			            for (IBHashedIdentity recipient : hid_for_queue){
			                String dest = encodeAMQPname("ibeidentity-", recipient.identity_);
			                if(DBG) Log.v(TAG, "exchangeDeclarePassive " + dest);
							try {
								if(mGroupProbeChannel == null)
									mGroupProbeChannel = mConnection.createChannel();
								if(DBG) Log.v(TAG, "exchangeDeclarePassive " + dest);
								mGroupProbeChannel.exchangeDeclarePassive(dest);
							} catch(IOException e) {
								mGroupProbeChannel = null;
								//TODO: XXX hack
								//if the user hasn't connected yet, we have to dump their messages
								//into a specific well known queue, because we don't know what the name
								//of their device key is
								if(DBG) Log.v(TAG, "queueDeclare " + "initial-" + dest);
								mOutgoingChannel.queueDeclare("initial-" + dest, true, false, false, null);                        
								if(DBG) Log.v(TAG, "exchangeDeclare " + dest);
				                mOutgoingChannel.exchangeDeclare(dest, "fanout", true);
				                if(DBG) Log.v(TAG, "queueBind " + "initial-" + dest + " " + dest);
				                mOutgoingChannel.queueBind("initial-" + dest, dest, "");
							}
							if(DBG) Log.v(TAG, "exchangeBind " + dest + " " + group_exchange_name);
			                mOutgoingChannel.exchangeBind(dest, group_exchange_name, "");
			            }
			            mDeclaredGroups.add(group_exchange_name);
					}
	
					if(DBG) Log.v(TAG, "basicPublish => " + group_exchange_name);
					long delivery_tag = mOutgoingChannel.getNextPublishSeqNo();
					mOutgoingChannel.basicPublish(group_exchange_name, "", true, false, null, encodedBytes);
					mMessageWaitingForAck.add(id);
					mMessageWaitingForAckByTag.put(delivery_tag, id);
					if(mFailedOperation == FailedOperationType.FailedPublish) {
						mFailureDelay = MIN_DELAY;
						mFailedOperation = FailedOperationType.FailedNone;
					}
					return true;
				} catch (Throwable e) {
					Log.e(TAG, "Failed to send message, aborting connection", e);
					closeConnection(FailedOperationType.FailedPublish);
					return false;
				}
			}
		});
    }
    void attachToQueues() throws IOException {
		Log.i(TAG, "Setting up identity exchange and device queue");

		DefaultConsumer consumer = new DefaultConsumer(mIncomingChannel) {
			@Override
			public void handleDelivery(final String consumerTag, final Envelope envelope,
					final BasicProperties properties, final byte[] body) throws IOException 
			{
				if(DBG) Log.i(TAG, "recevied message: " + envelope.getExchange());
				assert(body != null);
				//TODO: throttle if we have too many incoming?
				//TODO: check blacklist up front?
				//TODO: check hash up front?
				MEncodedMessage encoded = new MEncodedMessage();
				encoded.encoded_ = body;
				mEncodedMessageManager.insertEncoded(encoded);
                getContentResolver().notifyChange(MusubiService.ENCODED_RECEIVED, null);

                //we have to do this in our AMQP thread, or add synchronization logic
				//for all of the AMQP related fields.
                mAMQPHandler.post(new Runnable() {
					public void run() {
	                	if(mIncomingChannel == null) {
	                		//it can close in this time window
	                		return;
	                	}
		                try {
							mIncomingChannel.basicAck(envelope.getDeliveryTag(), false);
							if(mFailedOperation == FailedOperationType.FailedReceive) {
								mFailureDelay = MIN_DELAY;
								mFailedOperation = FailedOperationType.FailedNone;
							}
						} catch (Throwable e) {
							Log.e(TAG, "failed to ack message on AMQP", e);
							//next connection that receives a packet wins
							closeConnection(FailedOperationType.FailedReceive);
						}
		                
					}
				});
			}
		};
		byte[] device_name = new byte[8];
		ByteBuffer.wrap(device_name).putLong(mDeviceManager.getLocalDeviceName());
		String device_queue_name = encodeAMQPname("ibedevice-", device_name);
		//leaving these since they mark the beginning of the connection and shouldn't be too frequent (once per drop)
		Log.v(TAG, "queueDeclare " + device_queue_name);
		mIncomingChannel.queueDeclare(device_queue_name, true, false, false, null);                        
		//TODO: device_queue_name needs to involve the identities some how? or be a larger byte array
		List<MIdentity> mine = mIdentitiesManager.getOwnedIdentities();
		for(MIdentity me : mine) {
			IBHashedIdentity id = IdentitiesManager.toIBHashedIdentity(me, 0);
			String identity_exchange_name = encodeAMQPname("ibeidentity-", id.identity_);
			Log.v(TAG, "exchangeDeclare " + identity_exchange_name);
			mIncomingChannel.exchangeDeclare(identity_exchange_name, "fanout", true);
			Log.v(TAG, "queueBind " + device_queue_name + " " + identity_exchange_name);
			mIncomingChannel.queueBind(device_queue_name, identity_exchange_name, "");
			try {
				Log.v(TAG, "queueDeclarePassive " + "initial-" + identity_exchange_name);
				Channel incoming_initial = mConnection.createChannel();
				incoming_initial.queueDeclarePassive("initial-" + identity_exchange_name);
				try {
					Log.v(TAG, "queueUnbind " + "initial-" + identity_exchange_name + " " + identity_exchange_name);
					//	we now have claimed our identity, unbind this queue
					incoming_initial.queueUnbind("initial-" + identity_exchange_name, identity_exchange_name, "");
					//but also drain it
				} catch(IOException e) {
				}
				Log.v(TAG, "basicConsume " + "initial-" + identity_exchange_name);
				mIncomingChannel.basicConsume("initial-" + identity_exchange_name, consumer);
			} catch(IOException e) {
				//no one sent up messages before we joined
				//IF we deleted it: we already claimed our identity, so we ate this queue up
			}
		}
		
		Log.v(TAG, "basicConsume " + device_queue_name);
		mIncomingChannel.basicConsume(device_queue_name, false, "", true, true, null, consumer);
    }
    void initiateConnection() {
    	if(mConnection != null) {
    		//just for information sake, this is a legitimate event
    		Log.i(TAG, "Already connected when triggered to initiate connection");
    		return;
    	}
		Log.i(TAG, "Network is up connection is being attempted");
		try {
			mConnection = mConnectionFactory.newConnection();
			mConnection.addShutdownListener(new ShutdownListener() {
				@Override
				public void shutdownCompleted(ShutdownSignalException cause) {
					if(!cause.isInitiatedByApplication()) {
				        //start the connection
						mAMQPHandler.post(new Runnable() {
							@Override
							public void run() {
								closeConnection(FailedOperationType.FailedConnect);
							}
						});
					}
				}
			});

			mDeclaredGroups = new HashSet<String>();
			mMessageWaitingForAck = new TLongHashSet();
			mMessageWaitingForAckByTag = new TLongLongHashMap();

			mIncomingChannel = mConnection.createChannel();
			mIncomingChannel.basicQos(10);
			attachToQueues();
			
			mOutgoingChannel = mConnection.createChannel();
			//TODO: these callbacks run in another thread, so this is not correct
			//we need some synchronized or a customized ExecutorService that
			//posts to the handler (though, that may not be possible if they demand
			//n threads to run on
			mOutgoingChannel.addConfirmListener(new ConfirmListener() {
				@Override
				public void handleNack(long deliveryTag, boolean multiple) throws IOException {
					//don't immediately try to resend, just flag it, it will be rescanned later
					//this probably only happens if the server is temporarily out of space
					long encoded_id = mMessageWaitingForAckByTag.get(deliveryTag);
					mMessageWaitingForAckByTag.remove(deliveryTag);
					mMessageWaitingForAck.remove(encoded_id);
				}
				
				@Override
				public void handleAck(long deliveryTag, boolean multiple) throws IOException {
                    //delivered!
					long encoded_id = mMessageWaitingForAckByTag.get(deliveryTag);
					
					//mark the db entry as processed
					MEncodedMessage encoded = mEncodedMessageManager.lookupMetadataById(encoded_id);
					assert(encoded.outbound_);
					encoded.processed_ = true;
					encoded.processedTime_ = new Date().getTime();
					mEncodedMessageManager.updateEncodedMetadata(encoded);
					
					mMessageWaitingForAckByTag.remove(deliveryTag);
					mMessageWaitingForAck.remove(encoded_id);

					long feedId = mEncodedMessageManager.getFeedIdForEncoded(encoded_id);
					if (feedId != -1) {
						Uri feedUri = MusubiContentProvider.uriForItem(Provided.FEEDS_ID, feedId);
						getContentResolver().notifyChange(feedUri, null);
					}
				}
			});
			mOutgoingChannel.confirmSelect();
			mConnectionReady = true;
			
			//once we have successfully done our work, we can
			//reset the failure delay, FYI, internal exceptions in the
			//message sender will cause backoff to MAX_DELAY
			if(mFailedOperation == FailedOperationType.FailedConnect) {
				mFailureDelay = MIN_DELAY;
				mFailedOperation = FailedOperationType.FailedNone;
			}
		} catch (Throwable e) {
			closeConnection(FailedOperationType.FailedConnect);
			Log.e(TAG, "Failed to connect to AMQP", e);
		}
		//slight downside here is that if publish a message causes the fault,
		//then we will always reconnect and disconnect
		sendMessages();
	}

    private ObjectMapper getObjectMapper() {
        if (mMapper == null) {
            mMapper = new ObjectMapper(new BsonFactory());
        }
        return mMapper;
    }
    
    public boolean isConnectionReady() {
    	return mConnectionReady;
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Received start id " + startId + ": " + intent);
        return START_STICKY;
    }


    @Override
    public void onDestroy() {
    	//kick the background thread into shutdown mode
    	mAMQPHandler.post(new Runnable() {
			@Override
			public void run() {
				closeConnection(FailedOperationType.FailedNone);
				//give it half a second to close down
		    	mAMQPHandler.postDelayed(new Runnable() {
		    		public void run() {
		    			mThread.getLooper().quit();
		    		}
		    	}, 500);	
			}
		});
    	//wait for it to clean up
    	try {
			mAMQPHandler.getLooper().getThread().join();
		} catch (InterruptedException e) {}
    }
	
    public class AMQPServiceBinder extends Binder {
    	public AMQPService getService() {
            return AMQPService.this;
        }
    }
    private final IBinder mBinder = new AMQPServiceBinder();
	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}
}
