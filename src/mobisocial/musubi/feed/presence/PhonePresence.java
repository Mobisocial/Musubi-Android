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

package mobisocial.musubi.feed.presence;

import mobisocial.musubi.Helpers;
import mobisocial.musubi.feed.iface.FeedPresence;
import mobisocial.musubi.objects.PhoneStateObj;
import mobisocial.musubi.objects.StatusObj;
import mobisocial.musubi.ui.MusubiBaseActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.telephony.TelephonyManager;
import android.widget.Toast;

/**
 * Sends notice of received/sent phone calls.
 *
 */
public class PhonePresence extends FeedPresence {
    private boolean mSharePhoneState = false;
    private static String sPhoneNumber;

    @Override
    public String getName() {
        return "Phone";
    }

    @Override
    public void onPresenceUpdated(final Context context, final Uri feedUri, boolean present) {
        if (mSharePhoneState) {
            if (getFeedsWithPresence().size() == 0) {
                context.getApplicationContext().unregisterReceiver(mReceiver);
                Toast.makeText(context, "No longer sharing phone state", Toast.LENGTH_SHORT).show();
                mSharePhoneState = false;
            }
        } else {
            if (getFeedsWithPresence().size() > 0) {
                IntentFilter iF = new IntentFilter();
                iF.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
                iF.addAction(Intent.ACTION_NEW_OUTGOING_CALL);
                iF.setPriority(0);
                context.getApplicationContext().registerReceiver(mReceiver, iF);
                Toast.makeText(context, "Now sharing phone state", Toast.LENGTH_SHORT).show();
                mSharePhoneState = true;
            }
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            if (Intent.ACTION_NEW_OUTGOING_CALL.equals(intent.getAction())) {
                sPhoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
                return;
            } else if (intent.hasExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)) {
                sPhoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
            }
            if (mSharePhoneState &&
                    TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) {
                String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
                String who = sPhoneNumber;
                if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
                    sPhoneNumber = null;
                }
                for (Uri feedUri : getFeedsWithPresence()) {
                    Helpers.sendToFeed(context.getApplicationContext(), PhoneStateObj.from(state, who), feedUri);
                }
            }
        }
    };
}
