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

package mobisocial.musubi;

import mobisocial.musubi.feed.presence.Push2TalkPresence;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;

public class RemoteControlReceiver extends BroadcastReceiver {
    private static final String TAG = "msb-remoteReceiver";
    private static final boolean DBG = false;
    private static SpecialKeyEventHandler sSpecialKeyEventHandler;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
            handleSpecialButton(context, (KeyEvent)intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT));
        }
    }

    public void handleSpecialButton(Context context, KeyEvent event) {
        if (DBG) Log.d(TAG, "Special key event received: " + event.getAction());
        if (sSpecialKeyEventHandler != null) {
            if (DBG) Log.d(TAG, "Trying registered handler");
            if (sSpecialKeyEventHandler.onSpecialKeyEvent(event)) {
                if (DBG) Log.d(TAG, "Key event consumed by handler");
                return;
            }
        }

        if (DBG) Log.d(TAG, "Default special key handler");
        if (event.getKeyCode() == KeyEvent.KEYCODE_HEADSETHOOK &&
                event.getAction() == KeyEvent.ACTION_DOWN) {
            if (Push2TalkPresence.getInstance().isOnCall()) {
                Intent record = new Intent();
                record.setClass(context, VoiceRecordActivity.class);
                record.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                record.putExtra("presence_mode", true);
                context.startActivity(record);
            }
        }
    }

    public interface SpecialKeyEventHandler {
        public boolean onSpecialKeyEvent(KeyEvent event);
    }

    public static void setSpecialKeyEventHandler(SpecialKeyEventHandler h) {
        sSpecialKeyEventHandler = h;
    }

    public static void clearSpecialKeyEventHandler() {
        sSpecialKeyEventHandler = null;
    }
}