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

import mobisocial.musubi.R;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.provider.MusubiContentProvider.Provided;
import mobisocial.socialkit.musubi.DbObj;

import org.mobisocial.corral.CorralHelper.DownloadProgressCallback;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

public class DownloadProgressNotificationCallback implements DownloadProgressCallback {
    final Context mContext;
    final NotificationManager mNotificationManager;
    final Notification mNotification;
    final long mObjId;

    int NOTIFICATION_TRANSFERRING = 912;
    int NOTIFICATION_COMPLETE = 913;

    public DownloadProgressNotificationCallback(Context context, long objId) {
        mContext = context;
        mNotificationManager = (NotificationManager)context.getSystemService(
                Context.NOTIFICATION_SERVICE);

        String ticker = "Download in progress...";
        long time = System.currentTimeMillis();
        mNotification = new Notification(R.drawable.icon, ticker, time);
        mObjId = objId;
    }
    @Override
    public void onProgress(DownloadState state, DownloadChannel channel, int progress) {
        String channelStr = getChannelString(channel);

        Intent musubi = new Intent(Intent.ACTION_MAIN);
        musubi.addCategory(Intent.CATEGORY_LAUNCHER);
        musubi.setPackage(mContext.getPackageName());
        final PendingIntent contentIntent = PendingIntent.getActivity(mContext, 0, musubi, 0);
        mNotification.contentIntent = contentIntent;
        mNotification.flags = Notification.FLAG_ONGOING_EVENT;

        int notificationId = NOTIFICATION_TRANSFERRING;
        switch (state) {
            case DOWNLOAD_PENDING:
                mNotification.setLatestEventInfo(mContext, "Download pending...",
                        "Waiting for other downloads to complete.", contentIntent);       
                break;
            case PREPARING_CONNECTION:
                mNotification.setLatestEventInfo(mContext, "Download in progress",
                        "Preparing to download from " + channelStr, contentIntent);       
                break;
            case TRANSFER_IN_PROGRESS:
                String text = "Downloading from " + channelStr;
                if (progress > 0) {
                    text += " (" + progress + "%)";
                }
                mNotification.setLatestEventInfo(mContext, "Download in progress",
                        text, contentIntent);
                break;
            case TRANSFER_COMPLETE:
                notificationId = NOTIFICATION_COMPLETE;
                mNotificationManager.cancel(NOTIFICATION_TRANSFERRING);
                mNotification.tickerText = "Download complete";
                if (progress == SUCCESS) {
                    Intent view = new Intent(Intent.ACTION_VIEW, MusubiContentProvider.uriForItem(Provided.OBJS_ID, mObjId));
                    PendingIntent viewingIntent = PendingIntent.getActivity(mContext, 0, view, 0);
                    mNotification.setLatestEventInfo(mContext, "Download complete",
                            "Your file was downloaded successfully.", viewingIntent);    
                } else {
                    mNotification.setLatestEventInfo(mContext, "Download error",
                            "Failed to download file.", contentIntent);
                }
                mNotification.flags = Notification.FLAG_AUTO_CANCEL;
        }
        mNotificationManager.notify(notificationId, mNotification);
    }

    String getChannelString(DownloadChannel channel) {
        switch (channel) {
            case BLUETOOTH:
                return "Bluetooth";
            case LAN:
                return "LAN";
            case SERVER:
                return "server";
        }
        return "Unknown";
    }
}
