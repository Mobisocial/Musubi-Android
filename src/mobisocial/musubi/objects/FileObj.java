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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import mobisocial.musubi.R;
import mobisocial.musubi.feed.iface.Activator;
import mobisocial.musubi.feed.iface.DbEntryHandler;
import mobisocial.musubi.feed.iface.FeedRenderer;
import mobisocial.musubi.ui.MusubiBaseActivity;
import mobisocial.musubi.ui.fragments.FeedListFragment.FeedSummary;
import mobisocial.musubi.ui.widget.DbObjCursorAdapter.DbObjCursor;
import mobisocial.musubi.util.CommonLayouts;
import mobisocial.socialkit.Obj;
import mobisocial.socialkit.musubi.DbObj;
import mobisocial.socialkit.obj.MemObj;

import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.mobisocial.corral.BackgroundableDownloadDialogFragment;
import org.mobisocial.corral.CorralDownloadClient;
import org.mobisocial.corral.CorralDownloadHandler;
import org.mobisocial.corral.CorralDownloadHandler.CorralDownloadFuture;
import org.mobisocial.corral.CorralHelper.DownloadProgressCallback;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

//TODO: add thumbnails
//Add all the fields you need, hook it up, etc
public class FileObj extends DbEntryHandler implements FeedRenderer, Activator {
	public static final long EMBED_SIZE_LIMIT = 200 * 1024;
	public static final long CORRAL_SIZE_LIMIT = 30 * 1024 * 1024;
	public static final String TAG = "FileObj";

    public static final String TYPE = "file";

    //TODO: larger files need to be sent differently
    public static final String CORRAL_URI = "corral_uri";
    //thumbnail could be embedded as the __html magic
	
	public static final String OBJ_FILENAME = "filename";
	public static final String OBJ_FILESIZE = "filesize";

	private static final SimpleDateFormat sDateFormat = new SimpleDateFormat("yyyy-MM-dd");
    
    @Override
    public String getType() {
        return TYPE;
    }

    static MemObj from(String mime_type, String filename, long size, byte[] data) {
    	if(data.length > EMBED_SIZE_LIMIT)
    		throw new RuntimeException("file too large for push");
    	JSONObject json = new JSONObject();
    	try {
    		json.put(OBJ_FILENAME, filename);
    		json.put(OBJ_FILESIZE, size);
    		json.put(CorralDownloadClient.OBJ_MIME_TYPE, mime_type);
    	} catch(JSONException e) {
    		throw new RuntimeException("json encode failed", e);
    	}
        return new MemObj(TYPE, json, data);
    }

    public static Obj from(Context context, Uri dataUri) throws IOException {
    	//TODO: is this the proper way to do it?
        if (dataUri == null) {
            throw new NullPointerException();
        }
        ContentResolver cr = context.getContentResolver();
        InputStream in = cr.openInputStream(dataUri);
    	long length = in.available();

		String ext;
		String mimeType;
		String filename;

    	MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
    	if ("content".equals(dataUri.getScheme())) {
    	    ContentResolver resolver = context.getContentResolver();
    	    mimeType = resolver.getType(dataUri);
    	    ext = mimeTypeMap.getExtensionFromMimeType(mimeType);
    	    filename = "Musubi-" + sDateFormat.format(new Date());
    	} else {
    	    ext = MimeTypeMap.getFileExtensionFromUrl(dataUri.toString());
    	    mimeType = mimeTypeMap.getMimeTypeFromExtension(ext);
    	    filename = Uri.parse(dataUri.toString()).getLastPathSegment();
    	    if (filename == null) {
    	        filename = "Musubi-" + sDateFormat.format(new Date());
    	    }
    	}

    	if (mimeType == null || mimeType.isEmpty()) {
    	    throw new IOException("Unidentified mime type");
    	}

    	if (ext == null || ext.isEmpty()) {
            ext = mimeTypeMap.getExtensionFromMimeType(mimeType);
        }

    	if (!ext.isEmpty() && !filename.endsWith(ext)) {
            filename = filename + "." + ext;
        }

    	if (mimeType.startsWith("video/")) {
    	    return VideoObj.from(context, dataUri, mimeType);
    	} else if (mimeType.startsWith("image/")) {
    	    return PictureObj.from(context, dataUri, true);
    	}

    	if (length > EMBED_SIZE_LIMIT) {
        	if (length > CORRAL_SIZE_LIMIT) {
        		throw new IOException("file too large for push");
        	} else {
        		return fromCorral(context, mimeType, filename, length, dataUri);
        	}
    	} else {
	    	in = cr.openInputStream(dataUri);
	    	return from(mimeType, filename, length, IOUtils.toByteArray(in));
    	}
    }
    
    public static MemObj fromCorral(Context context, String mime_type, String filename, long length, Uri dataUri){
    	
    	JSONObject json = new JSONObject();
    	try {
    		json.put(OBJ_FILENAME, filename);
    		json.put(CorralDownloadClient.OBJ_MIME_TYPE, mime_type);
    		json.put(CorralDownloadClient.OBJ_LOCAL_URI, dataUri);
            json.put(OBJ_FILESIZE, length);
    	} catch(JSONException e) {
    		throw new RuntimeException("json encode failed", e);
    	}
        return new MemObj(TYPE, json, null);
    }

    @Override
    public View createView(Context context, ViewGroup frame) {
    	LinearLayout container = new LinearLayout(context);
        container.setLayoutParams(CommonLayouts.FULL_WIDTH);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER);

        ImageView imageView = new ImageView(context);
        imageView.setLayoutParams(new LinearLayout.LayoutParams(
                                      LinearLayout.LayoutParams.WRAP_CONTENT,
                                      LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView valueTV = new TextView(context);
        
        valueTV.setLayoutParams(new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.FILL_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT));
        valueTV.setGravity(Gravity.BOTTOM | Gravity.LEFT);
        valueTV.setPadding(4, 0, 0, 0);

        container.addView(imageView);
        container.addView(valueTV);
        return container;
    }

    @Override
	public void render(Context context, View frame, DbObjCursor obj, boolean allowInteractions) {
		JSONObject json = obj.getJson();
		String fname = "";
		String mimetype = "application/octet-stream";
		int iconid = R.drawable.icon_default;
		try {
			fname = json.getString(OBJ_FILENAME);
			mimetype = json.getString(CorralDownloadClient.OBJ_MIME_TYPE);
			iconid = getIconid(fname, mimetype);
		} catch (JSONException e) {
			e.printStackTrace();
		}

		ImageView imageView = (ImageView)((ViewGroup)frame).getChildAt(0);
		TextView valueTV = (TextView)((ViewGroup)frame).getChildAt(1);
		imageView.setImageResource(iconid);
		String ftype = getDisplayFileType(fname, mimetype);
		valueTV.setText(ftype+" File: \""+fname+"\".");
	}

	@Override
    synchronized public void activate(final Context context, final DbObj obj) {
	    CorralDownloadClient client = CorralDownloadClient.getInstance(context);
	    JSONObject json = obj.getJson();
	    final String mimeType;
        try {
            mimeType = json.getString(CorralDownloadClient.OBJ_MIME_TYPE);
        } catch (JSONException e) {
            Toast.makeText(context, "Could not view this file.", Toast.LENGTH_SHORT);
            Log.e(TAG, "Error viewing file", e);
            return;
        }

        if (client.fileAvailableLocally(obj)) {
            Uri uri = client.getAvailableContentUri(obj);
            viewfile(context, uri, mimeType);
            return;
        }

        if (obj.getRaw() != null) {
            // in-place file delivery. How convenient.
            File cacheFile = CorralDownloadClient.localFileForContent(obj, false);
            try {
                FileOutputStream output = new FileOutputStream(cacheFile);
                output.write(obj.getRaw());
                output.close();
                viewfile(context, Uri.fromFile(cacheFile), mimeType);
            } catch (IOException e) {
                Toast.makeText(context, "Error loading this file.", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        // attempt a download.
        CorralDownloadFuture future = CorralDownloadHandler.startOrFetchDownload(context, obj);
        FileDownloadDialogFragment f = new FileDownloadDialogFragment(future, mimeType);
        future.registerCallback(f);
        ((MusubiBaseActivity)context).showDialog(f, false);
    }

	public class FileDownloadDialogFragment extends BackgroundableDownloadDialogFragment {
        private final String mMimeType;

        public FileDownloadDialogFragment(CorralDownloadFuture future, String mimeType) {
            super(future);
            mMimeType = mimeType;
        }

        @Override
        public void onProgress(DownloadState state, DownloadChannel channel, int progress) {
            super.onProgress(state, channel, progress);
            if (state == DownloadState.TRANSFER_COMPLETE) {
                if (progress == DownloadProgressCallback.SUCCESS) {
                    final Activity me = getActivity();
                    me.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            viewfile(me, getResult(), mMimeType);
                        }
                    });
                } else {
                    Toast.makeText(getActivity(), "Error fetching video", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
	
	public static void viewfile(Context context, Uri contentUri, String mimetype){
		Intent intent = new Intent(Intent.ACTION_VIEW);
		if(mimetype==null || mimetype.isEmpty()){
			return;
		}
		try{
			intent.setDataAndType(contentUri, mimetype);
		    context.startActivity(intent);
		}catch (ActivityNotFoundException anfe){
			// TODO redirect Google Play to download viewer app for each filetype
			return;
		}
	}
	
    private static int getIconid(String filename, String mimetype){
    	
    	int iconid = R.drawable.icon_default;
		String ext = getSuffix(filename);
		if(ext!=null){
			if(ext.equalsIgnoreCase("ppt") || ext.equalsIgnoreCase("pptx")){
				iconid = R.drawable.icon_powerpoint;
			}else if(ext.equalsIgnoreCase("xls") || ext.equalsIgnoreCase("xlsx") || ext.equalsIgnoreCase("csv")){
				iconid = R.drawable.icon_excel;
			}else if(ext.equalsIgnoreCase("doc") || ext.equalsIgnoreCase("docx")){
				iconid = R.drawable.icon_word;
			}else if(ext.equalsIgnoreCase("pdf")){
				iconid = R.drawable.icon_pdf;
			}else if(ext.equalsIgnoreCase("mov")){
				iconid = R.drawable.icon_mov;
			}else if(ext.equalsIgnoreCase("xml")){
				iconid = R.drawable.icon_xml;
			}else if(ext.equalsIgnoreCase("html")){
				iconid = R.drawable.icon_html;
			}else if(ext.equalsIgnoreCase("png")){
				iconid = R.drawable.icon_png;
			}else if(ext.equalsIgnoreCase("jpg") || ext.equalsIgnoreCase("jpeg")){
				iconid = R.drawable.icon_jpg;
			}else if(ext.equalsIgnoreCase("zip") || ext.equalsIgnoreCase("rar")){
				iconid = R.drawable.icon_compress;
			}else if(mimetype.startsWith("image/")){
				iconid = R.drawable.icon_image;
			}else if(mimetype.startsWith("video/")){
				iconid = R.drawable.icon_movie;
			}else if(mimetype.startsWith("audio/")){
				iconid = R.drawable.icon_music;
			}else if(mimetype.startsWith("text/")){
				iconid = R.drawable.icon_text;
			}
		}
		return iconid;
    }
    
    private static String getDisplayFileType(String filename, String mimetype){
		String ext = getSuffix(filename);
		if(ext!=null){
			if(ext.equalsIgnoreCase("ppt") || ext.equalsIgnoreCase("pptx")){
				return "Powerpoint";
			}else if(ext.equalsIgnoreCase("xls") || ext.equalsIgnoreCase("xlsx") || ext.equalsIgnoreCase("csv")){
				return "Excel";
			}else if(ext.equalsIgnoreCase("doc") || ext.equalsIgnoreCase("docx")){
				return "Word";
			}else if(ext.equalsIgnoreCase("pdf")){
				return "PDF";
			}else if(ext.equalsIgnoreCase("mov")){
				return "Video";
			}else if(ext.equalsIgnoreCase("xml")){
				return "XML";
			}else if(ext.equalsIgnoreCase("html")){
				return "HTML";
			}else if(ext.equalsIgnoreCase("png")){
				return "Image";
			}else if(ext.equalsIgnoreCase("jpg") || ext.equalsIgnoreCase("jpeg")){
				return "Image";
			}else if(ext.equalsIgnoreCase("zip") || ext.equalsIgnoreCase("rar")){
				return "Compressed";
			}else if(mimetype.startsWith("image/")){
				return "Image";
			}else if(mimetype.startsWith("video/")){
				return "Video";
			}else if(mimetype.startsWith("audio/")){
				return "Audio";
			}else if(mimetype.startsWith("text/")){
				return "Text";
			}
		}
		return "";
	}
	
    private static String getSuffix(String filename) {
        if (filename == null)
            return null;
        int point = filename.lastIndexOf(".");
        if (point != -1) {
            return filename.substring(point + 1);
        }
        return filename;
    }

	@Override
	public void getSummaryText(Context context, TextView view, FeedSummary summary) {
		JSONObject obj = summary.getJson();
		view.setTypeface(null, Typeface.ITALIC);
		view.setText(summary.getSender() 
				  	 + " posted a new file: " 
					 + obj.optString(FileObj.OBJ_FILENAME));
	}
    

	//TODO: how to handle the edit?
}
