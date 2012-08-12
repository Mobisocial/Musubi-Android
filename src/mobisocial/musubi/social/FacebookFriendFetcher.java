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

package mobisocial.musubi.social;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import org.apache.http.util.ByteArrayBuffer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.Bundle;
import android.util.Log;

import com.facebook.android.Facebook;
import com.facebook.android.FacebookError;
import com.facebook.android.Util;

public class FacebookFriendFetcher {
	public static final String TAG = "FacebookFriendFetch";
	static final boolean DBG = false;

	private static final int CHUNK_SIZE = 100;
	private static final int NUM_RETRIES = 3;
	
	private final Facebook mFacebook;
	private JSONObject mUserInfo = null;
	
	public FacebookFriendFetcher(Facebook facebook) {
		mFacebook = facebook;
	}
	
	/*
	 * Get a list of friends' uids, names, and profile picture URLs updated after lastUpdateTime
	 */
	public JSONArray getFriendInfo() throws Exception {
		try {
			// Ask for friend information as FQL
			// This query gets the user id, full name, and small profile picture
			// of all of the logged-in user's friends (and the user himself)
			if (mFacebook.isSessionValid()) {
				Bundle params = new Bundle();
				params.putString("q", "SELECT uid, name, pic_square FROM user " +
						"WHERE uid = me() OR uid in (SELECT uid2 FROM friend WHERE uid1 = me())");
				JSONObject data = Util.parseJson(mFacebook.request("fql", params));
				if (DBG) Log.d(TAG, data.toString());
				return data.getJSONArray("data");
			}
		} catch (FacebookError e) {
			Log.e(TAG, "Facebook request error", e);
		} 
		return null;
	}
	
	private JSONObject sendFacebookRequestWithRetries(String location)
	        throws IOException {
	    if (mFacebook.isSessionValid()) {
    	    for (int i = 0; i < NUM_RETRIES; i++) {
    	        try {
    	            JSONObject result = Util.parseJson(mFacebook.request(location));
    	            if (result != null) {
    	                return result;
    	            }
    	        } catch (Throwable e) {
    	            Log.e(TAG, "Facebook request error, e");
    	        }
    	    }
	    }
	    throw new IOException();
	}
	
	/*
	 *  Get current logged in user's Facebook login email
	 */
	public String getLoggedinUserEmail() {
		String email = null;
		if(mUserInfo == null) {
			try {
				if(mFacebook.isSessionValid()) {
					mUserInfo = sendFacebookRequestWithRetries("/me");
					email = mUserInfo.getString("email");
				}
			} catch (Throwable e) {
				Log.w(TAG, "Facebook request error, couldn't get email");
			}
		} else {
			try {
				email = mUserInfo.getString("email");
			} catch (JSONException e) {
				Log.w(TAG, "Facebook request error, couldn't find email");
			}
		}
		
		return email;
	}
	
	/*
	 *  Get current logged in user's Facebook id
	 */
	public String getLoggedinUserId() {
		String id = null;
		if(mUserInfo == null) {
			try {
				if(mFacebook.isSessionValid()) {
					mUserInfo = sendFacebookRequestWithRetries("/me");
					id = mUserInfo.getString("id");
				}
			} catch (Throwable e) {
				Log.e(TAG, "Facebook request error", e);
			}
		} else {
			try {
				id = mUserInfo.getString("id");
			} catch (JSONException e) {
				Log.e(TAG, "Facebook request error", e);
			}
		}
		
		return id;
	}
	
	/*
	 *  Get current logged in user's Facebook name
	 */
	public String getLoggedinUserName() {
		String name = null;
		if(mUserInfo == null) {
			try {
				if(mFacebook.isSessionValid()) {
					mUserInfo = sendFacebookRequestWithRetries("/me");
					name = mUserInfo.getString("name");
				}
			} catch (Throwable e) {
				Log.e(TAG, "Facebook request error", e);
			}
		} else {
			try {
				name = mUserInfo.getString("name");
			} catch (JSONException e) {
				Log.e(TAG, "Facebook request error", e);
			}
		}
		
		return name;
	}
	
	/*
	 *  Get current logged in user's Facebook profile picture
	 */
	public byte[] getLoggedinUserPhoto() {
		if(mFacebook.isSessionValid()) {
			Bundle params = new Bundle();
			params.putString("access_token", mFacebook.getAccessToken());
			String  url = "https://graph.facebook.com/me/picture" + "?" + Util.encodeUrl(params);
			return getImageFromURL(url);
		}
		
		return null;
	}
	
	
	/*
	 * Given a URL, return a byte array representing the data
	 */
	public static byte[] getImageFromURL(String remoteUrl) {
		try {
			// Grab the content
			URL url = new URL(remoteUrl);
			URLConnection ucon = url.openConnection();
			InputStream is = ucon.getInputStream();
			
			// Read the content chunk by chunk
			BufferedInputStream bis = new BufferedInputStream(is, 8192);
			ByteArrayBuffer baf = new ByteArrayBuffer(0);
			byte[] chunk = new byte[CHUNK_SIZE];
			int current = bis.read(chunk);
			while (current != -1) {
				baf.append(chunk, 0, current);
				current = bis.read(chunk);
			}
			return baf.toByteArray();
		} catch (IOException e) {
			Log.e(TAG, "HTTP error", e);
		}
		return null;
	}
}
