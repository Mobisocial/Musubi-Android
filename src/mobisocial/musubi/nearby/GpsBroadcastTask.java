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

package mobisocial.musubi.nearby;

import gnu.trove.list.array.TByteArrayList;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Date;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.musubi.App;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.helpers.FeedManager;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.nearby.location.GridHandler;
import mobisocial.musubi.ui.MusubiBaseActivity;
import mobisocial.musubi.ui.util.UiUtil;
import mobisocial.musubi.util.MyLocation;
import mobisocial.musubi.util.Util;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;

import android.content.Context;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

/**
 * Shares a feed encrypted using the GPS server.
 */
public class GpsBroadcastTask extends AsyncTask<Void, Void, Void> {
    private final boolean DBG = MusubiBaseActivity.DBG;
    public static final String TAG = "GpsBroadcastTask";
    private final Context mContext;
    private final String mmPassword;
    private final MyLocation mmMyLocation;
    private boolean mmLocationScanComplete = false;
    private Location mmLocation = null;
    private MFeed mFeed = null;
    private boolean mSucceeded = false;

    public GpsBroadcastTask(Context context, MFeed feed, String password) {
        mmPassword = password;
        mmMyLocation = new MyLocation();
        mContext = context;
        mFeed = feed;
    }

    @Override
    protected void onPreExecute() {
        mmMyLocation.getLocation(mContext, mmLocationResult);
    }

    @Override
    protected Void doInBackground(Void... params) {
        if (DBG) Log.d(TAG, "Uploading group for nearby gps...");
        while (!mmLocationScanComplete) {
            synchronized (mmLocationResult) {
                if (!mmLocationScanComplete) {
                    try {
                        if (DBG) Log.d(TAG, "Waiting for location results...");
                        mmLocationResult.wait();
                    } catch (InterruptedException e) {
                    }
                }
            }
        }
        if (DBG) Log.d(TAG, "Got location " + mmLocation);
        if (isCancelled()) {
            return null;
        }

        try {
        	SQLiteOpenHelper db = App.getDatabaseSource(mContext);
        	FeedManager fm = new FeedManager(db);
        	IdentitiesManager im = new IdentitiesManager(db);
            String group_name = UiUtil.getFeedNameFromMembersList(fm, mFeed);
            byte[] group_capability = mFeed.capability_;
            List<MIdentity> owned = im.getOwnedIdentities();
            MIdentity sharer = null;
            for(MIdentity i : owned) {
            	if(i.type_ != Authority.Local) {
            		sharer = i;
            		break;
            	}
            }
            String sharer_name = UiUtil.safeNameForIdentity(sharer);
            byte[] sharer_hash = sharer.principalHash_;
            
            byte[] thumbnail = fm.getFeedThumbnailForId(mFeed.id_);
            if(thumbnail == null)
            	thumbnail = im.getMusubiThumbnail(sharer) != null ? sharer.musubiThumbnail_ : im.getThumbnail(sharer);
            int member_count = fm.getFeedMemberCount(mFeed.id_);

            JSONObject group = new JSONObject();
            group.put("group_name", group_name);
            group.put("group_capability", Base64.encodeToString(group_capability, Base64.DEFAULT));
            group.put("sharer_name", sharer_name);
            group.put("sharer_type", sharer.type_.ordinal());
            group.put("sharer_hash", Base64.encodeToString(sharer_hash, Base64.DEFAULT));
            if(thumbnail != null)
            	group.put("thumbnail", Base64.encodeToString(thumbnail, Base64.DEFAULT));
            group.put("member_count", member_count);

            byte[] key = Util.sha256(("happysalt621" + mmPassword).getBytes());
            byte[] data = group.toString().getBytes();
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            
            byte[] partial_enc_data; 
    		Cipher cipher;
    		AlgorithmParameterSpec iv_spec;
    		SecretKeySpec sks;
    		try {
    			cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
    		} catch (Exception e) {
    			throw new RuntimeException("AES not supported on this platform", e);
    		}
    		try {
    			iv_spec = new IvParameterSpec(iv);
    		    sks = new SecretKeySpec(key, "AES");
    			cipher.init(Cipher.ENCRYPT_MODE, sks, iv_spec);
    		} catch (Exception e) {
    			throw new RuntimeException("bad iv or key", e);
    		}
    		try {
    			partial_enc_data = cipher.doFinal(data);
    		} catch (Exception e) {
    			throw new RuntimeException("body encryption failed", e);
    		}
            
    		TByteArrayList bal = new TByteArrayList(iv.length + partial_enc_data.length);
    		bal.add(iv);
    		bal.add(partial_enc_data);
    		byte[] enc_data = bal.toArray();
    		

            if (DBG) Log.d(TAG, "Posting to gps server...");

            
            
            Uri uri = Uri.parse("http://bumblebee.musubi.us:6253/nearbyapi/0/sharegroup");

            StringBuffer sb = new StringBuffer();
            DefaultHttpClient client = new DefaultHttpClient();
            HttpPost httpPost = new HttpPost(uri.toString());
            httpPost.addHeader("Content-Type", "application/json");
            JSONArray buckets = new JSONArray();
            JSONObject descriptor = new JSONObject();
            
            double lat = mmLocation.getLatitude();
            double lng = mmLocation.getLongitude();
            long[] coords = GridHandler.getGridCoords(lat, lng, 5280 / 2);
            for(long c : coords) {
        	    MessageDigest md;
                try {
	                byte[] obfuscate = ("sadsalt193s" + mmPassword).getBytes();
                    md = MessageDigest.getInstance("SHA-256");
                    ByteBuffer b = ByteBuffer.allocate(8 + obfuscate.length);
                    b.putLong(c);
                    b.put(obfuscate);
                    String secret_bucket = Base64.encodeToString(md.digest(b.array()), Base64.DEFAULT);
                	buckets.put(buckets.length(), secret_bucket);
                } catch (NoSuchAlgorithmException e) {
                    throw new RuntimeException("your platform does not support sha256", e);
                }
            }
            descriptor.put("buckets", buckets);
            descriptor.put("data", Base64.encodeToString(enc_data, Base64.DEFAULT));
            descriptor.put("expiration", new Date().getTime() + 1000 * 60 * 60);
            
            httpPost.setEntity(new StringEntity(descriptor.toString()));
            try {
                HttpResponse execute = client.execute(httpPost);
                InputStream content = execute.getEntity().getContent();
                BufferedReader buffer = new BufferedReader(new InputStreamReader(content));
                String s = "";
                while ((s = buffer.readLine()) != null) {
                    if (isCancelled()) {
                        return null;
                    }
                    sb.append(s);
                }
                if(sb.toString().equals("ok"))
                	mSucceeded = true;
                else {
                	System.err.println(sb);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            //TODO: report failures etc
        } catch (Exception e) {
            Log.e(TAG, "Failed to broadcast group", e);
        }
        return null;
    }

    @Override
    protected void onPostExecute(Void result) {
    	Toast.makeText(mContext, mSucceeded ? "Shared group nearby" : "Error sharing group" , Toast.LENGTH_LONG).show();
    }
    private final MyLocation.LocationResult mmLocationResult = new MyLocation.LocationResult() {
        @Override
        public void gotLocation(final Location location) {
            if (DBG) Log.d(TAG, "got location");
            mmLocation = location;
            mmLocationScanComplete = true;
            synchronized (mmLocationResult) {
                mmLocationResult.notify();
            }
        }
    };
}
