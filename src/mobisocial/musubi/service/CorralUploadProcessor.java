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

package mobisocial.musubi.service;

import mobisocial.musubi.App;
import mobisocial.musubi.R;
import mobisocial.musubi.model.MPendingUpload;
import mobisocial.musubi.model.helpers.ObjectManager;
import mobisocial.musubi.model.helpers.PendingUploadManager;
import mobisocial.socialkit.musubi.DbObj;
import mobisocial.socialkit.musubi.Musubi;

import org.json.JSONObject;
import org.mobisocial.corral.CorralDownloadClient;
import org.mobisocial.corral.CorralHelper;
import org.mobisocial.corral.CorralHelper.UploadProgressCallback;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

/**
 * Monitors the pending_uploads table for files that should be sent
 */
public class CorralUploadProcessor extends ContentObserver {
    private static final String TAG = "UploadProcessor";

    final int NOTIFICATION_ID = 1024;
    final Context mContext;
    final NotificationManager mNotificationManager;
    final HandlerThread mThread;
    final SQLiteOpenHelper mDb;
    final Musubi mMusubi;
    final ObjectManager mObjectManager;

    public static CorralUploadProcessor newInstance(Context context, SQLiteOpenHelper db) {
        HandlerThread thread = new HandlerThread("CorralUploadThread");
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
        return new CorralUploadProcessor(context, db, thread);
    }

    private CorralUploadProcessor(Context context, SQLiteOpenHelper db, HandlerThread thread) {
        super(new Handler(thread.getLooper()));
        mThread = thread;
        mContext = context;
        mNotificationManager = (NotificationManager)context.getSystemService(
                Context.NOTIFICATION_SERVICE);
        mDb = db;
        mMusubi = App.getMusubi(mContext);
        mObjectManager = new ObjectManager(mDb);
    }

    @Override
    public void onChange(boolean selfChange) {
        long[] ids = new PendingUploadManager(mDb).getPendingUploadObjects();
        for (final long objId : ids) {
            DbObj obj = mMusubi.objForId(objId);
            if (obj == null) {
                Log.w(TAG, "object " + objId + " does not exist");
                deletePendingUploadByObjectId(objId);
                continue;
            }
            final String universalHash = obj.getUniversalHashString();
            JSONObject json = obj.getJson();
            if (json == null || !json.has(CorralDownloadClient.OBJ_LOCAL_URI)
                    || !json.has(CorralDownloadClient.OBJ_MIME_TYPE)) {
                Log.w(TAG, "not enough info for upload of " + objId);
                deletePendingUploadByObjectId(objId);
                continue;
            }
            if (!json.has(CorralDownloadClient.OBJ_PRESHARED_KEY)) {
                Log.w(TAG, "no shared secret for encryption");
                deletePendingUploadByObjectId(objId);
                continue;
            }

            String ticker = "Upload in progress...";
            long time = System.currentTimeMillis();
            final Notification notification = new Notification(R.drawable.icon, ticker, time);
            notification.setLatestEventInfo(mContext, "Upload in progress", "Preparing Upload...", null);
            Intent musubi = new Intent(Intent.ACTION_MAIN);
            musubi.addCategory(Intent.CATEGORY_LAUNCHER);
            musubi.setPackage(mContext.getPackageName());
            final PendingIntent contentIntent = PendingIntent.getActivity(mContext, 0, musubi, 0);

            notification.contentIntent = contentIntent;
            notification.flags = Notification.FLAG_ONGOING_EVENT;
            mNotificationManager.notify(NOTIFICATION_ID, notification);

            boolean uploadSuccessful;
            Log.d(TAG, "starting upload...");

            UploadProgressCallback callback = new UploadProgressCallback() {
                @Override
                public void onProgress(UploadState state, int progress) {
                    String message = "Please wait.";
                    switch (state) {
                        case TRANSFER_IN_PROGRESS:
                            message = "Uploading file (" + progress + "%)";
                            break;
                        case FINISHING_UP:
                            message = "Finishing up...";
                    }

                    notification.setLatestEventInfo(mContext, "Upload in progress",
                            message, null);
                    notification.contentIntent = contentIntent;
                    notification.flags = Notification.FLAG_ONGOING_EVENT;
                    mNotificationManager.notify(NOTIFICATION_ID, notification);
                }

                @Override
                public boolean isCancelled() {
                    DbObj obj = mMusubi.objForId(objId);
                    return (obj == null || !universalHash.equals(obj.getUniversalHashString()));
                }
            };

            try {
                uploadSuccessful = CorralHelper.uploadContent(mContext, obj, callback);
            } catch (Throwable t) {
                // TODO: there is a potential NPE in the upload mechanism.
                // TODO: differentiate local errors and network errors for retries.
                Log.e(TAG, "exception during upload", t);
                deletePendingUploadByObjectId(objId);
                uploadSuccessful = false;
            }
            Log.d(TAG, "upload complete: " + uploadSuccessful);

            if (uploadSuccessful) {
                mNotificationManager.cancel(NOTIFICATION_ID);
                deletePendingUploadByObjectId(objId);
                mContext.getContentResolver().notifyChange(MusubiService.PREPARED_ENCODED, this);
            } else if (callback.isCancelled()) {
                mNotificationManager.cancel(NOTIFICATION_ID);
            } else {
                notification.setLatestEventInfo(mContext, "Upload failed.", "There was a problem uploading your file.", contentIntent);
                notification.flags = 0;
                mNotificationManager.notify(NOTIFICATION_ID, notification);
            }
        }
    }

    void deletePendingUploadByObjectId(long objId) {
        String table = MPendingUpload.TABLE;
        String whereClause = MPendingUpload.COL_OBJECT_ID + "=" + objId;
        String[] whereArgs = null;
        mDb.getWritableDatabase().delete(table, whereClause, whereArgs);
    }
}
