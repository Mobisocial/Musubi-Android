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
import mobisocial.musubi.objects.PictureObj;
import mobisocial.musubi.objects.VideoObj;
import mobisocial.musubi.service.WizardStepHandler;
import mobisocial.musubi.ui.MusubiBaseActivity;
import mobisocial.socialkit.Obj;
import mobisocial.socialkit.musubi.Musubi;

import org.mobisocial.corral.ContentCorral;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

/**
 * Captures an image to share with a feed.
 *
 */
public class CamcorderAction extends FeedAction {
    private static final String TAG = "CamcorderAction";
    private static final int REQUEST_CAPTURE_MEDIA = 9413;
    private Uri mFeedUri;

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
        mFeedUri = feedUri;

        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        //intent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, 1024*1024*30);
        intent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 45);
        intent.putExtra(Musubi.EXTRA_FEED_URI, feedUri);
        startActivityForResult(intent, REQUEST_CAPTURE_MEDIA);
    }

    @Override
    public boolean isActive(Context c) {
        return MusubiBaseActivity.isDeveloperModeEnabled(c);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CAPTURE_MEDIA) {
            if (resultCode != Activity.RESULT_OK) {
                return;
            }
            new CameraCaptureTask(data).execute();
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState != null) {
            mFeedUri = savedInstanceState.getParcelable("feed");
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putParcelable("feed", mFeedUri);
    }

    class CameraCaptureTask extends AsyncTask<Void, Void, Boolean> {
        Intent mData;
        Throwable mError;
        Obj mObj;

        public CameraCaptureTask(Intent data) {
            mData = data;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            if (mData == null || mData.getData() == null) {
                return false;
            }

            Uri mediaUri;
            String type;

            mediaUri = mData.getData();
            type = mData.getType();
            if (type == null) {
                type = getActivity().getContentResolver().getType(mediaUri);
            }

            if (type != null && type.startsWith("video/")) {
                try {
                    mObj = VideoObj.from(getActivity(), mediaUri, type);
                } catch (IOException e) {
                    Log.e(TAG, "Failed to fetch video", e);
                    return false;
                }
            }

            if (type != null && type.startsWith("image/")) {
                Uri storedUri = ContentCorral.storeContent(getActivity(), mediaUri, type);
                try {
                    mObj = PictureObj.from(getActivity(), storedUri, true);
                } catch (IOException e) {
                    Log.e(TAG, "Corral photo action had an issue", e);
                    try {
                        mObj = PictureObj.from(getActivity(), mediaUri, true);
                    } catch(Throwable t) {
                        Log.e(TAG, "fallback photo action had an issue", t);
                    }
                }
            }

            if (mObj == null) {
                return false;
            }

            Helpers.sendToFeed(getActivity(), mObj, mFeedUri);
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (result) {
                Helpers.emailUnclaimedMembers(getActivity(), mObj, mFeedUri);
                WizardStepHandler.accomplishTask(getActivity(), WizardStepHandler.TASK_TAKE_PICTURE);
            } else {
                Toast.makeText(getActivity(), "Failed to capture media.",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }
}
