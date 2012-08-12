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

import java.util.Arrays;
import java.util.Calendar;

import mobisocial.metrics.MusubiMetrics;
import mobisocial.musubi.App;
import mobisocial.musubi.Helpers;
import mobisocial.musubi.R;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.helpers.FeedManager;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.model.helpers.MyAccountManager;
import mobisocial.musubi.objects.IntroductionObj;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.provider.MusubiContentProvider.Provided;
import mobisocial.musubi.service.MusubiService;
import mobisocial.musubi.ui.MusubiBaseActivity;
import mobisocial.musubi.ui.fragments.FeedListFragment.FeedListAdapter;
import mobisocial.musubi.ui.fragments.FeedListFragment.FeedSummaryLoader;
import mobisocial.musubi.ui.util.UiUtil;
import mobisocial.musubi.util.LessSpammyContentObserver;
import mobisocial.socialkit.Obj;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.SupportActivity;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;

/**
 * Displays a list of all user-accessible threads (feeds).
 *
 */
public class ConversationsFragment extends ListFragment implements LoaderManager.LoaderCallbacks<Cursor> {
    private static final String TAG = "ConversationsFragment";
    private static final boolean DBG = MusubiBaseActivity.DBG;
    private FeedListAdapter mFeeds;

    public static final String ARG_IDENTITY_ID = "identity_id";
    
    private OnFeedSelectedListener mFeedSelectedListener;
    private SQLiteOpenHelper mDatabaseSource;
    private FeedManager mFeedManager;
	private long[] mFeedIds;
	private long mIdentityId;
	private Activity mActivity;
	private LessSpammyContentObserver mObserver;
	
    public ConversationsFragment() {
        if (DBG) Log.d(TAG, "Instantiating new ConversationsFragment");
    }

    public interface OnFeedSelectedListener {
        public void onFeedSelected(int position, Uri feedUri);
    }


    @Override
    public void onAttach(SupportActivity activity) {
        super.onAttach(activity);
        mActivity = activity.asActivity();
        if (DBG) Log.d(TAG, "Attaching FeedListFragment.");
        mFeedSelectedListener = (OnFeedSelectedListener) activity;
        mDatabaseSource = App.getDatabaseSource(mActivity);
        mFeedManager = new FeedManager(mDatabaseSource);

        mIdentityId = (getArguments() != null) ? getArguments().getLong(ARG_IDENTITY_ID) : 0;
        mFeedIds = mFeedManager.getFeedsForIdentityId(mIdentityId);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mObserver = new LessSpammyContentObserver(new Handler(mActivity.getMainLooper())) {
        	@Override
        	public void lessSpammyOnChange(boolean arg0) {
        		if(mFeeds == null || !isAdded()) {
        			return;
                }
        		initLoaders(true);
        	}
		};
    }

    @Override
    public void onResume() {
        super.onResume();
    	mActivity.getContentResolver().registerContentObserver(MusubiService.PRIMARY_CONTENT_CHANGED, false, mObserver);
        mObserver.resetTimeout();
    	mObserver.dispatchChange(false);
    }
    
	@Override
	public void onPause() {
    	super.onPause();
    	mActivity.getContentResolver().unregisterContentObserver(mObserver);
	}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_feed_list, container, false);
        return v;
    }


    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (null != mActivity.findViewById(R.id.feed_view)) {
            getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        }

        Button startConversation = new Button(getActivity());
        startConversation.setText("New conversation");
        startConversation.setOnClickListener(mNewConversationListener);
        getListView().addHeaderView(startConversation);

        mFeeds = new FeedListAdapter(mActivity);
        mFeeds.setPinnedPartitionHeadersEnabled(false);

        for (int i = 0; i < FeedListFragment.DAYS_TO_SHOW+1; i++) {
        	mFeeds.addPartition(false, true);
        }
        setListAdapter(mFeeds);

        /** Load the latest feeds in the background **/
        initLoaders(false);
        mActivity.findViewById(R.id.start_something).setVisibility(View.GONE);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        getListView().setItemChecked(position, true);
        Long feedId = (Long)v.getTag();
        Uri feedUri = MusubiContentProvider.uriForItem(Provided.FEEDS, feedId);
        int feedPosition = position - getListView().getHeaderViewsCount();
        mFeedSelectedListener.onFeedSelected(feedPosition, feedUri);
    }

    void initLoaders(boolean restart) {
    	LoaderManager lm = getLoaderManager();
    	Calendar cal = Calendar.getInstance();
    	cal.set(Calendar.HOUR_OF_DAY, 0);
    	cal.set(Calendar.MINUTE, 0);

    	Bundle args = new Bundle();
    	args.putLong("start", cal.getTimeInMillis());
    	if (restart) {
    		lm.restartLoader(0, args, this);
    	} else {
    		lm.initLoader(0, args, this);
    	}
    	cal.add(Calendar.DAY_OF_MONTH, -1);
    	for (int i = 1; i < FeedListFragment.DAYS_TO_SHOW; i++) {
    		long time = cal.getTimeInMillis();
    		args = new Bundle();
    		args.putLong("start", time);
    		args.putLong("end", time + FeedListFragment.ONE_DAY);
    		if (restart) {
        		lm.restartLoader(i, args, this);
        	} else {
        		lm.initLoader(i, args, this);
        	}
    		cal.add(Calendar.DAY_OF_MONTH, -1);
    	}
    	args = new Bundle();
    	args.putLong("end", cal.getTimeInMillis() + FeedListFragment.ONE_DAY);
    	if (restart) {
    		lm.restartLoader(FeedListFragment.DAYS_TO_SHOW, args, this);
    	} else {
    		lm.initLoader(FeedListFragment.DAYS_TO_SHOW, args, this);
    	}
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        StringBuilder selection = new StringBuilder(FeedManager.visibleFeedSelection(mFeedIds));
        FeedSummaryLoader cl = new FeedSummaryLoader(getActivity(), selection.toString());
        cl.setUpdateThrottle(500);
        return cl;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
    	mFeeds.changeCursor(loader.getId(), cursor);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> arg0) {
    }

    View.OnClickListener mNewConversationListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            IdentitiesManager im = new IdentitiesManager(mDatabaseSource);
            MIdentity[] peeps = new MIdentity[2];
            peeps[1] = im.getIdentityForId(mIdentityId);
            peeps[0] = im.getMyDefaultIdentity(peeps[1]);
            MFeed feed = mFeedManager.createExpandingFeed(im.getIdentityForId(mIdentityId));
            UiUtil.addToWhitelistsIfNecessary(mFeedManager, new MyAccountManager(mDatabaseSource), mFeedManager.getFeedMembers(feed), true);

            Uri feedUri = MusubiContentProvider.uriForItem(Provided.FEEDS, feed.id_);
            Obj invitedObj = IntroductionObj.from(Arrays.asList(peeps), true);
            Helpers.sendToFeed(mActivity, invitedObj, feedUri);

            Intent view = new Intent(Intent.ACTION_VIEW);
            view.setDataAndType(feedUri, FeedManager.MIME_TYPE);
            view.setPackage(getActivity().getPackageName());
            startActivity(view);

            App.getUsageMetrics(mActivity).report(MusubiMetrics.FEED_CREATED_FROM_PROFILE);
        }
    };
}
