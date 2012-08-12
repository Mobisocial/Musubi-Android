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

package mobisocial.musubi.ui.fragments;

import java.io.IOException;
import java.util.HashSet;

import mobisocial.metrics.MusubiMetrics;
import mobisocial.musubi.App;
import mobisocial.musubi.R;

import org.apache.commons.io.IOUtils;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnKeyListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.SupportActivity;
import android.text.Html;
import android.text.util.Linkify;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;
import android.widget.TabWidget;
import android.widget.TextView;

public class EulaFragment extends DialogFragment {
	public interface Callback {
		void eulaResult(boolean accepted);
	}
	public static final String TAG = "EulaFragment";


	public static final String PREFS_NAME = "eula";
	public static final String EULA_VERSION_KEY = "version";
	//bump me and update the copy bundled in the assests directory
	public static final int CURRENT_VERSION = 0;
	
	public static final String EULA_LOCAL_ASSET = "eula.txt";
	public static final String EULA_WEB_URL = "http://mobisocial.stanford.edu/musubi/public/#/eula";
	public static final String PRIVACY_LOCAL_ASSET = "privacypolicy.txt";
	public static final String PRIVACY_WEB_URL = "http://mobisocial.stanford.edu/musubi/public/#/privacy";

	public static final int EULA_WEB_DELAY = 12 * 1000;

	public static final String EULA_MAIL_SUBJECT = "Musubi Usage Agreements";
	
	private Button mAcceptButton;
	private Button mRejectButton;
	private Button mEmailButton;
	private CheckBox mAcceptedCheckbox;
	private ScrollView mEulaScroll;
	private LinearLayout mLoading;
	private TextView mEulaBodyView;
	private TextView mPrivacyBodyView;
	private LinearLayout mEulaAcceptArea;
	private Button mDismissButton;
	private Activity mActivity;
	private TabHost mTabs;
	private String mEulaTxt;
	private String mPrivacyTxt;
	private boolean mAccepted = false;


	private boolean mRequired;


	public EulaFragment() {
		//in case something weird happens
		//the default is true
		mRequired = true;
	}


	public EulaFragment(boolean required) {
		mRequired = required;
	}

	public static boolean needsEULA(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(EulaFragment.PREFS_NAME, 0);
        int old_version = prefs.getInt(EulaFragment.EULA_VERSION_KEY, -1);
        if(old_version < 0 || old_version < EulaFragment.CURRENT_VERSION) {
        	return true;
        }
        return false;
	}

	public static void acceptedEULA(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(EulaFragment.PREFS_NAME, 0);
        prefs.edit().putInt(EulaFragment.EULA_VERSION_KEY, EulaFragment.CURRENT_VERSION).commit();
	}
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}
    @Override
    public void onAttach(SupportActivity activity) {
        super.onAttach(activity);
        mActivity = activity.asActivity();
    }
    
	class AgreementLoader extends AsyncTask<Void, Void, Void> {
        @Override
        protected void onPreExecute() {
            mEulaScroll.setVisibility(View.GONE);
            mLoading.setVisibility(View.VISIBLE);
        }

        @Override
        protected Void doInBackground(Void... params) {
            if(mEulaTxt == null) {
                try {
                    mEulaTxt = IOUtils.toString(mActivity.getResources().getAssets().open(EULA_LOCAL_ASSET));
                } catch (IOException e) {
                    Log.e(TAG, "failed to set text on eula view", e);
                    mEulaTxt = "Visit " + EULA_WEB_URL;
                    Linkify.addLinks(mEulaBodyView, Linkify.ALL);
                }
            }
            if(mPrivacyTxt == null) {
                try {
                    mPrivacyTxt = IOUtils.toString(mActivity.getResources().getAssets().open(PRIVACY_LOCAL_ASSET));
                } catch (IOException e) {
                    Log.e(TAG, "failed to set text on eula view", e);
                    mPrivacyTxt = "Visit " + PRIVACY_WEB_URL;
                    Linkify.addLinks(mPrivacyBodyView, Linkify.ALL);
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            mEulaBodyView.setText(mEulaTxt);
            mPrivacyBodyView.setText(mPrivacyTxt);

            mEulaScroll.setVisibility(View.VISIBLE);
            mLoading.setVisibility(View.GONE);
        }
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		Dialog dialog = super.onCreateDialog(savedInstanceState);
		dialog.setContentView(R.layout.eula);
	
		//load the tabs
        mTabs = (TabHost)dialog.findViewById(android.R.id.tabhost);
        mTabs.setup();  

        TabSpec tab_one = mTabs.newTabSpec("eula_tab"); 
        tab_one.setContent(R.id.eula_body);  
        tab_one.setIndicator("EULA"); 
        mTabs.addTab(tab_one);  

        TabSpec tab_two = mTabs.newTabSpec("privacy_tab"); 
        tab_two.setContent(R.id.priv_body);  
        tab_two.setIndicator("Privacy Policy");
        mTabs.addTab(tab_two);  

        //work around lack of ability to control tab height from xml completely
        TabWidget tab_widget = (TabWidget)dialog.findViewById(android.R.id.tabs);
        mTabs.getTabWidget().getChildAt(0).getLayoutParams().height = tab_widget.getLayoutParams().height;
        mTabs.getTabWidget().getChildAt(1).getLayoutParams().height = tab_widget.getLayoutParams().height;
        
		//load the agreements
        mEulaScroll = (ScrollView)dialog.findViewById(R.id.eula_scroll);
        mLoading = (LinearLayout)dialog.findViewById(R.id.loading);
        mEulaBodyView = (TextView)dialog.findViewById(R.id.eula_body);
        mPrivacyBodyView = (TextView)dialog.findViewById(R.id.priv_body);

        mAcceptedCheckbox = (CheckBox)dialog.findViewById(R.id.eula_checkbox);
        mAcceptButton = (Button)dialog.findViewById(R.id.eula_accept);
        mRejectButton = (Button)dialog.findViewById(R.id.eula_reject);
        mEmailButton = (Button)dialog.findViewById(R.id.eula_email);
        mDismissButton = (Button)dialog.findViewById(R.id.eula_dismiss);
        mEulaAcceptArea = (LinearLayout)dialog.findViewById(R.id.eula_accept_area);
        
        mAcceptedCheckbox.setOnCheckedChangeListener(new OnAcceptToggle());
        mAcceptButton.setOnClickListener(new OnAccept());
        mRejectButton.setOnClickListener(new OnReject());
        mEmailButton.setOnClickListener(new OnEmail());
        mDismissButton.setOnClickListener(new OnDismiss());
        
		if(!mRequired) {
			mEulaAcceptArea.setVisibility(View.GONE);
			mAcceptButton.setVisibility(View.GONE);
			mRejectButton.setVisibility(View.GONE);
			mDismissButton.setVisibility(View.VISIBLE);
		} else {
			mEulaAcceptArea.setVisibility(View.VISIBLE);
			mAcceptButton.setVisibility(View.VISIBLE);
			mRejectButton.setVisibility(View.VISIBLE);
			mDismissButton.setVisibility(View.GONE);
		}

		mAcceptButton.setEnabled(mAcceptedCheckbox.isChecked());        
        dialog.setTitle("Musubi Usage Agreements");
        dialog.setOnKeyListener(new OnKey());

        new AgreementLoader().execute();
    	return dialog;
    }
	class OnAcceptToggle implements OnCheckedChangeListener {
		@Override
		public void onCheckedChanged(CompoundButton buttonView,
				boolean isChecked) {
			mAcceptButton.setEnabled(isChecked);
		}
	}
	class OnAccept implements OnClickListener {
		@Override
		public void onClick(View v) {
			if(!mAcceptedCheckbox.isChecked()) {
				return;
			}
			App.getUsageMetrics(mActivity).report(MusubiMetrics.EULA_ACCEPTED);
			mAccepted = true;
			broadcastResult();
			dismiss();
		}
	}
	class OnReject implements OnClickListener {
		@Override
		public void onClick(View v) {
		    App.getUsageMetrics(mActivity).report(MusubiMetrics.EULA_DECLINED);
			broadcastResult();
			dismiss();
		}
	}
	class OnDismiss implements OnClickListener {
		@Override
		public void onClick(View v) {
			dismiss();
		}
	}
	class OnEmail implements OnClickListener {
		@Override
		public void onClick(View v) {
		    App.getUsageMetrics(mActivity).report(MusubiMetrics.EULA_EMAIL_REQUESTED);
			Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(android.content.Intent.EXTRA_SUBJECT, EULA_MAIL_SUBJECT);
            intent.putExtra(android.content.Intent.EXTRA_TEXT, mEulaTxt + "\n\n\n\n\n" + mPrivacyTxt);
			startActivity(Intent.createChooser(intent, "Send Agreements..."));
		}
	}
	public class OnKey implements OnKeyListener {
		@Override
		public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
			//disable back
	        if (mRequired && keyCode == KeyEvent.KEYCODE_BACK) {
	        	return true;
	        }
	        return false;
		}
	}

	//closing one eula should close them all
    private static HashSet<Callback> sCallbacks = new HashSet<Callback>();


	public void addCallback(Callback callback) {
		sCallbacks.add(callback);
	}
	
	public void broadcastResult() {
		for(Callback c : sCallbacks) {
			c.eulaResult(mAccepted);
		}
		sCallbacks.clear();
	}
 }