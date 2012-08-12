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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import mobisocial.musubi.Helpers;
import mobisocial.musubi.R;
import mobisocial.musubi.feed.iface.FeedAction;
import mobisocial.musubi.objects.PictureObj;
import mobisocial.musubi.ui.FeedPannerActivity;
import mobisocial.musubi.ui.LatestPictureActivity;
import mobisocial.musubi.util.ActivityCallout;
import mobisocial.musubi.util.InstrumentedActivity;
import mobisocial.socialkit.obj.MemObj;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LabeledIntent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

/**
 * Adds a PictureObj to a feed from an external Android application
 * such as the Gallery.
 *
 */
public class GalleryAction extends FeedAction {
    private static final String TAG = "GalleryAction";

    @Override
    public String getName() {
        return "Gallery";
    }

    @Override
    public Drawable getIcon(Context c) {
        return c.getResources().getDrawable(R.drawable.ic_attach_picture_holo_light);
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
                new Thread() {
                    @Override
                    public void run() {
                        try {
                        	Uri uri = data.getData();
                            // TODO: mimeType; local_uri = data.toString();
                            MemObj outboundObj = PictureObj.from(mmContext, uri, true);
                            Helpers.sendToFeed(mmContext, outboundObj, mmFeedUri);
                            Helpers.emailUnclaimedMembers(mmContext, outboundObj, mmFeedUri);
                        } catch (IOException e) {
                            Toast.makeText(mmContext, "Error reading photo data.", Toast.LENGTH_SHORT).show();
                            Log.e(TAG, "Error reading photo data.", e);
                        }
                    }
                }.start();
            }
        }

        @Override
        public Intent getStartIntent() {
            Intent gallery = new Intent(Intent.ACTION_GET_CONTENT);
            gallery.setType("image/*");
            Intent chooser = Intent.createChooser(gallery, "Choose image...");

            List<Intent> more = new ArrayList<Intent>();
            File pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            File screenshots = new File(pictures, "Screenshots");
            if (screenshots.isDirectory()) {
                Intent screenie = new Intent("mobisocial.musubi.LATEST_SCREENSHOT");
                screenie.setClass(mmContext, LatestPictureActivity.class);
                //screenie.setClass(mmContext, LatestPictureActivity.class);
                screenie.putExtra(LatestPictureActivity.EXTRA_BUCKET, LatestPictureActivity.BUCKET_SCREENSHOTS);
                Intent labeled = new LabeledIntent(screenie, mmContext.getPackageName(), "Latest Screenshot", R.drawable.ic_launcher_gallery);
                more.add(labeled);
            }

            Intent shareLatest = new Intent(Intent.ACTION_GET_CONTENT);
            shareLatest.setType("image/*");
            shareLatest.setPackage("com.bjdodson.sharelatestphoto");
            ResolveInfo info = mmContext.getPackageManager().resolveActivity(
                    shareLatest, PackageManager.MATCH_DEFAULT_ONLY);
            if (info == null) {
                Intent camera = new Intent("mobisocial.musubi.LATEST_CAMERA");
                camera.setClass(mmContext, LatestPictureActivity.class);
                camera.putExtra(LatestPictureActivity.EXTRA_BUCKET, LatestPictureActivity.BUCKET_CAMERA);
                Intent labeled = new LabeledIntent(camera, mmContext.getPackageName(), "Latest from Camera", R.drawable.ic_launcher_camera);
                more.add(labeled);
            }

            if (more.size() > 0) {
                chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, more.toArray(new Intent[more.size()]));
            }
            return chooser;
        }
    };
}
