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

package org.mobisocial.corral;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import mobisocial.crypto.IBHashedIdentity;
import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.crypto.IBIdentity;
import mobisocial.crypto.IBSignatureScheme.UserKey;
import mobisocial.musubi.App;
import mobisocial.musubi.encoding.NeedsKey.Signature;
import mobisocial.musubi.identity.AphidIdentityProvider;
import mobisocial.musubi.identity.IdentityProvider;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.model.helpers.UserKeyManager;
import mobisocial.musubi.provider.TestSettingsProvider.Settings;
import mobisocial.musubi.util.CertifiedHttpClient;
import mobisocial.musubi.util.Util;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class CorralTicketProvider {

	private String TAG = "CorralTicketProvider";
	
	private Context mContext;
	private static final String SERVER_URL = null;

	private static final int TYPE_OTHER = 0;
	private static final int TYPE_EMAIL = 1;
	private static final int TYPE_PHONE = 2;
	private static final int TYPE_OPENID = 3;
	private static final int TYPE_TWITTER = 4;
	private static final int TYPE_FACEBOOK = 5;
	
    private IdentitiesManager mIdentitiesManager;
    private SQLiteOpenHelper mDatabaseSource;
    private UserKeyManager mUserKeyManager;
    private IdentityProvider mIdentityProvider;
    
    private CookieStore cookieStore = null;
    private String myidentity=null;
    private String mysign=null;
    private String datestr=null;

	public CorralTicketProvider(Context context){
        mContext = context;
		cookieStore = null;
		
		// from AphidIdentityProvider.java
		mDatabaseSource = App.getDatabaseSource(mContext);
        mIdentitiesManager = new IdentitiesManager(mDatabaseSource);
        
        // from MusubiService.java
        Settings test_settings = App.getTestSettings(mContext);
        if(test_settings != null && test_settings.mAlternateIdentityProvider != null) {
        	mIdentityProvider = test_settings.mAlternateIdentityProvider;
        } else {
        	mIdentityProvider = new AphidIdentityProvider(mContext);
        }

		// from KeyUpdateHandler.java
        mUserKeyManager = new UserKeyManager(mIdentityProvider.getEncryptionScheme(),
				mIdentityProvider.getSignatureScheme(), mDatabaseSource);

	}
	
	public String getObjName(byte[] key){
		return CorralHelper.bin2hex(Util.sha256(key));
	}
	
	public String getDatestr(){
		return datestr;
	}
	
	public String getUploadTicket(String objName, String type, String length, String md5) throws IOException, JSONException{
		if (SERVER_URL == null) {
			Log.w(TAG, "no file upload service");
			return null;
		}
		String ticket = null;
		
		// get Identity
		setIdentityAndSignature(null);
		if(myidentity==null){
			Log.e(TAG, "Failed to get IDENTITY.");
			return null;
		}
		String shortidentity = myidentity.substring(0,66);
		String url = SERVER_URL+"user/"+shortidentity+"/object/"+objName+"/upload-ticket/"+type+"/"+length+"/"+md5;
		Log.d(TAG, "Now accessing: "+url);
		
		// Set up HTTP request
		DefaultHttpClient http = new CertifiedHttpClient(mContext);
		http.setCookieStore(cookieStore);
		HttpGet httpget = new HttpGet(url);
//		httpget.setHeader( "Connection", "Keep-Alive" );

	    HttpResponse response = http.execute(httpget);
		Log.d(TAG, "StatusCode: "+response.getStatusLine().getStatusCode());
		
		// TODO DELETE
		Header[] headers = response.getAllHeaders();
		for(Header header: headers){
			Log.d(TAG, "Header: name=" + header.getName() + ", val=" + header.getValue());
		}
		if(response.getStatusLine().getStatusCode()==HttpStatus.SC_FORBIDDEN){
	    	Header header = response.getFirstHeader("WWW-Authenticate");
	    	if(header==null){
	    		Log.e(TAG, "Failed to get Corral header.");
	    		return null;
	    	}
    		String headervalue = header.getValue();
    		if(!headervalue.startsWith("Corral=")){
	    		Log.e(TAG, "Failed to get Corral header.");
    			return null;
    		}
    		String[] str = headervalue.split("\"");
    		String challenge = str[1];
    		setIdentityAndSignature(challenge);
    		
    		if(mysign==null || myidentity==null){
    			Log.e(TAG, "Failed to get IDENTITY and SIGNATURE.");
    			return null;
    		}
	    	
	    	httpget.setHeader("Authorization", "Corral "+myidentity+" "+mysign);
	    	Log.d(TAG, "Corral "+myidentity+" "+mysign);
	    	
		    response = http.execute(httpget);
			Log.d(TAG, "StatusCode: "+response.getStatusLine().getStatusCode());
			
	    }
		if(response.getStatusLine().getStatusCode()==200){
			cookieStore = http.getCookieStore();
			Log.d(TAG, "Saved Cookie: "+cookieStore.getCookies().get(0).getValue());
	
			BufferedReader rd = new BufferedReader(new InputStreamReader(
					response.getEntity().getContent()));
			String responseStr = "";
			String line = "";
			while ((line = rd.readLine()) != null) {
				responseStr += line;
			}
			Log.d(TAG, "Server response:" + responseStr);
			JSONObject jso = new JSONObject(responseStr);
			ticket = jso.getString("ticket");
			datestr = jso.getString("date");
	    }
		
		return ticket;

	}
	
	public void putACL(String objName, MIdentity[] buddies) throws ClientProtocolException, IOException {
		if (SERVER_URL == null) {
			Log.w(TAG, "no file upload service");
			return;
		}
		Log.d(TAG, "---Start to Put ACL---");
		
		int i = 0;
		String[] ident = new String[buddies.length];
		for(MIdentity buddy: buddies){
			ident[i] = getShortIdentity(buddy);
			i++;
		}
		
		String shortidentity = getShortIdentity(mIdentitiesManager.getMyDefaultIdentity());
		
		String url = SERVER_URL+"user/"+shortidentity+"/object/"+objName+"/update-acl/";
		// Set up HTTP request
		DefaultHttpClient http = new CertifiedHttpClient(mContext);
		http.setCookieStore(cookieStore);
		HttpPost httppost = new HttpPost(url);
		String postdata = "";
		for(MIdentity buddy: buddies){
			String si = getShortIdentity(buddy);
			if(si!=null){
				if(postdata.length()>0){
					postdata+=",";
				}
				postdata+=si;
			}
		}
        List<NameValuePair> nameValuePair = new ArrayList<NameValuePair>(1);
        nameValuePair.add(new BasicNameValuePair("identities", postdata));

        httppost.setEntity(new UrlEncodedFormEntity(nameValuePair));
        HttpResponse response = http.execute(httppost);

        if(response.getStatusLine().getStatusCode()!=HttpStatus.SC_OK){
            throw new IOException("Failed to post ACL. StatusCode:"+response.getStatusLine().getStatusCode());
		}
	}

	public String getDownloadTicket(String objName) throws IOException, JSONException{
		if (SERVER_URL == null) {
			Log.w(TAG, "no file upload service");
			return null;
		}
		String ticket = null;
		
		// get Identity
		setIdentityAndSignature(null);
		if(myidentity==null){
			Log.e(TAG, "Failed to get IDENTITY.");
			return null;
		}
		String shortidentity = myidentity.substring(0,66);
		String url = SERVER_URL+"user/"+shortidentity+"/object/"+objName+"/download-ticket/";
		Log.d(TAG, "Now accessing: "+url);
		
		// Set up HTTP request
		DefaultHttpClient http = new CertifiedHttpClient(mContext);
		http.setCookieStore(cookieStore);
		HttpGet httpget = new HttpGet(url);
//		httpget.setHeader( "Connection", "Keep-Alive" );

	    HttpResponse response = http.execute(httpget);
		Log.d(TAG, "StatusCode: "+response.getStatusLine().getStatusCode());
		
		// TODO DELETE
		Header[] headers = response.getAllHeaders();
		for(Header header: headers){
			Log.d(TAG, "Header: name=" + header.getName() + ", val=" + header.getValue());
		}
		if(response.getStatusLine().getStatusCode()==HttpStatus.SC_FORBIDDEN){
	    	Header header = response.getFirstHeader("WWW-Authenticate");
	    	if(header==null){
	    		Log.e(TAG, "Failed to get Corral header.");
	    		return null;
	    	}
    		String headervalue = header.getValue();
    		if(!headervalue.startsWith("Corral=")){
	    		Log.e(TAG, "Failed to get Corral header.");
    			return null;
    		}
    		String[] str = headervalue.split("\"");
    		String challenge = str[1];
    		setIdentityAndSignature(challenge);
    		
    		if(mysign==null || myidentity==null){
    			Log.e(TAG, "Failed to get IDENTITY and SIGNATURE.");
    			return null;
    		}
	    	
	    	httpget.setHeader("Authorization", "Corral "+myidentity+" "+mysign);
	    	Log.d(TAG, "Corral "+myidentity+" "+mysign);
	    	
		    response = http.execute(httpget);
			Log.d(TAG, "StatusCode: "+response.getStatusLine().getStatusCode());
			
	    }
		if(response.getStatusLine().getStatusCode()==HttpStatus.SC_OK){
			cookieStore = http.getCookieStore();
			Log.d(TAG, "Saved Cookie: "+cookieStore.getCookies().get(0).getValue());
	
			BufferedReader rd = new BufferedReader(new InputStreamReader(
					response.getEntity().getContent()));
			String responseStr = "";
			String line = "";
			while ((line = rd.readLine()) != null) {
				responseStr += line;
			}
			JSONObject jso = new JSONObject(responseStr);
			ticket = jso.getString("ticket");
			datestr = jso.getString("date");
	    }
		return ticket;
	}
	
	private void setIdentityAndSignature(String challenge){
		
        MIdentity id = mIdentitiesManager.getMyDefaultIdentity();
		IBIdentity ident = IdentitiesManager.toIBIdentity(id,
				IdentitiesManager.computeTemporalFrameFromHash(id.principalHash_));
		try {
			
			UserKey userkey = mUserKeyManager.getSignatureKey(id, ident);
			int type = getTypeByAuthority(ident.authority_);

			myidentity = String.format("%02x", type)+
					CorralHelper.bin2hex(id.principalHash_)+
					String.format("%016x", ident.temporalFrame_);
			
			if(challenge!=null){
//				String signature = CorralHelper.bin2hex(userkey.key_);
				IBHashedIdentity from = new IBHashedIdentity(ident.authority_,
						ident.hashed_, ident.temporalFrame_);
				byte[] signed = mIdentityProvider.getSignatureScheme().sign(from, userkey, challenge.getBytes());
				mysign = CorralHelper.bin2hex(signed);
			}
    					
		} catch (Signature e) {
			Log.e(TAG, "Key needs to be updated...");
			// TODO Withdraw Signature key from server like this...
			// UserKey n_userkey = mIdentityProvider.syncGetSignatureKey(from);
			e.printStackTrace();
			return;
		}
	}

	private String getShortIdentity(MIdentity id){
		int type = getTypeByAuthority(id.type_);
		return String.format("%02x", type)+CorralHelper.bin2hex(id.principalHash_);
	}
	
	private int getTypeByAuthority(Authority ath){
		int type=TYPE_OTHER;
		switch(ath){
		case Email:
			type = TYPE_EMAIL;
			break;
		case PhoneNumber:
			type = TYPE_PHONE;
			break;
		case OpenID:
			type = TYPE_OPENID;
			break;
		case Twitter:
			type = TYPE_TWITTER;
			break;
		case Facebook:
			type = TYPE_FACEBOOK;
			break;
		}
		return type;
	}
}