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

package mobisocial.musubi.provider;

import java.util.Date;
import java.util.List;

import mobisocial.musubi.feed.iface.DbEntryHandler;
import mobisocial.musubi.feed.iface.FeedRenderer;
import mobisocial.musubi.model.DbRelation;
import mobisocial.musubi.model.MApp;
import mobisocial.musubi.model.MDevice;
import mobisocial.musubi.model.MFact;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MFeedApp;
import mobisocial.musubi.model.MFeedMember;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MObject;
import mobisocial.musubi.model.SKFeedMembers;
import mobisocial.musubi.model.SKIdentities;
import mobisocial.musubi.model.SKObjects;
import mobisocial.musubi.model.helpers.DatabaseFile;
import mobisocial.musubi.model.helpers.DatabaseManager;
import mobisocial.musubi.model.helpers.DeviceManager;
import mobisocial.musubi.model.helpers.SQLClauseHelper;
import mobisocial.musubi.obj.ObjHelpers;
import mobisocial.musubi.objects.AppObj;
import mobisocial.musubi.objects.PictureObj;
import mobisocial.musubi.service.MusubiService;
import mobisocial.musubi.util.UriImage;
import mobisocial.socialkit.Obj;
import mobisocial.socialkit.musubi.DbObj;

import org.json.JSONException;
import org.json.JSONObject;
import org.mobisocial.corral.ContentCorral;
import org.mobisocial.corral.CorralDownloadClient;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;

/**
 * Manages Musubi's social database, providing access to third-party
 * applications with access control.
 */
public class MusubiContentProvider extends ContentProvider {
    static final String TAG = "MusubiContentProvider";

    public static final String SUPER_APP_ID = "mobisocial.musubi";
    public static final String UNKNOWN_APP_ID = "mobisocial.unknown.app";
    public static final String AUTHORITY = "org.musubi.db";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

    static final boolean DBG = false;
    private DatabaseManager mDatabaseManager;

	private static MusubiContentProvider sInstance;
	private DbInsertionThread mDbInsertionThread;

	static MusubiContentProvider getInstance() {
		return sInstance; // constructed by framework
	}

	private DatabaseManager getDatabaseManager() {
		if (mDatabaseManager == null) {
			mDatabaseManager = new DatabaseManager(getContext());
		}
		return mDatabaseManager;
	}
	public MusubiContentProvider() {
		sInstance = this;
	}

	public DbInsertionThread getDbInsertionThread() {
		if (mDbInsertionThread == null) {
			mDbInsertionThread = new DbInsertionThread();
			mDbInsertionThread.start();
		}
		return mDbInsertionThread;
	}

    /**
     * Types that can be queried in this Content Provider.
     * 
     * Other "pseudo-providers":
     *   subfeeds-- use the parent_id field of objects
     *   feed_members-- we auto-join on feeds._id if a query includes a feed_id parameter
     */
    public enum Provided {
        OBJECTS, OBJS_ID, FEEDS, FEEDS_ID, IDENTITIES, IDENTITIES_ID, FACTS, FEED_MEMBERS_ID;

        @Override
        public String toString() {
            switch (this) {
                case OBJECTS:
                case OBJS_ID:
                    return MObject.TABLE;
                case FEEDS:
                case FEEDS_ID:
                    return MFeed.TABLE;
                case IDENTITIES:
                case IDENTITIES_ID:
                    return MIdentity.TABLE;
                case FACTS:
                    return MFact.TABLE;
                case FEED_MEMBERS_ID:
                    return MFeedMember.TABLE;
                default: return null;
            }
        }
    };

    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    static {
        sUriMatcher.addURI(AUTHORITY, "objects", Provided.OBJECTS.ordinal());
        sUriMatcher.addURI(AUTHORITY, "objects/#", Provided.OBJS_ID.ordinal());
        sUriMatcher.addURI(AUTHORITY, "feeds", Provided.FEEDS.ordinal());
        sUriMatcher.addURI(AUTHORITY, "feeds/#", Provided.FEEDS_ID.ordinal()); // allow negative id
        sUriMatcher.addURI(AUTHORITY, "identities", Provided.IDENTITIES.ordinal());
        sUriMatcher.addURI(AUTHORITY, "identities/#", Provided.IDENTITIES_ID.ordinal());
        sUriMatcher.addURI(AUTHORITY, "feed_members/#", Provided.FEED_MEMBERS_ID.ordinal());
        sUriMatcher.addURI(AUTHORITY, "facts", Provided.FACTS.ordinal());
    }

    public static Uri createUri(String encodedPath) {
        return CONTENT_URI.buildUpon().appendEncodedPath(encodedPath).build();
    }

    public static Uri uriForDir(Provided type) {
        String path = type.toString();
        if (path == null) {
            throw new IllegalArgumentException("Can't get dir for provided type " + type);
        }
        return CONTENT_URI.buildUpon().appendEncodedPath(path).build();
    }

    public static Uri uriForItem(Provided type, long id) {
        String dir = type.toString();
        if (dir == null) {
            throw new IllegalArgumentException("Can't look up item for provided type " + type);
        }
        return CONTENT_URI.buildUpon().appendEncodedPath(dir).appendEncodedPath(Long.toString(id))
                .build();
    }

    @Override
    public String getType(Uri uri) {
        int match = sUriMatcher.match(uri);
        if (match == UriMatcher.NO_MATCH) {
            return null;
        }
        return getType(Provided.values()[match]);
    }

    public static String getType(Provided provided) {
        switch (provided) {
            case OBJECTS:
                return "vnd.android.cursor.dir/vnd.mobisocial.obj";
            case OBJS_ID:
                return "vnd.android.cursor.item/vnd.mobisocial.obj";
            case FEEDS:
                return "vnd.android.cursor.dir/vnd.mobisocial.feed";
            case FEEDS_ID:
                return "vnd.android.cursor.item/vnd.mobisocial.feed";
            case IDENTITIES:
                return "vnd.android.cursor.dir/vnd.mobisocial.identity";
            case IDENTITIES_ID:
                return "vnd.android.cursor.item/vnd.mobisocial.identity";
            case FEED_MEMBERS_ID:
                return "vnd.android.cursor.item/vnd.mobisocial.membership";
            case FACTS:
                return "vnd.android.cursor.dir/vnd.mobisocial.fact";
            default:
                throw new IllegalStateException("Unmatched-but-known content type");
        }
    }

    @Override
    public boolean onCreate() {
        // restoreDatabase();
        Log.i(TAG, "Creating MusubiContentProvider");
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        ContentResolver resolver = getContext().getContentResolver();
        final String realAppId = getCallingActivityId();

        if (realAppId == null) {
            Log.d(TAG, "No AppId for calling activity. Ignoring query.");
            return null;
        }

        if (DBG) {
            Log.d(TAG, "Processing query: " + selection + " on " + uri + " from appId " + realAppId);
        }

        int match = sUriMatcher.match(uri);
        if (match == UriMatcher.NO_MATCH) {
            Log.e(TAG, "Unmatched uri " + uri);
            return null;
        }
        selection = selectionFromUri(uri, selection);
        Cursor result = null;
        SQLiteDatabase db = getDatabaseManager().getDatabase();
        switch (Provided.values()[match]) {
            case FEEDS:
                if (!isSuperApp(realAppId)) {
                    return null;
                }
                result = db.query(MFeed.TABLE, projection, selection, selectionArgs,
                		null, null, sortOrder, null);
                break;
            case FEEDS_ID:
                throw new IllegalArgumentException("You probably want /objects?feed_id=<id>");
            case IDENTITIES:
                if (projection == null) {
                    projection = SKIdentities.getViewColumns();
                }
                if (!isSuperApp(realAppId)) {
                    MApp app = getDatabaseManager().getAppManager().ensureApp(realAppId);
                    String idSubSelection = "SELECT " + MObject.COL_IDENTITY_ID + " FROM "
                            + MObject.TABLE + " WHERE " + MObject.COL_APP_ID + " = " + app.id_;
                    selection = SQLClauseHelper.andClauses(selection, SKIdentities.COL_ID +
                            " in (" + idSubSelection + ")");
                }
                result = new IdentityCursorWrapper(getContext(), db.query(SKIdentities.TABLE, projection, selection,
                        selectionArgs, null, null, sortOrder, null));
                break;
            case IDENTITIES_ID:
                if (projection == null) {
                    projection = SKIdentities.getViewColumns();
                }
                Long idId = Long.parseLong(uri.getLastPathSegment());
                if (!appAllowedForIdentity(realAppId, idId)) {
                    return new MatrixCursor(projection);
                }
                selection = SQLClauseHelper.andClauses(selection, SKIdentities.COL_ID + "= ?");
                selectionArgs = SQLClauseHelper.andArguments(selectionArgs, idId);

                // IdentityCursorWrapper requires identity_id to be the last column.
                projection = SQLClauseHelper.andArguments(projection, SKIdentities.COL_ID);
                result = new IdentityCursorWrapper(getContext(), db.query(SKIdentities.TABLE, projection, selection,
                        selectionArgs, null, null, sortOrder, null));
                break;
            case OBJECTS:
                // Currently, apps are only allowed to see data from their own authority.
                if (!isSuperApp(realAppId)) {
                    selection = SQLClauseHelper.andClauses(selection, MObject.COL_APP_ID + " = ?");
                    selectionArgs = SQLClauseHelper.andArguments(selectionArgs, realAppId);
                }
                result = db.query(SKObjects.TABLE, projection, selection,
                        selectionArgs, null, null, sortOrder);
                break;
            case OBJS_ID:
                if (!isSuperApp(realAppId)) {
                    selection = SQLClauseHelper.andClauses(selection, MObject.COL_APP_ID + " = ?");
                    selectionArgs = SQLClauseHelper.andArguments(selectionArgs, realAppId);
                }
                // objects by database id
                Long objId = Long.parseLong(uri.getLastPathSegment());
                selection = SQLClauseHelper.andClauses(selection, MObject.COL_ID + " = ?");
                selectionArgs = SQLClauseHelper.andArguments(selectionArgs, new String[] {
                    Long.toString(objId)
                });
                result = db.query(MObject.TABLE, projection, selection,
                        selectionArgs, null, null, sortOrder);
                break;
            case FEED_MEMBERS_ID:
                if (projection == null) {
                    projection = SKFeedMembers.getViewColumns();
                }
                Long feedId = Long.parseLong(uri.getLastPathSegment());
                if (!appAllowedForFeed(realAppId, feedId)) {
                    Log.w(TAG, "access denied");
                    return new MatrixCursor(projection);
                }

                // IdentityCursorWrapper requires identity_id to be the last column.
                projection = SQLClauseHelper.andArguments(projection, SKFeedMembers.COL_IDENTITY_ID);

                // Restrict query to the given feed id
                selection = SQLClauseHelper.andClauses(selection, 
                        SKFeedMembers.TABLE + "." + SKFeedMembers.COL_FEED_ID + " = ?");
                selectionArgs = SQLClauseHelper.andArguments(selectionArgs,
                        new String[] { Long.toString(feedId) });

                // Wrap the cursor so we can return a "safe" name and thumbnail
                result = new IdentityCursorWrapper(getContext(), db.query(SKFeedMembers.TABLE,
                        projection, selection, selectionArgs, null, null, sortOrder));
                break;
            case FACTS:
                if (!SUPER_APP_ID.equals(realAppId)) {
                    String selection2 = MFact.COL_APP_ID + " in "
                            + SQLClauseHelper.appOrUnknown(realAppId);
                    selection = SQLClauseHelper.andClauses(selection, selection2);
                }
                result = db.query(MFact.TABLE, projection, selection,
                        selectionArgs, null, null, sortOrder);
                break;
        }

        if (result != null) {
            result.setNotificationUri(resolver, uri);
        } else {
            Log.w(TAG, "Unrecognized query: " + uri);
        }

        return result;
    }

    /**
     * Inserts a message locally that has been received from some agent,
     * typically from a remote device.
     */
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        if (DBG)
            Log.i(TAG, "Insert called on " + uri + ", " + values);
        final String appId = getCallingActivityId();
        if (appId == null) {
            Log.d(TAG, "No AppId for calling activity. Ignoring query.");
            return null;
        }

        int match = sUriMatcher.match(uri);
        if (Provided.values()[match] != Provided.OBJECTS) {
            Log.e(TAG, "Unsupported insert.");
            return null;
        }

        Uri result = insertObjWithContentValues(appId, values);
        return result;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        final String appId = getCallingActivityId();

        if (appId == null) {
            Log.d(TAG, "No AppId for calling activity. Ignoring query.");
            return 0;
        }

        if (!isSuperApp(appId)) {
            throw new RuntimeException("Operation not yet supported");
        }

        int match = sUriMatcher.match(uri);
        if (match == UriMatcher.NO_MATCH) {
            Log.e(TAG, "Unmatched uri " + uri);
            return -1;
        }

        return 0;
    }

    String selectionFromUri(Uri uri, String base) {
        if (uri.getQuery() == null) return base;
        return SQLClauseHelper.andClauses(base, uri.getQuery().replace("&", " AND "));
    }

    /**
     * Modifies the given values to reflect the relations
     */
    private static void prepareObjRelations(SQLiteDatabase db, ContentValues values) throws DbInsertionError {
        Long parentId = values.getAsLong(DbObj.COL_PARENT_ID);
        if (parentId == null) {
            return;
        }

        String table = DbObj.TABLE;
        String[] columns = new String[] {
            DbObj.COL_UNIVERSAL_HASH
        };
        String selection = DbObj.COL_ID + " = ?";
        String[] selectionArgs = new String[] {
            Long.toString(parentId)
        };
        String groupBy = null, having = null, orderBy = null;
        Cursor c = db.query(table, columns, selection, selectionArgs, groupBy, having, orderBy);
        if (!c.moveToFirst()) {
            throw new DbInsertionError("Could not find parent obj " + parentId);
        }

        byte[] parentObjHash = c.getBlob(0);
        if (parentObjHash == null) {
            throw new DbInsertionError("Parent hash not available");
        }
        try {
            JSONObject json;
            if (values.containsKey(DbObj.COL_JSON)) {
                json = new JSONObject(values.getAsString(MObject.COL_JSON));
            } else {
                json = new JSONObject();
            }

            json.put(ObjHelpers.TARGET_HASH, ObjHelpers.hashToString(parentObjHash));
            json.put(ObjHelpers.TARGET_RELATION, DbRelation.RELATION_PARENT);
            values.put(DbObj.COL_JSON, json.toString());
        } catch (JSONException e) {
            throw new DbInsertionError("Error parsing json", e);
        }
    }

    /**
     * Validates the content values and returns a row for insertion.
     */
    private MObject rowForContentValues(String realAppId, ContentValues values) throws DbInsertionError {
        long timestamp = new Date().getTime();

        Long feedId = values.getAsLong(DbObj.COL_FEED_ID);
        if (feedId == null) {
            throw new DbInsertionError("Feed id required");
        }

        if (!appAllowedForFeed(realAppId, feedId)) {
            throw new DbInsertionError("App not allowed in feed");
        }

        Long identityId = getDatabaseManager().getFeedManager().getOwnedIdentityForFeed(feedId);
        if (identityId == null) {
            throw new DbInsertionError("No owned id for this feed.");
        }

        DeviceManager dm = getDatabaseManager().getDeviceManager();
        long deviceName = dm.getLocalDeviceName();
        MDevice device = dm.getDeviceForName(identityId, deviceName);
        if (device == null) {
            throw new DbInsertionError("No device found for id " + identityId);
        }

        String type = values.getAsString(MObject.COL_TYPE);
        if (type == null) {
            throw new DbInsertionError("Type is required");
        }

        Long parentId = null;
        if (values.containsKey(DbObj.COL_PARENT_ID)) {
            parentId = values.getAsLong(DbObj.COL_PARENT_ID);
            if (parentId == null) {
                throw new DbInsertionError("Bad format for parent_id");
            }
        }

        String jsonSrc = null;
        JSONObject json = null;
        if (values.containsKey(DbObj.COL_JSON)) {
            jsonSrc = values.getAsString(DbObj.COL_JSON);
            if (jsonSrc.length() > DatabaseFile.SIZE_LIMIT) {
                throw new DbInsertionError("Message json size too large for sending");
            }
            try {
                json = new JSONObject(jsonSrc);
            } catch (JSONException e) {
                throw new DbInsertionError("Could not parse json", e);
            }
        }
        String appId = realAppId;
        if (isSuperApp(realAppId)) {
            if (json != null && json.has(AppObj.CLAIMED_APP_ID)) {
                appId = json.optString(AppObj.CLAIMED_APP_ID);
                json.remove(AppObj.CLAIMED_APP_ID);
                if (appId == null || appId.length() == 0) {
                    Log.e(TAG, "Bad app listed. Reverting to real app id.");
                    appId = realAppId;
                }
            } else if (values.containsKey(ObjHelpers.CALLER_APP_ID)) {
                appId = values.getAsString(ObjHelpers.CALLER_APP_ID);
            }
        }

        Integer intKey = null;
        if (values.containsKey(DbObj.COL_INT_KEY)) {
            intKey = values.getAsInteger(DbObj.COL_INT_KEY);
            if (intKey == null) {
                throw new DbInsertionError("Bad format for int field");
            }
        }

        String name = null;
        if (values.containsKey(DbObj.COL_STRING_KEY)) {
            name = values.getAsString(DbObj.COL_STRING_KEY);
            if (name == null) {
                throw new DbInsertionError("Bad format for name field");
            }
            if (name.length() > DatabaseFile.SIZE_LIMIT) {
                throw new DbInsertionError("Message name too large for sending");
            }
        }

        byte[] raw = null;
        if (values.containsKey(DbObj.COL_RAW)) {
            raw = values.getAsByteArray(DbObj.COL_RAW);
            if (raw == null) {
                throw new DbInsertionError("Bad format for raw field");
            }

            // XXX this seems like a horrible place for this.

        	//scale down pictures that come in too large
            //this also stored them in the corral
            //if its already in the corral, don't rescale because we already
            //compressed this, and we don't really want another copy
        	if(type.equals(PictureObj.TYPE) && json != null && !json.has("localUri")) {
        		byte[] new_raw = handleDownscalePicture(raw, json);
        		if(raw != new_raw) {
        			jsonSrc = json.toString();
        		}
        		raw = new_raw;
        	}
            if (raw.length > DatabaseFile.SIZE_LIMIT) {
        		throw new DbInsertionError("Messasge raw size too large for sending");
            }
        }

        boolean renderable = false;
        DbEntryHandler h = ObjHelpers.forType(type);
        if (h instanceof FeedRenderer) {
            renderable = true;
        } else {
            if (json != null && json.has(Obj.FIELD_HTML)) {
                renderable = true;
            }
        }

        MApp app = getDatabaseManager().getAppManager().ensureApp(appId);
        MObject o = new MObject();
        o.feedId_ = feedId;
        o.identityId_ = identityId;
        o.deviceId_ = device.id_;
        o.parentId_ = parentId;
        o.appId_ = app.id_;
        o.timestamp_ = timestamp;
        o.type_ = type;
        o.stringKey_ = name;
        o.json_ = jsonSrc;
        o.raw_ = raw;
        o.intKey_ = intKey;
        o.lastModifiedTimestamp_ = timestamp;
        o.processed_ = false;
        o.renderable_ = renderable;
        return o;
    }

    public static void insertInBackground(Obj obj, Uri feedUri, String assumedAppId) {
    	final ContentValues values = DbObj.toContentValues(feedUri, null, obj);
        if (assumedAppId != null) {
            values.put(ObjHelpers.CALLER_APP_ID, assumedAppId);
        }

        Handler insertHandler = getInstance().getDbInsertionThread().getHandler();
        Message msg = insertHandler.obtainMessage();
        msg.what = DbInsertionThread.INSERT;
        msg.obj = values;
        insertHandler.sendMessage(msg);
    }

    private byte[] handleDownscalePicture(byte[] raw, JSONObject json) {
    	//TODO: corral storage directly from within the web app
    	Uri corralUri = ContentCorral.storeContent(getContext(), raw, CorralDownloadClient.typeForBytes(raw, PictureObj.TYPE));
    	if(corralUri == null)
    		return raw;
    	try {
			byte[] new_raw = new UriImage(getContext(), corralUri).getResizedImageData(PictureObj.MAX_IMAGE_WIDTH, PictureObj.MAX_IMAGE_HEIGHT, PictureObj.MAX_IMAGE_SIZE);
    		json.put("localUri", corralUri);
    		return new_raw;
		} catch (Throwable t) {
			return raw;
		}
	}

	String getCallingActivityId() {
        int pid = Binder.getCallingPid();
        if (pid == Process.myPid()) {
            return SUPER_APP_ID;
        }

        ActivityManager am = (ActivityManager) getContext().getSystemService(
                Activity.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> lstAppInfo = am.getRunningAppProcesses();

        for (ActivityManager.RunningAppProcessInfo ai : lstAppInfo) {
            if (ai.pid == pid) {
                return ai.processName;
            }
        }

        Log.d(TAG, "Missing app id for pid " + pid);
        Log.d(TAG, "Local pid " + Process.myPid());
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        final String appId = getCallingActivityId();
        if (appId == null) {
            Log.d(TAG, "No AppId for calling activity. Ignoring query.");
            return 0;
        }

        int match = sUriMatcher.match(uri);
        if (match == UriMatcher.NO_MATCH) {
            Log.d(TAG, "Unmatched uri " + uri);
            return -1;
        }

        switch (Provided.values()[match]) {
            case OBJS_ID:
                return -111;
            default:
                return 0;
        }
    }

    public static boolean isSuperApp(String appId) {
        return SUPER_APP_ID.equals(appId);
    }

    boolean appAllowedForIdentity(String appId, long identityId) {
        /**
         * SELECT
         * FROM objects, apps, feed_members
         * WHERE
         * fm.identity_id = ?
         * and objects.feed_id = fm.feed_Id
         * and objects.app_id = apps._id
         * and apps.app_id = ?
         */
        if (SUPER_APP_ID.equals(appId)) {
            return true;
        }
        StringBuilder sql = new StringBuilder(100)
            .append("SELECT ").append(MApp.TABLE).append(".").append(MApp.COL_APP_ID)
            .append(" FROM ").append(MFeedMember.TABLE).append(",")
            .append(MObject.TABLE).append(",").append(MApp.TABLE)
            .append(" WHERE ").append(MFeedMember.TABLE).append(".").append(MFeedMember.COL_FEED_ID)
            .append(" = ").append(MObject.TABLE).append(".").append(MObject.COL_FEED_ID)
            .append(" AND ").append(MFeedMember.TABLE).append(".").append(MFeedMember.COL_IDENTITY_ID)
            .append(" = ? AND ").append(MObject.TABLE).append(".").append(MObject.COL_APP_ID)
            .append(" = ").append(MApp.TABLE).append(".").append(MApp.COL_ID)
            .append(" AND ").append(MApp.TABLE).append(".").append(MApp.COL_APP_ID).append("=?")
            .append(" LIMIT 1");
        SQLiteDatabase db = getDatabaseManager().getDatabase();
        String[] selectionArgs = new String[] { Long.toString(identityId), appId };

        Cursor c = db.rawQuery(sql.toString(), selectionArgs);
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    boolean appAllowedForFeed(String appId, long feedId) {
        if (SUPER_APP_ID.equals(appId)) {
            return true;
        }
        StringBuilder sql = new StringBuilder(100)
            .append("SELECT ").append(MFeedApp.COL_ID).append(" FROM ").append(MFeedApp.TABLE)
            .append(" WHERE ").append(MFeedApp.COL_FEED_ID).append("=? AND ")
            .append(MFeedApp.COL_APP_ID).append("=? LIMIT 1");
        SQLiteDatabase db = getDatabaseManager().getDatabase();
        MApp app = getDatabaseManager().getAppManager().ensureApp(appId);
        String[] selectionArgs = new String[] { Long.toString(feedId), Long.toString(app.id_) };

        Cursor c = db.rawQuery(sql.toString(), selectionArgs);
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    static class DbInsertionError extends Exception {
        private static final long serialVersionUID = 1972051851209225969L;

        public DbInsertionError(String msg) {
            super(msg);
        }

        public DbInsertionError(String msg, Throwable cause) {
            super(msg, cause);
        }
    }

    Uri insertObjWithContentValues(String parentAppId, ContentValues values) {
    	MObject object;
        try {
            prepareObjRelations(getDatabaseManager().getDatabase(), values);
            object = rowForContentValues(parentAppId, values);
        } catch (DbInsertionError e) {
            if (DBG) Log.e(TAG, "DbInsertionError", e);
            return null;
        }

        try {
        	getDatabaseManager().getObjectManager().insertObject(object);
        } catch (Throwable e) {
        	 Log.e(TAG, "Error inserting object", e);
             return null;
        }
        Uri result = uriForItem(Provided.OBJS_ID, object.id_);
        ContentResolver resolver = getContext().getContentResolver();
        if (result != null) {
            if (DBG) Log.d(TAG, "just inserted " + values.getAsString(MObject.COL_JSON));
            resolver.notifyChange(MusubiService.PLAIN_OBJ_READY, null);
            resolver.notifyChange(result, null);
        }
        return result;
    }

    class DbInsertionThread extends Thread {
    	public static final int INSERT = 0;
    	private Handler mHandler;

    	public DbInsertionThread() {
    		setName("DbInsertionThread");
    	}

    	@Override
    	public void run() {
    		Looper.prepare();
    		mHandler = new Handler() {
    			@Override
    			public void handleMessage(Message msg) {
    				switch (msg.what) {
    				case INSERT:
    					ContentValues cv = (ContentValues)msg.obj;
    					insertObjWithContentValues(SUPER_APP_ID, cv);
    				}
    			}
    		};
    		synchronized (this) {
    			notify();
    		}
    		Looper.loop();
    	}

    	public Handler getHandler() {
    		if (mHandler == null) {
    			synchronized (this) {
    				while (mHandler == null) {
    					try {
    						wait();
    					} catch (InterruptedException e) {}
    				}
    			}
    		}
    		return mHandler;
    	}
    }
}
