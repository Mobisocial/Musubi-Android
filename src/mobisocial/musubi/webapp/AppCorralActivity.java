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

import mobisocial.musubi.App;
import mobisocial.musubi.BJDNotImplementedException;
import mobisocial.musubi.Helpers;
import mobisocial.musubi.PickContactsActivity;
import mobisocial.musubi.R;
import mobisocial.musubi.objects.AppObj;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.ui.MusubiBaseActivity;
import mobisocial.musubi.util.ActivityCallout;
import mobisocial.socialkit.Obj;
import mobisocial.socialkit.musubi.DbFeed;
import mobisocial.socialkit.musubi.Musubi;
import mobisocial.socialkit.musubi.multiplayer.Multiplayer;
import mobisocial.socialkit.obj.MemObj;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * A web-based 'app store' for finding new Musubi apps. Also
 * contains a partial implementaion of the SocialKit-JS library.
 */
public class AppCorralActivity extends MusubiBaseActivity {
    private static final String EXTRA_CURRENT_PAGE = "page";
    private String mCurrentPage;
    private SocialKitJavascript mSocialKitJavascript;
    private Uri mFeedUri;
    private Musubi mMusubi;
    WebView mWebView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mFeedUri = (Uri)getIntent().getParcelableExtra(Musubi.EXTRA_FEED_URI);
        setContentView(R.layout.appcorral);
        if (savedInstanceState != null) {
            mCurrentPage = savedInstanceState.getString(EXTRA_CURRENT_PAGE);
        } else if (getIntent().getData() != null) {
            mCurrentPage = getIntent().getDataString();
        } else {
            mCurrentPage = "http://musubi.us/apps";
        }
        WebViewClient webViewClient = new AppStoreWebViewClient();
        
        mWebView = (WebView) findViewById(R.id.webview);
        mWebView.getSettings().setJavaScriptEnabled(true);
        //mWebView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        mWebView.setWebViewClient(webViewClient);
        mSocialKitJavascript = SocialKitJavascript.bindAccess(this, MusubiContentProvider.SUPER_APP_ID, 0);
        mWebView.addJavascriptInterface(mSocialKitJavascript, SocialKitJavascript.MUSUBI_JS_VAR);
        mWebView.loadUrl(mCurrentPage);

        mMusubi = App.getMusubi(this);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Check if the key event was the BACK key and if there's history
        if ((keyCode == KeyEvent.KEYCODE_BACK) && mWebView.canGoBack()) {
            mWebView.goBack();
            return true;
        }
        // If it wasn't the BACK key or there's no web page history, bubble up to the default
        // system behavior (probably exit the activity)
        return super.onKeyDown(keyCode, event);
    }
    
    class AppStoreWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();
            if (scheme.startsWith("http")) {
                if (!uri.getPath().endsWith(".apk")) {
                    return false;   
                }
            }

            // TODO: Launch in WebAppActivity
            if (scheme.startsWith("socialkit")) {
                String appUrl = uri.buildUpon().scheme("http").build().toString();
                Intent app = new Intent(Intent.ACTION_VIEW);
                app.setClass(AppCorralActivity.this, WebAppActivity.class);
                app.putExtra(Musubi.EXTRA_FEED_URI, mFeedUri);
                app.putExtra(WebAppActivity.EXTRA_APP_URI, Uri.parse(appUrl));
                startActivity(app);
                return true;
            }
            // Otherwise, the link is not for a page on my site, so launch another Activity that handles URLs
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            if (DBG) Log.d(TAG, "Page loaded, injecting musubi SocialKit bridge for " + url);
            mCurrentPage = url;

            // Launch musubi app
            DbFeed dbFeed = mMusubi.getFeed(mFeedUri);
            SocialKitJavascript.SKFeed feed = mSocialKitJavascript.new SKFeed(dbFeed);
            SocialKitJavascript.SKUser user = mSocialKitJavascript.new SKUser(
            		mMusubi.userForLocalDevice(dbFeed.getUri()));
            String initSocialKit = new StringBuilder("javascript:")
                .append("Musubi._launch(").append(
                        user.toJson() + ", " + feed.toJson() + ",'someappid', false)").toString();
            Log.d(TAG, "Android calling " + initSocialKit);
            mWebView.loadUrl(initSocialKit);
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description,
                String failingUrl) {
            if (DBG) {
                Log.d(TAG, "socialkit.js error: " + errorCode + ", " + description);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(EXTRA_CURRENT_PAGE, mCurrentPage);
    }

    /**
     * When a webapp is launched, we create an Obj of type "app"
     * representing the app session. The obj's json has a field
     * web_url identifying the webapp code, and a "membership"
     * list of participants. This class manages the picker for
     * selecting contacts. Once the user has selected participants,
     * the obj is created and sent to the current feed, and the app is
     * launched.
     */
    private class MembersSelectedCallout implements ActivityCallout {
        private final Context mContext;
        private final Uri mFeedUri;
        private final String mAppUrl;
        private int mMaxPlayers = -1;

        public MembersSelectedCallout(Context context, Uri feedUri, String appUrl) {
            mFeedUri = feedUri;
            mContext = context;
            mAppUrl = appUrl;
        }

        public void setMaxAdditionalMembers(int p) {
            mMaxPlayers = p;
        }

        @Override
        public Intent getStartIntent() {
            Intent i = new Intent(mContext, PickContactsActivity.class);
            i.putExtra(PickContactsActivity.INTENT_EXTRA_PARENT_FEED, mFeedUri);
            i.putExtra(PickContactsActivity.INTENT_EXTRA_MEMBERS_MAX, mMaxPlayers);
            return i;
        }

        @Override
        public void handleResult(int resultCode, Intent data) {
            if (resultCode == Activity.RESULT_OK) {
                // Create and share new application instance
                Obj obj = objForPickerResult(mAppUrl, data);
                /*SignedObjFuture future =*/ Helpers.sendToFeed(mContext, obj, mFeedUri);
                //new AppObj().activate(mContext, future.get());
            } else {
                Log.i(TAG, "No members selected.");
            }
        }

        public Obj objForPickerResult(String appUrl, Intent data) {
            long[] contactIds = data.getLongArrayExtra("contacts");
            JSONArray participantIds = new JSONArray();

            BJDNotImplementedException.except(BJDNotImplementedException.MSG_LOCAL_PERSON_ID);
            String personId = null;
            participantIds.put(personId);
            for (long id : contactIds) {
                Log.d(TAG, "objForPicker is broken");
                // TODO: Add to participantIds array
            }
            JSONObject json = new JSONObject();
            try {
                json.put(Multiplayer.OBJ_MEMBERSHIP, participantIds);
                json.put(AppObj.WEB_URL, appUrl);
            } catch (JSONException e) {
                Log.e(TAG, "Error setting up json");
            }
            return new MemObj(AppObj.TYPE, json);
        }
    }
}
