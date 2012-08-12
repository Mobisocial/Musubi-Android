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

package mobisocial.musubi.webapp;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import mobisocial.musubi.App;
import mobisocial.musubi.R;
import mobisocial.musubi.ui.MusubiBaseActivity;
import mobisocial.socialkit.musubi.DbFeed;
import mobisocial.socialkit.musubi.DbObj;
import mobisocial.socialkit.musubi.Musubi;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.mobisocial.corral.ContentCorral;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.ConsoleMessage;
import android.webkit.ConsoleMessage.MessageLevel;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.Toast;

/**
 * Runs a webapp by injecting SocialKit-JS in a webview.
 * 
 * SocialKitJS is bound to the application given in the extra EXTRA_APP_ID,
 * which must be set by Musubi in a trusted way-- this activity cannot be
 * safely exported.
 * 
 * {@see AppObj}
 */
public class WebAppActivity extends MusubiBaseActivity {
	public static final String EXTRA_APP_NAME = "name";
	public static final String EXTRA_APP_ID = "appid";
	public static final String EXTRA_APP_URI = "appurl";
    private static final String EXTRA_CURRENT_PAGE = "page";
    private String mCurrentPage;
    private String mAppId;
    private Uri mObjUri;
    private Uri mFeedUri;
    private DbObj mArgumentData;
    private DbFeed mArgumentFeed;
    private String mArgumentName;
    WebView mWebView;
	private Musubi mMusubi;
	private boolean mDestroyed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.appcorral);

        mMusubi = App.getMusubi(this);
        getSupportActionBar().hide();

        mAppId = getIntent().getStringExtra(EXTRA_APP_ID);
        if (mAppId == null) {
            toast("Must set app id for socialKitJS binding.");
            finish();
            return;
        }

        mArgumentName = getIntent().getStringExtra(EXTRA_APP_NAME);
        if (mArgumentName == null) {
        	mArgumentName = "Application";
        }

        mObjUri = getIntent().getData();
        if (mObjUri != null) {
            mArgumentData = mMusubi.objForUri(mObjUri);
        }

        mFeedUri = (Uri)getIntent().getParcelableExtra(Musubi.EXTRA_FEED_URI);
        if (mFeedUri != null) {
            mArgumentFeed = mMusubi.getFeed(mFeedUri);
        }

        
        if (savedInstanceState != null) {
            mCurrentPage = savedInstanceState.getString(EXTRA_CURRENT_PAGE);
        } else {
            Uri appUrl = getIntent().getParcelableExtra(EXTRA_APP_URI);
            if (appUrl != null) {
                mCurrentPage = appUrl.toString();
            }
        } 

        if (mCurrentPage == null) {
            Log.w(TAG, "No WebApp specified, bailing.");
            finish();
            return;
        }

        mWebView = (WebView) findViewById(R.id.webview);
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        WebAppWebViewClient webapp = new WebAppWebViewClient(this, mWebView, mAppId);
        mWebView.setWebViewClient(webapp);
        mWebView.addJavascriptInterface(webapp.mSocialKitJavascript, SocialKitJavascript.MUSUBI_JS_VAR);
        mWebView.setWebChromeClient(new WebAppWebChromeClient(webapp));

        new DataFromLocalhostTask(webapp).execute();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
    	//provide an override to escape
        if (keyCode == KeyEvent.KEYCODE_BACK) {
        	finish();
        }
    	return super.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // Check if the key event was the BACK key and if there's history
        if ((keyCode == KeyEvent.KEYCODE_BACK) && mWebView.canGoBack()) {
            mWebView.goBack();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
    		mWebView.loadUrl("javascript:globalAppContext.back()");
            Log.w(TAG, "pressed back");
            return true;
    	}
        // If it wasn't the BACK key or there's no web page history, bubble up to the default
        // system behavior (probably exit the activity)
        return super.onKeyUp(keyCode, event);
    }

    class WebAppWebChromeClient extends WebChromeClient {
        private WebAppWebViewClient mWebViewClient;

        public WebAppWebChromeClient(WebAppWebViewClient webViewClient) {
            mWebViewClient = webViewClient;
        }

        @Override
        public boolean onJsBeforeUnload(WebView view, String url, String message, JsResult result) {
            mWebViewClient.mSocialKitJavascript.unbind();
            return false;
        }
        
        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
        	if(consoleMessage.messageLevel() == MessageLevel.ERROR) {
        		//if there is an error in this web app for whatever reason
        		//including it not handling our callbacks correctly
        		//then go back
        	    String msg = "Long Press BACK to EXIT.\n" +
        	    		"This application had an error: " + consoleMessage.sourceId() + ":" + 
                        consoleMessage.lineNumber() + ":" + consoleMessage.message();
        		Toast.makeText(WebAppActivity.this, msg, Toast.LENGTH_LONG).show();
        	}
        	return false;
        }
    }

    class WebAppWebViewClient extends WebViewClient {
        private SocialKitJavascript mSocialKitJavascript;
    	private AlertDialog mAlertDialog;
    	private ProgressDialog mProgressDialog;

    	public WebAppWebViewClient(Activity context, WebView webView, String appId) {
    	    long objId = 0, feedId = 0;
    	    try {
    	        objId = Long.parseLong(mObjUri.getLastPathSegment());
    	    } catch (Throwable t) {}

    	    mSocialKitJavascript = SocialKitJavascript.bindAccess(context, appId, objId);
            mAlertDialog = new AlertDialog.Builder(WebAppActivity.this).create();

            mProgressDialog = new ProgressDialog(WebAppActivity.this);
            mProgressDialog.setTitle(mArgumentName);
            mProgressDialog.setMessage("Loading...");
            mProgressDialog.setCancelable(true);
            mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    finish();
                }
            });
    	}
    	
        @Override
        public void onPageFinished(WebView view, String url) {
            if (DBG) Log.d(TAG, "Page loaded, injecting musubi SocialKit bridge for " + url);
            mCurrentPage = url;

            // Launch musubi app
            SocialKitJavascript.SKUser user = null;
            SocialKitJavascript.SKDbObj obj = null;
            SocialKitJavascript.SKFeed feed = null;
            if (mArgumentData != null) {
                user = mSocialKitJavascript.new SKUser(
                		mMusubi.userForLocalDevice(
                                mArgumentData.getContainingFeed().getUri()));
                obj = mSocialKitJavascript.new SKDbObj(mArgumentData);
                feed = mSocialKitJavascript.new SKFeed(mArgumentData.getContainingFeed());
            } else if (mArgumentFeed != null) {
                obj = null;
                feed = mSocialKitJavascript.new SKFeed(mArgumentFeed);
                user = mSocialKitJavascript.new SKUser(mMusubi
                        .userForLocalDevice(mFeedUri));
            }

            String appId;
            String objJson;
            if (obj == null) {
                appId = url;
                objJson = "false";
            } else {
                appId = mArgumentData.getAppId();
                objJson = obj.toJson().toString();
            }
            String initSocialKit = new StringBuilder("javascript:")
                .append("Musubi._launch(").append(user.toJson() + ", " + feed.toJson() +
                        ", '" + appId + "', " + objJson + ")").toString();
            Log.d(TAG, "Android calling " + initSocialKit);
            mWebView.loadUrl(initSocialKit);
            ProgressDialog d = mProgressDialog;
            if (d != null && d.isShowing()) {
            	d.dismiss();
            }
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description,
                String failingUrl) {
            if (DBG) {
                Log.d(TAG, "socialkit.js error: " + errorCode + ", " + description);
            }
            mAlertDialog.setTitle("Connectivity Problem");
            mAlertDialog.setMessage("There was a problem loading " + mArgumentName + ". Please make sure that you have connectivity.");
            mAlertDialog.setButton("OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                	WebAppActivity.this.finish();
                    return;
                }
            });
            mAlertDialog.show();
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            mSocialKitJavascript.setLoadedUrl(url);
        }
    }

    @Override
    protected void onDestroy() {
    	super.onDestroy();
    	//this works around a memory leak with the webview that occurs at least on all <= 2.3.7
    	LinearLayout web_view_parent = (LinearLayout)findViewById(R.id.db1_root);
    	web_view_parent.removeAllViews();
    	mDestroyed  = true;
    	mWebView.destroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(EXTRA_CURRENT_PAGE, mCurrentPage);
    }

    /**
     * Serves content to a WebView from localhost rather than the original host.
     * This allows the webview to interact with content from the local device
     * while avoiding issues involving the same origin policy.
     *
     */
    class DataFromLocalhostTask extends AsyncTask<Void, Void, Void> {
        WebAppWebViewClient webapp;
        String data = null;
        String baseUrl = null;
        String mCachedPage;

        public DataFromLocalhostTask(WebAppWebViewClient webapp) {
            this.webapp = webapp;
        }

        @Override
        protected void onPreExecute() {
            webapp.mProgressDialog.show();
        }

        @Override
        protected Void doInBackground(Void... params) {
        	Uri appUri = Uri.parse(mCurrentPage);
        	Uri cached = ContentCorral.getWebappCacheUrl(appUri);
        	if (cached != null) {
        		mCachedPage = cached.toString();
        	} else {
        		mCachedPage = ContentCorral.cacheWebApp(appUri, "tmp").toString();
        	}
        	return null;
        }
        @Override
        protected void onPostExecute (Void result) {
        	if(mDestroyed)
        		return;
        	mWebView.loadUrl(mCachedPage);
        }
    }
}