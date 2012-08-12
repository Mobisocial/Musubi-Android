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

import java.security.SecureRandom;

import mobisocial.crypto.IBHashedIdentity;
import mobisocial.musubi.feed.iface.DbEntryHandler;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.util.Base64;

public class SharedSecretObj extends DbEntryHandler {

    public static final String TYPE = "shared_secret";
    public static final String RAW = "raw";
    public static final SecureRandom random = new SecureRandom();

    /*public static byte[] getOrPushSecret(Context context, Contact other) {
    	if(other.secret != null) {
    		return other.secret;
    	}
    	//TODO: this really needs to be refactored into the contentprovider/helpers etc
        ContentValues values = new ContentValues();
        byte[] ss = new byte[32];
        random.nextBytes(ss);
        values.put(Contact.SHARED_SECRET, ss);
        context.getContentResolver().update(
            Uri.parse(DungBeetleContentProvider.CONTENT_URI + "/contacts"), 
            values, "_id=?", new String[]{String.valueOf(other.id)});
        Helpers.sendMessage(context, other.id, json(ss), TYPE);
        return ss;
    }*/

    public static JSONObject json(byte[] shared_secret){
        JSONObject obj = new JSONObject();
        try{
            obj.put(RAW, Base64.encodeToString(shared_secret, Base64.DEFAULT));
        }catch(JSONException e){}
        return obj;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    public void handleDirectMessage(Context context, IBHashedIdentity from, JSONObject obj){
        /*String raw_b64;
		try {
			raw_b64 = obj.getString(RAW);
		} catch (JSONException e) {
			e.printStackTrace();
			return;
		}
        byte[] ss = FastBase64.decode(raw_b64);
        if(from.secret != null && new BigInteger(from.secret).compareTo(new BigInteger(ss)) > 0) {
        	//ignore the new key according to a time independent metric...
        	return;
        }

        ContentValues values = new ContentValues();
        values.put(Contact.SHARED_SECRET, ss);
        context.getContentResolver().update(
            Uri.parse(DungBeetleContentProvider.CONTENT_URI + "/contacts"), 
            values, "_id=?", new String[]{String.valueOf(from.id)});
            */
    }
    
}
