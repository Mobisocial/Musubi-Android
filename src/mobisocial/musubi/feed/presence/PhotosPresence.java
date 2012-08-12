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
import mobisocial.musubi.objects.PictureObj;
import mobisocial.socialkit.obj.MemObj;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.widget.Toast;

/**
 * Automatically share photos that have been captured with the default camera.
 *
 */
public class PhotosPresence extends FeedPresence {
    private static final String TAG = "livephotos";
    private boolean mSharePhotos = false;
    private PhotoContentObserver mPhotoObserver;

    @Override
    public String getName() {
        return "Photos";
    }

    @Override
    public void onPresenceUpdated(final Context context, final Uri feedUri, boolean present) {
        if (mSharePhotos) {
            if (getFeedsWithPresence().size() == 0) {
                Toast.makeText(context, "No longer sharing photos", Toast.LENGTH_SHORT).show();
                context.getContentResolver().unregisterContentObserver(mPhotoObserver);
                mSharePhotos = false;
                mPhotoObserver = null;
            }
        } else {
            if (getFeedsWithPresence().size() > 0) {
                mSharePhotos = true;
                mPhotoObserver = new PhotoContentObserver(context);
                context.getContentResolver().registerContentObserver(
                        Images.Media.EXTERNAL_CONTENT_URI, true, mPhotoObserver);
                Toast.makeText(context, "Now sharing new photos", Toast.LENGTH_SHORT).show();
            }
        }
    }

    class PhotoContentObserver extends ContentObserver {
        private final Context mmContext;
        private Uri mLastShared;

        public PhotoContentObserver(Context context) {
            super(new Handler(context.getMainLooper()));
            mmContext = context;
        }

        public void onChange(boolean selfChange) {
            if (mSharePhotos) {
                try {
                    Uri photo = getLatestCameraPhoto();
                    if (photo == null || photo.equals(mLastShared)) {
                        return;
                    }
                    mLastShared = photo;
                    MemObj obj = PictureObj.from(mmContext, photo, true);
                    for (Uri uri : getFeedsWithPresence()) {
                        Helpers.sendToFeed(mmContext, obj, uri);
                    }
                } catch (IOException e) {}
            }
        };

        private Uri getLatestCameraPhoto() {
            String selection = ImageColumns.BUCKET_DISPLAY_NAME + " = 'Camera'";
            String[] selectionArgs = null;
            String sort = ImageColumns._ID + " DESC LIMIT 1";
            Cursor c =
                android.provider.MediaStore.Images.Media.query(mmContext.getContentResolver(),
                        Images.Media.EXTERNAL_CONTENT_URI,
                        new String[] { ImageColumns._ID }, selection, selectionArgs, sort );
            try {
	
	            int idx = c.getColumnIndex(ImageColumns._ID);
	            if (c.moveToFirst()) {
	                return Uri.withAppendedPath(Images.Media.EXTERNAL_CONTENT_URI, c.getString(idx));
	            }
	            return null;
            } finally {
            	c.close();
            }
        }
    };
}
