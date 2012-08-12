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

package mobisocial.musubi.obj.handler;

import mobisocial.musubi.App;
import mobisocial.musubi.feed.iface.DbEntryHandler;
import mobisocial.musubi.feed.iface.FeedRenderer;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.PresenceAwareNotify;
import mobisocial.musubi.model.helpers.FeedManager;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.obj.ObjHelpers;
import mobisocial.musubi.objects.PictureObj;
import mobisocial.musubi.objects.StatusObj;
import mobisocial.musubi.objects.VoiceObj;
import mobisocial.socialkit.musubi.DbObj;
import mobisocial.socialkit.musubi.Musubi;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

/**
 * Handles notifications associated with a received obj.
 * First, we check with the sender application to allow it to handle this data.
 * If the application does not indicate that we should not notify the user,
 * we check to see if the obj should be auto activated (for example, in tv mode).
 * Finally, we send a standard notification.
 *
 * An application prevents a notification event by setting the result data
 * to RESULT_CANCELLED (setResultCode(Activity.RESULT_CANCELLED)).
 *
 */
public class NotificationHandler {
    String TAG = "NotificationObjHandler";
    private static final int NO_NOTIFY = 0;
    private static final int NOTIFY = 1;
    private static final int AUTO_ACTIVATE = 2;

    private static final String ACTION_DATA_RECEIVED = "mobisocial.intent.action.DATA_RECEIVED";
    private static final String EXTRA_NOTIFICATION = "notification";
    private static final String EXTRA_OBJ_URI = "objUri";

    private final AutoActivateObjHandler mAutoActivate = new AutoActivateObjHandler();
    final Context mContext;
	private Musubi mMusubi;
	private IdentitiesManager mIdentitiesManager;
	
    public NotificationHandler(Context context) {
        mContext = context;
        mMusubi = App.getMusubi(context);
        SQLiteOpenHelper helper = App.getDatabaseSource(mContext);
        mIdentitiesManager = new IdentitiesManager(helper);
    }

    BroadcastReceiver mAppHandler = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (getResultCode() != Activity.RESULT_OK) {
                return;
            }

            int notification = intent.getExtras().getInt(EXTRA_NOTIFICATION);
            Uri objUri = intent.getExtras().getParcelable(EXTRA_OBJ_URI);
            DbObj obj = mMusubi.objForUri(objUri);
            //this deals with non-isolated namespaces in the testing framework. :(
            if (obj == null)
            	return;
            
            //don't notify if the sender is not whitelisted
            MIdentity sender = mIdentitiesManager.getIdentityForId(obj.getSenderId());
            if(sender == null) {
            	Log.w(TAG, "obj has no longer valid sending identity? " + obj.getSenderId());
            	return;
            }
            if (!sender.whitelisted_) {
            	return;
            }
            
            if (notification == AUTO_ACTIVATE) {
                // Auto-activate without notification.
                DbEntryHandler handler = ObjHelpers.forType(obj.getType());
                mAutoActivate.afterDbInsertion(context, handler, obj);
            } else if (notification == NOTIFY) {
            	if (obj.getContainingFeed() != null) {
	                Uri feedUri = obj.getContainingFeed().getUri();
	                Intent launch = FeedManager.getViewingIntent(context, feedUri);
	                String msgText = null;
	                String defaultText = "New Musubi message";
	                String name = obj.getSender().getName();

	                String type = obj.getType();
	                if (StatusObj.TYPE.equals(type)) {
	                    try {
	                        msgText = obj.getJson().getString(StatusObj.TEXT);
	                        if (name != null) {
	                            msgText = name + ": " + msgText;
	                        }
	                    } catch (Exception e) {
	                    }
	                } else if (PictureObj.TYPE.equals(type)) {
	                    msgText = "New picture from " + name;
	                } else if (VoiceObj.TYPE.equals(type)) {
	                    msgText = "New voice message from " + name;
	                }

	                if (msgText == null) {
	                    msgText = defaultText + " from " + name;
	                }

                    if (Build.VERSION.SDK_INT < 11) {
                        launch.setFlags (Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    } else { 
                        launch.setFlags (Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                    }

                    PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
                            launch, PendingIntent.FLAG_CANCEL_CURRENT);
                    (new PresenceAwareNotify(context)).notify("New Musubi message",
                            msgText, contentIntent, feedUri);
            	} else {
            		Log.e(TAG, "No containing feed found for " + obj.toString());
            	}
            }
        }
    };

    public void handle(DbEntryHandler handler, boolean fromOwnedIdentity, DbObj obj) {
        int notification = NOTIFY;
        
        if (fromOwnedIdentity) { 
            notification = NO_NOTIFY;
        }

        if (handler == null || !(handler instanceof FeedRenderer)) {
            notification = NO_NOTIFY;
        }

        if (mAutoActivate.willActivate(mContext, obj)) {
            notification = AUTO_ACTIVATE;
        }

        if (!handler.doNotification(mContext, obj)) {
            notification = NO_NOTIFY;
        }

        // Let applications handle their own messages
        Intent objReceived = new Intent(ACTION_DATA_RECEIVED);
        objReceived.setPackage(obj.getAppId());
        objReceived.putExtra(EXTRA_NOTIFICATION, notification);
        objReceived.putExtra(EXTRA_OBJ_URI, obj.getUri());

        Bundle initialExtras = null;
        int initialCode = Activity.RESULT_OK;
        String initialData = null;
        Handler scheduler = null;
        String receiverPermission = null;
        mContext.sendOrderedBroadcast(objReceived, receiverPermission, mAppHandler, scheduler,
                initialCode, initialData, initialExtras);
    }
}
