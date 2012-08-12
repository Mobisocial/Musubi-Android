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

import java.io.IOException;

import mobisocial.musubi.Helpers;
import mobisocial.musubi.feed.iface.FeedPresence;
import mobisocial.musubi.objects.VideoObj;
import mobisocial.socialkit.Obj;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore.Video;
import android.provider.MediaStore.Video.VideoColumns;
import android.widget.Toast;

public class VideosPresence extends FeedPresence {
    private static final String TAG = "livevideos";
    private boolean mShareVideos = false;
    private VideoContentObserver mVideoObserver;

    @Override
    public String getName() {
        return "Videos";
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public void onPresenceUpdated(final Context context, final Uri feedUri, boolean present) {
        if (mShareVideos) {
            if (getFeedsWithPresence().size() == 0) {
                Toast.makeText(context, "No longer sharing videos", Toast.LENGTH_SHORT).show();
                context.getContentResolver().unregisterContentObserver(mVideoObserver);
                mShareVideos = false;
                mVideoObserver = null;
            }
        } else {
            if (getFeedsWithPresence().size() > 0) {
                mShareVideos = true;
                mVideoObserver = new VideoContentObserver(context);
                context.getContentResolver().registerContentObserver(
                        Video.Media.EXTERNAL_CONTENT_URI, true, mVideoObserver);
                Toast.makeText(context, "Now sharing new videos", Toast.LENGTH_SHORT).show();
            }
        }
    }

    class VideoContentObserver extends ContentObserver {
        private final Context mmContext;
        private Uri mLastShared;

        public VideoContentObserver(Context context) {
            super(new Handler(context.getMainLooper()));
            mmContext = context;
        }

        public void onChange(boolean selfChange) {
            if (mShareVideos) {
                try {
                    Uri video = getLatestVideo();
                    if (video == null || video.equals(mLastShared)) {
                        return;
                    }
                    mLastShared = video;
                    Obj obj = VideoObj.from(mmContext, video, null);
                    for (Uri uri : getFeedsWithPresence()) {
                        Helpers.sendToFeed(mmContext, obj, uri);
                    }
                } catch (IOException e) {}
            }
        };

        private Uri getLatestVideo() {
            Cursor c =
                android.provider.MediaStore.Video.query(mmContext.getContentResolver(),
                        Video.Media.EXTERNAL_CONTENT_URI,
                        new String[] { VideoColumns._ID });

            try {
	            int idx = c.getColumnIndex(VideoColumns._ID);
	            if (c.moveToLast()) {
	                return Uri.withAppendedPath(Video.Media.EXTERNAL_CONTENT_URI, c.getString(idx));
	            }
	            return null;
            } finally {
            	c.close();
            }
        }
    };
}
