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
import java.util.Iterator;

import mobisocial.crypto.IBHashedIdentity;
import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.musubi.App;
import mobisocial.musubi.feed.iface.DbEntryHandler;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MObject;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.socialkit.obj.MemObj;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;
import android.util.Log;

/**
 * An object that provides minimal information about introduced participants to a group.
 * This ensures the minimum latency in a person being able to tell who is participating
 * in a feed.  Other approaches, such as sending profile to people we discover (which we still do),
 * have one round-trip of latency.
 *
 */
public class OutOfBandInvitedObj extends DbEntryHandler {

    public static final String TYPE = "oobinvited";
    public static final String IDENTITIES = "identities";
    public static final String ID_AUTHORITY = "authority";
    public static final String ID_PRINCIPAL_HASH = "hash";

    @Override
    public String getType() {
        return TYPE;
    }

    public static MemObj from(Iterator<MIdentity> iterator) {
        return new MemObj(TYPE, json(iterator));
    }
    public static JSONObject json(Iterator<MIdentity> iterator){
    	JSONArray array = new JSONArray();
        JSONObject obj = new JSONObject();
        try{
        	while(iterator.hasNext()) {
        		MIdentity id = iterator.next();
	        	JSONObject identity = new JSONObject();
	        	identity.put(ID_AUTHORITY, id.type_.ordinal());
	        	identity.put(ID_PRINCIPAL_HASH, Base64.encodeToString(id.principalHash_, Base64.DEFAULT));
	        	array.put(identity);
        	}
        	obj.put(IDENTITIES, array);
        }catch(JSONException e){}
        return obj;
    }
    
    @Override
    public boolean processObject(Context context, MFeed feed, MIdentity sender,
    		MObject object) {
        SQLiteOpenHelper databaseSource = App.getDatabaseSource(context);
    	IdentitiesManager identitiesManager = new IdentitiesManager(databaseSource);

        if (object.json_ == null) {
            Log.w(TAG, "bad introduction format");
            return false;
        }
        JSONObject json;
        try {
            json = new JSONObject(object.json_);
        } catch (JSONException e) {
            Log.e(TAG, "Bad json in database", e);
            return false;
        }
        JSONArray array;
        try {
			array = json.getJSONArray(IDENTITIES);
		} catch (JSONException e) {
            Log.e(TAG, "json identity array missing", e);
            return false;
		}
        for(int i = 0; i < array.length(); ++i) {
        	JSONObject identity;
			try {
				identity = array.getJSONObject(i);
			} catch (JSONException e) {
				Log.e(TAG, "identity entry in introduction access error", e);
				continue;
			}
			int authority = -1;
			String principalHashString = null;
        	try {
				authority = identity.getInt(ID_AUTHORITY);
				principalHashString = identity.getString(ID_PRINCIPAL_HASH);
			} catch (JSONException e) {
				Log.e(TAG, "identity entry in introduction missing key fields", e);
				continue;
			}
        	
        	byte[] principalHash = Base64.decode(principalHashString, Base64.DEFAULT);
        	IBHashedIdentity hid = new IBHashedIdentity(Authority.values()[authority], principalHash, 0);
        	MIdentity ident = identitiesManager.getIdentityForIBHashedIdentity(hid);
        	if(ident == null) {
        		//this introduction has to be sent to both participants, so the low leve
        		//will already have added the identity
        		Log.e(TAG, "identity introduction for totally unseen identities");
        		continue;
        	}
        	if(!ident.hasSentEmail_) {
        		ident.hasSentEmail_ = true;
        		identitiesManager.updateIdentity(ident);
        	}
        }
        return false;
    }
}
