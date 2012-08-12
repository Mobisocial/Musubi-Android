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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import mobisocial.musubi.App;
import mobisocial.musubi.ui.MusubiBaseActivity;
import mobisocial.socialkit.User;

import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

/**
 * Record user events, supporting anonymous and non-anonymous reporting.
 */
public class UsageMetrics {
    public static final String CHIRP_REPORTING_ENDPOINT = null;//"https://chirp.yourserver.com";
    public static final String CHIRP_VERSIONING_ENDPOINT = null;//"https://chirp.yoursever.com/version/latest.json";
    public enum ReportingLevel { ANONYMOUS, NON_ANONYMOUS, DISABLED };

    static ReportingThread mReportingThread;

    private final Context mContext;
    private final User mUser;
    private ReportingLevel mLevel = ReportingLevel.ANONYMOUS;

	/**
	 * Creates a UserMetrics for tracking wholly anonymous statistics.
	*/
	public UsageMetrics(Context context) {
	    mContext = context.getApplicationContext();
	    mUser = null;
	    init();
	}

	public void setReportingLevel(ReportingLevel level) {
	    mLevel = level;
	}

	/**
	 * Creates a UserMetrics with stats linked against the given Musubi user.
	*/
	public UsageMetrics(Context context, User user) {
	    mContext = context.getApplicationContext();
	    mUser = user;
	    mLevel = ReportingLevel.NON_ANONYMOUS;
	    init();
	}

	private void init() {
	    if (mReportingThread == null) {
	        mReportingThread = new ReportingThread();
	        mReportingThread.start();
	        synchronized (mReportingThread) {
    	        while (!mReportingThread.isStarted) {
    	            try {
    	                mReportingThread.wait();
    	            } catch (InterruptedException e) {}
    	        }
	        }
	    }
	}
    private static UsageMetrics sUsageMetrics;
    public static UsageMetrics getUsageMetrics(Context c) {
    	synchronized(UsageMetrics.class) {
	        if (sUsageMetrics == null) {
	            if (MusubiBaseActivity.isDeveloperModeEnabled(c)) {
	                sUsageMetrics = new UsageMetrics(c, App.getMusubi(c).userForLocalDevice(null));
	            } else {
	                sUsageMetrics = new UsageMetrics(c);
	            }
	        }
	        return sUsageMetrics;
    	}
    }
	

	public void report(Throwable exception) {
		if (mLevel == ReportingLevel.DISABLED || UsageMetrics.CHIRP_REPORTING_ENDPOINT == null) {
            return;
        }
        Message msg = mReportingThread.mHandler.obtainMessage(ReportingHandler.HTTP_REQUEST);
        HttpPost post = new HttpPost(UsageMetrics.CHIRP_REPORTING_ENDPOINT);
        List<NameValuePair> data = new ArrayList<NameValuePair>();
        JSONObject json = MusubiExceptionHandler.jsonForException(mContext, exception, true);
        data.add(new BasicNameValuePair("json", json.toString()));
        try {
            post.setEntity(new UrlEncodedFormEntity(data, HTTP.UTF_8));
            msg.obj = post;
            mReportingThread.mHandler.sendMessage(msg);
        } catch (IOException e) {
        }
	}

	public void report(String feature) {
		if (mLevel == ReportingLevel.DISABLED || UsageMetrics.CHIRP_REPORTING_ENDPOINT == null) {
	        return;
	    }
	    Message msg = mReportingThread.mHandler.obtainMessage(ReportingHandler.HTTP_REQUEST);
	    HttpPost post = new HttpPost(UsageMetrics.CHIRP_REPORTING_ENDPOINT);
	    List<NameValuePair> data = new ArrayList<NameValuePair>();
        JSONObject json = jsonForReport(feature);
        try {
            json.put("feature", feature);
        } catch (JSONException e) {}
        data.add(new BasicNameValuePair("json", json.toString()));
        try {
            post.setEntity(new UrlEncodedFormEntity(data, HTTP.UTF_8));
    	    msg.obj = post;
    	    mReportingThread.mHandler.sendMessage(msg);
        } catch (IOException e) {
        }
	}

	public void report(String feature, String value) {
        if (mLevel == ReportingLevel.DISABLED || UsageMetrics.CHIRP_REPORTING_ENDPOINT == null) {
            return;
        }
        Message msg = mReportingThread.mHandler.obtainMessage(ReportingHandler.HTTP_REQUEST);
        HttpPost post = new HttpPost(UsageMetrics.CHIRP_REPORTING_ENDPOINT);
        List<NameValuePair> data = new ArrayList<NameValuePair>();
        JSONObject json = jsonForReport(feature);
        try {
            json.put("feature", feature);
            json.put("value", value);
        } catch (JSONException e) {}
        data.add(new BasicNameValuePair("json", json.toString()));
        try {
            post.setEntity(new UrlEncodedFormEntity(data, HTTP.UTF_8));
            msg.obj = post;
            mReportingThread.mHandler.sendMessage(msg);
        } catch (IOException e) {
        }
    }

	private JSONObject jsonForReport(String feature) {
	    JSONObject json = new JSONObject();
	    try {
	        json.put("type", "feature");
	        json.put("timestamp", Long.toString(new Date().getTime()));
	        json.put("feature", feature);
	        if (mUser != null && mLevel == ReportingLevel.NON_ANONYMOUS) {
	            json.put("user", mUser.getId());
	        }
	    } catch (JSONException e) {}
	    return json;
	}

	class ReportingThread extends Thread {
	    public Handler mHandler;
	    public boolean isStarted = false;

	    public void run() {
	        Looper.prepare();
	        mHandler = new ReportingHandler();
	        isStarted = true;
	        synchronized (this) {
                notify();
            }
	        Looper.loop();
	    }
	}

	class ReportingHandler extends Handler {
	    static final int HTTP_REQUEST = 1;

	    public void handleMessage(android.os.Message msg) {
            switch (msg.what) {
                case HTTP_REQUEST:
                    HttpClient http = MusubiExceptionHandler.getHttpClient(mContext);
                    HttpUriRequest request = (HttpUriRequest) msg.obj;
                    try {
                        http.execute(request);
                    } catch (IOException e) {
                    }
                break;
            }
        }
	}
}