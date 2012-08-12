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

import gnu.trove.list.array.TLongArrayList;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import mobisocial.musubi.App;
import mobisocial.musubi.R;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.helpers.FeedManager;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.provider.MusubiContentProvider.Provided;
import mobisocial.musubi.ui.util.UiUtil;
import mobisocial.musubi.ui.widget.MultiIdentitySelector;
import mobisocial.musubi.ui.widget.MultiIdentitySelector.OnIdentitiesUpdatedListener;
import mobisocial.musubi.ui.widget.MultiIdentitySelector.OnRequestAddIdentityListener;
import mobisocial.musubi.util.SimpleCursorLoader;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RecentTaskInfo;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Intents.Insert;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CursorAdapter;
import android.widget.Gallery;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class FeedIdentityGrid extends MusubiBaseActivity implements LoaderManager.LoaderCallbacks<Cursor> {
    static final int LOAD_FEEDS = 0;

	private static final int REQUEST_ADD_CONTACT = 1;

    MultiIdentitySelector mIdentitySelector;
    SQLiteOpenHelper mDatabaseSource;
    IdentitiesManager mIdentitiesManager;
    FeedManager mFeedManager;
    ListView mFeedListView;
    FeedMembersCursorAdapter mFeedAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //TODO: this begs for being in util, huh.  i wonder where else it is used.
        //check that we aren't going to send a message using the local authority
        //to our friends.  This is similar to the initial hidden state of the 
        //person picker on the feed list
        mDatabaseSource = App.getDatabaseSource(this);
        mIdentitiesManager = new IdentitiesManager(mDatabaseSource);
        mFeedManager = new FeedManager(mDatabaseSource);
        if(mIdentitiesManager.getOwnedIdentities().size() < 2) {
        	Toast.makeText(this, "You must connect an account in Musubi to be able to share with your contacts", Toast.LENGTH_SHORT).show();
        	Intent intent = new Intent(this, FeedListActivity.class);
        	intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        	startActivity(intent);
        	return;
        }

        setTitle("Share");
        RelativeLayout window = new RelativeLayout(this);
        LayoutParams fill = new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
        window.setLayoutParams(fill);

        // Identity multi-select
        mIdentitySelector = new MultiIdentitySelector(this);
        mIdentitySelector.setOnIdentitiesUpdatedListener(mIdentitiesUpdatedListener);
        mIdentitySelector.setOnRequestAddIdentityListener(mOnRequestAddIdentityListener);
        RelativeLayout.LayoutParams selectorParams = new RelativeLayout.LayoutParams(
                LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
        selectorParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        mIdentitySelector.setLayoutParams(selectorParams);
        mIdentitySelector.setId(R.id.people);

        // Feed list
        mFeedListView = new ListView(this);
        RelativeLayout.LayoutParams listParams = new RelativeLayout.LayoutParams(
                LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
        listParams.addRule(RelativeLayout.BELOW, R.id.people);
        listParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        mFeedListView.setLayoutParams(listParams);
        mFeedListView.setOnItemClickListener(mFeedClickListener);
        // Must be called before setAdapter():
        //mFeedListView.addHeaderView(mHeaderView);

        // Bind to content view
        window.addView(mIdentitySelector);
        window.addView(mFeedListView);
        setContentView(window, fill);

        getSupportLoaderManager().initLoader(LOAD_FEEDS, null, this);
    }

	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(requestCode == REQUEST_ADD_CONTACT) {
			if(resultCode == Activity.RESULT_OK) {
				UiUtil.addedContact(this, data, mIdentitySelector);
			}
		}
	};
	private OnRequestAddIdentityListener mOnRequestAddIdentityListener = new OnRequestAddIdentityListener() {
		@Override
		public void onRequestAddIdentity(String enteredText) {
            Intent i = new Intent(Intent.ACTION_INSERT_OR_EDIT);
            i.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
        	if(enteredText != null) {
            	Pattern emailPattern = Pattern.compile("\\b[A-Z0-9._%-]+@[A-Z0-9.-]+\\.[A-Z]{2,4}\\b", Pattern.CASE_INSENSITIVE);
            	if (emailPattern.matcher(enteredText).matches()) {
        			i.putExtra(Insert.EMAIL, enteredText);
            	} else {
            		i.putExtra(Insert.NAME, enteredText);
            	}
        	}
            startActivityForResult(i, REQUEST_ADD_CONTACT);
		}
	};

    String getCallerAppId() {
        ActivityManager manager = (ActivityManager)getSystemService(ACTIVITY_SERVICE);
        List<RecentTaskInfo> infos = manager.getRecentTasks(1, 0);
        if (infos.size() == 0) {
            Log.w(TAG, "couldn't get info");
            return null;
        }

        Intent base = infos.get(0).baseIntent;
        return base.getComponent().getPackageName();
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        long[] ids;
        if (args == null) {
            ids = new long[0];
        } else {
            ids = args.getLongArray("ids");
        }
        switch (id) {
            case LOAD_FEEDS:
                return new FeedsWithMembersLoader(this, mFeedManager, ids);
        }
        return null;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (mFeedAdapter == null) {
            mFeedAdapter = new FeedMembersCursorAdapter(this, data);
            mFeedListView.setAdapter(mFeedAdapter);
        } else {
            mFeedAdapter.changeCursor(data);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {   
        mFeedAdapter.changeCursor(null);
    }

    OnIdentitiesUpdatedListener mIdentitiesUpdatedListener = new OnIdentitiesUpdatedListener() {
        public void onIdentitiesUpdated() {
            Set<MIdentity> idSet = mIdentitySelector.getSelectedIdentities();
            long[] ids = new long[idSet.size()];
            int i = 0;
            for (MIdentity id : idSet) {
                ids[i++] = id.id_;
            }
            Bundle args = new Bundle();
            args.putLongArray("ids", ids);
            getSupportLoaderManager().restartLoader(LOAD_FEEDS, args, FeedIdentityGrid.this);
        }
    };

    OnItemClickListener mFeedClickListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            long feedId = (Long)view.getTag(R.id.feed_label);
            Uri feedUri = MusubiContentProvider.uriForItem(Provided.FEEDS, feedId);
            MFeed mfeed = new MFeed();
            mfeed.id_ = feedId;
            MIdentity[] list = mFeedManager.getFeedMembers(mfeed);
            String text = "bye";
            Toast.makeText(FeedIdentityGrid.this, text, Toast.LENGTH_SHORT).show();
            finish();
        }
    };

    public static class FeedsWithMembersLoader extends SimpleCursorLoader {
        final String[] mColumns = new String[] { MFeed.COL_ID, MFeed.COL_NUM_UNREAD,
                MFeed.COL_NAME, MFeed.COL_LATEST_RENDERABLE_OBJ_ID };
        final long[] mIdentityIds;
        final FeedManager mFeedManager;

        public FeedsWithMembersLoader(Context context, FeedManager feedManager, long[] idIds) {
            super(context);
            mIdentityIds = idIds;
            mFeedManager = feedManager;
        }

        @Override
        public Cursor loadInBackground() {
            if (mIdentityIds.length == 0) {
                return allFeedsCursor();
            }
            return identityFilteredCursor();
        }

        Cursor allFeedsCursor() {
            String selection = FeedManager.VISIBLE_FEED_SELECTION;
            String sortOrder = MFeed.COL_LATEST_RENDERABLE_OBJ_TIME + " desc";
            return getContext().getContentResolver().query(MusubiContentProvider.uriForDir(Provided.FEEDS),
                    mColumns, selection, null, sortOrder);
        }

        Cursor identityFilteredCursor() {
            assert(mIdentityIds.length > 0);
            long[] feedIds = mFeedManager.getFeedsForIdentityId(mIdentityIds[0]);
            for (int p = 1; p < mIdentityIds.length; p++) {
                long[] filter = mFeedManager.getFeedsForIdentityId(mIdentityIds[p]);
                int i = 0, j = 0;
                TLongArrayList survivors = new TLongArrayList(feedIds.length);
                while (i < feedIds.length && j < filter.length) {
                    if (feedIds[i] == filter[j]) {
                        survivors.add(feedIds[i]);
                        i++;
                        j++;
                    } else if (feedIds[i] > filter[j]) {
                        i++;
                    } else {
                        j++;
                    }
                }
                feedIds = new long[survivors.size()];
                survivors.toArray(feedIds);
                if (feedIds.length == 0) {
                    break;
                }
            }

            final MatrixCursor cursor = new MatrixCursor(mColumns, feedIds.length);
            for (long f : feedIds) {
                MFeed feed = mFeedManager.lookupFeed(f);
                Object[] values = new Object[mColumns.length];
                values[0] = feed.id_;
                values[1] = feed.numUnread_;
                values[2] = feed.name_;
                values[3] = feed.latestRenderableObjId_;
                cursor.addRow(values);
            }
            return cursor;
        }
    }

    public static class FeedMembersCursorAdapter extends CursorAdapter {
        private FeedManager mFeedManager;
        private final FeedIdentityGrid mContext;
        
        public FeedMembersCursorAdapter(FeedIdentityGrid context, Cursor c) {
            super(context, c);
            mContext = context;
            SQLiteOpenHelper helper = App.getDatabaseSource(context);
            mFeedManager = new FeedManager(helper);
        }

        @Override
        public View newView(Context context, Cursor c, ViewGroup parent) {
            LinearLayout membershipView = new LinearLayout(context);
            membershipView.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.FILL_PARENT, AbsListView.LayoutParams.WRAP_CONTENT));

            TextView title = new TextView(context);
            title.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            Gallery gallery = new Gallery(context);
            gallery.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            gallery.setId(R.id.people);
            gallery.setGravity(Gravity.LEFT);
            gallery.setSpacing(4);
            gallery.setClickable(false);
            gallery.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
            hackGalleryInit(gallery);
            membershipView.addView(gallery);
            return membershipView;
        }

        void hackGalleryInit(Gallery gallery) {
            DisplayMetrics metrics = new DisplayMetrics();
            mContext.getWindowManager().getDefaultDisplay().getMetrics(metrics);
            MarginLayoutParams mlp = (MarginLayoutParams) gallery.getLayoutParams();
            mlp.setMargins(-(metrics.widthPixels/2), 
                           mlp.topMargin, 
                           mlp.rightMargin, 
                           mlp.bottomMargin
            );
        }

        @Override
        public void bindView(final View v, final Context context, final Cursor c) {
            long feedId = c.getLong(c.getColumnIndexOrThrow(MFeed.COL_ID));

            v.setTag(R.id.feed_label, feedId);
            Gallery gallery = (Gallery)v.findViewById(R.id.people);
            Cursor members = mFeedManager.getKnownProfileFeedMembersCursor(c.getLong(0));
            gallery.setAdapter(new MembersCursorAdapter(context, members));

           /* TextView title = (TextView)v.findViewById(R.id.text);
            MFeed feed = new MFeed();
            feed.id_ = feedId;
            title.setText(UiUtil.getFeedNameFromMembersList(mFeedManager, feed));*/
            
        }
    }

    static class MembersCursorAdapter extends CursorAdapter {
        final IdentitiesManager idm;
        public MembersCursorAdapter(Context context, Cursor members) {
            super(context, members);
            idm = new IdentitiesManager(App.getDatabaseSource(context));
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            MIdentity identity = new MIdentity();
            identity.id_ = cursor.getLong(0);
            Bitmap cached = UiUtil.safeGetContactThumbnail(context, idm, identity);
            ((ImageView)view).setImageBitmap(cached);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            // TODO: Render nam under icon
            ImageView icon = new ImageView(context);
            int DP = (int)context.getResources().getDisplayMetrics().density * 75;
            icon.setLayoutParams(new Gallery.LayoutParams(DP, DP));
            icon.setPadding(8, 8, 8, 8);
            return icon;
        }
    }
}
