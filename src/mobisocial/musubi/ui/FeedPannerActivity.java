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

import mobisocial.musubi.App;
import mobisocial.musubi.R;
import mobisocial.musubi.model.helpers.FeedManager;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.provider.MusubiContentProvider.Provided;
import mobisocial.musubi.service.MusubiService;
import mobisocial.musubi.ui.fragments.FeedViewFragment;
import mobisocial.musubi.ui.util.EmojiSpannableFactory;
import mobisocial.musubi.ui.util.UiUtil;
import mobisocial.musubi.util.LessSpammyContentObserver;
import mobisocial.nfc.Nfc;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.text.Spannable;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

/**
 * A view of a single feed, which can be panned left/right to see other feeds
 * based on this panner's query.
 * 
 * TODO: PannerActivity<QueryType, AdapterType>
 */
public class FeedPannerActivity extends MusubiBaseActivity implements OnPageChangeListener, LoaderCallbacks<ArrayList<Long>> {
    private ViewPager mFeedViewPager;
    private FeedFragmentAdapter mFragmentAdapter;

    private Nfc mNfc;
    private FeedManager mFeedManager;
    private Uri mFeedUri;
    LessSpammyContentObserver mObserver;
    final ArrayList<Long> mFeeds = new ArrayList<Long>();
	private boolean mDualPane;
	private InputMethodManager mInputMethodManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mNfc = new Nfc(this);
        mFeedManager = new FeedManager(App.getDatabaseSource(this));
        mObserver = new LessSpammyContentObserver(new Handler(getMainLooper())) {
            @Override
            public void lessSpammyOnChange(boolean arg0) {
                long feedId = Long.parseLong(mFeedUri.getLastPathSegment());
                String feedName = UiUtil.getFeedNameFromMembersList(mFeedManager, mFeedManager.lookupFeed(feedId));
                Spannable feedSpan = EmojiSpannableFactory.getInstance(FeedPannerActivity.this).newSpannable(feedName);
                setTitle(feedSpan);
            }
        };

        setContentView(R.layout.activity_feed_home);
        mDualPane = findViewById(R.id.feed_pager) == null;
        if(mDualPane) {
        	Intent i = new Intent();
        	i.setData(getIntent().getData());
        	i.setClass(this, FeedListActivity.class);
        	finish();
        	return;
        }

        mInputMethodManager = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        mFeedViewPager = (ViewPager)findViewById(R.id.feed_pager);
        mFeedViewPager.setOnPageChangeListener(this);
        
        int id = getResources().getIdentifier("action_bar_title", "id", "android");
        if(id == 0)
            id = R.id.abs__action_bar_title;
        TextView mTitle = (TextView)findViewById(id);
        mTitle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(FeedPannerActivity.this, FeedDetailsActivity.class);
                intent.setDataAndType(mFeedUri, MusubiContentProvider.getType(Provided.FEEDS_ID));
                startActivity(intent);
            }
        });

        setTitle("Musubi Conversation");        

        if (getIntent() != null && getIntent().getData() != null) {
        	mFeedUri = getIntent().getData();
        }
        //clear the intent so we dont try to scroll to that page again
        setIntent(null);
        mFragmentAdapter = new FeedFragmentAdapter();
        mFeedViewPager.setAdapter(mFragmentAdapter);
        getSupportLoaderManager().initLoader(0, null, this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mNfc.onPause(this);
        App.setCurrentFeed(this, null);
    	getContentResolver().unregisterContentObserver(mObserver);
    }

    //this is called after new intent which makes it always have the right logic
    //for picking which feed to show.
    @Override
    protected void onResume() {
        super.onResume();
        mNfc.onResume(this);

        App.setCurrentFeed(this, mFeedUri);
		getContentResolver().registerContentObserver(MusubiService.FEED_UPDATED, false, mObserver);
        mObserver.resetTimeout();
    	mObserver.dispatchChange(false);
    }

    @Override
    protected void onNewIntent(Intent intent) {
    	setIntent(intent);
        if (mNfc.onNewIntent(this, intent)) return;
    }

    class FeedFragmentAdapter extends FragmentStatePagerAdapter {
        public FeedFragmentAdapter() {
            super(getSupportFragmentManager());
        }

        @Override
        public int getCount() {
            return mFeeds.size();
        }

        @Override
        public Fragment getItem(int position) {
            Uri feedUri = MusubiContentProvider.uriForItem(Provided.FEEDS, mFeeds.get(position));
            Bundle args = new Bundle();
            args.putParcelable(FeedViewFragment.ARG_FEED_URI, feedUri);
            FeedViewFragment f = new FeedViewFragment();
            f.setArguments(args);
            return f;
        }
    }

    @Override
    public void onBackPressed() {
    	super.onBackPressed();
    	finish();
    }

    @Override
    public void onPageScrollStateChanged(int state) {
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
    }

    @Override
    public void onPageSelected(int position) {
    	long feedId = mFeeds.get(position);
    	mFeedUri = MusubiContentProvider.uriForItem(Provided.FEEDS, feedId);
        String feedName = UiUtil.getFeedNameFromMembersList(mFeedManager, mFeedManager.lookupFeed(feedId));
        App.setCurrentFeed(this, mFeedUri);
        Spannable span = EmojiSpannableFactory.getInstance(this).newSpannable(feedName);
        setTitle(span);
        mInputMethodManager.hideSoftInputFromWindow(mFeedViewPager.getWindowToken(), 0);

        //TODO: if you want some NFC behavior shown on the feed view, then it would go here 
        //... atleast some code to switch what is being shared as different pages become active
        
        //getActionBarHelper().setBackgroundDrawable(new ColorDrawable(Feed.colorFor(feedName)));
    }

	@Override
	public Loader<ArrayList<Long>> onCreateLoader(int id, Bundle args) {
        return new FeedIdListLoader(this);
	}

	@Override
	public void onLoadFinished(Loader<ArrayList<Long>> loader, ArrayList<Long> data) {
		mFeeds.clear();
		mFeeds.addAll(data);
        loadInitialFeed();
	}

	@Override
	public void onLoaderReset(Loader<ArrayList<Long>> loader) {
		// TODO Auto-generated method stub
		
	}

	static class FeedIdListLoader extends AsyncTaskLoader<ArrayList<Long>> {
		final FeedManager mFeedManager;
		ArrayList<Long> mData;

		public FeedIdListLoader(Context context) {
			super(context);
			mFeedManager = new FeedManager(App.getDatabaseSource(context));
		}

		@Override
		public ArrayList<Long> loadInBackground() {
			mData = mFeedManager.getFeedIdsForDisplay();
			return mData;
		}

		@Override
		protected void onStartLoading() {
			if (mData != null) {
				deliverResult(mData);
			} else {
				forceLoad();
			}
		}
	}

	void loadInitialFeed() {
		if (mFeeds.size() == 0) {
            Toast.makeText(this, "No feeds to view!", Toast.LENGTH_SHORT).show();
            App.setCurrentFeed(this, null);
            finish();
            return;
        }

		if (mFeedUri == null) {
			Toast.makeText(this, "No feed selected!", Toast.LENGTH_SHORT).show();
			return;
		}

		long desired_feed = ContentUris.parseId(mFeedUri);
        int size = mFeeds.size();
        for(int i = 0; i < size; ++i) {
        	if(mFeeds.get(i) == desired_feed) {
        		mFeedViewPager.setCurrentItem(i);
        		break;
        	}
        }
	}
}
