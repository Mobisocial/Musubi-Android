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

package mobisocial.musubi.feed.presence;

import mobisocial.musubi.Helpers;
import mobisocial.musubi.feed.iface.FeedPresence;
import mobisocial.musubi.objects.LocationObj;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

public class LocationPresence extends FeedPresence {
    private static final String TAG = "locationPresence";
    LocationManager mLocationManager;
    private boolean mShareLocation = false;
    private Context mContext;

    @Override
    public String getName() {
        return "Location";
    }

    @Override
    public void onPresenceUpdated(final Context context, final Uri feedUri, boolean present) {
        if (mLocationManager == null) {
            mContext = context.getApplicationContext();
            mLocationManager =
                    (LocationManager)mContext.getSystemService(Context.LOCATION_SERVICE);
        }
        if (mShareLocation) {
            if (getFeedsWithPresence().size() == 0) {
                mLocationManager.removeUpdates(mLocationListener);
                Toast.makeText(context, "No longer sharing location", Toast.LENGTH_SHORT).show();
                mShareLocation = false;
            }
        } else {
            if (getFeedsWithPresence().size() > 0) {
                String provider;
                if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    provider = LocationManager.GPS_PROVIDER;
                } else if (mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    provider = LocationManager.NETWORK_PROVIDER;
                } else {
                    Toast.makeText(context, "No location provider available.",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                mLocationManager.requestLocationUpdates(
                        provider, TWO_MINUTES, 10, mLocationListener);
                Toast.makeText(context, "Now sharing location", Toast.LENGTH_SHORT).show();
                mShareLocation = true;
            }
        }
    }

    private static final int TWO_MINUTES = 1000 * 60 * 2;

    /** Determines whether one Location reading is better than the current Location fix
      * @param location  The new Location that you want to evaluate
      * @param currentBestLocation  The current Location fix, to which you want to compare the new one
      */
    private boolean isBetterLocation(Location location, Location currentBestLocation) {
        if (currentBestLocation == null) {
            // A new location is always better than no location
            return true;
        }

        // Check whether the new location fix is newer or older
        long timeDelta = location.getTime() - currentBestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
        boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
        boolean isNewer = timeDelta > 0;

        // If it's been more than two minutes since the current location, use the new location
        // because the user has likely moved
        if (isSignificantlyNewer) {
            return true;
        // If the new location is more than two minutes older, it must be worse
        } else if (isSignificantlyOlder) {
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(),
                currentBestLocation.getProvider());

        // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true;
        }
        return false;
    }

    /** Checks whether two providers are the same */
    private boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
          return provider2 == null;
        }
        return provider1.equals(provider2);
    }

    private LocationListener mLocationListener = new LocationListener() {
        private Location mmLastLocation;
        @Override
        public void onLocationChanged(Location location) {
            if (mmLastLocation != null) {
                if (Math.abs(location.getTime() - mmLastLocation.getTime()) < 60*2*1000) {
                    if (mmLastLocation.distanceTo(location) < 10) {
                        return;
                    }
                }
            }
            mmLastLocation = location;
            for (Uri uri : getFeedsWithPresence()) {
                Helpers.sendToFeed(mContext, LocationObj.from(location), uri);
            }
        }

        @Override
        public void onProviderDisabled(String provider) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }  
    };
}