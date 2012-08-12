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

package mobisocial.musubi.webapp;

import java.net.URLDecoder;

import mobisocial.musubi.App;
import mobisocial.musubi.model.MApp;
import mobisocial.musubi.model.MObject;
import mobisocial.musubi.model.helpers.AppManager;
import mobisocial.musubi.model.helpers.SQLClauseHelper;
import mobisocial.musubi.objects.AppObj;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.provider.MusubiContentProvider.Provided;
import mobisocial.musubi.ui.MusubiBaseActivity;
import mobisocial.socialkit.Obj;
import mobisocial.socialkit.musubi.DbFeed;
import mobisocial.socialkit.musubi.DbIdentity;
import mobisocial.socialkit.musubi.DbObj;
import mobisocial.socialkit.musubi.Musubi;
import mobisocial.socialkit.obj.MemObj;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mobisocial.corral.ContentCorral;

import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

/**
 * Creates bindings in Javascript for the local Musubi database with
 * access granted to the specified app.
 */
class SocialKitJavascript {
    static final String MUSUBI_JS_VAR = "Musubi_android_platform";
    private static final String TAG = "socialkit.js";
    final Activity mContext;
    final Musubi mMusubi;

    /**
     * TODO: Manage apps through AppRegistry.
     */
    String mLoadedUrl;
    String mAppTitle;
    String mAppId;
    String mAppToken;

    private SocialKitJavascript(Activity context, String appId) {
        mContext = context;
        mMusubi = App.getMusubi(context);
        mAppId = appId;
    }

    public static SocialKitJavascript bindAccess(Activity context, String appId, long objId) {
        SocialKitJavascript skjs = new SocialKitJavascript(context, appId);
        skjs.mAppToken = ContentCorral.registerForAccessToken(appId, objId);
        return skjs;
    }

    public void unbind() {
        ContentCorral.unregisterAppToken(mAppToken);
    }

    public void setLoadedUrl(String url) {
        mLoadedUrl = url;
    }

    public String _queryFeed(String feedId, String query, String sortOrder) {
        Log.d(TAG, "querying " + feedId + ", " + query);
        Uri uri = MusubiContentProvider.uriForDir(Provided.OBJECTS);
        String[] projection = new String[] { DbObj.COL_ID };
        String selection = MObject.COL_FEED_ID + "=? AND " + MObject.COL_APP_ID + "=?";
        selection = SQLClauseHelper.andClauses(selection, query);
        String[] selectionArgs = new String[] { feedId, mAppId };
        if (!isDefined(sortOrder)) {
        	sortOrder = null;
        }
        Cursor c = mContext.getContentResolver().query(uri, projection, selection, selectionArgs, sortOrder);

        JSONArray results = new JSONArray();
        try {
            while (c.moveToNext()) {
                results.put(new SKDbObj(mMusubi.objForId(c.getLong(0))).toJson());
            }
            return results.toString();
        } finally {
            c.close();
        }
    }

    public String _querySubfeed(String objId, String query, String sortOrder) {
        Log.d(TAG, "querying subfeed " + objId + ", " + query);
        Uri uri = MusubiContentProvider.uriForDir(Provided.OBJECTS);
        String[] projection = new String[] { DbObj.COL_ID };
        String selection = MObject.COL_PARENT_ID + "=? AND " + MObject.COL_APP_ID + "=?";
        selection = SQLClauseHelper.andClauses(selection, query);
        String[] selectionArgs = new String[] { objId, mAppId };
        if (!isDefined(sortOrder)) {
        	sortOrder = null;
        }
        Cursor c = mContext.getContentResolver().query(uri, projection, selection, selectionArgs, sortOrder);

        JSONArray results = new JSONArray();
        try {
            while (c.moveToNext()) {
                results.put(new SKDbObj(mMusubi.objForId(c.getLong(0))).toJson());
            }
            return results.toString();
        } finally {
            c.close();
        }
    }


    private boolean isDefined(String jsString) {
    	return (jsString != null && jsString.length() > 0 && !jsString.equals("undefined"));
    }

    public String _urlForRaw(long objId) {
        return Uri.parse("http://127.0.0.1:" + ContentCorral.SERVER_PORT).buildUpon()
            .appendPath("raw").appendPath("" + objId)
            .appendQueryParameter("ticket", mAppToken).toString();
    }
    
    //TODO: delete me, I am redundant
    public void _back() {
    	_quit();
    }
    
    public void _postObjToFeed(String objJson, String feedIdString) {
        long feedId;
        JSONObject browserJson;
        try {
            feedId = Long.parseLong(feedIdString);
        } catch (NumberFormatException e) {
            Log.e(TAG, "bad feedId for postObj", e);
            return;
        }
        try {
            browserJson = new JSONObject(objJson);
        } catch (JSONException e) {
            Log.e(TAG, "Bad json from web");
            return;
        }

        DbFeed feed = mMusubi.getFeed(DbFeed.uriForId(feedId));
        try {
            Obj javaObj = objFromJson(mAppId, browserJson);
            feed.postObj(javaObj);
        } catch (Exception e) {
            Log.e(TAG, "error posting obj to feed: " + objJson, e);
        }
    }

    public void _postObjToSubfeed(String objJson, String feedIdString, String parentIdString) {
    	long feedId;
        long parentId;
        JSONObject browserJson;
        try {
            feedId = Long.parseLong(feedIdString);
            parentId = Long.parseLong(parentIdString);
        } catch (NumberFormatException e) {
            Log.e(TAG, "bad feedId for postObj", e);
            return;
        }
        try {
            browserJson = new JSONObject(objJson);
        } catch (JSONException e) {
            Log.e(TAG, "Bad json from web");
            return;
        }

        DbObj parentObj = mMusubi.objForId(parentId);
        if (parentObj == null || parentObj.getContainingFeed().getLocalId() != feedId) {
        	Log.e(TAG, "Failed to post to parent obj " + parentObj);
        	return;
        }

        try {
            Obj javaObj = objFromJson(mAppId, browserJson);
            parentObj.getSubfeed().postObj(javaObj);
        } catch (Exception e) {
            Log.e(TAG, "error posting obj to feed: " + objJson, e);
        }
    }

    public void _quit() {
        mContext.finish();
    }

    public void _setConfig(String config) {
        Log.d(TAG, "config " + config);
        try {
            JSONObject obj = new JSONObject(config);
            if (obj.has("title")) {
                mAppTitle = obj.getString("title");
            }
        } catch (JSONException e) {
            // don't sweat it.
        }
    }

    public void _log(String text) {
        Log.d(TAG, text);
    }

    public void showToast(String toast) {
        Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show();
    }

    public boolean isDeveloperModeEnabled() {
        return MusubiBaseActivity.isDeveloperModeEnabled(mContext);
    }

    Obj objFromJson(String appId, JSONObject source) throws JSONException {
        String type = source.getString(SKObj.FIELD_TYPE);
        JSONObject json = null;
        byte[] raw = null;
        Integer intKey = null;
        String stringKey = null;

        if (source.has(SKObj.FIELD_JSON)) {
            json = source.getJSONObject(SKObj.FIELD_JSON);
        } else if (source.has("data")) {
            Log.w(TAG, "old SocialKitJS version detected");
            json = source.getJSONObject("data");
        } else {
            json = new JSONObject();
        }

        try {
            json.put(AppObj.CLAIMED_APP_ID, appId);
            MApp app = new AppManager(App.getDatabaseSource(mContext)).lookupAppByAppId(appId);
            if (app != null && app.name_ != null) {
                json.put(AppObj.APP_NAME, app.name_);
            }
        } catch (JSONException e) {
            throw new IllegalStateException("Bad json libary", e);
        }

        if (source.has(SKObj.FIELD_RAW_DATA_URL)) {
            DataUri data = new DataUri(source.getString(SKObj.FIELD_RAW_DATA_URL));
            raw = data.data;
        }
        if (source.has(SKObj.FIELD_INT_KEY)) {
            intKey = source.getInt(SKObj.FIELD_INT_KEY);
        }
        if (source.has(SKObj.FIELD_STRING_KEY)) {
            stringKey = source.getString(SKObj.FIELD_STRING_KEY);
        }
        return new MemObj(type, json, raw, intKey, stringKey);
    }

    /**
     * JSON representations of common Musubi classes.
     */
    abstract class SocialKitApiConversion<JavaType> implements Jsonable {
        private final JavaType mNativeType;
        public SocialKitApiConversion(JavaType nativeType) {
            mNativeType = nativeType;
        }

        public JavaType getJavaImplementation() {
            return mNativeType;
        }
    }

    class SKUser extends SocialKitApiConversion<DbIdentity> {
        public SKUser(DbIdentity user) {
            super(user);
        }

        @Override
        public JSONObject toJson() {
            DbIdentity user = getJavaImplementation();
            JSONObject o = new JSONObject();
            try {
                o.put("name", user.getName());
                o.put("id", user.getLocalId());
                o.put("personId", user.getId());
            } catch (JSONException e) {}
            return o;
        }
    }

    /**
     * Don't confuse this with SKDbObj
     *
     */
    class SKObj extends SocialKitApiConversion<MemObj> {
        static final String FIELD_RAW_DATA_URL = "raw_data_url";
        static final String FIELD_JSON = "json";
        static final String FIELD_TYPE = "type";
        static final String FIELD_INT_KEY = "intKey";
        static final String FIELD_STRING_KEY = "stringKey";

        public SKObj(MemObj obj) {
            super(obj);
        }

        @Override
        public JSONObject toJson() {
            MemObj obj = getJavaImplementation();
            JSONObject json = new JSONObject();
            try {
                json.put(FIELD_TYPE, obj.getType());
                json.put(FIELD_JSON, obj.getJson());
                if (obj.getRaw() != null) {
                    json.put(FIELD_RAW_DATA_URL, null);//TODO
                }
                json.put(FIELD_INT_KEY, obj.getIntKey());
                json.put(FIELD_STRING_KEY, obj.getStringKey());
            } catch (JSONException e) {}
            return json;
        }
    }

    /**
     * Don't confuse this with SKObj
     *
     */
    class SKDbObj extends SocialKitApiConversion<DbObj> {
        static final String FIELD_OBJ_ID = "objId";
        static final String FIELD_SENDER = "sender";
        static final String FIELD_HASH = "hash";
        static final String FIELD_RAW_DATA_URL = "raw_data_url";
        static final String FIELD_JSON = "json";
        static final String FIELD_TYPE = "type";
        static final String FIELD_INT_KEY = "intKey";
        static final String FIELD_STRING_KEY = "stringKey";
        static final String FIELD_FEED_ID = "session";

        public SKDbObj(DbObj obj) {
            super(obj);
        }

        @Override
        public JSONObject toJson() {
            DbObj obj = getJavaImplementation();
            JSONObject json = new JSONObject();
            try {
                //json.put("data", obj.getJson());
                json.put(FIELD_HASH, obj.getUniversalHashString());
                json.put(FIELD_OBJ_ID, obj.getLocalId());
                json.put(FIELD_SENDER, new SKUser(obj.getSender()).toJson());

                json.put(FIELD_TYPE, obj.getType());
                json.put(FIELD_JSON, obj.getJson());
                json.put(FIELD_FEED_ID, obj.getContainingFeed().getLocalId());
                if (obj.getRaw() != null) {
                    json.put(FIELD_RAW_DATA_URL, null);//TODO
                }
                json.put(FIELD_INT_KEY, obj.getIntKey());
                json.put(FIELD_STRING_KEY, obj.getStringKey());
            } catch (JSONException e) {}
            return json;
        }
    }

    class SKFeed extends SocialKitApiConversion<DbFeed> {
        public SKFeed(DbFeed feed) {
            super(feed);
        }

        @Override
        public JSONObject toJson() {
            DbFeed feed = getJavaImplementation();
            JSONObject o = new JSONObject();
            try {
                o.put("name", feed.getUri().getLastPathSegment());
                o.put("uri", feed.getUri().toString());
                o.put("session", feed.getUri().getLastPathSegment());
                JSONArray m = new JSONArray();
                for (DbIdentity u : feed.getMembers()) {
                    m.put(new SKUser(u).toJson());
                }
                o.put("members", m);
            } catch (JSONException e) {}
            return o;
        }
    }

    interface Jsonable {
        public JSONObject toJson();
    }

    /**
     * A parsed "data:base64;f00"-style uri.
     */
    public static class DataUri {
        public final String mimeType;
        public final String parameters;
        public final byte[] data;

        public DataUri(String dataUri) throws IllegalArgumentException {
            if (dataUri == null) {
                throw new NullPointerException();
            }
            if (!dataUri.startsWith("data:")) {
                throw new IllegalArgumentException("Not a data uri");
            }
            int commaIndex = dataUri.indexOf(',');
            if (commaIndex == -1) {
                throw new IllegalArgumentException("DataUri has no data");
            }

            String mediaType = dataUri.substring(5, commaIndex);
            String content = dataUri.substring(commaIndex + 1);

            if (mediaType.length() == 0) {
                mediaType = "text/plain;charset=US-ASCII";
            }

            if (mediaType.endsWith(";base64")) {
                mediaType = mediaType.substring(0, mediaType.length() - 7);
                data = Base64.decode(content, Base64.DEFAULT);
                // strange, URL_SAFE not compatible with html library.
            } else {
                data = URLDecoder.decode(content).getBytes();
            }

            int colon = mediaType.indexOf(";");
            if (colon != -1) {
                mimeType = mediaType.substring(0, colon);
                parameters = mediaType.substring(colon + 1);
            } else {
                mimeType = mediaType;
                parameters = null;
            }
        }
    }
}