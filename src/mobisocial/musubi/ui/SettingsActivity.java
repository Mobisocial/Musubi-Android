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

package mobisocial.musubi.ui;

import java.util.ArrayList;
import java.util.List;

import mobisocial.musubi.App;
import mobisocial.musubi.R;
import mobisocial.musubi.feed.iface.Filterable;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.obj.ObjHelpers;
import mobisocial.musubi.ui.fragments.AccountLinkDialog;
import mobisocial.musubi.ui.fragments.SettingsFragment;
import mobisocial.musubi.ui.fragments.ViewProfileFragment;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.View;

import com.viewpagerindicator.TabPageIndicator;
import com.viewpagerindicator.TitleProvider;

public class SettingsActivity extends MusubiBaseActivity implements Filterable {
    public static final String PREFS_NAME = "MusubiPrefsFile";

    public static final String PREF_ALREADY_SAW_FACEBOOK_POST = "facebook_posted";
    public static final String PREF_ANONYMOUS_STATS = "stats";
	public static final String PREF_RINGTONE = "ringtone";
	public static final String PREF_SHARE_APPS = "share_apps";
	public static final boolean PREF_SHARE_APPS_DEFAULT = true;
	public static final String PREF_AUTOPLAY = "autoplay";
	public static final boolean PREF_AUTOPLAY_DEFAULT = false;
	public static final String PREF_WIFI_FINGERPRINTING = "wifi_fingerprinting";
	public static final boolean PREF_WIFI_FINGERPRINTING_DEFAULT = false;
	
	public static final String ACTION = "settings_action";
	public static enum SettingsAction {PROFILE, ACCOUNT, SETTINGS};
    
    private static final String TAG = "SettingsActivity";

	public static final String PREF_SHARE_CONTACT_ADDRESS = "share_address";
	public static final boolean PREF_SHARE_CONTACT_ADDRESS_DEFAULT = false;
    private ViewPager mViewPager;
    private final List<Fragment> mFragments = new ArrayList<Fragment>();
    private final List<String> mLabels = new ArrayList<String>();

    private final String[] filterTypes = ObjHelpers.getRenderableTypes();
    private boolean[] checked;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tabbed);
        checked = new boolean[filterTypes.length];
    	
        for(int x = 0; x < filterTypes.length; x++) {
        	checked[x] = true;
        }

        
        setTitle("My Settings");
        mLabels.add("Profile");
        mLabels.add("Accounts");
        mLabels.add("Settings");
        SQLiteOpenHelper SQLiteOpenHelper = App.getDatabaseSource(this);
        IdentitiesManager identitiesManager = new IdentitiesManager(SQLiteOpenHelper);
        long myId = identitiesManager.getOwnedIdentities().get(0).id_;
        mFragments.add(ViewProfileFragment.newInstance(myId));
        mFragments.add(AccountLinkDialog.newInstance());
        mFragments.add(SettingsFragment.newInstance());

        PagerAdapter adapter = new ViewFragmentAdapter(getSupportFragmentManager(), mFragments, mLabels);
        mViewPager = (ViewPager)findViewById(R.id.feed_pager);
        mViewPager.setAdapter(adapter);

        //Bind the title indicator to the adapter
        TabPageIndicator tabIndicator = (TabPageIndicator)findViewById(R.id.feed_titles);
        tabIndicator.setViewPager(mViewPager);

        if (getIntent().getStringExtra(ACTION) != null) {
	        switch (SettingsAction.valueOf(getIntent().getStringExtra(ACTION))) {
		        case PROFILE :
		        	Log.w(TAG, "viewing profile");
		        	mViewPager.setCurrentItem(0);
		        	break;
		        case ACCOUNT :
		        	Log.w(TAG, "viewing account");
		        	mViewPager.setCurrentItem(1);
		        	break;
		        case SETTINGS :
		        	Log.w(TAG, "viewing settings q");
		        	mViewPager.setCurrentItem(2);
		        	break;
		        default :
		        	mViewPager.setCurrentItem(0);
		        	break;
	        }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        for (Fragment f : mFragments) {
        	f.onActivityResult(requestCode, resultCode, data);
        }
    }
    public class ViewFragmentAdapter extends FragmentPagerAdapter implements TitleProvider {
        final int NUM_ITEMS;
        final List<Fragment> mFragments;
        final List<String> mTitles;

        public ViewFragmentAdapter(FragmentManager fm, List<Fragment> fragments, List<String> titles) {
            super(fm);
            mFragments = fragments;
            mTitles = titles;
            NUM_ITEMS = mFragments.size();
        }

        @Override
        public int getCount() {
            return NUM_ITEMS;
        }

        @Override
        public Fragment getItem(int position) {
            return mFragments.get(position);
        }

		@Override
		public String getTitle(int position) {
            return mTitles.get(position);
		}
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
    	super.onConfigurationChanged(newConfig);
    }
    private View.OnClickListener mViewSelected = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Integer i = (Integer)v.getTag();
            mViewPager.setCurrentItem(i);
        }
    };


    @Override
	public String[] getFilterTypes() {
		return filterTypes;
	}

	@Override
	public boolean[] getFilterCheckboxes() {
		return checked;
	}

	@Override
	public void setFilterCheckbox(int position, boolean check) {
		checked[position] = check;
	}
}