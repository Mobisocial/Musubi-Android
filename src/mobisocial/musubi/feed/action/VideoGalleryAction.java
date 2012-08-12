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

import java.io.IOException;

import mobisocial.musubi.Helpers;
import mobisocial.musubi.R;
import mobisocial.musubi.feed.iface.FeedAction;
import mobisocial.musubi.objects.VideoObj;
import mobisocial.musubi.util.ActivityCallout;
import mobisocial.musubi.util.InstrumentedActivity;
import mobisocial.socialkit.Obj;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

/**
 * Adds a VideoObj to a feed from an external Android application
 * such as the Gallery.
 *
 */
public class VideoGalleryAction extends FeedAction {
    private static final String TAG = "VideoGalleryAction";

    @Override
    public String getName() {
        return "Video";
    }

    @Override
    public Drawable getIcon(Context c) {
        return c.getResources().getDrawable(R.drawable.ic_attach_capture_video_holo_light);
    }

    @Override
    public void onClick(final Context context, final Uri feedUri) {
        ((InstrumentedActivity)context).doActivityForResult(new GalleryCallout(context, feedUri));
    }

    @Override
    public boolean isActive(Context c) {
        return true;
    }

    class GalleryCallout implements ActivityCallout {
        private final Context mmContext;
        private final Uri mmFeedUri;

        private GalleryCallout(Context context, Uri feedUri) {
            mmContext = context;
            mmFeedUri = feedUri;
        }

        @Override
        public void handleResult(int resultCode, final Intent data) {
            if (resultCode == Activity.RESULT_OK) {
                try {
                    // Files are uploaded to the Corral by virtue of the localUri field.
                    Obj outboundObj = VideoObj.from(mmContext, data.getData(), data.getType());
                    Helpers.sendToFeed(mmContext, outboundObj, mmFeedUri);
                    Helpers.emailUnclaimedMembers(mmContext, outboundObj, mmFeedUri);
                } catch (IOException e) {
                    Toast.makeText(mmContext, "Error fetching video.", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error fetching video", e);
                }
            }
        }

        @Override
        public Intent getStartIntent() {
            Intent gallery = new Intent(Intent.ACTION_GET_CONTENT);
            gallery.setType("video/*");
            return Intent.createChooser(gallery, null);
        }
    };
}
