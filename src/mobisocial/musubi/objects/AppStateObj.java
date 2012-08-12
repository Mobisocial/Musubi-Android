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
import java.util.Date;
import java.util.List;

import mobisocial.crypto.IBHashedIdentity;
import mobisocial.musubi.App;
import mobisocial.musubi.R;
import mobisocial.musubi.feed.iface.Activator;
import mobisocial.musubi.feed.iface.DbEntryHandler;
import mobisocial.musubi.feed.iface.FeedRenderer;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MObject;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.model.helpers.ObjectManager;
import mobisocial.musubi.obj.ObjHelpers;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.service.WebRenderService;
import mobisocial.musubi.ui.fragments.FeedListFragment.FeedSummary;
import mobisocial.musubi.ui.widget.DbObjCursorAdapter.DbObjCursor;
import mobisocial.musubi.util.Util;
import mobisocial.socialkit.Obj;
import mobisocial.socialkit.musubi.DbObj;
import mobisocial.socialkit.obj.MemObj;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A snapshot of an application's state.
 */
public class AppStateObj extends DbEntryHandler implements FeedRenderer, Activator {
	private static final String TAG = "AppStateObj";
	private static final boolean DBG = false;

    public static final String TYPE = "appstate";
    public static final String ARG = "arg";
    public static final String STATE = "state";
    public static final String THUMB_RAW_JPG = "__b64jpgthumb";
    public static final String THUMB_TEXT = "__text";
    public static final String PACKAGE_NAME = "packageName";
    public static final String OBJ_INTENT_ACTION = "intentAction";
	public static final int MAX_HEIGHT = 225;

    @Override
    public String getType() {
        return TYPE;
    }

    public static Obj from(String appId, String type, JSONObject json, byte[] raw, Integer intKey,
            String strKey) {
        if (type == null) {
            type = TYPE;
        }
        if (json == null) {
            json = new JSONObject();
        }
        try {
            json.put(AppObj.ANDROID_PACKAGE_NAME, appId);
        } catch (JSONException e) {
            throw new IllegalStateException("Bad json libary", e);
        }
        return new MemObj(type, json, raw, intKey, strKey);
    }

    @Override
    public boolean processObject(Context context, MFeed feed, MIdentity sender, MObject object) {
        JSONObject json;
        try {
            json = new JSONObject(object.json_);
        } catch (JSONException e) {
            Log.e(TAG, "Bad app object json", e);
            return false;
        }
    	SQLiteOpenHelper helper = App.getDatabaseSource(context);
        ObjectManager om = new ObjectManager(helper);
    	if (json.has(ObjHelpers.TARGET_HASH)) {
            String hashA = json.optString(ObjHelpers.TARGET_HASH);
            byte[] uHash;
            try {
                uHash = Util.convertToByteArray(hashA);
            } catch (Exception e) {
                Log.e(TAG, "Couldn't convert universal hash");
                return true;
            }

            long idA = om.getObjectIdForHash(uHash);            
            if (idA == -1) {
                Log.e(TAG, "No objId found for hash " + hashA);
                return true;
            }

            MObject parent = om.getObjectForId(idA);
            object.lastModifiedTimestamp_ = new Date().getTime();
            om.updateObject(parent);
        }
    	return true;
	}

    @Override
    public View createView(Context context, ViewGroup frame) {
    	return new LinearLayout(context);
    }

    @Override
	public void render(final Context context, final View view, DbObjCursor obj, boolean allowInteractions) {
    	ViewGroup frame = (LinearLayout)view;
    	frame.removeAllViews();

	    JSONObject content = obj.getJson();
	    boolean rendered = false;
	    AppState ref = new AppState(content);
	    byte[] raw = ref.getThumbnailPicture();
	    if (content.has(THUMB_RAW_JPG) && raw != null) {
	        rendered = true;
            ImageView imageView = new ImageView(context);
            imageView.setLayoutParams(new LinearLayout.LayoutParams(
                                          LinearLayout.LayoutParams.WRAP_CONTENT,
                                          LinearLayout.LayoutParams.WRAP_CONTENT));
            //TODO: this is complete BS, pass in thing has a local id which is a perfect hash
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPurgeable = true;
            options.inInputShareable = true;
        	Bitmap b = BitmapFactory.decodeByteArray(raw, 0, raw.length, options);
            imageView.setImageBitmap(b);
            frame.addView(imageView);
	    }

	    String thumbnail = ref.getThumbnailText();
	    if (thumbnail != null) {
	        rendered = true;
            TextView valueTV = new TextView(context);
            valueTV.setText(thumbnail);
            valueTV.setLayoutParams(new LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.WRAP_CONTENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT));
            valueTV.setGravity(Gravity.TOP | Gravity.LEFT);
            frame.addView(valueTV);
	    }

	    thumbnail = ref.getThumbnailHtml();
        if (thumbnail != null) {
            rendered = true;
            renderHtml(context, frame, thumbnail);
        }

	    if (!rendered) {
	        String appName = content.optString(PACKAGE_NAME);
	        if (appName.contains(".")) {
	            appName = appName.substring(appName.lastIndexOf(".") + 1);
	        }
            String text = "Welcome to " + appName + "!";
            TextView valueTV = new TextView(context);
            valueTV.setText(text);
            valueTV.setLayoutParams(new LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.WRAP_CONTENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT));
            valueTV.setGravity(Gravity.TOP | Gravity.LEFT);
            frame.addView(valueTV);
        }
    }

	public static Intent getLaunchIntent(Context context, DbObj obj) {
	    JSONObject content = obj.getJson();

	    if (DBG) Log.d(TAG, "Getting launch intent for " + content);
	    Uri  appFeed = obj.getContainingFeed().getUri();
	    String appId = obj.getAppId();
	    // TODO: Hack for deprecated launch method
	    if (appId.equals(MusubiContentProvider.SUPER_APP_ID)) {
	        appId = content.optString(PACKAGE_NAME);
	    }
	    if (DBG) Log.d(TAG, "Preparing launch of " + appId + " on " + appFeed);
	    
	    Intent launch = new Intent();
	    if (content.has(OBJ_INTENT_ACTION)) {
	        launch.setAction(content.optString(OBJ_INTENT_ACTION));
	    } else {
	        launch.setAction(Intent.ACTION_MAIN);
	    }

        launch.addCategory(Intent.CATEGORY_LAUNCHER);
        launch.putExtra(AppObj.EXTRA_FEED_URI, appFeed);

        // TODO: optimize!
        List<ResolveInfo> resolved = context.getPackageManager().queryIntentActivities(launch, 0);
        for (ResolveInfo r : resolved) {
            ActivityInfo activity = r.activityInfo;
            if (activity.packageName.equals(appId)) {
                launch.setClassName(activity.packageName, activity.name);
                launch.putExtra("mobisocial.db.PACKAGE", activity.packageName);
                return launch;
            }
        }

        Intent market = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appId));
        return market;
	}

	public interface Callback {
	    public void onAppSelected(String pkg, String arg, Intent localLaunch);
	}

    @Override
    public boolean doNotification(Context context, DbObj obj) {
        JSONObject json = obj.getJson();
        if (json == null) {
            return true;
        }
        if (json.has("notify")) {
            return json.optBoolean("notify");
        }
        if (!json.has("membership")) {
            return true;
        }
        try {
            IdentitiesManager im = new IdentitiesManager(App.getDatabaseSource(context));
            JSONArray arr = json.getJSONArray("membership");
            for (int i = 0; i < arr.length(); i++) {
                try {
                    String personId = arr.getString(i);
                    byte[] personBytes = Util.convertToByteArray(personId);
                    IBHashedIdentity hid = new IBHashedIdentity(personBytes);
                    MIdentity maybeMe = im.getIdentityForIBHashedIdentity(hid);
                    if (maybeMe != null && maybeMe.owned_) {
                        return true;
                    }
                } catch (Exception e) {}
            }
        } catch (JSONException e) {}
        return false;
    }

    @Override
    public void activate(Context context, DbObj obj) {
        if (DBG) Log.d(TAG, "activating appstate " + obj.getAppId() + "; hash=" + obj.getHash());
        Intent launch = getLaunchIntent(context, obj);

        if (!(context instanceof Activity)) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(launch);
    }

    public static void renderHtml(Context context, ViewGroup frame, String html) {
        Resources res = context.getResources();
        int targetWidth, targetHeight;
        if (res.getBoolean(R.bool.is_tablet)) {
            targetWidth = (int)(context.getResources().getDisplayMetrics().widthPixels * 0.333f);
            targetHeight = (int)(context.getResources().getDisplayMetrics().heightPixels * 0.4f);    
        } else {
            targetWidth = (int)(context.getResources().getDisplayMetrics().widthPixels * 0.5f);
            targetHeight = (int)(context.getResources().getDisplayMetrics().heightPixels * 0.333f);
        }
		ImageView imageView = WebRenderService.newLazyImageWeb(context, html, targetWidth, targetHeight);
        frame.addView(imageView);
    }

    static class AppState extends MemObj {
        public static final String EXTRA_APPLICATION_PACKAGE = "mobisocial.db.PACKAGE";
        public static final String EXTRA_APPLICATION_STATE = "mobisocial.db.STATE";
        public static final String EXTRA_APPLICATION_IMG = "mobisocial.db.THUMBNAIL_IMAGE";
        public static final String EXTRA_APPLICATION_TEXT = "mobisocial.db.THUMBNAIL_TEXT";

        public AppState(JSONObject json) {
            super(AppStateObj.TYPE, json);
        }

        public String pkg() {
            return getJson().optString("packageName");
        }

        public String getThumbnailText() {
            if (getJson().has(AppStateObj.THUMB_TEXT)) {
                return getJson().optString(AppStateObj.THUMB_TEXT);
            }
            return null;
        }

        public String getThumbnailHtml() {
            if (getJson().has(Obj.FIELD_HTML)) {
                return getJson().optString(Obj.FIELD_HTML);
            }
            return null;
        }
        
        public byte[] getThumbnailPicture() {
        	 if (getJson().has(AppStateObj.THUMB_RAW_JPG)) {
                 return Base64.decode(getJson().optString(AppStateObj.THUMB_RAW_JPG), Base64.DEFAULT);
             }
             return null;
        }
        
    }

	@Override
	public void getSummaryText(Context context, TextView view, FeedSummary summary) {
		JSONObject obj = summary.getJson();
		String text = " did something in an app!";
		if (obj.has(THUMB_TEXT)) {
			text = " did something in " + obj.optString(THUMB_TEXT + "!");
		}
		String appName = obj.optString(PACKAGE_NAME);
        if (appName.contains(".")) {
            text = " did something in " + appName.substring(appName.lastIndexOf(".") + 1) +"!";
        }
        
		view.setTypeface(null, Typeface.ITALIC);
		view.setText(summary.getSender() + text);
	}
}
