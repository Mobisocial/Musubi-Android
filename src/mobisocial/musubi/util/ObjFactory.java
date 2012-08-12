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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mobisocial.musubi.objects.AppObj;
import mobisocial.musubi.objects.PictureObj;
import mobisocial.musubi.objects.StatusObj;
import mobisocial.musubi.objects.StoryObj;
import mobisocial.musubi.objects.WebAppObj;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.ui.SendContentActivity;
import mobisocial.musubi.util.OGUtil.OGData;
import mobisocial.socialkit.Obj;
import mobisocial.socialkit.obj.MemObj;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.ActivityManager;
import android.app.ActivityManager.RecentTaskInfo;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Patterns;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

public class ObjFactory {
	public final static String TAG = "ObjFromIntent";

	public static Obj objForSendIntent(final Context context, Intent intent) {
        Obj obj = null;
        String type = intent.getType();
        if (hasImage(intent)) {
            try {
            	Uri uri = (Uri)intent.getParcelableExtra(Intent.EXTRA_STREAM);
            	// XXX copy data into Corral?
            	obj = PictureObj.from(context, uri, true);
            } catch (IOException e) {
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(context, "Remote image sources not supported", Toast.LENGTH_SHORT).show();
                    }
                });
                Log.e(TAG, "Couldn't load picture", e);
                return null;
            }
        } else if (type.startsWith("vnd.mobisocial.obj/")) {
            if (intent.hasExtra("json")) {
                String objType = type.replace("vnd.mobisocial.obj/", "");
                try {
                    obj =  new MemObj(objType, new JSONObject(intent.getStringExtra("json")));
                } catch (JSONException e) {
                    return null;
                }
            }
            return null;
        } else {
            Uri data = intent.getData();
            String mime = intent.getType();
            CharSequence charSequence = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            String txt = charSequence == null ? null : charSequence.toString();
            Uri uri;
            if (data != null) {
                uri = data;
            } else if (txt != null) {
                uri = extractFirstUri(txt);
            } else {
                return null;
            }
            if(uri != null) {
            	obj = handleSendURLObj(intent, mime, txt, uri);
            } else if(txt != null){
            	obj = StatusObj.from(txt);
            } else {
            	return null;
            }
        }
        if (obj != null) {
            String callerAppId = getCallerAppId(context, intent);
            if (obj.getJson() != null && callerAppId != null) {
                try {
                    obj.getJson().put(AppObj.ANDROID_PACKAGE_NAME, callerAppId);
                    obj.getJson().put(AppObj.CLAIMED_APP_ID, callerAppId);
                    try {
                        PackageManager pm = context.getPackageManager();
                        PackageInfo info = pm.getPackageInfo(callerAppId, 0);
                        String appName = info.applicationInfo.loadLabel(pm).toString();
                        obj.getJson().put(AppObj.APP_NAME, appName);
                    } catch (NameNotFoundException e) {
                        Log.w(TAG, "package not found", e);
                    }
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        return obj;
    }

	public static Obj objForText(String text) {
	    String trim = text.trim();
	    if (Patterns.WEB_URL.matcher(trim).matches()) {
	        return handleSendURLObj(null, null, null, Uri.parse(trim));
	    } else {
	        return StatusObj.from(text);   
	    }
	}

	static Obj handleSendURLObj(Intent intent, String mime, String txt, Uri uri) {
		if (uri.getPath() != null && uri.getPath().contains("/musubi/app")) {
		    return WebAppObj.forUri(uri);
		}

		String url = uri.toString();
		String original_url = url;
		String title = null;
		byte[] thumbnail_bytes = null;
		byte[] favicon_bytes = null;

		if (intent != null) {
    		if (intent.hasExtra(Intent.EXTRA_SUBJECT)) {
    		    title = intent.getStringExtra(Intent.EXTRA_SUBJECT);
    		}
    		Bitmap favicon = null;
    		if (intent.hasExtra("share_favicon")) {
    			favicon = (Bitmap)intent.getParcelableExtra("share_favicon");
    		}
    		if(favicon != null) {
    			ByteArrayOutputStream baos = new ByteArrayOutputStream();  
    			favicon.compress(Bitmap.CompressFormat.PNG, 100, baos); //bm is the bitmap object   
    			favicon_bytes = baos.toByteArray();
    		}
    		
    		Bitmap thumbnail = null;
    		if (intent.hasExtra(Intent.EXTRA_SHORTCUT_ICON)) {
    			thumbnail = (Bitmap)intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON);
    		}
    		if(thumbnail != null) {
    		    ByteArrayOutputStream baos = new ByteArrayOutputStream();  
    			thumbnail.compress(Bitmap.CompressFormat.PNG, 100, baos); //bm is the bitmap object   
    			thumbnail_bytes = baos.toByteArray();            
    		}
		}

		OGData og = OGUtil.getOrGuess(url);
		if (og == null && txt == null && title == null && thumbnail_bytes == null && url != null) {
		    return StatusObj.from(url);
		}
		if(og != null) {
			if(og.mImage != null)
				thumbnail_bytes = og.mImage;
			if(og.mDescription != null)
				txt = og.mDescription;
			if(og.mMimeType != null)
				mime = og.mMimeType;
			if(title == null && og.mTitle != null)
				title = og.mTitle;
			if(og.mUrl != null)
				url = og.mUrl;
		}
		//get rid of crap where people send us the same thing under several extras
		if(txt != null && txt.equals(url)) {
			txt = null;
		}
		if(title != null && title.equals(url)) {
			title = null;
		}
		if((txt != null || title != null) && thumbnail_bytes != null) {
			//only send a big picture if we have nothing else to send
			Bitmap b = BitmapFactory.decodeByteArray(thumbnail_bytes, 0, thumbnail_bytes.length);
			int w = b.getWidth();
			int h = b.getHeight();
			if(w > h) {
				h = h * Math.min(100, w) / w;
				w = Math.min(100, w);
			} else {
				w = w * Math.min(100, h) / h;
				h = Math.min(100, h);
			}
			Bitmap b2 = Bitmap.createScaledBitmap(b, w, h, true);
			b.recycle();
			b = b2;
		    ByteArrayOutputStream baos = new ByteArrayOutputStream();  
			b.compress(Bitmap.CompressFormat.PNG, 100, baos); //bm is the bitmap object   
			thumbnail_bytes = baos.toByteArray();            
			b.recycle();
		}
		
		return StoryObj.from(original_url, url, mime, txt, title, favicon_bytes, thumbnail_bytes);
	}

    public static String getCallerAppId(Context context, Intent intent) {
        final String SUPER = MusubiContentProvider.SUPER_APP_ID;
        ActivityManager manager = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        List<RunningTaskInfo> running = manager.getRunningTasks(1);
        if (running.size() > 0) {
            RunningTaskInfo thisTask = running.get(0);
            String pkg = thisTask.baseActivity.getPackageName();
            if (!SUPER.equals(pkg)) {
                return pkg;
            } else if (SUPER.equals(intent.getStringExtra(SendContentActivity.EXTRA_CALLING_APP))) {
                return SUPER;
            }
        }

        // The sending activity issued the SEND intent with FLAG_ACTIVITY_NEW_TASK.
        List<RecentTaskInfo> infos = manager.getRecentTasks(2, 0);
        if (infos.size() == 0) {
            Log.w(TAG, "couldn't get info");
            return null;
        }
        RecentTaskInfo task = infos.get(0);
        if (SUPER.equals(task.baseIntent.getComponent().getPackageName()) && infos.size() > 1) {
            task = infos.get(1);
        }
        Intent base = task.baseIntent;
        return base.getComponent().getPackageName();
    }

    static final Pattern schemePattern = Pattern.compile("\\b[-0-9a-zA-Z+\\.]+:\\S+");
    static Uri extractFirstUri(String text) {
        Matcher m = schemePattern.matcher(text);
        while(m.find()) {
            Uri uri = Uri.parse(m.group());
            String scheme = uri.getScheme();
            if (scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                return uri;
            }
        }
        return null;
    }

    private static boolean hasImage(Intent intent) {
    	if (intent.getType().startsWith("image/")) {
    		return true;
    	}
    	if (intent.hasExtra(Intent.EXTRA_STREAM)) {
    		Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
    		String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
    		if (extension.equals("jpg") || extension.equals("png")) {
    			return true;
    		}
    	}
    	return false;
    }
}
