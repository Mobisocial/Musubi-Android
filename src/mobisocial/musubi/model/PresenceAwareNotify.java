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

package mobisocial.musubi.model;

import mobisocial.musubi.App;
import mobisocial.musubi.R;
import mobisocial.musubi.feed.presence.Push2TalkPresence;
import mobisocial.musubi.ui.MusubiBaseActivity;
import mobisocial.musubi.ui.SettingsActivity;
import mobisocial.musubi.ui.fragments.SettingsFragment;
import android.app.KeyguardManager.KeyguardLock;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.PowerManager;

public class PresenceAwareNotify {
    private static final String TAG = "PresenceAwareNotify";
	public static final int NOTIFY_ID = 9847184;
	private NotificationManager mNotificationManager;
	private final long[] VIBRATE = new long[] {0, 250, 80, 100, 80, 80, 80, 250};
	Context mContext;


    public PresenceAwareNotify(Context context) {
        mContext = context;
        mNotificationManager = (NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    public void notify(String notificationTitle, String notificationMsg,
            PendingIntent contentIntent) {
        notify(notificationTitle, notificationMsg, contentIntent, null);
    }

    public void notify(String notificationTitle, String notificationMsg,
            PendingIntent contentIntent, Uri feedUri) {
    	boolean doAlert = true;
        if (mContext.getSharedPreferences("main", 0).getBoolean("autoplay", false)) {
            return;
        }

        if (MusubiBaseActivity.isResumed()) {
            Uri currentUri = App.getCurrentFeed();
            if (currentUri != null && currentUri.equals(feedUri)) {
                return;
            }
        }
                
        if (Push2TalkPresence.getInstance().isOnCall()) {
        	doAlert = false;
        }

        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        if (pm.isScreenOn()) {
            doAlert = false;
        }

        Notification notification = new Notification(
            R.drawable.icon, notificationMsg, System.currentTimeMillis());        

        notification.setLatestEventInfo(
            mContext, 
            notificationTitle, 
            notificationMsg, 
            contentIntent);
        notification.flags = Notification.FLAG_ONLY_ALERT_ONCE|Notification.FLAG_AUTO_CANCEL;

        if (doAlert) {
            SharedPreferences settings = mContext.getSharedPreferences(SettingsActivity.PREFS_NAME, 0);
            String uri = settings.getString(SettingsActivity.PREF_RINGTONE, null);
            boolean vibrating = settings.getBoolean(SettingsFragment.PREF_VIBRATING, SettingsFragment.PREF_VIBRATING_DEFAULT);

            if (vibrating) {
                notification.vibrate = VIBRATE;
            }
            if(!uri.equals("none")) {
            	notification.sound = Uri.parse(uri);
            }
            notification.flags |= Notification.FLAG_SHOW_LIGHTS;
            notification.ledARGB = 0xff0022ff;
            notification.ledOnMS = 500;
            notification.ledOffMS = 2500;

        }
        mNotificationManager.notify(NOTIFY_ID, notification);
    }

    public void cancelAll() {
        mNotificationManager.cancel(NOTIFY_ID);
    }
}