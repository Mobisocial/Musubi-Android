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
import android.widget.ListAdapter;

public abstract class FeedProcessor {
    public final LinkedHashSet<Uri> mActiveFeeds = new LinkedHashSet<Uri>();

    public abstract String getName();

    public abstract ListAdapter getListAdapter(Context context, Uri feedUri);
}
