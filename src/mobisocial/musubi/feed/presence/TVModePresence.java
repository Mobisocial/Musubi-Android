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

import mobisocial.musubi.feed.iface.Activator;
import mobisocial.musubi.feed.iface.DbEntryHandler;
import mobisocial.musubi.feed.iface.FeedPresence;
import mobisocial.musubi.obj.handler.IObjHandler;
import mobisocial.socialkit.SignedObj;
import mobisocial.socialkit.musubi.DbObj;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

/**
 * Automatically launches all openable content.
 */
public class TVModePresence extends FeedPresence implements IObjHandler {
    private static final String TAG = "interrupt";
    private boolean mInterrupt = false;
    private static TVModePresence sInstance;

    private TVModePresence() {

    }

    @Override
    public String getName() {
        return "TV Mode";
    }

    @Override
    public void onPresenceUpdated(final Context context, final Uri feedUri, boolean present) {
        mInterrupt = getFeedsWithPresence().size() > 0;
    }

    public static TVModePresence getInstance() {
        if (sInstance == null) {
            sInstance = new TVModePresence();
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
        if (mInterrupt && getFeedsWithPresence().contains(feedUri)) {
            if (typeInfo instanceof Activator) {
                if (DBG) Log.d(TAG, "activating via tv mode");
                ((Activator) typeInfo).activate(context, null);
            }
        }
    }
}
