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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import mobisocial.musubi.nearby.item.NearbyItem;
import mobisocial.musubi.nearby.scanner.GpsScannerTask;
import mobisocial.musubi.nearby.scanner.NearbyScannerTask;
import mobisocial.musubi.ui.MusubiBaseActivity;
import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.util.Log;

/**
 * An AsyncTask for discovering nearby users and feeds.
 */
public class NearbyLookup {
    private static final boolean DBG = MusubiBaseActivity.DBG;
    private static final String TAG = "NearbyLookup";

    private final Activity mContext;
    private final String mGpsPassword;

    public NearbyLookup(Activity context) {
        this(context, null);
    }

    public NearbyLookup(Activity context, String gpsPassword) {
        mContext = context;
        mGpsPassword = gpsPassword;
    }

    /**
     * Initiates a lookup for nearby people, devices, and feeds.
     * @param listener an (optional) callback for handling discovered results
     */
    public LookupFuture doLookup(NearbyResultListener listener) {
        return new LookupFuture(this, listener);
    }

    public interface NearbyResultListener {
        public void onDiscoveryBegin();
        public void onDiscoveryComplete();
        public void onItemDiscovered(NearbyItem item);
    }

    public class LookupFuture implements Future<List<NearbyItem>> {
        private final List<NearbyScannerTask> mScannerTasks = new ArrayList<NearbyScannerTask>();
        private int mCompletedDiscoveries = 0;
        private NearbyLookup mLookup;
        private boolean mCancelled = false;
        private boolean mDone = false;
        private NearbyResultListener mSuppliedListener;

        private final Set<Uri> mNearbyUris = new HashSet<Uri>();
        private final List<NearbyItem> mNearbyList = new LinkedList<NearbyItem>();
        private final NearbyResultListener mInternalListener = new NearbyResultListener() {
            @Override
            public synchronized void onItemDiscovered(NearbyItem item) {
                if (DBG) Log.d(TAG, "Discovered nearby item " + item);
                if (!mNearbyUris.contains(item.uri)) {
                    mNearbyUris.add(item.uri);
                    mNearbyList.add(item);

                    if (mSuppliedListener != null) {
                        mSuppliedListener.onItemDiscovered(item);
                    }
                }
            }

            public void onDiscoveryComplete() {
                if (++mCompletedDiscoveries == mScannerTasks.size()) {
                    if (mSuppliedListener != null) {
                        mSuppliedListener.onDiscoveryComplete();
                    }
                    synchronized (mInternalListener) {
                        notify();
                    }
                }
            }

            @Override
            public void onDiscoveryBegin() {
                if (mSuppliedListener != null) {
                    mSuppliedListener.onDiscoveryBegin();
                }
            }
        };

        private LookupFuture(NearbyLookup lookup, NearbyResultListener listener) {
            mLookup = lookup;
            mSuppliedListener = listener;
            Activity context = mLookup.mContext;
            WifiManager wifi = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);
            mScannerTasks.add(new GpsScannerTask(context, mLookup.mGpsPassword));
//            mScannerTasks.add(new MulticastScannerTask(context, wifi));
//            mScannerTasks.add(new AttributeScannerTask(context, wifi));

            for (NearbyScannerTask task : mScannerTasks) {
                task.setNearbyResultListener(mInternalListener);
                mInternalListener.onDiscoveryBegin();
                task.execute();
            }
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (mDone) {
                return false;
            }
            boolean cancelled = true;
            for (NearbyScannerTask task : mScannerTasks) {
                cancelled &= task.cancel(true);
            }
            mCancelled = cancelled;
            return mCancelled;
        }

        @Override
        public List<NearbyItem> get() throws InterruptedException, ExecutionException {
            synchronized (mInternalListener) {
                while (!mDone) {
                    try {
                        wait();
                    } catch (InterruptedException e) {}
                }
            }
            return mNearbyList;
        }

        @Override
        public List<NearbyItem> get(long timeout, TimeUnit unit) throws InterruptedException,
                ExecutionException, TimeoutException {
            synchronized (mInternalListener) {
                while (!mDone) {
                    try {
                        wait(unit.convert(timeout, TimeUnit.MILLISECONDS));
                    } catch (InterruptedException e) {}
                }
            }
            return mNearbyList;
        }

        @Override
        public boolean isCancelled() {
            return mCancelled;
        }

        @Override
        public boolean isDone() {
            return mDone;
        }

        public int getDiscoveredItemCount() {
            return mNearbyList.size();
        }

        public List<NearbyItem> getDiscoveredItems() {
            ArrayList<NearbyItem> items;
            synchronized (mInternalListener) {
                items = new ArrayList<NearbyItem>(mNearbyList.size());
                items.addAll(mNearbyList);
            }
            return items;
        }
    }
}
