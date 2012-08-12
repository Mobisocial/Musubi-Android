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

package mobisocial.musubi.service;

import gnu.trove.procedure.TLongProcedure;
import gnu.trove.set.hash.TLongHashSet;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import mobisocial.metrics.UsageMetrics;
import mobisocial.musubi.App;
import mobisocial.musubi.feed.iface.DbEntryHandler;
import mobisocial.musubi.model.DbObjCache;
import mobisocial.musubi.model.DbRelation;
import mobisocial.musubi.model.MApp;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MFeed.FeedType;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MObject;
import mobisocial.musubi.model.helpers.DatabaseManager;
import mobisocial.musubi.obj.ObjHelpers;
import mobisocial.musubi.obj.handler.NotificationHandler;
import mobisocial.musubi.obj.handler.ProfileScanningObjHandler;
import mobisocial.musubi.objects.AppObj;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.provider.MusubiContentProvider.Provided;
import mobisocial.musubi.util.Util;
import mobisocial.socialkit.musubi.DbObj;
import mobisocial.socialkit.musubi.Musubi;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

/**
 * Scans for messages that should be sent over the network.
 * @see MusubiService
 * @see MessageDecodeProcessor
 */
class ObjPipelineProcessor extends ContentObserver {
    private static final String TAG = "ObjPipelineProcessor";
    static final long ONE_WEEK = 1000*60*60*24*7;
    private final Context mContext;
    private final DatabaseManager mDatabaseManager;
    private final Set<String> mPendingParentHashes;
    private final NotificationHandler mNotificationHandler;
    private final ProfileScanningObjHandler mProfileScanner;
    private final Musubi mMusubi;
    final HandlerThread mThread;

    public static ObjPipelineProcessor newInstance(Context context) {
        HandlerThread thread = new HandlerThread("ObjPipelineThread");
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
        return new ObjPipelineProcessor(context, thread);
    }

    private ObjPipelineProcessor(Context context, HandlerThread thread) {
        super(new Handler(thread.getLooper()));
        mThread = thread;
        mContext = context;
        mMusubi = App.getMusubi(context);
        mDatabaseManager = new DatabaseManager(context);
        mPendingParentHashes = new HashSet<String>();
        mNotificationHandler = new NotificationHandler(context);
        mProfileScanner = new ProfileScanningObjHandler();
    }

    @Override
    public void onChange(boolean selfChange) {
        final ContentResolver resolver = mContext.getContentResolver();
        SQLiteDatabase db = mDatabaseManager.getDatabase();
        long[] ids = getUnprocessedObjs();
        TLongHashSet feedsToNotify = new TLongHashSet(ids.length);

        for (long id : ids) {
            boolean setParent = false;
            long accessTime = new Date().getTime();
            MObject object = mDatabaseManager.getObjectManager().getObjectForId(id);
            //object can be null, because the delete obj might take it away from us
            if(object == null)
            	continue;
        	try {
	            MIdentity sender = mDatabaseManager.getIdentitiesManager().getIdentityForId(object.identityId_);
	            boolean keepObject = true;
	
	            assert(object != null);
	            assert(object.universalHash_ != null);
	            assert(!object.processed_);
	            assert(Util.shortHash(object.universalHash_) == object.shortUniversalHash_);

	            JSONObject json = null;
	            if (object.json_ != null) {
	                try {
	                    json = new JSONObject(object.json_);
	                } catch (JSONException e) {
	                    Log.e(TAG, "Ejecting object with bad json " + object.json_);
	                    mDatabaseManager.getObjectManager().delete(object.id_);
	                    continue;
	                }
	                if (json.has(ObjHelpers.TARGET_HASH)) {
	                    String relation = json.optString(ObjHelpers.TARGET_RELATION);
	                    boolean isParent = (relation == null ||
	                            relation.equals(DbRelation.RELATION_PARENT));
	                    String hashString = json.getString(ObjHelpers.TARGET_HASH);
	                    if (isParent && hashString != null && hashString.length() > 12) { 
                            byte[] hash;
                            try { 
                                hash = Util.convertToByteArray(hashString);
                                long parentId = mDatabaseManager.getObjectManager().getObjectIdForHash(hash);
                                if (parentId == -1) {
                                    Log.w(TAG, "no parent hash for " + hashString);
                                    if (object.lastModifiedTimestamp_ < System.currentTimeMillis() - ONE_WEEK) {
                                    	Log.e(TAG, "removing old object with no parent: " + object.json_);
                                    	mDatabaseManager.getObjectManager().delete(object.id_);
                                    	continue;
                                    }
                                    mPendingParentHashes.add(hashString);
                                    continue;
                                }
                                setParent = true;
                                object.parentId_ = parentId;
                            } catch (Exception e) {
                                Log.e(TAG, "bad parent hash " + hashString);
                            }
	                    }
	                }
	                if (json.has(AppObj.APP_NAME) || json.has(AppObj.ANDROID_PACKAGE_NAME)) {
	                    try {
	                        MApp app = mDatabaseManager.getAppManager().lookupApp(object.appId_);
	                        boolean appUpdated = false;
	                        if (json.has(AppObj.APP_NAME)) {
	                            String name = json.getString(AppObj.APP_NAME);
	                            if (!name.equals(app.name_)) {
	                                app.name_ = name;
	                                appUpdated = true;
	                            }
	                        }
	                        if (json.has(AppObj.ANDROID_PACKAGE_NAME)) {
	                            String pkg = json.getString(AppObj.ANDROID_PACKAGE_NAME);
	                            if (!pkg.equals(app.androidPackage_)) {
	                                app.androidPackage_ = pkg;
	                                appUpdated = true;
	                            }	                            
	                        }
    	                    
    	                    if (appUpdated) {
    	                        mDatabaseManager.getAppManager().updateApp(app);
    	                    }
	                    } catch (JSONException e) {
	                        Log.e(TAG, "error extracting from json", e);
	                    }
	                }
	            }

	            DbObj obj;
	            try {
	                obj = getDbObj(object, json);
	            } catch (JSONException e) {
	                Log.e(TAG, "Ejecting obj with bad json " + object.json_);
	                mDatabaseManager.getObjectManager().delete(object.id_);
	                continue;
	            }

	            mProfileScanner.handleObjFromNetwork(mContext, obj);
	            DbEntryHandler helper = ObjHelpers.forType(object.type_);

	            db.beginTransaction();
	            try {
		            MFeed feed = mDatabaseManager.getFeedManager().lookupFeed(object.feedId_);
		            if (helper.isRenderable(obj)) {
		                object.renderable_ = true;
		                feed.latestRenderableObjId_ = object.id_;
		                feed.latestRenderableObjTime_ = accessTime; // local, not remote
		                Uri feedUri = MusubiContentProvider.uriForItem(Provided.FEEDS, feed.id_);
		                boolean viewingFeed = feedUri.equals(App.getCurrentFeed());
		                if (!sender.owned_ && !viewingFeed) {
		                    feed.numUnread_++;
		                }
		                feedsToNotify.add(feed.id_);
		            }
		            keepObject = helper.processObject(mContext, feed, sender, object);
		            object.processed_ = true;

	                if (setParent && object.renderable_) {
	                    cacheParentId(db, object, object.parentId_);
	                    updateParentLastModified(db, object.parentId_);
	                }
	                if (feed.type_ == FeedType.ONE_TIME_USE) {
	                    mDatabaseManager.getFeedManager().deleteFeedAndMembers(feed);
	                } else {
	                    mDatabaseManager.getFeedManager().updateFeed(feed);
	                }
	                if (keepObject) {
	                    mDatabaseManager.getObjectManager().updateObjectPipelineMetadata(object);   
	                } else {
	                    mDatabaseManager.getObjectManager().delete(object.id_);
	                }
	                db.setTransactionSuccessful();
	            } finally {
	                db.endTransaction();
	            }
	
	            // If this object has pending children, notify this processor:
	            String hashString = Util.convertToHex(object.universalHash_);
                if (mPendingParentHashes.contains(hashString)) {
                    mPendingParentHashes.remove(hashString);
                    resolver.notifyChange(MusubiService.APP_OBJ_READY, this);
                }

	            resolver.notifyChange(MusubiContentProvider.uriForItem(Provided.OBJECTS, id), this);
	            mNotificationHandler.handle(helper, sender.owned_, obj);
        	} catch(Exception e) {
        	    Log.e(TAG, "Error processing object " + object.id_ + ": " + object.type_, e);
                mDatabaseManager.getObjectManager().delete(object.id_);
                UsageMetrics.getUsageMetrics(mContext).report(e);
        	}
        }

        if (feedsToNotify.size() > 0) {
            resolver.notifyChange(MusubiContentProvider.uriForDir(Provided.FEEDS), this);
            feedsToNotify.forEach(new TLongProcedure() {
                @Override
                public boolean execute(long id) {
                    resolver.notifyChange(MusubiContentProvider.uriForItem(Provided.FEEDS, id),
                            ObjPipelineProcessor.this);
                    return true;
                }
            });
        }
    }

    DbObj getDbObj(MObject object, JSONObject json) throws JSONException {
        String appId = mDatabaseManager.getAppManager().getAppIdentifier(object.appId_);
        String type = object.type_;
        String stringKey = object.stringKey_;
        long localId = object.id_;
        byte[] hash = object.universalHash_;
        byte[] raw = object.raw_;
        long senderId = object.identityId_;
        long feedId = object.feedId_;
        Integer intKey = object.intKey_;
        long timestamp = object.timestamp_;
        Long parentId = object.parentId_;
        return new DbObj(mMusubi, appId, feedId, parentId, senderId, localId, type, json, raw, intKey, stringKey, timestamp, hash);
    }

    // Visible for testing
    long[] getUnprocessedObjs() {
        String table = MObject.TABLE;
        String[] columns = new String[] { MObject.COL_ID };
        String selection = MObject.COL_ENCODED_ID + " IS NOT NULL AND " +
                MObject.COL_PROCESSED + " = 0";
        String[] selectionArgs = null;
        String groupBy = null, having = null, orderBy = null;
        Cursor c = mDatabaseManager.getDatabase().query(
                table, columns, selection, selectionArgs, groupBy, having, orderBy);
        int i = 0;
        long[] ids = new long[c.getCount()];
        while (c.moveToNext()) {
            ids[i++] = c.getLong(0);
        }
        try {
            return ids;
        } finally {
            c.close();
        }
    }

    void cacheParentId(SQLiteDatabase db, MObject child, long parentId) {
        ContentValues values = new ContentValues();
        values.put(DbObjCache.PARENT_OBJ, parentId);
        values.put(DbObjCache.LATEST_OBJ, child.id_);

        MObject other = getCachedLatest(parentId);
        if (other != null) {
            if (child.intKey_ == null || other.intKey_ == null || child.intKey_ > other.intKey_) {
                String whereClause = DbObjCache.PARENT_OBJ + " = ?";
                String[] whereArgs = new String[] { Long.toString(parentId) };
                db.update(DbObjCache.TABLE, values, whereClause, whereArgs);
            }
        } else {
            db.insert(DbObjCache.TABLE, null, values);
        }
    }

    private MObject getCachedLatest(long parentId) {
        SQLiteOpenHelper h = App.getDatabaseSource(mContext);
        Cursor c = null;
        String[] columns = new String[] { DbObjCache.LATEST_OBJ };
        String selection = DbObjCache.PARENT_OBJ + " = ?";
        String[] selectionArgs = new String[] { Long.toString(parentId) };
        String groupBy = null;
        String having = null;
        String orderBy = null;
        c = h.getWritableDatabase().query(DbObjCache.TABLE, columns, selection, selectionArgs,
                groupBy, having, orderBy);
        try {
            if (c.moveToFirst()) {
                long id = c.getLong(0);
                return mDatabaseManager.getObjectManager().getObjectForId(id);
            }
        } finally {
            c.close();
        }
        return null;
    }

    /**
     * Update the parent's last modified timestamp
     */
    void updateParentLastModified(SQLiteDatabase db, long id) {
        ContentValues cv = new ContentValues();
        cv.put(MObject.COL_LAST_MODIFIED_TIMESTAMP, new Date().getTime());
        String whereClause = MObject.COL_ID + "=?";
        String[] whereArgs = new String[] { Long.toString(id) };
        db.update(MObject.TABLE, cv, whereClause, whereArgs);
    }
}