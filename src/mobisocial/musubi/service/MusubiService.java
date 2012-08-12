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

import mobisocial.musubi.App;
import mobisocial.musubi.R;
import mobisocial.musubi.identity.AphidIdentityProvider;
import mobisocial.musubi.identity.IdentityProvider;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.provider.TestSettingsProvider.Settings;
import mobisocial.musubi.service.MessageEncodeProcessor.ProcessorThread;
import mobisocial.musubi.syncadapter.SyncService;

import org.mobisocial.corral.ContentCorral;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.widget.Toast;

/**
 * <p>A persistent service for managing Musubi's object processing subsystem.
 *
 * <p>The MusubiService manages a set of services that manage objects as
 * they are sent/received from the network. These services are implemented
 * as Handlers running on their own HandlerThreads.
 *
 * <p>We refer to the 'outer' data model as messages and the 'inner' data model
 * as objects. An object is made available to applications only after it has
 * been processed, and processing may only occur once an object and its encoding
 * are both available.
 *
 * <p>The flow of data is as follows:
 * <ul>
 *   <li>Send: insert object => encode message => process object & send message
 *   <li>Receive: insert message => decode message => process object
 * </ul>
 *
 * <p>The service architecture in more details:
 * <pre>
 * Frontend sends obj:
 * -- ensure feed exists
 * -- insert into objects with processed=0
 * notify PLAIN_OBJ_READY
 *
 * Frontend discovers new identity (eg, address book updated)
 * -- ensure idHash exists in identities
 * notify IDENTITY_AVAILABLE
 * if (owned) notify IDENTITY_OWNED
 *
 * Frontend talks to network:
 * listen on IDENTITY_OWNED
 * loop over network available
 * -- insert into encoded
 * notify ENCODED_RECEIVED
 *
 * MessageEncodeHandler:
 * listen on PLAIN_OBJ_READY
 * loop over objects.encoded_id is null
 * -- ensure feed exists
 * -- insert into encoded
 * -- set object.encoded_id, object.universal_hash
 * notify PREPARED_ENCODED
 * notify APP_OBJ_READY
 * 
 * MessageDecodeHandler:
 * listen on ENCODED_RECEIVED
 * loop over encoded.processed=0 and encoded.outbound=0
 * -- look for identity profile updates
 * -- verify grey/whitelist
 * -- verify feed exists, add user
 * -- insert into objects
 * -- set encoded.processed=1
 * -- set object.encoded_id, object.universal_hash
 * notify APP_OBJ_READY
 *
 * ObjPipelineHandler:
 * listen on APP_OBJ_READY, IDENTITY_AVAILABLE
 * loop on objects.processed=0 (and objects.encoded != null???) TODO
 * -- assert (short(univ_hash) = short_univ_hash)
 * -- triggers ObjHandlers (render caches, user notifications,
 * -- sets objects.processed=1
 * notify itemUri(objects, oid), itemUri(feeds, fid)
 *
 * AMQPService:
 * listen on PREPARED_ENCODED
 * loop over encoded.processed=0 AND encoded.outbound=1
 * -- delivers to network (w/ ack)
 * -- sets encoded.processed=1
 * </pre>
 */
public class MusubiService extends Service {
    static final boolean DBG = true;
    public static final String TAG = "MusubiService";

	private NotificationManager mNotificationManager;
	private SQLiteOpenHelper mDatabaseSource;
	private ContentCorral mContentCorral;
	MessageEncodeProcessor mMessageEncodeProcessor;
	MessageDecodeProcessor mMessageDecodeProcessor;
	ObjPipelineProcessor mPipelineProcessor;
	ProfilePushProcessor mProfilePushProcessor;
	CorralUploadProcessor mCorralUploadProcessor;
	WizardStepHandler mWizardStepHandler;
	KeyUpdateHandler mKeyUpdateHandler;
	FacebookUpdateHandler mFacebookUpdateHandler;
	AddressBookUpdateHandler mAddressBookUpdateHandler;
	IdentityProvider mIdentityProvider;

	/**
	 * Called when a new object has been added by an application on this device.
	 */
	public static final Uri PLAIN_OBJ_READY = MusubiContentProvider.createUri("send-obj");

	/**
	 * Called when a a previously unencoded object has been encoded.
	 * @See MessageEncodeProcessor
	 */
	public static final Uri PREPARED_ENCODED = MusubiContentProvider.createUri("send-encoded-obj");

	/**
	 * Called when an encoded object has been received from a remote device.
	 * @See {@link MessageDecodeProcessor}
	 */
	public static final Uri ENCODED_RECEIVED = MusubiContentProvider.createUri("rec-encoded-obj");

	/**
	 * Called when an encoded object received from another application has been decoded.
	 */
	public static final Uri APP_OBJ_READY = MusubiContentProvider.createUri("obj-available");

	/**
	 * Called when an owned identity has been added to the database.
	 */
	public static final Uri OWNED_IDENTITY_AVAILABLE = MusubiContentProvider.createUri("owned-id-available");

	/**
	 * Called when one of this device's account's profile has been updated.
	 * @see ProfilePushProcessor
	 */
	public static final Uri MY_PROFILE_UPDATED = MusubiContentProvider.createUri("my-profile-updated");

	/**
	 * Called when a feed has been updated.
	 */
	public static final Uri FEED_UPDATED = MusubiContentProvider.createUri("feed-updated");

	/**
	 * Called when a contact has been added to the local address book. 
	 * @See ProfilePushProcessor
	 */
	public static final Uri WHITELIST_APPENDED = MusubiContentProvider.createUri("whitelist-appended");

	/**
	 * Called when a contact has some renderable metadata changed. 
	 * @See ProfilePushProcessor
	 */
	public static final Uri PRIMARY_CONTENT_CHANGED = MusubiContentProvider.createUri("profile-updated");

	/**
	 * Called when a whitelist/blacklist/graylist changes other than a whitelist append
	 * @See ProfilePushProcessor
	 */
	public static final Uri COLORLIST_CHANGED = MusubiContentProvider.createUri("colorlist-change");

	/**
	 * Triggered when a user has explicitly requested a profile sync.
	 */
	public static final Uri PROFILE_SYNC_REQUESTED = MusubiContentProvider.createUri("profile-sync-requested");

	/**
	 * Called when the user has completed a step in the wizard.
	 * @see WizardStepHandler
	 */
	public static final Uri WIZARD_STEP_TAKEN = MusubiContentProvider.createUri("wizard-step-taken");

	/**
	 * Called when a fresh token has been associated with one of this device's accounts.
	 */
	public static final Uri AUTH_TOKEN_REFRESH = MusubiContentProvider.createUri("token-refresh");
	
	/**
	 * Called when a new facebook is linked and periodically updated
	 */
	public static final Uri FACEBOOK_FRIEND_REFRESH = MusubiContentProvider.createUri("facebook-friend-refresh");

	/**
	 * Called when network status changed.  Should reset any exponential backoffs.
	 */
	public static final Uri NETWORK_CHANGED = MusubiContentProvider.createUri("network-changed");

	/**
	 * Called when network status changed.  Should reset any exponential backoffs.
	 */
	public static final Uri USER_ACTIVITY_RESUME = MusubiContentProvider.createUri("user-activity-resume");
	
	/**
	 * Called a new identity has been inserted by a user action.  This means we should ignore any batching we normally attempt to do.
	 */
	public static final Uri FORCE_RESCAN_CONTACTS = MusubiContentProvider.createUri("rescan-contacts");

	/**
	 * Called when we need to force a profile object to be sent to a new friend we discovered.
	 */
	public static final Uri FORCE_PROFILE_PUSH =  MusubiContentProvider.createUri("force-profile-push");

	/**
	 * Called when the address book scanner has completed a pass.
	 */
	public static final Uri ADDRESS_BOOK_SCANNED = MusubiContentProvider.createUri("address-book-scanned");

	public static final Uri REQUEST_ADDRESS_BOOK_SCAN = MusubiContentProvider.createUri("scan-address-book");

	/**
	 * Called once the wizard is known to have sent its first message.
	 */
	public static final Uri WIZARD_READY = MusubiContentProvider.createUri("wizard-ready");

	/**
	 * Called when we need to trigger the app manifests to update (for now once on start)
	 */
	public static final Uri UPDATE_APP_MANIFESTS = MusubiContentProvider.createUri("update-app-manifests");

	/**
	 * Notifies the uploader service that content is available.
	 */
	public static final Uri UPLOAD_AVAILABLE = MusubiContentProvider.createUri("upload-available");

	public static final Uri DOWNLOAD_REQUESTED = MusubiContentProvider.createUri("download-request");

	public static final String EXTRA_OBSERVER = "mobisocial.musubi.service.OBSERVER";
	
    @Override
    public void onCreate() {
        mNotificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        mDatabaseSource = App.getDatabaseSource(this);

        new InitializeServiceTask().execute();
    }

    @Override
    public void onDestroy() {
    	//happens in testing only AFAIK.
    	if(mNotificationManager != null)
    		mNotificationManager.cancel(R.string.active);
        Toast.makeText(this, R.string.stopping, Toast.LENGTH_SHORT).show();
    }

    public class MusubiServiceBinder extends Binder {
    	public MusubiService getService() {
            return MusubiService.this;
        }
    }
    private final IBinder mBinder = new MusubiServiceBinder();
	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	class InitializeServiceTask extends AsyncTask<Void, Void, Void> {
	    @Override
	    protected Void doInBackground(Void... params) {
	        Context context = MusubiService.this;
	        ContentResolver resolver = getContentResolver();
	        Settings test_settings = App.getTestSettings(context);

	        // TODO: content corral should manage it's own ip ups and downs.
	        // XXX: Pre-ICS, BluetoothAdapter.getDefaultAdapter() must be
            // called on a thread with a looper.
            // Starting corral deferred until postExecute.
            mContentCorral = new ContentCorral(context);

	        //we share the identity provider because the initialization of the ib signature and 
	        //encryption scheme take a quite some time
	        if (test_settings != null && test_settings.mAlternateIdentityProvider != null) {
	            mIdentityProvider = test_settings.mAlternateIdentityProvider;
	        } else {
	            mIdentityProvider = new AphidIdentityProvider(context);
	        }

	        /*
	         * Looping observer services. Each looper runs on its own thread.
	         */
	        mKeyUpdateHandler = KeyUpdateHandler.newInstance(context, mDatabaseSource, mIdentityProvider);
	        resolver.registerContentObserver(AUTH_TOKEN_REFRESH, true, mKeyUpdateHandler);
	        
	        mFacebookUpdateHandler = FacebookUpdateHandler.newInstance(context, mDatabaseSource);
	        resolver.registerContentObserver(FACEBOOK_FRIEND_REFRESH, true, mFacebookUpdateHandler);
	        
	        mMessageEncodeProcessor = MessageEncodeProcessor.newInstance(context, mDatabaseSource, mKeyUpdateHandler, mIdentityProvider);
	        resolver.registerContentObserver(PLAIN_OBJ_READY, false, mMessageEncodeProcessor);

	        mMessageDecodeProcessor = MessageDecodeProcessor.newInstance(context, mDatabaseSource, mKeyUpdateHandler, mIdentityProvider);
	        resolver.registerContentObserver(ENCODED_RECEIVED, false, mMessageDecodeProcessor);

	        mPipelineProcessor = ObjPipelineProcessor.newInstance(context);
	        resolver.registerContentObserver(APP_OBJ_READY, false, mPipelineProcessor);
	        resolver.registerContentObserver(OWNED_IDENTITY_AVAILABLE, false, mPipelineProcessor);

	        mProfilePushProcessor = ProfilePushProcessor.newInstance(context, mDatabaseSource);
	        resolver.registerContentObserver(OWNED_IDENTITY_AVAILABLE, false, mProfilePushProcessor);
	        resolver.registerContentObserver(WHITELIST_APPENDED, false, mProfilePushProcessor);
	        resolver.registerContentObserver(PROFILE_SYNC_REQUESTED, false, mProfilePushProcessor);
	        resolver.registerContentObserver(MY_PROFILE_UPDATED, false,
	                mProfilePushProcessor.getProfileUpdateObserver());
	        //run it when we start in case we crashed have pending work to do
	        mProfilePushProcessor.dispatchChange(false);

	        mCorralUploadProcessor = CorralUploadProcessor.newInstance(context, mDatabaseSource);
            resolver.registerContentObserver(UPLOAD_AVAILABLE, false, mCorralUploadProcessor);

	        mWizardStepHandler = WizardStepHandler.newInstance(context, mDatabaseSource);
	        resolver.registerContentObserver(WIZARD_STEP_TAKEN, true, mWizardStepHandler);

	        if (test_settings == null || test_settings.mShouldDisableAddressBookSync == false) {
	            mAddressBookUpdateHandler = AddressBookUpdateHandler.newInstance(context,
	                    mDatabaseSource, resolver);
	            resolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true,
	                    mAddressBookUpdateHandler);
	            resolver.registerContentObserver(REQUEST_ADDRESS_BOOK_SCAN, true,
                        mAddressBookUpdateHandler);
	        }
	        
	        /*
	         * The tokens of each identity should be refreshed periodically.
	         * The user's list of Facebook friends should also be refreshed periodically
	         */
	        AlarmManager am = (AlarmManager)getSystemService(ALARM_SERVICE);
	        long currentTime = System.currentTimeMillis();
	        final long TWENTY_MINUTES = 1000*60*20;
	        
	        // Key refresh alarm (daily)
	        final Intent refreshAuthTokenIntent = new Intent(MusubiIntentService.ACTION_AUTH_TOKEN_REFRESH);
	        refreshAuthTokenIntent.setClass(context, MusubiIntentService.class);
	        PendingIntent keyAlarmSender = PendingIntent.getService(context, 0, refreshAuthTokenIntent, 0);
	        am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME,
	        		currentTime + AlarmManager.INTERVAL_DAY,
	                AlarmManager.INTERVAL_DAY, keyAlarmSender);
	        
	        // Facebook friend refresh alarm (daily)
	        final Intent fbRefreshIntent = new Intent(MusubiIntentService.ACTION_FACEBOOK_REFRESH);
	        fbRefreshIntent.setClass(context, MusubiIntentService.class);
	        PendingIntent fbAlarmSender = PendingIntent.getService(context, 0, fbRefreshIntent, 0);
	        am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME,
	        		currentTime + AlarmManager.INTERVAL_DAY + TWENTY_MINUTES,
	                AlarmManager.INTERVAL_DAY, fbAlarmSender);

	        final Intent rdIntent = new Intent(MusubiIntentService.ACTION_ROLLING_DELETE);
	        rdIntent.setClass(context, MusubiIntentService.class);
	        PendingIntent rdAlarmSender = PendingIntent .getService(context, 0, rdIntent, 0);
	        am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME,
	        		currentTime + AlarmManager.INTERVAL_DAY + (TWENTY_MINUTES*2),
	                AlarmManager.INTERVAL_DAY, rdAlarmSender);	        

	        getContentResolver().notifyChange(MusubiService.WIZARD_STEP_TAKEN, null);
	        Handler process_starter = new Handler(getMainLooper());

	        final int ms = 1000;
	        int ticks = 0;
	        process_starter.postDelayed(new Runnable() {
	            @Override
	            public void run() {
	            	startService(refreshAuthTokenIntent);
	            }
	        }, ++ticks * ms);
	        process_starter.postDelayed(new Runnable() {
	            @Override
	            public void run() {
	                mMessageDecodeProcessor.dispatchChange(false);
	            }
	        }, ++ticks * ms);
	        process_starter.postDelayed(new Runnable() {
	            @Override
	            public void run() {
	                mMessageEncodeProcessor.dispatchChange(false);
	            }
	        }, ++ticks * ms);
	        process_starter.postDelayed(new Runnable() {
	            @Override
	            public void run() {
	                mPipelineProcessor.dispatchChange(false);
	            }
	        }, ++ticks * ms);
	        process_starter.postDelayed(new Runnable() {
	            @Override
	            public void run() {
	                mAddressBookUpdateHandler.dispatchChange(false);
	            }
	        }, ++ticks * ms);
	        process_starter.postDelayed(new Runnable() {
	            @Override
	            public void run() {
	                mProfilePushProcessor.dispatchChange(false);
	            }
	        }, ++ticks * ms);
	        process_starter.postDelayed(new Runnable() {
	            @Override
	            public void run() {
	            	startService(fbRefreshIntent);
	            }
	        }, ++ticks * ms);
	        process_starter.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mCorralUploadProcessor.dispatchChange(false);
                }
            }, ++ticks * ms);
	        process_starter.postDelayed(new Runnable() {
	            @Override
	            public void run() {
	            	startService(rdIntent);
	            }
	        }, ++ticks * ms);

	        Intent amqp_intent = new Intent(context, AMQPService.class);
	        startService(amqp_intent);
	        Intent app_update_intent = new Intent(context, AppUpdaterService.class);
	        startService(app_update_intent);
	        Intent sync_intent = new Intent(context, SyncService.class);
	        startService(sync_intent);
	        WebRenderService.bindAndSaveService(context);

	        return null;
	    }

	    @Override
	    protected void onPostExecute(Void result) {
	        if (mContentCorral != null) {
	            mContentCorral.start();
	        }
	    }
	}

	public void shutdownThreads() {
    	if(mMessageEncodeProcessor != null && mMessageEncodeProcessor.mThread != null) {
    		mMessageEncodeProcessor.mThread.getLooper().quit();
    		try {
				mMessageEncodeProcessor.mThread.join();
			} catch (InterruptedException e) {}
    		for (ProcessorThread t : mMessageEncodeProcessor.mProcessorThreads) {
    		    t.mLooper.quit();
    		    try {
                    t.join();
                } catch (InterruptedException e) {}
    		}
    	}
    	if(mMessageDecodeProcessor != null && mMessageDecodeProcessor.mThread != null) {
    		mMessageDecodeProcessor.mThread.getLooper().quit();
    		try {
	    		mMessageDecodeProcessor.mThread.join();
			} catch (InterruptedException e) {}
    	}
    	if(mPipelineProcessor != null && mPipelineProcessor.mThread != null) {
    		mPipelineProcessor.mThread.getLooper().quit();
    		try {
	    		mPipelineProcessor.mThread.join();
			} catch (InterruptedException e) {}
    	}
    	if(mProfilePushProcessor != null && mProfilePushProcessor.mThread != null) {
    		mProfilePushProcessor.mThread.getLooper().quit();
    		try {
	    		mProfilePushProcessor.mThread.join();
			} catch (InterruptedException e) {}
    	}
    	if (mCorralUploadProcessor != null && mCorralUploadProcessor.mThread != null) {
    	    mCorralUploadProcessor.mThread.getLooper().quit();
    	    try {
    	        mCorralUploadProcessor.mThread.join();
    	    } catch (InterruptedException e) {}
    	}
    	if(mWizardStepHandler != null && mWizardStepHandler.mThread != null) {
    		mWizardStepHandler.mThread.getLooper().quit();
    		try {
	    		mWizardStepHandler.mThread.join();
			} catch (InterruptedException e) {}
    	}
    	if(mKeyUpdateHandler != null && mKeyUpdateHandler.mThread != null) {
    		mKeyUpdateHandler.mThread.getLooper().quit();
    		try {
	    		mKeyUpdateHandler.mThread.join();
			} catch (InterruptedException e) {}
    	}
    	if(mAddressBookUpdateHandler != null && mAddressBookUpdateHandler.mThread != null) {
    		mAddressBookUpdateHandler.mThread.getLooper().quit();
    		try {
	    		mAddressBookUpdateHandler.mThread.join();
			} catch (InterruptedException e) {}
    	}
	}
}