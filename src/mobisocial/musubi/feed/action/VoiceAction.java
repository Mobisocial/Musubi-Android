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

package mobisocial.musubi.feed.action;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import mobisocial.musubi.R;
import mobisocial.musubi.VoiceRecordActivity;
import mobisocial.musubi.feed.iface.FeedAction;
import mobisocial.musubi.ui.fragments.FeedViewFragment;

/**
 * Record a voice note to share with a feed.
 *
 */
public class VoiceAction extends FeedAction { // TODO: Move to VoiceObj implements FeedAction

    @Override
    public String getName() {
        return "Voice";
    }

    @Override
    public Drawable getIcon(Context c) {
        return c.getResources().getDrawable(R.drawable.ic_attach_capture_audio_holo_light);
    }

    @Override
    public void onClick(Context context, Uri feedUri) {
        Intent record = new Intent();
        record.setClass(context, VoiceRecordActivity.class);
        record.putExtra(FeedViewFragment.ARG_FEED_URI, feedUri);
        context.startActivity(record);
    }

    @Override
    public boolean isActive(Context c) {
        return true;
    }
}
