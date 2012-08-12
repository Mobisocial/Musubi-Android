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
import mobisocial.musubi.ui.fragments.FeedListFragment.FeedSummary;
import mobisocial.musubi.ui.widget.DbObjCursorAdapter.DbObjCursor;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * An interface for {@link DbEntryHandler} objects that have a 
 * visible entry in a feed.
 */
public interface FeedRenderer {
	public void render(Context context, View view, DbObjCursor obj, boolean allowInteractions) throws Exception;
	public View createView(Context context, ViewGroup frame);
	public void getSummaryText(Context context, TextView view, FeedSummary summary);
}
