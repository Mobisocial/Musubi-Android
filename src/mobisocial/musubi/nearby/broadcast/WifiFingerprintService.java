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

package mobisocial.musubi.nearby.broadcast;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import mobisocial.musubi.App;
import mobisocial.musubi.model.DbContactAttributes;
import mobisocial.musubi.ui.SettingsActivity;
import mobisocial.musubi.util.Util;
import mobisocial.socialkit.musubi.Musubi;
import mobisocial.socialkit.obj.MemObj;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

/**
 * A persistent service for managing Musubi's long-lived tasks such
 * as network connectivity.
 */
public class WifiFingerprintService extends Service {
    public static final String TAG = "DungBeetleService";
    
    private Timer updateFingerprintTimer;

    private final long UPDATE_INTERVAL = 1000 * 60; // every minute

	private Musubi mMusubi;

    @Override
    public void onCreate() {
    	mMusubi = App.getMusubi(this);
        updateFingerprintTimer = new Timer();
        updateFingerprintTimer.scheduleAtFixedRate(new UpdateFingerprint(), 0, UPDATE_INTERVAL);
    }

   private class UpdateFingerprint extends TimerTask {
        public void run() {
        	
        	SharedPreferences p = getSharedPreferences(SettingsActivity.PREFS_NAME, 0);
    		boolean wifi_fingerprinting = p.getBoolean(SettingsActivity.PREF_WIFI_FINGERPRINTING, SettingsActivity.PREF_WIFI_FINGERPRINTING_DEFAULT);
    		
    		if(wifi_fingerprinting) {
	        	//did the fingerprint change enough?
	        	String oldFingerprintString = DbContactAttributes.getDeviceAttribute(
	    												WifiFingerprintService.this, 
	    												DbContactAttributes.ATTR_WIFI_FINGERPRINT);
	        	if(oldFingerprintString == null) {
	        		oldFingerprintString = "";
	        	}
	        	Set<String> oldFingerprint = new HashSet<String>(Arrays.asList(oldFingerprintString.split(":")));
	        	
	        	WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
	        	List<ScanResult> results = wifi.getScanResults();
	        	Set<String> newFingerprint = new HashSet<String>();
	        	if (results == null) {
	        	    return;
	        	}
	        	for (ScanResult result : results) {
                    newFingerprint.add(Util.MD5(result.SSID));
                }
	        	
	    		int initialSize = oldFingerprint.size();
	    		
	    		//compute intersection of old and new fingerprint
	    		oldFingerprint.retainAll(newFingerprint);
	    		
	    		int overlapSize = oldFingerprint.size();
	    		
	    		//only update if less than 50% of the old fingerprint is still valid
	    		if(initialSize > 0 && (double)overlapSize / (double)initialSize < .5) {
	    			String fingerprint = Util.computeWifiFingerprint(wifi.getScanResults());
	    			
	    			
	    			JSONObject loc = new JSONObject();
	    	        try {
	    	            loc.put(DbContactAttributes.ATTR_WIFI_FINGERPRINT, fingerprint);
	    	        } catch (JSONException e) {
	    	            // Impossible json exception
	    	        }
	    	        // XXX killed for now
	    	        //mMusubi.getAppFeed().postObj(new MemObj("locUpdate", loc));
	    	        //Log.w(TAG, "sending fingerprint");
	    		}
    		}
        }
    }

    @Override
    public void onDestroy() {
//        Toast.makeText(this, R.string.stopping, Toast.LENGTH_SHORT).show();
        updateFingerprintTimer.cancel();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    // This is the object that receives interactions from clients.  See
    // RemoteService for a more complete example.
    private final IBinder mBinder = new Binder(){
    	WifiFingerprintService getService(){
                return WifiFingerprintService.this;
            }
        };


}
