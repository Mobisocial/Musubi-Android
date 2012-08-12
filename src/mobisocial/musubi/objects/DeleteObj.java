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
import java.util.HashSet;
import java.util.Set;

import mobisocial.musubi.App;
import mobisocial.musubi.feed.iface.DbEntryHandler;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MObject;
import mobisocial.musubi.model.helpers.EncodedMessageManager;
import mobisocial.musubi.model.helpers.FeedManager;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.model.helpers.ObjectManager;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.provider.MusubiContentProvider.Provided;
import mobisocial.musubi.util.Util;
import mobisocial.socialkit.obj.MemObj;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * An obj requesting the deletion of some other obj.
 *
 */
public class DeleteObj extends DbEntryHandler {
    private static final String TAG = "dungbeetle";

    public static final String TYPE = "delete";
    public static final String HASH = "hash";
    public static final String HASHES = "hashes";
    /**
     * If true, delete Objs without marking them "deleted".
     */
    public static final String FORCE = "force";

    @Override
    public String getType() {
        return TYPE;
    }

    public static MemObj from(String[] hashStrings, boolean force) {
        return new MemObj(TYPE, json(hashStrings, force));
    }

    static JSONObject json(String[] hashStrings, boolean force) {
        JSONArray arr = new JSONArray();
        for (String hash : hashStrings) {
            arr.put(hash);
        }
        JSONObject obj = new JSONObject();
        try {
            obj.put(HASHES, arr);
            obj.put(FORCE, force);
        } catch(JSONException e) {}
        return obj;
    }

	@Override
	public boolean processObject(Context context, MFeed feed, MIdentity sender, MObject object) {
        try {
            JSONObject json = new JSONObject(object.json_);
            Set<byte[]> hashes = new HashSet<byte[]>();
            if (json.optJSONArray(HASHES) != null) {
                JSONArray jsonHashes = json.optJSONArray(HASHES);
                for (int i = 0; i < jsonHashes.length(); i++) {
                    try {
                        String hashStr = jsonHashes.getString(i);
                        hashes.add(Util.convertToByteArray(hashStr));
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to convert hash " + jsonHashes.optString(i));
                    }
                }
            } else {
                Log.d(TAG, "DeleteObj with no hashes!");
                return false;
            }
            Log.d(TAG, "marking or deleting " + hashes.size());
            markOrDeleteFeedObjs(context, feed, sender, hashes,
                    (json.has(FORCE) && json.optBoolean(FORCE)));

        } catch (JSONException e) {
            Log.e(TAG, "bad json in deleteObj", e);
        }
        return false;
	}

	private void markOrDeleteFeedObjs(Context context, MFeed feed, MIdentity sender,
	        Set<byte[]> hashes, boolean force) {
	    SQLiteOpenHelper db = App.getDatabaseSource(context);
	    EncodedMessageManager em = new EncodedMessageManager(db);
	    ObjectManager om = new ObjectManager(db);
	    FeedManager fm = new FeedManager(db);
	    IdentitiesManager im = new IdentitiesManager(db);
	    for (byte[] hash : hashes) {
	        long objId = -1;
	        try {
	            objId = om.getObjectIdForHash(hash);
	        } catch (IllegalArgumentException e) {
	            Log.e(TAG, "Bad hash of len " + hash.length);
	        }
	        if (objId == -1) {
	            String hashStr = Util.convertToHex(hash);
	            Log.w(TAG, "Object for " + hashStr + " not found for delete, and ooo deletion not currently supported.");
	            continue;
	        }
            MObject object = om.getObjectForId(objId);
            //TODO: store a dummy object or someting so we can block this one 
            if(object == null)
            	continue;
            em.delete(object.encodedId_);
            if(feed.latestRenderableObjId_ != null && feed.latestRenderableObjId_ == object.id_) {
            	feed.latestRenderableObjId_ = null;
            	feed.latestRenderableObjTime_  = null;
            	fm.updateFeed(feed);
            }
	        if (force || sender.owned_) {
	            om.delete(objId);
	            Log.d(TAG, "deleted " + objId);
	        } else {
	            MIdentity person = im.getIdentityForId(object.identityId_);
	            if (person.owned_) {
	                object.deleted_ = true;
	                om.updateObject(object);
	            } else {
	                om.delete(objId);
	                Log.d(TAG, "deleted " + objId);
	            }
	        }
	        //clean up corrupted feeds
            if(feed.latestRenderableObjId_ == null) {
                Long repl = om.getLatestFeedRenderable(feed.id_);
                Log.d(TAG, "replacing with " + (repl != null ? repl : "null"));
            	feed.latestRenderableObjId_ = repl;
            	feed.latestRenderableObjTime_ = repl != null ? new Date().getTime() : null;
            	fm.updateFeed(feed);
            }
            context.getContentResolver().notifyChange(
                    MusubiContentProvider.uriForItem(Provided.OBJECTS, object.id_), null);
	    }

        context.getContentResolver().notifyChange(
                MusubiContentProvider.uriForDir(Provided.OBJECTS), null);
        context.getContentResolver().notifyChange(
                MusubiContentProvider.uriForDir(Provided.FEEDS), null);
    }
}