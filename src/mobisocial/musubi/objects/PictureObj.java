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
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.SoftReference;

import mobisocial.musubi.App;
import mobisocial.musubi.ImageGalleryActivity;
import mobisocial.musubi.R;
import mobisocial.musubi.feed.iface.Activator;
import mobisocial.musubi.feed.iface.DbEntryHandler;
import mobisocial.musubi.feed.iface.FeedRenderer;
import mobisocial.musubi.model.DbContactAttributes;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MObject;
import mobisocial.musubi.ui.MusubiBaseActivity;
import mobisocial.musubi.ui.fragments.FeedListFragment.FeedSummary;
import mobisocial.musubi.ui.widget.DbObjCursorAdapter.DbObjCursor;
import mobisocial.musubi.util.CommonLayouts;
import mobisocial.musubi.util.UriImage;
import mobisocial.socialkit.musubi.DbObj;
import mobisocial.socialkit.obj.MemObj;

import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.mobisocial.corral.ContentCorral;
import org.mobisocial.corral.CorralDownloadClient;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class PictureObj extends DbEntryHandler implements FeedRenderer, Activator {
	public static final String TAG = "PictureObj";

    public static final String TYPE = "picture";
    public static final String DATA = "data";
    public static final String TEXT = "text";

    public static final int MAX_IMAGE_WIDTH = 1280;
    public static final int MAX_IMAGE_HEIGHT = 720;
    public static final int MAX_IMAGE_SIZE = 40*1024;

    @Override
    public String getType() {
        return TYPE;
    }

    public static MemObj from(byte[] data) {
    	return from(data, "");
    }
    /** 
     * This does NOT do any SCALING!
     */
    public static MemObj from(byte[] data, String text) {
    	JSONObject base = new JSONObject();
        try{
            base.put(TEXT, text);
        }catch(JSONException e){}
        
        return new MemObj(TYPE, base, data);
    }

    public static MemObj from(JSONObject base, byte[] data) {
        return new MemObj(TYPE, base, data);
    }

    public static MemObj from(Context context, Uri imageUri, boolean referenceOrig) throws IOException {
    	return from(context, imageUri, referenceOrig, null);
    }
    
    public static MemObj from(Context context, Uri imageUri, boolean referenceOrig, String text) throws IOException {
        // Query gallery for camera picture via
        // Android ContentResolver interface
        ContentResolver cr = context.getContentResolver();

        UriImage image = new UriImage(context, imageUri);
        byte[] data = image.getResizedImageData(MAX_IMAGE_WIDTH, MAX_IMAGE_HEIGHT, MAX_IMAGE_SIZE);   

        JSONObject base = new JSONObject();
        // Maintain a reference to original file
        try {
            String type = cr.getType(imageUri);
            if (type == null) {
                type = "image/jpeg";
            }

            if (text != null && !text.isEmpty()) {
            	base.put(TEXT, text);
            }
            base.put(CorralDownloadClient.OBJ_MIME_TYPE, type);
            if (referenceOrig) {
                base.put(CorralDownloadClient.OBJ_LOCAL_URI, imageUri.toString());
                String localIp = ContentCorral.getLocalIpAddress();
                // TODO: Share IP if allowed for the given feed
                if (localIp != null && MusubiBaseActivity.isDeveloperModeEnabled(context)) {
                    base.put(DbContactAttributes.ATTR_LAN_IP, localIp);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "impossible json error possible!");
        }
        return new MemObj(TYPE, base, data);
    }

    @Override
    public View createView(Context context, ViewGroup parent) {
    	LinearLayout frame = new LinearLayout(context);
    	frame.setLayoutParams(CommonLayouts.FULL_WIDTH);
    	frame.setOrientation(LinearLayout.VERTICAL);
    	
    	ImageView imageView = new ImageView(context);
        imageView.setBackgroundResource(android.R.drawable.picture_frame);
        imageView.setPadding(6, 4, 8, 9);
        frame.addView(imageView);
        
        TextView textView = new TextView(context);
        frame.addView(textView);
        
        return frame;
    }

    @Override
	public void render(Context context, View view, DbObjCursor obj, boolean allowInteractions) {
    	LinearLayout frame = (LinearLayout)view;

    	ImageView imageView = (ImageView)frame.getChildAt(0);
    	FileDescriptor fd = obj.getFileDescriptorForRaw();
    	byte[] raw = null;
    	if (fd == null) {
    		raw = obj.getRaw();
    	}
    	

	    bindImageToView(context, imageView, raw, fd);
    	
    	String text = obj.getJson().optString(TEXT);
    	TextView textView = (TextView)frame.getChildAt(1);
    	textView.setText(text);
	}

    /**
     * Pass in one of raw or fd as the source of the image.
     * Not thread safe, only call on the ui thread.
     */
    protected static void bindImageToView(Context context, ImageView imageView, byte[] raw, FileDescriptor fd) {
        // recycle old images (vs. caching in ImageCache)
        if (imageView.getDrawable() != null) {
        	BitmapDrawable d = (BitmapDrawable)imageView.getDrawable();
        	if (d != null && d.getBitmap() != null) {
        		d.getBitmap().recycle();
        	}
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        if (fd != null) {
        	BitmapFactory.decodeFileDescriptor(fd, null, options);
        } else {
        	BitmapFactory.decodeByteArray(raw, 0, raw.length, options);
        }
        Resources res = context.getResources();

        float scaleFactor;
        if (res.getBoolean(R.bool.is_tablet)) {
            scaleFactor = 3.0f;
        } else {
            scaleFactor = 2.0f;
        }
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int pixels = dm.widthPixels;
        if (dm.heightPixels < pixels) {
            pixels = dm.heightPixels;
        }
        int width = (int)(pixels / scaleFactor);
        int height = (int)((float)width / options.outWidth * options.outHeight);
        int max_height = (int)(AppStateObj.MAX_HEIGHT * dm.density);
        if(height > max_height) {
        	width = width * max_height / height;
        	height = max_height;
        }

        options.inJustDecodeBounds = false;
        options.inTempStorage = getTempData();
        options.inSampleSize = 1;
        //TODO: lame, can just compute
        while(options.outWidth / (options.inSampleSize + 1) >= width && options.outHeight / (options.inSampleSize + 1) >= height) {
        	options.inSampleSize++;
        }
        options.inPurgeable = true;
        options.inInputShareable = true;

        Bitmap bm;
        if (fd != null) {
        	bm = BitmapFactory.decodeFileDescriptor(fd, null, options);
        } else {
        	bm = BitmapFactory.decodeByteArray(raw, 0, raw.length, options);
        }
        imageView.getLayoutParams().width = width + 13;
        imageView.getLayoutParams().height = height + 14;
        imageView.setImageBitmap(bm);
    }

	@Override
    public void activate(Context context, DbObj obj) {
	    // TODO: set data uri for obj
	    Intent intent = new Intent(context, ImageGalleryActivity.class);
	    intent.setData(obj.getContainingFeed().getUri());
	    intent.putExtra(ImageGalleryActivity.EXTRA_DEFAULT_OBJECT_ID, obj.getLocalId());
	    if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
	    context.startActivity(intent);
    }

	@Override
	public boolean processObject(Context context, MFeed feed, MIdentity sender, MObject object) {
	    DbObj obj = App.getMusubi(context).objForId(object.id_);
	    File thumbFile = CorralDownloadClient.localFileForContent(obj, true);
        try {
            FileOutputStream fout = new FileOutputStream(thumbFile);
            ByteArrayInputStream fin = new ByteArrayInputStream(object.raw_);
            IOUtils.copy(fin, fout);
            String[] paths = new String[] { thumbFile.getAbsolutePath() };
            MediaScannerConnection.scanFile(context, paths, null, null);
        } catch (IOException e) {
            Log.e(TAG, "Error saving thumbnail", e);
            thumbFile.delete();
        }
	    return true;
	}

	static byte[] mTempData;
	static byte[] getTempData() {
		if (mTempData == null) {
			mTempData = new byte[16*1024];
		}
		return mTempData;
	}

	@Override
	public void getSummaryText(Context context, TextView view, FeedSummary summary) {
		view.setTypeface(null, Typeface.ITALIC);
		
		JSONObject json = summary.getJson();
		if (json != null && json.optString(PictureObj.TEXT).length() > 0) {
			StringBuilder summaryText = new StringBuilder(50)
			.append(summary.getSender()).append(" shared a picture with the caption \"" + json.optString(StatusObj.TEXT) + "\"");
		view.setText(summaryText.toString());	
		}
		else {
			StringBuilder summaryText = new StringBuilder(50)
				.append(summary.getSender()).append(" shared a picture.");
			view.setText(summaryText.toString());
		}
	}
}
