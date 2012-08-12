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

import mobisocial.musubi.BJDNotImplementedException;
import mobisocial.musubi.Helpers;
import mobisocial.musubi.feed.iface.DbEntryHandler;
import mobisocial.musubi.feed.iface.FeedPresence;
import mobisocial.musubi.obj.handler.ObjHandler;
import mobisocial.musubi.objects.StatusObj;
import mobisocial.socialkit.SignedObj;
import mobisocial.socialkit.musubi.DbObj;
import mobisocial.socialkit.obj.MemObj;
import android.content.Context;
import android.net.Uri;
import android.widget.Toast;

/**
 * Drop messages. Good for canning spam.
 *
 */
public class DropMessagesPresence extends FeedPresence {
    private boolean mDropMessages = false;
    private static DropMessagesPresence sInstance;

    // TODO: proper singleton.
    public static DropMessagesPresence getInstance() {
        if (sInstance == null) {
            sInstance = new DropMessagesPresence();
        }
        return sInstance;
    }

    private DropMessagesPresence() {}

    @Override
    public String getName() {
        return "Drop Messages";
    }

    @Override
    public void onPresenceUpdated(final Context context, final Uri feedUri, boolean present) {
        if (mDropMessages) {
            if (getFeedsWithPresence().size() == 0) {
                Toast.makeText(context, "No longer ignoring your friends.", Toast.LENGTH_SHORT).show();
                mDropMessages = false;
            }
        } else {
            if (getFeedsWithPresence().size() > 0) {
                Toast.makeText(context, "Now ignoring your friends.", Toast.LENGTH_SHORT).show();
                mDropMessages = true;
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
            while (mDropMessages) {
                try {
                    Thread.sleep(WAIT_TIME);
                } catch (InterruptedException e) {}

                if (!mDropMessages) {
                    break;
                }

                Helpers.sendToFeeds(mContext, obj, getFeedsWithPresence());
            }
        }
    }

    public static class MessageDropHandler extends ObjHandler {
        @Override
        public boolean handleObjFromNetwork(Context context, SignedObj obj) {
            BJDNotImplementedException.except("TODO: Get URI for feed for SignedObj");
            Uri feedUri = null;
            return !getInstance().getFeedsWithPresence().contains(feedUri);
        }

        @Override
        public void afterDbInsertion(Context context, DbEntryHandler typeInfo, DbObj obj) {
        }
    }
}
