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

package mobisocial.musubi.util;

import java.io.File;
import java.io.IOException;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.util.Log;

/**
 * ActivityCallouts will fail if the activity called for a result causes
 * the calling activity to be killed and recreated. Avoid this class,
 * especially for expensive activities like the camera.
 */
public class PhotoTaker implements ActivityCallout {
    private static final String TAG = "phototaker";
	private final ResultHandler mResultHandler;
	private final Context mContext;
	private final boolean mSnapshot;
	private final int mSize;

	@Deprecated
	public PhotoTaker(Context c, ResultHandler handler, int size, boolean snapshot) {
		mContext = c;
		mResultHandler = handler;
        mSnapshot = snapshot;
        mSize = size;
	}

	@Deprecated
	public PhotoTaker(Context c, ResultHandler handler) {
        this(c, handler, 80, false);
	}

	@Override
	public Intent getStartIntent() {
		final Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
		intent.putExtra(MediaStore.EXTRA_OUTPUT,
                        Uri.fromFile(getTempFile(mContext)));
		return intent;
	}

	@Override
	public void handleResult(int resultCode, Intent resultData) {
		if (resultCode != Activity.RESULT_OK) {
			return;
		}

		final File file;
		final File path = new File(Environment.getExternalStorageDirectory(),
                                   mContext.getPackageName());
		if (!path.exists()) {
			path.mkdir();
		}

		file = getTempFile(mContext);
		mResultHandler.onResult(Uri.fromFile(file));
	}
	public interface ResultHandler {
		public void onResult(Uri imageUri);
	}

	public static File getTempFile(Context context) {
		// it will return /sdcard/image.tmp
		final File path = new File(Environment.getExternalStorageDirectory(),
                                   context.getPackageName());
		if (!path.exists()) {
			path.mkdir();
		}
		return new File(path, "image.tmp");
	}

	public static float rotationForImage(Context context, Uri uri) {
	    if (uri.getScheme().equals("content")) {
            String[] projection = { Images.ImageColumns.ORIENTATION };
            Cursor c = context.getContentResolver().query(
                    uri, projection, null, null, null);
            try {
	            if (c.moveToFirst()) {
	                return c.getInt(0);
	            }
            } finally {
            	c.close();
            }
        } else if (uri.getScheme().equals("file")) {
            try {
                ExifInterface exif = new ExifInterface(uri.getPath());
                int rotation = (int) PhotoTaker.exifOrientationToDegrees(
                        exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_NORMAL));
                return rotation;
            } catch (IOException e) {
                Log.e(TAG, "Error checking exif", e);
            }
        }
	    return 0f;
	}

	private static float exifOrientationToDegrees(int exifOrientation) {
        if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90) {
            return 90;
        } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_180) {
            return 180;
        } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) {
            return 270;
        }
        return 0;
    }
}
