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
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;

import mobisocial.crypto.IBHashedIdentity;
import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.musubi.App;
import mobisocial.musubi.Helpers;
import mobisocial.musubi.feed.iface.DbEntryHandler;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MObject;
import mobisocial.musubi.model.helpers.FeedManager;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.provider.MusubiContentProvider.Provided;
import mobisocial.musubi.service.MusubiService;
import mobisocial.musubi.util.Util;
import mobisocial.socialkit.Obj;
import mobisocial.socialkit.SignedObj;
import mobisocial.socialkit.obj.MemObj;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;
import android.util.Log;

//TODO: this an absymally lame copy-file, override needed methods, delete unchange methods implementation
//this needs hella cleanup
/**
 * An object that provides information about a participant who is requesting to join a group.
 * For now we just accept them blindly (the obj pipeline runs regardless of the 'accepted' flag
 * on the feed, so there is no other possibility ATM).
 *
 * A capability is ofcourse required so it isnt totally bat shit insane.
 */
public class JoinRequestObj extends DbEntryHandler {

    public static final String TYPE = "join_request";

    @Override
    public String getType() {
        return TYPE;
    }

    public static MemObj from(Collection<MIdentity> identities) {
        return new MemObj(TYPE, IntroductionObj.json(identities, false));
    }
    @Override
    public boolean isRenderable(SignedObj obj) {
    	// TODO Auto-generated method stub
    	return super.isRenderable(obj);
    }
    @Override
    public boolean processObject(Context context, MFeed feed, MIdentity sender,
    		MObject object) {
    	boolean anyChanged = false;
        SQLiteOpenHelper databaseSource = App.getDatabaseSource(context);
    	IdentitiesManager identitiesManager = new IdentitiesManager(databaseSource);
    	FeedManager feedManager = new FeedManager(databaseSource);
        if(identitiesManager.isMe(IdentitiesManager.toIBHashedIdentity(sender, 0))) {
        	return true;
        }

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
			array = json.getJSONArray(IntroductionObj.IDENTITIES);
		} catch (JSONException e) {
            Log.e(TAG, "json identity array missing for join", e);
            return false;
		}
        LinkedList<MIdentity> joined_identities = new LinkedList<MIdentity>();
        // TODO: use getIdentitiesForObj
        for(int i = 0; i < array.length(); ++i) {
            //TODO: enforce that a person can only join themself?  It's kind of nice to be able
        	//to join multiple people, but it enables annoying reflection attacks
        	JSONObject identity;
			try {
				identity = array.getJSONObject(i);
			} catch (JSONException e) {
				Log.e(TAG, "identity entry in join access error", e);
				continue;
			}
			int authority = -1;
			String principalHashString = null;
        	try {
				authority = identity.getInt(IntroductionObj.ID_AUTHORITY);
				principalHashString = identity.getString(IntroductionObj.ID_PRINCIPAL_HASH);
			} catch (JSONException e) {
				Log.e(TAG, "identity entry in introduction missing key fields", e);
				continue;
			}
        	String principal = null;
        	try {
				principal = identity.getString(IntroductionObj.ID_PRINCIPAL);
			} catch (JSONException e) {
			}
        	String name = null;
        	try {
				name = identity.getString(IntroductionObj.ID_NAME);
			} catch (JSONException e) {
			}
        	if(name == null && principal == null) {
        		//not much of an introduction
        		continue;
        	}
        	
        	byte[] principalHash = Base64.decode(principalHashString, Base64.DEFAULT);
        	IBHashedIdentity hid = new IBHashedIdentity(Authority.values()[authority], principalHash, 0);
        	MIdentity ident = identitiesManager.getIdentityForIBHashedIdentity(hid);
        	if(ident == null) {
        		//this introduction has to be sent to both participants, so the low level
        		//will already have added the identity
        		Log.e(TAG, "identity join for totally unseen identities");
        		continue;
        	}
        	if(ident.owned_) {
        		//we won't have a received profile version, so owned keeps us from self updating
        		continue;
        	}
        	//TODO: rely on  deferred handling for gray list participants
        	//TODO: check that the person is actually in the feed
        	boolean changed = false;
        	if(principal != null && ident.principal_ == null) {
            	if(!Arrays.equals(Util.sha256(principal.getBytes()), principalHash)) {
            		Log.e(TAG, "received mismatched principal and principal hash in join");
            		continue;
            	}
            	changed = true;
        		ident.principal_ = principal;
        	}
        	if(name != null && ident.receivedProfileVersion_ == 0) {
            	changed = true;
            	//each time someone join us, we'll just accept the new name
            	//as long as we never got a real profile.
        		ident.musubiName_ = name;
        	}
        	//TODO: low level already added them...need to do this some other way
        	//if(!feedManager.isFeedMember(feed.id_, ident.id_))
        		joined_identities.add(ident);
        	if(changed) {
        		identitiesManager.updateIdentity(ident);
        		anyChanged = true;
        	}
        }
        //we let the sucka in, so tell the other members of the feed
        Obj invitedObj = IntroductionObj.from(joined_identities, false);
        Helpers.sendToFeed(context, invitedObj, MusubiContentProvider.uriForItem(Provided.FEEDS_ID, feed.id_));
        if(anyChanged) {
        	context.getContentResolver().notifyChange(MusubiService.PRIMARY_CONTENT_CHANGED, null);
        }
        return true;
    }

}
