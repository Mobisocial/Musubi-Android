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

import java.util.List;

import mobisocial.musubi.nearby.NearbyLookup.NearbyResultListener;
import mobisocial.musubi.nearby.item.NearbyItem;

import android.os.AsyncTask;
import android.util.Log;

public abstract class NearbyScannerTask extends AsyncTask<Void, NearbyItem, List<NearbyItem>> {
    protected static final String TAG = "NearbyTask";
    protected static boolean DBG = true;
    private NearbyResultListener mListener;

    public void setNearbyResultListener(NearbyResultListener listener) {
        mListener = listener;
    }

    @Override
    protected final synchronized void onProgressUpdate(NearbyItem... values) {
        if (!isCancelled()) {
            mListener.onItemDiscovered(values[0]);
        }
    }

    @Override
    protected final void onPostExecute(List<NearbyItem> result) {
        if (!isCancelled() && result != null) {
            for (NearbyItem i : result) {
                mListener.onItemDiscovered(i);
            }
        }
        mListener.onDiscoveryComplete();
    }

    protected final void addNearbyItem(NearbyItem item) {
        if (DBG) Log.d(TAG, getClass().getSimpleName() + " found " + item);
        publishProgress(item);
    }
}