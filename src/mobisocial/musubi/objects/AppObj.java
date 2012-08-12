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

import java.util.List;

import mobisocial.musubi.BJDNotImplementedException;
import mobisocial.musubi.R;
import mobisocial.musubi.feed.iface.Activator;
import mobisocial.musubi.feed.iface.DbEntryHandler;
import mobisocial.musubi.feed.iface.FeedRenderer;
import mobisocial.musubi.model.DbObjCache;
import mobisocial.musubi.obj.ObjHelpers;
import mobisocial.musubi.ui.fragments.FeedListFragment.FeedSummary;
import mobisocial.musubi.ui.widget.DbObjCursorAdapter.DbObjCursor;
import mobisocial.musubi.util.CommonLayouts;
import mobisocial.musubi.webapp.WebAppActivity;
import mobisocial.socialkit.musubi.DbObj;
import mobisocial.socialkit.musubi.Musubi;
import mobisocial.socialkit.musubi.multiplayer.Multiplayer;
import mobisocial.socialkit.obj.MemObj;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Entry point for application sessions. Typically, an AppObj is created by
 * Musubi, but "owned" by a third-party application. That application can
 * attach application data
 *
 */
public class AppObj extends DbEntryHandler implements Activator, FeedRenderer {
    private static final String TAG = "musubi-appObj";
    private static final boolean DBG = false;

    public static final String TYPE = "app";
    public static final String ANDROID_PACKAGE_NAME = "__apkg";
    public static final String CLAIMED_APP_ID = "__ai";
    public static final String APP_NAME = "__an";
    public static final String ANDROID_CLASS_NAME = "android_cls";
    public static final String ANDROID_ACTION = "android_action";
    public static final String EXTRA_OBJ_URI = "mobisocial.db.OBJ_URI";
    public static final String EXTRA_FEED_URI = Musubi.EXTRA_FEED_URI;
    public static final String EXTRA_APPLICATION_ARGUMENT = "android.intent.extra.APPLICATION_ARGUMENT";

    public static final String WEB_URL = "web_url";

    private static final String SORT_ORDER =
            DbObj.COL_INT_KEY + " desc, " + DbObj.COL_IDENTITY_ID + " desc LIMIT 1";
    @Override
    public String getType() {
        return TYPE;
    }

    public static long[] contactsFromPicker(Intent pickerResult) {
        return pickerResult.getLongArrayExtra("contacts");
    }

    public static MemObj withDetails(Context context, String action,
            ResolveInfo resolveInfo, long[] contactIds) {
        String pkgName = resolveInfo.activityInfo.packageName;
        String className = resolveInfo.activityInfo.name;

        /**
         * TODO:
         * 
         * Identity Firewall Goes Here.
         * Membership details can be randomized in one of many ways.
         * The app (scrabble) may see games a set of gamers play together.
         * The app may always see random ids
         * The app may always see stable ids
         * 
         * Can also permute the cursor and member order.
         */

        JSONArray participantIds = new JSONArray();
        BJDNotImplementedException.except(BJDNotImplementedException.MSG_LOCAL_PERSON_ID);
        String personId = null;
        participantIds.put(personId);
        for (long id : contactIds) {
            Log.d(TAG, "Launching apps is broken");
        }
        JSONObject json = new JSONObject();
        try {
            json.put(Multiplayer.OBJ_MEMBERSHIP, (participantIds));
            json.put(ANDROID_ACTION, action);
            json.put(ANDROID_PACKAGE_NAME, pkgName);
            json.put(ANDROID_CLASS_NAME, className);
        } catch (JSONException e) {
            Log.d(TAG, "What? Impossible!", e);
        }
        return new MemObj(TYPE, json);
    }

    public static MemObj forApp(Context context, ResolveInfo info, String action) {
        /**
         * TODO:
         * 
         * Identity Firewall Goes Here.
         * Membership details can be randomized in one of many ways.
         * The app (scrabble) may see games a set of gamers play together.
         * The app may always see random ids
         * The app may always see stable ids
         * 
         * Can also permute the cursor and member order.
         */

        JSONObject json = new JSONObject();
        try {
            json.put(ANDROID_ACTION, action);
            json.put(ANDROID_PACKAGE_NAME, info.activityInfo.packageName);
            json.put(ANDROID_CLASS_NAME, info.activityInfo.name);
        } catch (JSONException e) {
            Log.d(TAG, "What? Impossible!", e);
        }
        return new MemObj(TYPE, json);
    }

    @Override
    public void activate(Context context, DbObj obj) {
        if (!(obj instanceof DbObj)) {
            Log.w(TAG, "Obj not ready yet!");
            return;
        }
        if (DBG) {
            JSONObject content = obj.getJson();
            Log.d(TAG, "activating app " + content + " from " + obj.getHash());
        }

        Intent launch = getLaunchIntent(context, (DbObj)obj);
        if (!(context instanceof Activity)) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(launch);
    }

    public static Intent getLaunchIntent(Context context, DbObj obj) {
        JSONObject content = obj.getJson(); 
        if (content.has(ANDROID_PACKAGE_NAME)) {
            Uri appFeed = obj.getContainingFeed().getUri();
            String action = content.optString(ANDROID_ACTION);
            String pkgName = content.optString(ANDROID_PACKAGE_NAME);
            String className = content.optString(ANDROID_CLASS_NAME);
    
            Intent launch = new Intent(action);
            launch.setClassName(pkgName, className);
            launch.addCategory(Intent.CATEGORY_LAUNCHER);
            // TODO: feed for related objs, not parent feed
            launch.putExtra(EXTRA_FEED_URI, appFeed);
            launch.putExtra(EXTRA_OBJ_URI, obj.getUri());

            List<ResolveInfo> resolved = context.getPackageManager().queryIntentActivities(launch, 0);
            if (resolved.size() > 0) {
                return launch;
            }

            Intent market = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkgName));
            return market;
        } else if (content.has(WEB_URL)) {
            Intent app = new Intent(Intent.ACTION_VIEW);
            app.setClass(context, WebAppActivity.class);
            app.putExtra(WebAppActivity.EXTRA_APP_URI, Uri.parse(content.optString(WEB_URL)));
            app.putExtra(WebAppActivity.EXTRA_APP_ID, obj.getAppId());
            app.setData(obj.getUri());
            return app;
        }
        return null;
    }

    @Override
	public View createView(Context context, ViewGroup frame) {
    	// XXX must be synchronized with ObjHelpers.renderGeneric()
    	return new LinearLayout(context);
    }

    @Override
    public void render(final Context context, final View view, DbObjCursor obj, boolean allowInteractions) throws Exception {
    	// XXX must be synchronized with ObjHelpers.renderGeneric()
    	LinearLayout frame = (LinearLayout) view;
    	frame.removeAllViews();

        PackageManager pm = context.getPackageManager();
        Drawable icon = null;
        String appName;
        if (obj.getJson() != null && obj.getJson().has(ANDROID_PACKAGE_NAME)) {
            appName = obj.getJson().optString(ANDROID_PACKAGE_NAME);
        } else {
            appName = "Unknown";
        }

        String pkg = obj.appId;
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setPackage(pkg);
        List<ResolveInfo> infos = pm.queryIntentActivities(intent, 0);
        if (infos != null && infos.size() > 0) {
            ResolveInfo info = infos.get(0);
            if (info.activityInfo.labelRes != 0) {
                appName = info.activityInfo.loadLabel(pm).toString();
                icon = info.loadIcon(pm);
            } else {
                appName = info.activityInfo.name;
            }
        } else {
            appName = obj.getJson().optString(ANDROID_PACKAGE_NAME);
            if (appName.contains(".")) {
                appName = appName.substring(appName.lastIndexOf(".") + 1);
            }
        }
         // TODO: Safer reference to containing view
        if (icon != null) {
            View parentView = (View)frame.getParent().getParent().getParent();
            ImageView avatar = (ImageView)parentView.findViewById(R.id.icon);
            avatar.setImageDrawable(icon);

            TextView label = (TextView)parentView.findViewById(R.id.name_text);
            label.setText(appName);
        }

        DbObjCursor childObj = getLatestChild(obj);
        if (childObj != null) {
            ObjHelpers.getFeedRenderer(childObj.getType()).render(context, frame, childObj,
                    allowInteractions);
        } else {
            String text;
            if (icon != null) {
                ImageView iv = new ImageView(context);
                iv.setImageDrawable(icon);
                iv.setAdjustViewBounds(true);
                iv.setMaxWidth(60);
                iv.setMaxHeight(60);
                iv.setLayoutParams(CommonLayouts.WRAPPED);
                frame.addView(iv);
                text = appName;
            } else {
                text = "New application: " + appName + ".";
            }
            // TODO: Show Market icon or app icon.
            TextView valueTV = new TextView(context);
            valueTV.setText(text);
            valueTV.setLayoutParams(new LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.WRAP_CONTENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT));
            valueTV.setGravity(Gravity.TOP | Gravity.LEFT);
            frame.addView(valueTV);
        }
    }

    /**
     * @see FeedModifiedObjHandler
     */
    private DbObjCursor getLatestChild(DbObjCursor parent) {
    	SQLiteDatabase db = parent.getDatabaseManager().getDatabase();
        Cursor c = null;
        String[] columns = new String[] { DbObjCache.LATEST_OBJ };
        String selection = DbObjCache.PARENT_OBJ + " = ?";
        String[] selectionArgs = new String[] { Long.toString(parent.objId) };
        String groupBy = null;
        String having = null;
        String orderBy = null;
        c = db.query(DbObjCache.TABLE, columns, selection, selectionArgs,
                groupBy, having, orderBy);
        try {
            if (c.moveToFirst()) {
                long id = c.getLong(0);
                return new DbObjCursor(parent.getDatabaseManager(), id);
            }
        } finally {
            c.close();
        }
        return null;
    }

	@Override
	public void getSummaryText(Context context, TextView view, FeedSummary summary) {
		JSONObject obj = summary.getJson();
		String appName;
        if (obj.has(ANDROID_PACKAGE_NAME)) {
            appName = obj.optString(ANDROID_PACKAGE_NAME);
        } else {
            appName = "Unknown";
        }
        
		view.setTypeface(null, Typeface.ITALIC);
		view.setText(summary.getSender() + "started a new session for " + appName + "!");
	}
}