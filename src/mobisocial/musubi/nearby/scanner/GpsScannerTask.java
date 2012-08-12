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

package mobisocial.musubi.nearby.scanner;

import gnu.trove.list.array.TByteArrayList;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.musubi.nearby.item.NearbyFeed;
import mobisocial.musubi.nearby.item.NearbyItem;
import mobisocial.musubi.nearby.location.GridHandler;
import mobisocial.musubi.ui.MusubiBaseActivity;
import mobisocial.musubi.util.MyLocation;
import mobisocial.musubi.util.Util;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;

import android.content.Context;
import android.location.Location;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;
import org.javatuples.Pair;

/**
 * Pings a server with GPS information to search for nearby items.
 */
public class GpsScannerTask extends NearbyScannerTask {
    private final boolean DBG = MusubiBaseActivity.DBG;
    private final Context mContext;
    private final String mmPassword;
    private final MyLocation mmMyLocation;
    private boolean mmLocationScanComplete = false;
    private Location mmLocation = null;

    public GpsScannerTask(Context context, String password) {
        mmPassword = password;
        mmMyLocation = new MyLocation();
        mContext = context;
    }

    @Override
    protected void onPreExecute() {
        mmMyLocation.getLocation(mContext, mmLocationResult);
    }

    @Override
    protected List<NearbyItem> doInBackground(Void... params) {
        if (DBG) Log.d(TAG, "Scanning for nearby gps...");
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
            if (DBG) Log.d(TAG, "Querying gps server...");
            Uri uri = Uri.parse("http://bumblebee.musubi.us:6253/nearbyapi/0/findgroup");

            StringBuffer sb = new StringBuffer();
            DefaultHttpClient client = new DefaultHttpClient();
            HttpPost httpPost = new HttpPost(uri.toString());
            httpPost.addHeader("Content-Type", "application/json");
            JSONArray buckets = new JSONArray();
            
            double lat = mmLocation.getLatitude();
            double lng = mmLocation.getLongitude();
            
            long[] coords = GridHandler.getGridCoords(lat, lng, 5280 / 2);
            Log.i(TAG, "coords: " + Arrays.toString(coords));
            
            //TODO: encrypt coords with mmPassword
            
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
            Log.i(TAG, "buckets: " + buckets);
            httpPost.setEntity(new StringEntity(buckets.toString()));
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
            } catch (Exception e) {
                e.printStackTrace();
            }
            HashSet<Pair<TByteArrayList, TByteArrayList>> dupes = new HashSet<Pair<TByteArrayList, TByteArrayList>>();

            String response = sb.toString();
            JSONArray groupsJSON = new JSONArray(response);
            Log.d(TAG, "Got " + groupsJSON.length() + " groups");
            for (int i = 0; i < groupsJSON.length(); i++) {
            	try {
	                String s_enc_data = groupsJSON.get(i).toString();
	                byte[] enc_data = Base64.decode(s_enc_data, Base64.DEFAULT);
	                byte[] key = Util.sha256(("happysalt621" + mmPassword).getBytes());
	                byte[] data;
	
	                
	        		Cipher cipher;
	        		AlgorithmParameterSpec iv_spec;
	        		SecretKeySpec sks;
	        		try {
	        			cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
	        		} catch (Exception e) {
	        			throw new RuntimeException("AES not supported on this platform", e);
	        		}
	        		try {
	        			iv_spec = new IvParameterSpec(enc_data, 0, 16);
	        		    sks = new SecretKeySpec(key, "AES");
	        			cipher.init(Cipher.DECRYPT_MODE, sks, iv_spec);
	        		} catch (Exception e) {
	        			throw new RuntimeException("bad iv or key", e);
	        		}
	        		try {
	        	        data = cipher.doFinal(enc_data, 16, enc_data.length - 16);
	        		} catch (Exception e) {
	        			throw new RuntimeException("body decryption failed", e);
	        		}
	                
	                JSONObject group = new JSONObject(new String(data));
	                
	                String group_name = group.getString("group_name");
	                byte[] group_capability = Base64.decode(group.getString("group_capability"), Base64.DEFAULT);
	                String sharer_name = group.getString("sharer_name");
	                byte[] sharer_hash = Base64.decode(group.getString("sharer_hash"), Base64.DEFAULT);
	                byte[] thumbnail = null;
	                if(group.has("thumbnail"))
	                	thumbnail = Base64.decode(group.getString("thumbnail"), Base64.DEFAULT);
	                int member_count = group.getInt("member_count");
	                int sharer_type = group.getInt("sharer_type");
	                Pair<TByteArrayList, TByteArrayList> p  = Pair.with(new TByteArrayList(sharer_hash), new TByteArrayList(group_capability));
	                if(dupes.contains(p))
	                	continue;
	                dupes.add(p);
	                addNearbyItem(new NearbyFeed(mContext, group_name, group_capability, sharer_name, Authority.values()[sharer_type], sharer_hash, thumbnail, member_count));
            	} catch(Throwable e) {
                    Log.e(TAG, "Failed to parse group " + i, e);
            	}
            }
        } catch (Exception e) {
            if (DBG) Log.d(TAG, "Error searching nearby feeds", e);
        }
        return null;
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
