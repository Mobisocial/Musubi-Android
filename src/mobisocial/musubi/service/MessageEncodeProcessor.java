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

import gnu.trove.list.array.TLongArrayList;
import gnu.trove.procedure.TLongProcedure;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.musubi.App;
import mobisocial.musubi.encoding.MessageEncoder;
import mobisocial.musubi.encoding.NeedsKey;
import mobisocial.musubi.encoding.ObjEncoder;
import mobisocial.musubi.encoding.ObjFormat;
import mobisocial.musubi.encoding.OutgoingMessage;
import mobisocial.musubi.encoding.TransportDataProvider;
import mobisocial.musubi.identity.IdentityProvider;
import mobisocial.musubi.identity.IdentityProviderException;
import mobisocial.musubi.model.MApp;
import mobisocial.musubi.model.MDevice;
import mobisocial.musubi.model.MEncodedMessage;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MFeed.FeedType;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MObject;
import mobisocial.musubi.model.MPendingUpload;
import mobisocial.musubi.model.MSignatureUserKey;
import mobisocial.musubi.model.helpers.DatabaseManager;
import mobisocial.musubi.model.helpers.MessageTransportManager;
import mobisocial.musubi.model.helpers.UserKeyManager;
import mobisocial.musubi.objects.DeleteObj;
import mobisocial.musubi.objects.LikeObj;
import mobisocial.musubi.objects.PictureObj;
import mobisocial.musubi.provider.TestSettingsProvider;
import mobisocial.musubi.util.Util;

import org.json.JSONException;
import org.json.JSONObject;
import org.mobisocial.corral.CorralDownloadClient;
import org.mobisocial.corral.CryptUtil;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.ContentObserver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

/**
 * Scans for outbound objects that need to be encoded. Encodes the messages and
 * notifies that newly encoded objects are available.
 * 
 * @see MusubiService
 * @see MessageDecodeProcessor
 * @see AMQPService
 */
public class MessageEncodeProcessor extends ContentObserver {
    private final String TAG = getClass().getSimpleName();
    private final boolean DBG = MusubiService.DBG;
    private final Context mContext;
    private final SQLiteOpenHelper mHelper;
    private MessageEncoder mMessageEncoder;
    private final DatabaseManager mDatabaseManager;
	private KeyUpdateHandler mKeyUpdateHandler;

	/**
	 * The number of recipients an object can have in processor A.
	 */
	final int SMALL_PROCESSOR_CUTOFF = 20;

    private boolean mSynchronousKeyFetch = false;
    final IdentityProvider mIdentityProvider;
	HandlerThread mThread;

	final List<ProcessorThread> mProcessorThreads;
	final TLongSet mObjectsPendingProcessing = new TLongHashSet();
    final TLongArrayList mFinishedProcessing = new TLongArrayList();
	private final SQLiteOpenHelper mDatabaseSource;

    public static MessageEncodeProcessor newInstance(Context context, SQLiteOpenHelper dbh, KeyUpdateHandler keyUpdateService, IdentityProvider identityProvider) {
        HandlerThread thread = new HandlerThread("MessageEncodeThread");
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
        return new MessageEncodeProcessor(context, dbh, thread, keyUpdateService, identityProvider);
    }

    private MessageEncodeProcessor(Context context, SQLiteOpenHelper dbh, HandlerThread thread, final KeyUpdateHandler keyUpdateService, IdentityProvider identityProvider) {
        super(new Handler(thread.getLooper()));
        mIdentityProvider = identityProvider;
        mDatabaseSource = dbh;
        mThread = thread;
        mContext = context;
        mHelper = dbh;
        mDatabaseManager = new DatabaseManager(mContext);

        TestSettingsProvider.Settings settings = App.getTestSettings(context);
        if(settings != null) {
        	mSynchronousKeyFetch = settings.mSynchronousKeyFetchInMessageEncodeDecode;
        }

        mKeyUpdateHandler = keyUpdateService;

        /**
         * Set up a number of threads to do the encoding heavywork.
         */
        mProcessorThreads = new ArrayList<ProcessorThread>(2);
        mProcessorThreads.add(new ProcessorThread("EncoderA"));
        mProcessorThreads.add(new ProcessorThread("EncoderB"));
        for (ProcessorThread proc : mProcessorThreads) {
            proc.start();
        }
        
        //do the part that hits the db on a background thread
        new Handler(thread.getLooper()).post(new Runnable() {
			@Override
			public void run() {
		        long myDevice = mDatabaseManager.getDeviceManager().getLocalDeviceName();
		        TransportDataProvider tdp = new MessageTransportManager(
		                mHelper, mIdentityProvider.getEncryptionScheme(), mIdentityProvider.getSignatureScheme(), myDevice);
		        mMessageEncoder = new MessageEncoder(tdp);
			}
    	});
    }

    @Override
    public void onChange(boolean selfChange) {
        final Set<ProcessorThread> processorThreadsThisBatch = new HashSet<ProcessorThread>();
        
        //to avoid a race in deleting object ids while this process is running, we have to do this whole
        //part in a transaction (the feed/obj deletion is in a transaction as well)
        //TODO: this may be kind of ugly.  an alternative would be to "clear" out the object data for space saving
        // (then it won't update indexes).  then a startup job could do the removals, or a periodic
        // job could manage the deletion.  though that sounds like it might be worse
        SQLiteDatabase db = mDatabaseSource.getWritableDatabase();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
        	db.beginTransactionNonExclusive();
        } else {
        	db.beginTransaction();
        }
        //first remove anything that we know has finished from the pending queue.
        //we are guaranteed that nothing is going to disappear or appear in this block.
        //we will only be removing objects that were sent (or sent and deleted by objpipelineprocessor).
        //either case is ok
        synchronized (mFinishedProcessing) {
        	//we have to clear the pending list in the thread that fills the pending list 
        	//otherwise it might double send a message
        	//the one lame thing here is that we keep this around until the next message is sent
        	//but its just an array on obj ids, so that should be ok
        	mFinishedProcessing.forEach(new TLongProcedure() {
				@Override
				public boolean execute(long objId) {
		            mObjectsPendingProcessing.remove(objId);
					return true;
				}
			});
        	mFinishedProcessing.clear();
		}
        
        //*** we need to avoid a delete followed by an insert happening in here 
        //*** this would cause an SQL id key to be reused and hence ignored by us

        //now compute the objects that we need to process.
        //we know these won't be deleted because we kick off
        //the job that deletes them
        long[] ids = mDatabaseManager.getObjectManager().objectsToEncode();
        db.endTransaction();

        if (DBG) Log.d(TAG, "MessageEncoder looping over " + ids.length + " objects...");
        for (long objId : ids) {
            synchronized (mObjectsPendingProcessing) {
                if (mObjectsPendingProcessing.contains(objId)) {
                    continue;
                }
                mObjectsPendingProcessing.add(objId);
            }
            ProcessorThread processor;
            long numRecipients = mDatabaseManager.getFeedManager().membersInObjectFeed(objId);
            if (numRecipients <= SMALL_PROCESSOR_CUTOFF) {
                processor = mProcessorThreads.get(0);
            } else {
                processor = mProcessorThreads.get(1);
            }

            Message msg = processor.mHandler.obtainMessage();
            msg.obj = objId;
            msg.what = ProcessorThread.ENCODE_MESSAGE;
            processor.mHandler.sendMessage(msg);
            processorThreadsThisBatch.add(processor);
        }
        

        for (ProcessorThread proc : processorThreadsThisBatch) {
            Message msg = proc.mHandler.obtainMessage();
            msg.what = ProcessorThread.NOTIFY;
            proc.mHandler.sendMessage(msg);
        }
    }

    /**
     * Handles requests to encode messages, listed by id.
     *
     */
    class ProcessorThread extends Thread {
        public static final int ENCODE_MESSAGE = 1;
        public static final int NOTIFY = 2;
        public Looper mLooper;
        public Handler mHandler;
        private boolean successSinceLastNotify = false;
        private boolean pendingUploadSinceLastNotify = false;

        public ProcessorThread(String name) {
            super(name);
            setPriority(Thread.MIN_PRIORITY);
        }

        public void run() {
            Looper.prepare();
            mLooper = Looper.myLooper();

            mHandler = new Handler() {

				public void handleMessage(Message msg) {
                    String tag = Thread.currentThread().getName();
                    switch (msg.what) {
                        case ENCODE_MESSAGE:
                            if (DBG) Log.d(tag, "Encoding message...");
                            long objId = (Long)msg.obj;
                            encodeObject(objId);
                            synchronized (mFinishedProcessing) {
                            	//keep a queue of messages that have finished being
                            	//processed so that the database scanning thread can
                            	//update the objects pending collection without having
                            	//a race condition
                            	//this is the source of duplicate messages...
                            	mFinishedProcessing.add(objId);
                            }
                            break;
                        case NOTIFY:
                            if (successSinceLastNotify) {
                                if (DBG) Log.d(tag, "Notifying encoded available...");
                                ContentResolver resolver = mContext.getContentResolver();
                                resolver.notifyChange(MusubiService.APP_OBJ_READY,
                                        MessageEncodeProcessor.this);
                                resolver.notifyChange(MusubiService.PREPARED_ENCODED,
                                        MessageEncodeProcessor.this);
                                successSinceLastNotify = false;
                                if (pendingUploadSinceLastNotify) {
                                    resolver.notifyChange(MusubiService.UPLOAD_AVAILABLE,
                                            MessageEncodeProcessor.this);
                                    pendingUploadSinceLastNotify = false;
                                }
                            } else {
                                if (DBG) Log.d(TAG, "No encodings to notify.");
                            }
                            break;
                    }
                }
            };

            Looper.loop();
        }

        public void encodeObject(long objId) {
            try {
                MObject object = mDatabaseManager.getObjectManager().getObjectForId(objId);
                assert(object != null);
                MFeed feed = mDatabaseManager.getFeedManager().lookupFeed(object.feedId_);
                assert(feed != null);
                MIdentity sender = mDatabaseManager.getIdentitiesManager().getIdentityForId(object.identityId_);
                assert(sender != null);
                if (sender == null) {
                    Log.e(TAG, "No sender for object " + object.id_);
                    mDatabaseManager.getObjectManager().delete(objId);
                    return;
                }

                boolean localOnly = sender.type_ == Authority.Local;
                assert(localOnly || sender.owned_);
                MApp app = mDatabaseManager.getAppManager().getAppBasics(object.appId_);
                assert(app != null);

                // Queue attached uploads.
                if (object.json_ != null) {
                    JSONObject json;
                    try {
                        json = new JSONObject(object.json_);
                    } catch (JSONException e) {
                        Log.e(TAG, "bad json in outbound object", e);
                        mDatabaseManager.getObjectManager().delete(objId);
                        return;
                    }

                    // check for auto-uploads
                    if (json.has(CorralDownloadClient.OBJ_LOCAL_URI)) {
                        boolean autoUpload = !PictureObj.TYPE.equals(object.type_);
                        prepareUpload(object, json, autoUpload);
                        pendingUploadSinceLastNotify |= autoUpload;
                    }
                }

                OutgoingMessage om = new OutgoingMessage();
                ObjFormat outbound = ObjEncoder.getPreparedObj(app, feed, object);

                if (feed.type_ == FeedType.ASYMMETRIC || feed.type_ == FeedType.ONE_TIME_USE) {
                    //when broadcasting a message to all friends, don't
                    //leak friend of friend information
                    om.blind_ = true;
                }
                //delete and likes are blind since they don't produce a new visible message
                //and this will let us not notify on them
                if(object.type_.equals(DeleteObj.TYPE) || object.type_.equals(LikeObj.TYPE)) {
                	om.blind_ = true;
                }

                om.data_ = ObjEncoder.encode(outbound);
                om.fromIdentity_ = sender;

                //Musubi app id is the application namespace
                om.app_ = Util.sha256(mContext.getApplicationInfo().className.getBytes());
                
                om.recipients_ = mDatabaseManager.getFeedManager().getFeedMembers(feed);
                
                //remove any blocked people
                for(MIdentity ident : om.recipients_) {
                	if(ident.blocked_) {
                		ArrayList<MIdentity> idents = new ArrayList<MIdentity>(om.recipients_.length);
                        for(MIdentity id : om.recipients_) {
                        	if(!id.blocked_) {
                        		idents.add(id);
                        	}
                        }
                        om.recipients_ = idents.toArray(new MIdentity[idents.size()]);
                		break;
                	}
                }
                om.hash_ = Util.sha256(om.data_);

                //universal hash it, must happen before the encoding step so
                //local messages can still run through the pipeline
                MDevice device = mDatabaseManager.getDeviceManager().getDeviceForId(object.deviceId_);
                assert(device.deviceName_ == mDatabaseManager.getDeviceManager().getLocalDeviceName());
                object.universalHash_ = ObjEncoder.computeUniversalHash(sender, device, om.hash_);
                object.shortUniversalHash_ = Util.shortHash(object.universalHash_);

                if (localOnly) {
                    object.encodedId_ = -1L;
                    mDatabaseManager.getObjectManager().updateObjectEncodedMetadata(object);
                    successSinceLastNotify = true;
                    return;
                }

                MEncodedMessage encoded;
                try {
                     encoded = mMessageEncoder.processMessage(om);
                } catch(NeedsKey.Signature e) {
                    if (!mSynchronousKeyFetch) {
                        throw e;
                    }
                    try
                    {
	                    UserKeyManager ukm = new UserKeyManager(mIdentityProvider.getEncryptionScheme(), mIdentityProvider.getSignatureScheme(), mHelper);
	                    MSignatureUserKey suk = new MSignatureUserKey();
	                    suk.identityId_ = sender.id_;
	                    suk.when_ = e.identity_.temporalFrame_;
	                    suk.userKey_ = mIdentityProvider.syncGetSignatureKey(e.identity_).key_;
	                    ukm.insertSignatureUserKey(suk);
	                    //// just do it again
	                    encoded = mMessageEncoder.processMessage(om);
                    } catch (IdentityProviderException exn) {
                    	Log.e(TAG, "User key retrieval failed while encoding obj " + objId, exn);
                    	return;
                    }
                } catch (NeedsKey e) {
                    Log.i(TAG, "Failed to encode obj because a user key was required." + objId, e);
                    if(mKeyUpdateHandler != null)
                        mKeyUpdateHandler.requestSignatureKey(e.identity_);
                    return;
                }

                object.encodedId_ = encoded.id_;
                mDatabaseManager.getObjectManager().updateObjectEncodedMetadata(object);
                successSinceLastNotify = true;
            } catch (Exception e) {
                Log.e(TAG, "Failed to encode obj " + objId, e);
            }
        }
    }

    void prepareUpload(MObject object, JSONObject json, boolean submitJob) {
        try {
            CryptUtil cu = new CryptUtil();
            String key = cu.getKey();
            json.put(CorralDownloadClient.OBJ_PRESHARED_KEY, key);
            object.json_ = json.toString();
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "no algorithm for encrypted file upload", e);
            return;
        } catch (JSONException e) {
            Log.e(TAG, "error preparing file upload json", e);
            return;
        }
        if (submitJob) {
            ContentValues values = new ContentValues();
            values.put(MPendingUpload.COL_OBJECT_ID, object.id_);
            mDatabaseSource.getWritableDatabase().insert(MPendingUpload.TABLE, null, values);
        }
    }
}
