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

package mobisocial.musubi.feed.iface;

import java.util.LinkedHashSet;

import android.content.Context;
import android.net.Uri;

/**
 * Base class for long-lasting activities associated with a feed.
 *
 */
public abstract class FeedPresence {
    public final LinkedHashSet<Uri> mActiveFeeds = new LinkedHashSet<Uri>();
    public abstract String getName();
    protected static String TAG = "feedPresence";
    protected final static boolean DBG = true;

    public final void setFeedPresence(Context context, Uri feed, boolean present) {
        if (present) {
            mActiveFeeds.add(feed);
        } else {
            mActiveFeeds.remove(feed);
        }
        onPresenceUpdated(context, feed, present);
    }

    protected abstract void onPresenceUpdated(Context context, Uri feed, boolean present);

    public final LinkedHashSet<Uri> getFeedsWithPresence() {
        return mActiveFeeds;
    }

    public boolean isPresent(Uri feedUri) {
        return mActiveFeeds.contains(feedUri);
    }

    @Override
    public String toString() {
        return getName();
    }

    public boolean isActive() {
        return true;
    }
}
