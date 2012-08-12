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

package org.mobisocial.widget;

import mobisocial.socialkit.musubi.DbObj;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.widget.RemoteViews;
import mobisocial.musubi.App;
import mobisocial.musubi.R;
import mobisocial.musubi.model.helpers.FeedManager;
import mobisocial.musubi.objects.StatusObj;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.provider.MusubiContentProvider.Provided;

public class StatusWidget extends AppWidgetProvider {
    static final String TAG = "musubi-widget";
    final static Uri feedUri = MusubiContentProvider.uriForItem(Provided.FEEDS, -1);

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // To prevent any ANR timeouts, we perform the update in a service
        context.startService(new Intent(context, UpdateService.class));
    }
    
    public static class UpdateService extends Service {
        ContentObserver mContentObserver;

        @Override
        public void onStart(Intent intent, int startId) {
            updateWidget();

            mContentObserver = new ContentObserver(new Handler(getMainLooper())) {
                @Override
                public void onChange(boolean selfChange) {
                    updateWidget();
                }
            };
            getContentResolver().registerContentObserver(feedUri, false, mContentObserver);
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        private void updateWidget() {
            RemoteViews updateViews = buildUpdate(UpdateService.this);

            // Push update for this widget to the home screen
            ComponentName thisWidget = new ComponentName(UpdateService.this, StatusWidget.class);
            AppWidgetManager manager = AppWidgetManager.getInstance(this);
            manager.updateAppWidget(thisWidget, updateViews);
        }

        public static RemoteViews buildUpdate(Context context) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_message);
            String status;

            String[] projection = new String[]
                    { DbObj.COL_ID, DbObj.COL_JSON, DbObj.COL_IDENTITY_ID };
            String selection = "type = ?";
            String[] selectionArgs = new String[] { StatusObj.TYPE };
            String sortOrder = DbObj.COL_ID + " desc limit 1";
            Cursor c = context.getContentResolver().query(feedUri, projection,
                    selection, selectionArgs, sortOrder);

            PendingIntent pendingIntent;
            if (c != null && c.moveToFirst()) {
                DbObj obj = App.getMusubi(context).objForCursor(c);
                if (obj == null || obj.getSender() == null) {
                    return null;
                }
                status = obj.getSender().getName() + ": " +
                        obj.getJson().optString(StatusObj.TEXT);

                Intent viewIntent = new Intent(Intent.ACTION_VIEW);
                viewIntent.setDataAndType(obj.getContainingFeed().getUri(), FeedManager.MIME_TYPE);
                pendingIntent = PendingIntent.getActivity(context, 0, viewIntent,
                        PendingIntent.FLAG_CANCEL_CURRENT);
            } else {
                status = "No messages found.";
                pendingIntent = null;
            }
            views.setTextViewText(R.id.message, status);
            views.setOnClickPendingIntent(R.id.message, pendingIntent);
            return views;
        }
    }
}