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
import mobisocial.musubi.objects.FileObj;
import mobisocial.musubi.util.ActivityCallout;
import mobisocial.musubi.util.InstrumentedActivity;
import mobisocial.socialkit.Obj;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

/**
 * Adds a FileObj to a feed from an external Android application
 * such as the Gallery.
 *
 */
public class FileGalleryAction extends FeedAction {
    private static final String TAG = "FileGalleryAction";

    @Override
    public String getName() {
        return "File";
    }

    @Override
    public Drawable getIcon(Context c) {
        return c.getResources().getDrawable(R.drawable.ic_menu_upload);
    }

    @Override
    public void onClick(final Context context, final Uri feedUri) {
        ((InstrumentedActivity)context).doActivityForResult(new GalleryCallout(context, feedUri));
    }

    @Override
    public boolean isActive(Context c) {
    	return true;
//        return MusubiBaseActivity.isDeveloperModeEnabled(c);
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
                new SendFileTask(data).execute();
            }
        }

        @Override
        public Intent getStartIntent() {
            Intent gallery = new Intent(Intent.ACTION_GET_CONTENT);
    		gallery.setType("*/*");
    		gallery.addCategory(Intent.CATEGORY_OPENABLE);
            return Intent.createChooser(gallery, null);
        }

        class SendFileTask extends AsyncTask<Void, Void, Boolean> {
            private final Intent mmmData;
            private Exception mmmException;

            SendFileTask(Intent data) {
                mmmData = data;
            }

            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    Obj outboundObj = FileObj.from(mmContext, mmmData.getData());
                    Helpers.sendToFeed(mmContext, outboundObj, mmFeedUri);
                    Helpers.emailUnclaimedMembers(mmContext, outboundObj, mmFeedUri);
                    return true;
                } catch (IOException e) {
                    mmmException = e;
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean result) {
                if (!result) {
                    Toast.makeText(mmContext, "File too large.", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error reading file.", mmmException);
                }
            }
        }
    };
}
