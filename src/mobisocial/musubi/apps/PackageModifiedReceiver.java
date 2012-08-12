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

package mobisocial.musubi.apps;

import java.io.ByteArrayOutputStream;
import java.util.List;

import mobisocial.musubi.objects.AppStateObj;
import mobisocial.musubi.ui.MusubiBaseActivity;
import mobisocial.musubi.ui.SettingsActivity;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;

public class PackageModifiedReceiver extends BroadcastReceiver {
    final String TAG = getClass().getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = context.getSharedPreferences(SettingsActivity.PREFS_NAME, 0);
        if (!prefs.getBoolean(SettingsActivity.PREF_SHARE_APPS, false)) {
            return;
        }

        String action = intent.getAction();
        if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
            if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                handleAppUpdate(context, intent.getData());
            } else {
                handleAppUninstall(context, intent.getData());
            }
        } else if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
            handleAppInstall(context, intent.getData());
        }
    }

    private void handleAppUpdate(Context context, Uri app) {
    }

    private void handleAppUninstall(Context context, Uri app) {
        try {
            String pkg = app.getSchemeSpecificPart();
            JSONObject json = new JSONObject();
            json.put(AppStateObj.THUMB_TEXT, "Uninstalled " + pkg + ".");
            json.put(AppStateObj.PACKAGE_NAME, pkg);
            json.put("notify", false);
            json.put("uninstalled", true);
            if (MusubiBaseActivity.isDeveloperModeEnabled(context)) {
                Log.w(TAG, "APP UNINSTALL NOT REPORTED");
            }
            //TODO, add to apps feed, but as what identity?
            //MFeed.WELL_KNOWN_APPS
            //feed.postObj(new MemObj(AppStateObj.TYPE, json));
        } catch (JSONException e) {
        }
    }

    private void handleAppInstall(Context context, Uri app) {
        try {
            String pkg = app.getSchemeSpecificPart();
            JSONObject json = new JSONObject();
            String appName = getAppLabel(context, pkg);
            json.put(AppStateObj.THUMB_TEXT, "Installed " + appName + ".");
            json.put(AppStateObj.PACKAGE_NAME, pkg);
            json.put("appName", appName);
            json.put("notify", false);
            json.put("installed", true);
            byte[] thumb = getAppIcon(context, pkg);
            if (thumb != null) {
                json.put(AppStateObj.THUMB_RAW_JPG, true);
            }
            if (MusubiBaseActivity.isDeveloperModeEnabled(context)) {
                Log.w(TAG, "APP INSTALL NOT REPORTED");
            }
            //TODO, add to apps feed, but as what identity?
            //MFeed.WELL_KNOWN_APPS
            //feed.postObj(new MemObj(AppStateObj.TYPE, json, thumb));
        } catch (JSONException e) {
        }
    }

    private String getAppLabel(Context context, String pkg) {
        Intent launch = new Intent(Intent.ACTION_MAIN);
        launch.addCategory(Intent.CATEGORY_LAUNCHER);
        launch.setPackage(pkg);
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> infos = pm.queryIntentActivities(launch, 0);
        if (infos != null && infos.size() > 0) {
            return infos.get(0).loadLabel(pm).toString();
        } else {
            return pkg;
        }
    }

    private byte[] getAppIcon(Context context, String pkg) {
        Intent launch = new Intent(Intent.ACTION_MAIN);
        launch.addCategory(Intent.CATEGORY_LAUNCHER);
        launch.setPackage(pkg);
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> infos = pm.queryIntentActivities(launch, 0);
        if (infos != null && infos.size() > 0) {
            Drawable d = infos.get(0).loadIcon(pm);
            if (d instanceof BitmapDrawable) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                Bitmap bm = ((BitmapDrawable)d).getBitmap();

                int MAX_WIDTH = 200;
                int width = bm.getWidth();
                int height = bm.getHeight();
                if (width > MAX_WIDTH) {
                    float scaleSize = ((float) MAX_WIDTH) / width;
                    Matrix matrix = new Matrix();
                    matrix.postScale(scaleSize, scaleSize);
                    bm = Bitmap.createBitmap(
                            bm, 0, 0, width, height, matrix, true);
                }

                bm.compress(Bitmap.CompressFormat.PNG, 100, baos);
                return baos.toByteArray();
            }
        }
        return null;
    }
}
