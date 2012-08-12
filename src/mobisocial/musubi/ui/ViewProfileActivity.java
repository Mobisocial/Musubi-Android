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

import mobisocial.metrics.UsageMetrics;
import mobisocial.musubi.App;
import mobisocial.musubi.R;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.helpers.FeedManager;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.provider.MusubiContentProvider.Provided;
import mobisocial.musubi.syncadapter.MusubiProfile;
import mobisocial.musubi.ui.fragments.ConversationsFragment;
import mobisocial.musubi.ui.fragments.ViewProfileFragment;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Data;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;

import com.viewpagerindicator.TabPageIndicator;
import com.viewpagerindicator.TitleProvider;

public class ViewProfileActivity extends MusubiBaseActivity implements
		ConversationsFragment.OnFeedSelectedListener  {
    @SuppressWarnings("unused")
    private static final String TAG = "ViewContactActivity";

    public static final String PROFILE_ID = "profile_id";
    
    private long[] mFeedIds;

    private ViewPager mViewPager;
    private final List<Fragment> mFragments = new ArrayList<Fragment>();
    private final List<String> mLabels = new ArrayList<String>();

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_tabbed);
    	
        Intent intent = getIntent();
        
        Long id = null;
        // intent from address book
        Intent is = getIntent();
        Uri data = getIntent().getData();
        String type = getIntent().getType();
        if (data != null) {
            if (type != null && type.equals(MusubiContentProvider.getType(Provided.IDENTITIES_ID))) {
                id = ContentUris.parseId(data);
            } else if(data.getAuthority().equals(ContactsContract.AUTHORITY)) { 
            	// intent sent from address book have null type
                long rawId = ContentUris.parseId(data);
                id = getMusubiId(rawId);
            }
        }
        if (id == null) {
            id = intent.getLongExtra(PROFILE_ID, -1);
        }
        
        setTitle("Relationships");
        mLabels.add("Profile");
        mFragments.add(ViewProfileFragment.newInstance(id));
        
        SQLiteOpenHelper helper = App.getDatabaseSource(this);
        IdentitiesManager im = new IdentitiesManager(helper);
        MIdentity identity = im.getIdentityForId(id);
        if(identity == null) {
        	UsageMetrics.getUsageMetrics(this).report(new Throwable("Invalid identity " + id + " passed tp ViewProfileActivity"));
        	finish();
        	return;
        }

    	FeedManager fm = new FeedManager(helper);
    	mFeedIds = fm.getFeedsForIdentityId(id);

        setTitle(identity.name_);
        
        if (!identity.owned_) {
        	mLabels.add("Conversations");
            Bundle args = new Bundle();
            args.putLong(ConversationsFragment.ARG_IDENTITY_ID, identity.id_);
            ConversationsFragment f = new ConversationsFragment();
            f.setArguments(args);
            mFragments.add(f);
        }
        else {
            setTitle("Your profile");
        }

        PagerAdapter adapter = new ViewFragmentAdapter(getSupportFragmentManager(), mFragments, mLabels);
        mViewPager = (ViewPager)findViewById(R.id.feed_pager);
        mViewPager.setAdapter(adapter);

        //Bind the tab indicator to the adapter
        TabPageIndicator tabIndicator = (TabPageIndicator)findViewById(R.id.feed_titles);
        tabIndicator.setViewPager(mViewPager);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }
    

    public class ViewFragmentAdapter extends FragmentPagerAdapter implements TitleProvider {
        final int NUM_ITEMS;
        final List<Fragment> mFragments;
        final List<String> mTitles;

        public ViewFragmentAdapter(FragmentManager fm, List<Fragment> fragments, List<String> titles) {
            super(fm);
            mFragments = fragments;
            mTitles = titles;
            assert(mFragments.size() == mTitles.size());
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

    private long getMusubiId(long rawId) {
    	final Uri uri = Data.CONTENT_URI;
    	final String[] projection = {MusubiProfile.DATA_PID};
    	final String selection = Data._ID + "=?";
    	long id = -1;
    	
    	Cursor c = getContentResolver().query(uri, projection, selection, new String[]{String.valueOf(rawId)}, null);
    	if(c == null) {
    		finish();
    	}
    	
    	try {
    		while(c.moveToNext()) {
    			id = c.getLong(0);
    		}
    	} finally {
    		c.close();
    	}
    	
    	return id;
    }

	@Override
	public void onFeedSelected(int position, Uri feedUri) {
        Intent panner = new Intent(this, FeedPannerActivity.class);
        panner.setData(feedUri);
    	startActivity(panner);
	}

}