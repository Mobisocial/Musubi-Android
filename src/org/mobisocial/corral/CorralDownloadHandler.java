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

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import mobisocial.musubi.App;
import mobisocial.socialkit.musubi.DbObj;
import mobisocial.socialkit.musubi.Musubi;

import org.mobisocial.corral.CorralHelper.DownloadProgressCallback;
import org.mobisocial.corral.CorralHelper.DownloadProgressCallback.DownloadChannel;
import org.mobisocial.corral.CorralHelper.DownloadProgressCallback.DownloadState;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;

public class CorralDownloadHandler {
    private static final String TAG = "DownloadProcessor";

    final int NOTIFICATION_ID = 1024;
    final Context mContext;
    final HandlerThread mHandlerThread;
    final Handler mHandler;
    final Map<Long, CorralDownloadFuture> mPendingDownloads;
    final Musubi mMusubi;
    static CorralDownloadHandler sInstance;

    public static CorralDownloadHandler getInstance(Context context) {
        if (sInstance == null) {
            HandlerThread thread = new HandlerThread("CorralDownloadThread");
            thread.setPriority(Thread.MIN_PRIORITY);
            thread.start();
            sInstance = new CorralDownloadHandler(context, thread);
        }
        return sInstance;
    }

    public static CorralDownloadFuture lookupDownload(Context context, long objId) {
        CorralDownloadHandler handler = getInstance(context);
        synchronized (handler.mPendingDownloads) {
            return handler.mPendingDownloads.get(objId);
        }
    }

    private CorralDownloadHandler(Context context, HandlerThread thread) {
        mPendingDownloads = new HashMap<Long, CorralDownloadFuture>();
        mHandlerThread = thread;
        mContext = context;
        mHandler = new Handler(mHandlerThread.getLooper());
        mMusubi = App.getMusubi(mContext);
    }

    public static CorralDownloadFuture startOrFetchDownload(Context context, DbObj obj) {
        return getInstance(context).startOrFetchDownload(obj);
    }

    CorralDownloadFuture startOrFetchDownload(final DbObj obj) {
        long objId = obj.getLocalId();
        synchronized (mPendingDownloads) {
            if (mPendingDownloads.containsKey(objId)) {
                return mPendingDownloads.get(objId);
            }
        }

        final CorralDownloadFuture future = new CorralDownloadFuture(objId);
        synchronized (mPendingDownloads) {
            mPendingDownloads.put(objId, future);
        }

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    CorralDownloadClient corral = CorralDownloadClient.getInstance(mContext);
                    future.setResult(corral.fetchContent(obj, future, future.mParentCallback));
                } catch (IOException e) {
                    future.setResult(null);
                }
            }
        });
        return future;
    }

    public class CorralDownloadFuture {
        final long mObjId;
        final Set<DownloadProgressCallback> mCallbacks = new HashSet<DownloadProgressCallback>();
        final DownloadProgressCallback mParentCallback;
        Object mLock = new Object();
        Uri mResult = null;
        boolean mFinished = false;
        boolean mCancelled;

        DownloadState mLastState;
        DownloadChannel mLastChannel;
        int mLastProgress;

        CorralDownloadFuture(long objId) {
            mObjId = objId;
            mParentCallback = new DownoadProgressCallbackRegistrar();
            mParentCallback.onProgress(DownloadState.DOWNLOAD_PENDING, DownloadChannel.NONE, 0);
        }

        public long getObjId() {
            return mObjId;
        }

        void setResult(Uri result) {
            synchronized (mLock) {
                mResult = result;
                mFinished = true;
                mLock.notify();
            }
        }

        public Uri getResult() throws InterruptedException {
            synchronized (mLock) {
                if(!mFinished)
                    mLock.wait();
                return mResult;
            }
        }

        public void cancel() {
            synchronized (mLock) {
                mFinished = mCancelled = true;
                mLock.notify();
            }
        }

        public boolean isCancelled() {
            return mCancelled;
        }

        public void registerCallback(DownloadProgressCallback callback) {
            synchronized (mCallbacks) {
                mCallbacks.add(callback);
                callback.onProgress(mLastState, mLastChannel, mLastProgress);
            }
        }

        public boolean unregisterCallback(DownloadProgressCallback callback) {
            synchronized (mCallbacks) {
                return mCallbacks.remove(callback);
            }
        }

        class DownoadProgressCallbackRegistrar implements DownloadProgressCallback {
            @Override
            public void onProgress(DownloadState state, DownloadChannel channel, int progress) {
                synchronized (mCallbacks) {
                    mLastState = state;
                    mLastChannel = channel;
                    mLastProgress = progress;

                    for (DownloadProgressCallback cb : mCallbacks) {
                        cb.onProgress(state, channel, progress);
                    }
                }

                if (state == DownloadState.TRANSFER_COMPLETE) {
                    synchronized (mPendingDownloads) {
                        mPendingDownloads.remove(mObjId);
                    }
                }
            }
        };
    }
}
