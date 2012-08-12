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

package mobisocial.musubi.objects;
import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.IOException;

import mobisocial.musubi.R;
import mobisocial.musubi.feed.iface.Activator;
import mobisocial.musubi.feed.iface.DbEntryHandler;
import mobisocial.musubi.feed.iface.FeedRenderer;
import mobisocial.musubi.ui.MusubiBaseActivity;
import mobisocial.musubi.ui.fragments.FeedListFragment.FeedSummary;
import mobisocial.musubi.ui.widget.DbObjCursorAdapter.DbObjCursor;
import mobisocial.musubi.util.Base64;
import mobisocial.musubi.util.CommonLayouts;
import mobisocial.socialkit.Obj;
import mobisocial.socialkit.musubi.DbObj;
import mobisocial.socialkit.obj.MemObj;

import org.json.JSONException;
import org.json.JSONObject;
import org.mobisocial.corral.BackgroundableDownloadDialogFragment;
import org.mobisocial.corral.CorralDownloadClient;
import org.mobisocial.corral.CorralDownloadHandler;
import org.mobisocial.corral.CorralDownloadHandler.CorralDownloadFuture;
import org.mobisocial.corral.CorralHelper.DownloadProgressCallback;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class VideoObj extends DbEntryHandler implements FeedRenderer, Activator {
    public static final String TAG = "VideoObj";

    public static final String TYPE = "video";
    public static final String DATA = "data";

    @Override
    public String getType() {
        return TYPE;
    }

    public static Obj from(Context context, Uri videoUri, String mimeType) throws IOException {
        // Query gallery for camera picture via
        // Android ContentResolver interface
        Bitmap curThumb = null;
        ContentResolver cr = context.getContentResolver();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPurgeable = true;
        options.inInputShareable = true;
        options.inSampleSize = 1;

        int targetSize = 200;
        if (videoUri.getScheme().equals("content")) {
            long videoId = Long.parseLong(videoUri.getLastPathSegment());
            curThumb = MediaStore.Video.Thumbnails.getThumbnail(cr, videoId,
                    MediaStore.Video.Thumbnails.MINI_KIND, options);
            int width = curThumb.getWidth();
            int height = curThumb.getHeight();
            int cropSize = Math.min(width, height);
            float scaleSize = ((float) targetSize) / cropSize;
            Matrix matrix = new Matrix();
            matrix.postScale(scaleSize, scaleSize);
            curThumb = Bitmap.createBitmap(
                    curThumb, 0, 0,width, height, matrix, true);
        } else {
            curThumb = createVideoThumbnail(videoUri.getPath(), targetSize);
        }

        if (curThumb == null) {
            throw new IOException("Could not fetch thumbnail for " + videoUri);
        }

        JSONObject base = new JSONObject();
        try {
            if (mimeType == null && videoUri.getScheme().equals("content")) {
                mimeType = cr.getType(videoUri);
            }
            base.put(CorralDownloadClient.OBJ_LOCAL_URI, videoUri.toString());
            base.put(CorralDownloadClient.OBJ_MIME_TYPE, mimeType);
        } catch (JSONException e) {
            Log.e(TAG, "impossible json error possible!");
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        curThumb.compress(Bitmap.CompressFormat.JPEG, 90, baos);
        byte[] data = baos.toByteArray();
        return new MemObj(TYPE, base, data);
    }

    @Override
    public View createView(Context context, ViewGroup frame) {
    	LinearLayout inner = new LinearLayout(context);
        inner.setLayoutParams(CommonLayouts.FULL_WIDTH);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        ImageView imageView = new ImageView(context);
        imageView.setLayoutParams(new LinearLayout.LayoutParams(
                                      LinearLayout.LayoutParams.WRAP_CONTENT,
                                      LinearLayout.LayoutParams.WRAP_CONTENT));
        ImageView iconView = new ImageView(context);
        iconView.setImageResource(R.drawable.play);
        iconView.setLayoutParams(new LinearLayout.LayoutParams(
                                      LinearLayout.LayoutParams.WRAP_CONTENT,
                                      LinearLayout.LayoutParams.WRAP_CONTENT));
        inner.addView(imageView);
        inner.addView(iconView);
        return inner;
    }

    @Override
    public void render(Context context, View view, DbObjCursor obj, boolean allowInteractions) {
    	LinearLayout inner = (LinearLayout)view;
    	ImageView imageView = (ImageView)inner.getChildAt(0);
        FileDescriptor fd = obj.getFileDescriptorForRaw();
        byte[] raw = null;
        if (fd == null) {
        	raw = obj.getRaw();
        }
        PictureObj.bindImageToView(context, imageView, raw, fd);
    }

    public Pair<JSONObject, byte[]> handleUnprocessed(Context context,
            JSONObject msg) {
        byte[] bytes = Base64.decode(msg.optString(DATA));
        msg.remove(DATA);
        return new Pair<JSONObject, byte[]>(msg, bytes);
    }

    @Override
    public void activate(final Context context, final DbObj obj) {
        final CorralDownloadClient client = CorralDownloadClient.getInstance(context);
        Log.d(TAG, "Corraling video");
        if (client.fileAvailableLocally(obj)) {
            Uri contentUri = client.getAvailableContentUri(obj);
            startViewer(context, contentUri);
        } else {
            final CorralDownloadFuture future = CorralDownloadHandler.startOrFetchDownload(context, obj);
            if (context instanceof MusubiBaseActivity) {
            	VideoDownloadDialogFragment f = new VideoDownloadDialogFragment(future);
            	((MusubiBaseActivity)context).showDialog(f, false);
            } else {
            	new FetchAndPlayVideoTask(context, future).execute();
            }
        }
    }

    public class VideoDownloadDialogFragment extends BackgroundableDownloadDialogFragment {
        public VideoDownloadDialogFragment(CorralDownloadFuture future) {
            super(future);
        }

        @Override
        public void onProgress(DownloadState state, DownloadChannel channel, int progress) {
            super.onProgress(state, channel, progress);
            if (state == DownloadState.TRANSFER_COMPLETE) {
                if (progress == DownloadProgressCallback.SUCCESS) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            startViewer(getActivity(), getResult());
                        }
                    });
                } else {
                    Toast.makeText(getActivity(), "Error fetching video", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    class FetchAndPlayVideoTask extends AsyncTask<Void, Void, Uri> {
    	final Context mContext;
    	final CorralDownloadFuture mFuture;
    	public FetchAndPlayVideoTask(Context context, CorralDownloadFuture future) {
    		mContext = context;
    		mFuture = future;
    	}

    	@Override
    	protected Uri doInBackground(Void... params) {
    		try {
    			return mFuture.getResult();
    		} catch (InterruptedException e) {
    			return null;
    		}
    	}

    	@Override
    	protected void onPostExecute(Uri result) {
    		if (result != null) {
    			startViewer(mContext, result);
    		}
    	}
    }

    private void startViewer(Context context, Uri contentUri) {
        Log.d(TAG, "Launching viewer for " + contentUri);
        Intent i = new Intent(Intent.ACTION_VIEW);
        if (!(context instanceof Activity)) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        i.setDataAndType(contentUri, "video/*");
        context.startActivity(i);
    }

    private static Bitmap createVideoThumbnail(String filePath, int targetWidth) {
        Bitmap bitmap = null;

        if (VERSION.SDK_INT >= VERSION_CODES.GINGERBREAD_MR1) {
            // Available before API 10 but unsupported and without thumbnail api.
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(filePath);
                bitmap = retriever.getFrameAtTime(-1);
            } catch (IllegalArgumentException ex) {
                // Assume this is a corrupt video file
            } catch (RuntimeException ex) {
                // Assume this is a corrupt video file.
            } finally {
                try {
                    retriever.release();
                } catch (RuntimeException ex) {
                    // Ignore failures while cleaning up.
                }
            }
        } else if (filePath != null) {
            bitmap = ThumbnailUtils.createVideoThumbnail(filePath,
                    MediaStore.Images.Thumbnails.MINI_KIND);
        }

        if (bitmap == null) return null;

        // Scale down the bitmap if it is bigger than we need.
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width > targetWidth) {
            float scale = (float) targetWidth / width;
            int w = Math.round(scale * width);
            int h = Math.round(scale * height);
            bitmap = Bitmap.createScaledBitmap(bitmap, w, h, true);
        }
        return bitmap;
    }

	@Override
	public void getSummaryText(Context context, TextView view, FeedSummary summary) {
		view.setTypeface(null, Typeface.ITALIC);
		view.setText(summary.getSender() + " posted a video");
	}
}
