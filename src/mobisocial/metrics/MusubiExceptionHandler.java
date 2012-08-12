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

package mobisocial.metrics;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import mobisocial.musubi.App;
import mobisocial.musubi.BootstrapActivity;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.ui.MusubiBaseActivity;
import mobisocial.musubi.ui.SettingsActivity;
import mobisocial.musubi.ui.util.UiUtil;
import mobisocial.musubi.util.CertifiedHttpClient;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.util.Log;

public class MusubiExceptionHandler implements UncaughtExceptionHandler {
    private final String TAG = getClass().getSimpleName();
    private final Context mContext;
    private final UncaughtExceptionHandler mDefaultHandler;

    public MusubiExceptionHandler(Context context, UncaughtExceptionHandler defaultHandler) {
        mContext = context;
        mDefaultHandler = defaultHandler;
    }

    @Override
    public void uncaughtException(final Thread thread, final Throwable ex) {
        new Thread() {
            @Override
            public void run() {
                try {
                    // force upgrade check on next boot.
                    SharedPreferences p = mContext.getSharedPreferences(
                            SettingsActivity.PREFS_NAME, 0);
                    p.edit().putLong(BootstrapActivity.PREF_LAST_UPDATE_CHECK, 0).commit();

                    // submit to chirp.
                    submitException(ex);
                } catch (IOException e) {
                    // TODO: put in UserMetrics database for later submission
                    Log.e(TAG, "failed to post message", e);
                }
            }
        }.start();

        Log.e(TAG, "Uncaught exception", ex);
        if (mDefaultHandler != null) {
            mDefaultHandler.uncaughtException(thread, ex);
        }
    }

    private void submitException(Throwable ex) throws IOException {
    	if (UsageMetrics.CHIRP_REPORTING_ENDPOINT == null) {
    		return;
    	}
        try {
            HttpClient http = getHttpClient(mContext);
            HttpPost post = new HttpPost(UsageMetrics.CHIRP_REPORTING_ENDPOINT);
            List<NameValuePair> data = new ArrayList<NameValuePair>();
            JSONObject json = jsonForException(mContext, ex, false);
            data.add(new BasicNameValuePair("json", json.toString()));
            post.setEntity(new UrlEncodedFormEntity(data, HTTP.UTF_8));

            HttpResponse response = http.execute(post);
            response.getEntity();
            int status = response.getStatusLine().getStatusCode();
            if (status != HttpStatus.SC_OK) {
                throw new IOException("Failed to post message to server. Http code: " + status);
            }
        } catch (ClientProtocolException e) {
            throw new IOException("Protocol exception while posting to server", e);
        }
    }

    static JSONObject jsonForException(Context context, Throwable ex, boolean caught) {
        JSONObject json = new JSONObject();
        try {
            Writer traceWriter = new StringWriter();
            PrintWriter printer = new PrintWriter(traceWriter);
            ex.printStackTrace(printer);
            json.put("type", "exception");
            json.put("caught", caught);
            json.put("app", context.getPackageName());
            json.put("message", ex.getMessage());
            json.put("trace", traceWriter.toString());
            json.put("timestamp", Long.toString(new Date().getTime()));

            boolean devmode = MusubiBaseActivity.isDeveloperModeEnabled(context);
            json.put("musubi_devmode", Boolean.toString(devmode));
            if (devmode) {
                IdentitiesManager im = new IdentitiesManager(App.getDatabaseSource(context));
                MIdentity id = im.getMyDefaultIdentity();
                String user = "Unknown";
                if (id != null) {
                    user = UiUtil.safeNameForIdentity(id);
                }
                json.put("musubi_devmode_user_id", user);
                user = App.getMusubi(context).userForLocalDevice(null).getName();
                json.put("musubi_devmode_user_name", user);
            }
            try {
                PackageInfo info =
                        context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                json.put("musubi_version_name", info.versionName);
                json.put("musubi_version_code",
                        Integer.toString(info.versionCode));
            } catch (NameNotFoundException e) {
                // shouldn't happen, but not fatal.
            }
            json.put("android_api", Integer.toString(Build.VERSION.SDK_INT));
            json.put("android_release", Build.VERSION.RELEASE);
            json.put("android_model", Build.MODEL);
            json.put("android_make", Build.MANUFACTURER);
        } catch (JSONException e) {}
        return json;
    }

    static final HttpClient getHttpClient(Context context) {
        HttpClient http = new CertifiedHttpClient(context);
        HttpParams params = http.getParams();
        HttpProtocolParams.setUseExpectContinue(params, false);
        HttpConnectionParams.setConnectionTimeout(params, 6000);
        HttpConnectionParams.setSoTimeout(params, 6000);
        return http;
    }

    public static void installHandler(Context context) {
        UncaughtExceptionHandler currentHandler = Thread.getDefaultUncaughtExceptionHandler();
        if (!(currentHandler instanceof MusubiExceptionHandler)) {
            Thread.setDefaultUncaughtExceptionHandler(
                    new MusubiExceptionHandler(context, currentHandler));
        }
    }
}
