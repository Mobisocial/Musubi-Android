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

import mobisocial.musubi.feed.iface.DbEntryHandler;
import mobisocial.musubi.feed.iface.FeedPresence;
import mobisocial.musubi.obj.handler.IObjHandler;
import mobisocial.musubi.objects.VoiceObj;
import mobisocial.socialkit.SignedObj;
import mobisocial.socialkit.musubi.DbObj;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

/**
 * Automatically plays back audio clips as they are received.
 *
 */
public class Push2TalkPresence extends FeedPresence implements IObjHandler {
    private static final String TAG = "push2talk";
    private boolean mEnabled = false;
    private static Push2TalkPresence sInstance;

    private Push2TalkPresence() {

    }

    @Override
    public String getName() {
        return "Push2Talk";
    }

    @Override
    public void onPresenceUpdated(final Context context, final Uri feedUri, boolean present) {
        mEnabled = getFeedsWithPresence().size() > 0;
    }

    public static Push2TalkPresence getInstance() {
        if (sInstance == null) {
            sInstance = new Push2TalkPresence();
        }
        return sInstance;
    }

    @Override
    public boolean handleObjFromNetwork(Context context, SignedObj obj) {
        return true;
    }

    @Override
    public void afterDbInsertion(Context context, DbEntryHandler typeInfo, DbObj obj) {
        Uri feedUri = obj.getContainingFeed().getUri();
        if (!mEnabled || !getFeedsWithPresence().contains(feedUri) ||
                !(typeInfo instanceof VoiceObj)) {
            return;
        }

        if (DBG) Log.d(TAG, "Playing audio via push2talk on " + feedUri);
        ((VoiceObj) typeInfo).activate(context, null);
    }

    public boolean isOnCall() {
        return mEnabled;
    }
}
