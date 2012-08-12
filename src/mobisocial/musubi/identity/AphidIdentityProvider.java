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

package mobisocial.musubi.identity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mobisocial.crypto.IBEncryptionScheme;
import mobisocial.crypto.IBHashedIdentity;
import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.crypto.IBIdentity;
import mobisocial.crypto.IBSignatureScheme;
import mobisocial.crypto.IBSignatureScheme.UserKey;
import mobisocial.musubi.App;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MMyAccount;
import mobisocial.musubi.model.MPendingIdentity;
import mobisocial.musubi.model.PresenceAwareNotify;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.model.helpers.MyAccountManager;
import mobisocial.musubi.model.helpers.PendingIdentityManager;
import mobisocial.musubi.ui.SettingsActivity;
import mobisocial.musubi.ui.fragments.AccountLinkDialog;
import mobisocial.musubi.util.CertifiedHttpClient;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;

public class AphidIdentityProvider implements IdentityProvider {
	public static final String TAG = "AphidIdentityProvider";
	
	private static final String ENCRYPTION_PUBLIC_PARAMETERS =
		"BUV+jbo5aCVPJgdETzxaemL2WAQVDWRdYw9qlt8jl6LlMfdnGFkh1gEjjVnw4jEhVafxg+D4xIBO" +
		"xHVe4SClyInvNa/EO9KkGnpkZI9MyKnwAB1YmKr/XSx34QC4TUncF7aGT95pAYsNZnkf0cZC7IX8" +
		"8oZQGh+FUokAIMlbuAZfm4m+qqnMFOiYCTz/P4MBHHUcD9eZz9ZYWnzGf8TQaGZAu7UBIXxTZ453" +
		"+7DzmLbhsi97c5s1XAIABM1AlQLFnpW0F0ErCeBh46BozB8Yojr/CxLq+Fda6QKtqMRMXIlCWxaF" +
		"W1ItocmQ3ca/dZ5u2hVM5QQ3C0eXf/jOyln2sn3BIwwe3vpTlrAHcBZZ7/S5G2rsXByRIeMHr2gE" +
		"GgJS9PV0q5zclv7jmSuXKwajI582G91K0pAe6YHwwhF1a3K5iYFFBFcQFXmYlFmj2TAmmohiMiaV" +
		"bWk2WBPJStN6+ml1AjcqT0DtEOTdcJtkC4zv1hR86WBoCNIIhmWF3tOLswoPRHLqp7Qfijex75TJ" +
		"MTxGGHK8fh6B0duHS7dNvhAUMB4VDVfJt0tq";
	private static final String SIGNATURE_PUBLIC_PARAMETERS = 
		"I2rwl+saWhxnJmficrgH1ZK79/gFnozVJmJAUdCj/9dvdBGhAi+d9QEggAW8I7GisfcXg26nHJkm" +
		"1YEDQxCJ8kQ6ptq1t//Yypsy4FaE2GWlAA==";
	
	private static final String URL_SCHEME = "https";
	private static final String SERVER_LOCATION = "aphid.musubi.us";
	private static final String KEYS_PATH = "/ibe/keys.py";
	private static final String CLAIM_PATH = "/ibe/claim.py";
	
	private static final String SERVER_NAME_SIG = "sig_key";
	private static final String SERVER_NAME_CRYPTO = "crypto_key";
	
	private static final String SIGNIN_TEXT = "Sign-In Required";
	
	private final Context mContext;
	
	private IBEncryptionScheme mEncryptionScheme;
	private IBSignatureScheme mSignatureScheme;
	private IdentitiesManager mIdentitiesManager;
	private PendingIdentityManager mPendingIdentityManager;
	
	private Map<Pair<Authority, String>, String> mKnownTokens;
	
	public AphidIdentityProvider(Context context) {
		mContext = context;
		mEncryptionScheme = new IBEncryptionScheme(
			Base64.decode(ENCRYPTION_PUBLIC_PARAMETERS, Base64.DEFAULT)
		);
		mSignatureScheme = new IBSignatureScheme(
			Base64.decode(SIGNATURE_PUBLIC_PARAMETERS, Base64.DEFAULT)
		);
		mIdentitiesManager = new IdentitiesManager(App.getDatabaseSource(mContext));
		mPendingIdentityManager = new PendingIdentityManager(App.getDatabaseSource(mContext));
		mKnownTokens = new HashMap<Pair<Authority, String>, String>();
	}
	
	public IBEncryptionScheme getEncryptionScheme() {
		return mEncryptionScheme;
	}
	
	///Create a new instance of /aphididentityprovider and call this to get the encryption
	//scheme so you can sign a challenge.
	public IBSignatureScheme getSignatureScheme() {
		return mSignatureScheme;
	}
	
	public UserKey syncGetSignatureKey(IBIdentity ident)
			throws IdentityProviderException {
		byte[] rawUserKey = getAphidResultForIdentity(ident, SERVER_NAME_SIG);
		assert(rawUserKey != null);
		return new UserKey(rawUserKey);
	}
	
	public mobisocial.crypto.IBEncryptionScheme.UserKey syncGetEncryptionKey(IBIdentity ident)
			throws IdentityProviderException {
		byte[] rawUserKey = getAphidResultForIdentity(ident, SERVER_NAME_CRYPTO);
		assert(rawUserKey != null);
		return new mobisocial.crypto.IBEncryptionScheme.UserKey(rawUserKey);
	}
	
	public UserKey syncGetSignatureKey(IBHashedIdentity hid)
			throws IdentityProviderException {
		IBIdentity ident = mIdentitiesManager.getIBIdentityForIBHashedIdentity(hid);
		if(ident == null) {
			throw new RuntimeException("you must know the real principal to request an aphid signature secret");
		}
		byte[] rawUserKey = getAphidResultForIdentity(ident, SERVER_NAME_SIG);
		assert(rawUserKey != null);
		return new UserKey(rawUserKey);
	}
	
	public mobisocial.crypto.IBEncryptionScheme.UserKey syncGetEncryptionKey(IBHashedIdentity hid)
			throws IdentityProviderException {
		IBIdentity ident = mIdentitiesManager.getIBIdentityForIBHashedIdentity(hid);
		if(ident == null) {
			throw new RuntimeException("you must know the real principal to request an aphid encryption secret");
		}
		byte[] rawUserKey = getAphidResultForIdentity(ident, SERVER_NAME_CRYPTO);
		assert(rawUserKey != null);
		return new mobisocial.crypto.IBEncryptionScheme.UserKey(rawUserKey);
	}
	
	/*
	 * Certain identities (e.g. phone numbers) require the server to solicit user response
	 */
	public boolean initiateTwoPhaseClaim(IBIdentity ident, String key, int requestId) {
        // Send the request to Aphid
        HttpClient http = new CertifiedHttpClient(mContext);
        List<NameValuePair> qparams = new ArrayList<NameValuePair>();
        qparams.add(new BasicNameValuePair("req", new Integer(requestId).toString()));
        qparams.add(new BasicNameValuePair("type", new Integer(ident.authority_.ordinal()).toString()));
        qparams.add(new BasicNameValuePair("uid", ident.principal_));
        qparams.add(new BasicNameValuePair("time", new Long(ident.temporalFrame_).toString()));
        qparams.add(new BasicNameValuePair("key", key));
        try {
            // Send the request
            URI uri = URIUtils.createURI(URL_SCHEME, SERVER_LOCATION, -1, CLAIM_PATH,
                    URLEncodedUtils.format(qparams, "UTF-8"), null);
            Log.d(TAG, "Aphid URI: " + uri.toString());
            HttpGet httpGet = new HttpGet(uri);
            HttpResponse response = http.execute(httpGet);
            int code = response.getStatusLine().getStatusCode();
            
            // Read the response
            BufferedReader rd = new BufferedReader(new InputStreamReader(
                    response.getEntity().getContent()));
            String responseStr = "";
            String line = "";
            while ((line = rd.readLine()) != null) {
                responseStr += line;
            }
            Log.d(TAG, "Server response:" + responseStr);
            
            // Only 200 should indicate that this worked
            if (code == HttpURLConnection.HTTP_OK) {
                // Mark as notified (suppress repeated texts)
                MIdentity mid = mIdentitiesManager.getIdentityForIBHashedIdentity(ident);
                if (mid != null) {
                    MPendingIdentity pendingIdent = mPendingIdentityManager.lookupIdentity(
                            mid.id_, ident.temporalFrame_, requestId);
                    if (pendingIdent == null) {
                        pendingIdent = mPendingIdentityManager.fillPendingIdentity(
                                mid.id_, ident.temporalFrame_);
                        mPendingIdentityManager.insertIdentity(pendingIdent);
                    }
                    pendingIdent.notified_ = true;
                    mPendingIdentityManager.updateIdentity(pendingIdent);
                }
                return true;
            }
        } catch (URISyntaxException e) {
            Log.e(TAG, "URISyntaxException", e);
        } catch (IOException e) {
            Log.i(TAG, "Error claiming keys.");
        }
	    return false;
	}

	public void setTokenForUser(Authority authority, String principal, String token) {
		mKnownTokens.put(
				new Pair<Authority, String>(authority, principal),
				token
		);
	}
	
	/*
	 * Send notifications when accounts cannot connect.
	 */
	private void sendNotification(String account) {
		Intent launch = new Intent(mContext, SettingsActivity.class);
		launch.putExtra(SettingsActivity.ACTION,
                SettingsActivity.SettingsAction.ACCOUNT.toString());
		PendingIntent contentIntent = PendingIntent.getActivity(mContext, 0,
                launch, PendingIntent.FLAG_CANCEL_CURRENT);
		(new PresenceAwareNotify(mContext)).notify(SIGNIN_TEXT,
                account + " account failed to connect", contentIntent);
	}
	
	/*
	 * Cache known Google tokens
	 */
	private void cacheGoogleTokens() throws IdentityProviderException {
		SQLiteOpenHelper db = App.getDatabaseSource(mContext);
		MyAccountManager am = new MyAccountManager(db);
		MMyAccount[] accounts = am.getClaimedAccounts(AccountLinkDialog.ACCOUNT_TYPE_GOOGLE);
		for (MMyAccount account : accounts) {
        	String gToken = null;
        	String googleAccount = account.accountName_;
        	if (googleAccount != null) {
        	    try {
        	        gToken = AccountLinkDialog.silentBlockForGoogleToken(mContext, googleAccount);
        	    } catch (IOException e) {
        	        // Connection errors should be treated differently from auth errors
        	        throw new IdentityProviderException.NeedsRetry(
        	                new IBIdentity(Authority.Email, googleAccount, 0));
        	    }
        		Log.d(TAG, "Google account:" + googleAccount);
        	}
        	if (gToken != null) {
        		setTokenForUser(Authority.Email, googleAccount, gToken);
        		Log.d(TAG, "Google token:" + gToken);
        	}
        	else if (googleAccount != null && gToken == null) {
        		// Authentication failures should be reported
        		sendNotification("Google");
        		throw new IdentityProviderException.Auth(
        				new IBIdentity(Authority.Email, googleAccount, 0));
        	}
		}
	}
	
	/*
	 * Cache the current Facebook token
	 */
	private void cacheCurrentFacebookToken() throws IdentityProviderException.Auth {
		String fAccount = getFacebookAccount();
    	String fToken = null;
    	if (fAccount != null) {
    		fToken = AccountLinkDialog.getActiveFacebookToken(mContext);
    		Log.d(TAG, "Facebook account:" + fAccount);
    	}
    	if (fToken != null) {
    		setTokenForUser(Authority.Facebook, fAccount, fToken);
    		Log.d(TAG, "Facebook token:" + fToken);
    	}
    	else if (fAccount != null && fToken == null) {
    		// Authentication failures should be reported
    		sendNotification("Facebook");
    		throw new IdentityProviderException.Auth(
    				new IBIdentity(Authority.Facebook, fAccount, 0));
    	}
	}

	String getFacebookAccount() {
	    MyAccountManager am = new MyAccountManager(App.getDatabaseSource(mContext));
        MMyAccount[] acc = am.getClaimedAccounts(AccountLinkDialog.ACCOUNT_TYPE_FACEBOOK);
        if (acc.length > 0) {
        	MIdentity identity = mIdentitiesManager.getIdentityForId(acc[0].identityId_);
        	return identity.principal_;
        }
        return null;
	}
	
	private byte[] getAphidResultForIdentity(IBIdentity ident, String property)
			throws IdentityProviderException {
		Log.d(TAG, "Getting key for " + ident.principal_);
		
		// Populate tokens from identity providers (only Google and Facebook for now)
		try {
			cacheGoogleTokens();
		} catch (IdentityProviderException.Auth e) {
			// No need to continue if this is our identity and token fetch failed
			if (e.identity.equalsStable(ident)) {
				throw new IdentityProviderException.Auth(ident);
			}
		} catch (IdentityProviderException.NeedsRetry e) {
		    if (e.identity.equalsStable(ident)) {
		        throw new IdentityProviderException.NeedsRetry(ident);
		    }
		}
		try {
			cacheCurrentFacebookToken();
		} catch (IdentityProviderException e) {
			// No need to continue if this is our identity and token fetch failed
			if (e.identity.equalsStable(ident)) {
				throw new IdentityProviderException.Auth(ident);
			}
		}
		
		String aphidType = null;
		String aphidToken = null;
		// Get a service-specific token if it exists
		Pair<Authority, String> userProperties =
				new Pair<Authority, String>(ident.authority_, ident.principal_);
		if (mKnownTokens.containsKey(userProperties)) {
			aphidToken = mKnownTokens.get(userProperties);
		}
		
		// The IBE server has its own identifiers for providers
		switch (ident.authority_) {
    		case Facebook:
    			aphidType = "facebook";
    			break;
    		case Email:
    			if (mKnownTokens.containsKey(userProperties)) {
    				aphidType = "google";
    			}
    			break;
    		case PhoneNumber:
    		    // Aphid doesn't return keys for a phone number without verification
    		    throw new IdentityProviderException.TwoPhase(ident);
		}

        // Do not ask the server for identities we don't know how to handle
		if (aphidType == null || aphidToken == null) {
		    throw new IdentityProviderException(ident);
		}
		
		// Bundle arguments as JSON
		JSONObject jsonObj = new JSONObject();
		try {
			jsonObj.put("type", aphidType);
			jsonObj.put("token", aphidToken);
			jsonObj.put("starttime", ident.temporalFrame_);
		} catch (JSONException e) {
			Log.e(TAG, e.toString());
		}
		JSONArray userinfo = new JSONArray();
		userinfo.put(jsonObj);
		
		// Contact the server
		try {
			JSONObject resultObj = getAphidResult(userinfo);
			if (resultObj == null) {
				throw new IdentityProviderException.NeedsRetry(ident);
			}
			String encodedKey = resultObj.getString(property);
			boolean hasError = resultObj.has("error");
			if (!hasError) {
				long temporalFrame = resultObj.getLong("time");
				if (encodedKey != null && temporalFrame == ident.temporalFrame_) {
					// Success!
					return Base64.decode(encodedKey, Base64.DEFAULT);
				}
				else {
					// Might have jumped the gun a little bit, so try again later
					throw new IdentityProviderException.NeedsRetry(ident);
				}
			}
			else {
				// Aphid authentication error means Musubi has a bad token
				String error = resultObj.getString("error");
				if (error.contains("401")) {
					// Authentication errors require user action
					String accountType = Character.toString(
								Character.toUpperCase(aphidType.charAt(0))
							) + aphidType.substring(1);
					sendNotification(accountType);
					throw new IdentityProviderException.Auth(ident);
				}
				else {
					// Other failures should be retried silently
					throw new IdentityProviderException.NeedsRetry(ident);
				}
			}
		} catch (IOException e) {
			Log.e(TAG, e.toString());
		} catch (JSONException e) {
			Log.e(TAG, e.toString());
		}
		throw new IdentityProviderException.NeedsRetry(ident);
	}
	
	private JSONObject getAphidResult(JSONArray userinfo) throws IOException {
		// Set up HTTP request
		HttpClient http = new CertifiedHttpClient(mContext);
		URI uri;
		try {
		    uri = URIUtils.createURI(URL_SCHEME, SERVER_LOCATION, -1, KEYS_PATH, null, null);
		} catch (URISyntaxException e) {
		    throw new IOException("Malformed URL", e);
		}
		HttpPost post = new HttpPost(uri);
		List<NameValuePair> postData = new ArrayList<NameValuePair>();
		postData.add(new BasicNameValuePair("userinfo", userinfo.toString()));
		Log.d(TAG, "Server request: " + userinfo.toString());
		
		// Send the request
		post.setEntity(new UrlEncodedFormEntity(postData, HTTP.UTF_8));
		HttpResponse response = http.execute(post);
		
		// Read the response
		BufferedReader rd = new BufferedReader(new InputStreamReader(
				response.getEntity().getContent()));
		String responseStr = "";
		String line = "";
		while ((line = rd.readLine()) != null) {
			responseStr += line;
		}
		Log.d(TAG, "Server response:" + responseStr);
		
		// Parse the response as JSON
		try {
			JSONArray arr = new JSONArray(responseStr);
			if (arr.length() != 0) {
				JSONObject object = arr.getJSONObject(0);
				return object;
			}
			else {
				return null;
			}
		} catch(JSONException e) {
			throw new IOException("Bad JSON format", e);
		}
	}
}
