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
import mobisocial.musubi.model.MObject;
import mobisocial.musubi.objects.StatusObj;
import mobisocial.socialkit.obj.MemObj;
import android.content.Context;
import android.net.Uri;
import android.widget.Toast;

/**
 * Sends messages rapidly. For testing, not annoying friends!
 *
 */
public class SpamPresence extends FeedPresence {
    private boolean mShareSpam = false;
    private SpamThread mSpamThread;

    @Override
    public String getName() {
        return "Spam";
    }

    @Override
    public void onPresenceUpdated(final Context context, final Uri feedUri, boolean present) {
        if (mShareSpam) {
            if (getFeedsWithPresence().size() == 0) {
                Toast.makeText(context, "No longer spamming", Toast.LENGTH_SHORT).show();
                mShareSpam = false;
                mSpamThread = null;
            }
        } else {
            if (getFeedsWithPresence().size() > 0) {
                Toast.makeText(context, "Now spamming your friends", Toast.LENGTH_SHORT).show();
                mShareSpam = true;
                mSpamThread = new SpamThread(context);
                mSpamThread.start();
            }
        }
    }

    class SpamThread extends Thread {
        long WAIT_TIME = 500;
        final Context mContext;

        public SpamThread(Context context) {
            mContext = context;
        }

        @Override
        public void run() {
            MemObj obj = StatusObj.from("StatusObj spam, StatusObj spam.");
            while (mShareSpam) {
                try {
                    Thread.sleep(WAIT_TIME);
                } catch (InterruptedException e) {}

                if (!mShareSpam) {
                    break;
                }

                Helpers.sendToFeeds(mContext, obj, getFeedsWithPresence());
            }
        }
    }
}
