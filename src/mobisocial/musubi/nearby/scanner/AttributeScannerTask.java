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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import mobisocial.musubi.model.DbContactAttributes;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.nearby.item.NearbyItem;
import mobisocial.musubi.nearby.item.NearbyUser;
import mobisocial.socialkit.User;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

/**
 * Scans the database for known location identifiers for users.
 *
 */
public class AttributeScannerTask extends NearbyScannerTask {
    final Context mContext;
    final WifiManager mWifiManager;

    public AttributeScannerTask(Context c, WifiManager wifiManager) {
        mContext = c;
        mWifiManager = wifiManager;
    }

    @Override
    protected List<NearbyItem> doInBackground(Void... params) {
        if (DBG) Log.d(TAG, "Scanning for nearby attributes...");
        List<User> wifiUsers = DbContactAttributes.getUsersWithAttribute(
                mContext, DbContactAttributes.ATTR_WIFI_SSID);

        /** Compare your wifi fingerprint with your friends' **/
        List<User> wifiFingerprintUsers = DbContactAttributes.getUsersWithAttribute(
    			mContext, DbContactAttributes.ATTR_WIFI_FINGERPRINT);
        String myFingerprintString = DbContactAttributes.getDeviceAttribute(mContext, DbContactAttributes.ATTR_WIFI_FINGERPRINT);
        if (myFingerprintString == null) {
        	myFingerprintString = "";
    	}
    	Set<String> myFingerprint = new HashSet<String>(Arrays.asList(myFingerprintString.split(":")));
    	if (myFingerprint.size() > 0) {
    		Log.w(TAG, "Checking over " + wifiFingerprintUsers.size() + " peers with wifi fingerprints");
    		for (User u : wifiFingerprintUsers) {
    			String theirFingerprintString = u.getAttribute(DbContactAttributes.ATTR_WIFI_FINGERPRINT);
    			if(theirFingerprintString == null) {
    				theirFingerprintString = "";
    			}
    	    	Set<String> theirFingerprint = new HashSet<String>(Arrays.asList(theirFingerprintString.split(":")));
    	    	
    	    	if (theirFingerprint.size() > 0 && !(theirFingerprint.size() == 1 && theirFingerprint.toArray()[0].toString().length() == 0)) {
    	    		
	    	    	int comparisonSize = Math.min(myFingerprint.size(), theirFingerprint.size());
	    	    	theirFingerprint.retainAll(myFingerprint);
	    	    	int intersection = theirFingerprint.size();
	    	    	
	    	    	//if there is a 50% match over the minimum comparison size, they're close enough
	    	    	if((double)intersection / (double)comparisonSize >= .5) {
	                	Log.w(TAG, "adding user " + u.getName() + " based on wifi fingerprint");
	    	    		addNearbyItem(new NearbyUser(mContext, u));
	    	    	}
    	    	}
    	    	
    		}
    	}
    		
        /** Last known wifi address we found them on **/
        String myWifi = mWifiManager.getConnectionInfo().getSSID(); // BSSID is narrower
        Log.d(TAG, "Checking clients last checked in to " + myWifi);
        if (myWifi != null) {
            // TODO: this should be a single query..
            Log.d(TAG, "Checking over " + wifiUsers.size() + " peers with known wifis");
            for (User u : wifiUsers) {
                String theirWifi = u.getAttribute(DbContactAttributes.ATTR_WIFI_SSID);
                if (myWifi.equals(theirWifi)) {
                	Log.w(TAG, "adding user " + u.getName() + " based on wifi ssid");
                    addNearbyItem(new NearbyUser(mContext, u));
                }
             }
        }
        
    	
        return null;
    }
}