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

import mobisocial.musubi.App;
import mobisocial.musubi.feed.iface.DbEntryHandler;
import mobisocial.musubi.model.DbLikeCache;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MObject;
import mobisocial.musubi.model.helpers.ObjectManager;
import mobisocial.musubi.obj.ObjHelpers;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.provider.MusubiContentProvider.Provided;
import mobisocial.musubi.util.Util;
import mobisocial.socialkit.obj.MemObj;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.util.Log;

public class LikeObj extends DbEntryHandler {
    private static final String TAG = "musubi";

    public static final String LABEL = "label";
    public static final String TYPE = "like_ref";

    public static MemObj forObj(String targetHashString) {
        return new MemObj(TYPE, json(targetHashString));
    }

    private static JSONObject json(String targetHashString) {
        JSONObject json = new JSONObject();
        try {
            json.put(ObjHelpers.TARGET_HASH, targetHashString);
        } catch (JSONException e) {
        }
        return json;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public boolean processObject(Context context, MFeed feed, MIdentity sender, MObject object) {
        SQLiteOpenHelper helper = App.getDatabaseSource(context);
        ObjectManager om = new ObjectManager(helper);

        if (object.json_ == null) {
            Log.w(TAG, "bad like format");
            return false;
        }
        JSONObject json;
        try {
            json = new JSONObject(object.json_);
        } catch (JSONException e) {
            Log.e(TAG, "Bad json in database", e);
            return false;
        }
        String hashString = json.optString(ObjHelpers.TARGET_HASH);
        if (hashString == null || hashString.length() == 0) {
            Log.e(TAG, "Bad hash " + hashString);
            return false;
        }
        byte[] hash;
        try {
            hash = Util.convertToByteArray(hashString);
        } catch (Exception e) {
            Log.e(TAG, "Couldn't convert hash " + hashString);
            return false;
        }
        long objId = om.getObjectIdForHash(hash);
        if (objId == -1) {
            // TODO: stubs for out-of-order objects
            Log.w(TAG, "unable to apply like");
            return false;
        }
        Cursor c = null;
        try {
            String table = DbLikeCache.TABLE;
            String[] columns = new String[] { DbLikeCache.COUNT, DbLikeCache.LOCAL_LIKE };
            String selection = DbLikeCache.PARENT_OBJ + " = ?";
            String[] selectionArgs = new String[] { Long.toString(objId) };
            c = helper.getWritableDatabase().query(table, columns, selection, selectionArgs,
                    null, null, null);

            int likeCount = 1;
            boolean fromOwnedIdentity = sender.owned_;
            int selfLikes = (fromOwnedIdentity) ? 1 : 0;
            if (c.moveToFirst()) {
                likeCount += c.getInt(0);
                selfLikes += c.getInt(1);

                ContentValues cv = new ContentValues();
                cv.put(DbLikeCache.COUNT, likeCount);
                cv.put(DbLikeCache.LOCAL_LIKE, selfLikes);
                helper.getWritableDatabase().update(table, cv, selection, selectionArgs);
            } else {
                ContentValues cv = new ContentValues();
                cv.put(DbLikeCache.PARENT_OBJ, objId);
                cv.put(DbLikeCache.COUNT, likeCount);
                cv.put(DbLikeCache.LOCAL_LIKE, selfLikes);
                helper.getWritableDatabase().insert(table, null, cv);
            }
            Uri feedUri = MusubiContentProvider.uriForItem(Provided.FEEDS, object.feedId_);
            context.getContentResolver().notifyChange(feedUri, null);
            return false;
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }
}