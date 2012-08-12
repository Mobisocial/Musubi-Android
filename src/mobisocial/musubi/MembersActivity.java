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

package mobisocial.musubi;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.helpers.FeedManager;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.provider.MusubiContentProvider.Provided;
import mobisocial.musubi.service.MusubiService;
import mobisocial.musubi.ui.ViewProfileActivity;
import mobisocial.musubi.ui.fragments.FeedViewFragment;
import mobisocial.musubi.ui.util.UiUtil;
import mobisocial.musubi.util.LessSpammyContentObserver;
import mobisocial.musubi.util.SimpleCursorLoader;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.SupportActivity;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Pick contacts and/or groups for various purposes.
 * TODO: Remove TabActivity in favor of fragments;
 * Make activity a floating window.
 * 
 * TODO: Picker should return personId, not id.
 */
public class MembersActivity extends FragmentActivity {

	public final static String INTENT_EXTRA_FEED_URI = "feed_uri";
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		Intent mIntent = getIntent();
		Uri mFeedUri = mIntent.getParcelableExtra(INTENT_EXTRA_FEED_URI);

		Fragment memberView = new FeedMembersFragment();
		
		Bundle args = new Bundle();
        args.putParcelable("feed_uri", mFeedUri);

        // TODO: Hack
        setTitle("Feed Members");
        ((TextView)findViewById(android.R.id.title)).setTextColor(Color.BLACK);
        memberView.setArguments(args);
        
        ((TextView)findViewById(android.R.id.title)).setText("Feed Members");
        setContentView(R.layout.activity_member_list);
        
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.member_list, memberView).commit();
    }

	public static class FeedMembersFragment extends ListFragment implements OnItemClickListener,
            LoaderManager.LoaderCallbacks<Cursor> {
        private ContactListCursorAdapter mContacts;
        public static final String TAG = "FeedMembersFragment";
        private static final int sDeletedColor = Color.parseColor("#66FF3333");
        private Uri mFeedUri;
        private IdentitiesManager mIdentitiesManager;
        private Activity mActivity;
    	private LessSpammyContentObserver mObserver;
        
        @Override
        public void onAttach(SupportActivity activity) {
            super.onAttach(activity);
            mActivity = activity.asActivity();
            mFeedUri = getArguments().getParcelable(FeedViewFragment.ARG_FEED_URI);
            SQLiteOpenHelper helper = App.getDatabaseSource(mActivity);
            mIdentitiesManager = new IdentitiesManager(helper);
        }
        
        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mObserver = new LessSpammyContentObserver(new Handler(mActivity.getMainLooper())) {
            	@Override
            	public void lessSpammyOnChange(boolean arg0) {
            		//its possible the callback can come in while the cursor is loading, so there
            		//may not be an mContacts
            		if(mContacts == null || mContacts.getCursor() == null || !isAdded()) {
            			return;
                    }
            		getLoaderManager().initLoader(0, null, FeedMembersFragment.this);
            	}
    		};
            getLoaderManager().initLoader(0, null, this);
        }
        
        @Override
        public void onResume() {
            super.onResume();
            mActivity.getContentResolver().registerContentObserver(MusubiService.WHITELIST_APPENDED, false, mObserver);
    		mActivity.getContentResolver().registerContentObserver(MusubiService.COLORLIST_CHANGED, false, mObserver);
            mObserver.resetTimeout();
        	mObserver.dispatchChange(false);
        }

        @Override
        public void onPause() {
            super.onPause();
        	mActivity.getContentResolver().unregisterContentObserver(mObserver);
        }

        
        @Override
        public void onDestroy() {
        	super.onDestroy();
        }
        
        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
        
            ListView lv = getListView();
            lv.setTextFilterEnabled(true);
            lv.setFastScrollEnabled(true);
            //registerForContextMenu(lv);
            lv.setOnItemClickListener(this);
            lv.setCacheColorHint(Color.WHITE);
        }
        
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id){
            Cursor cursor = (Cursor)mContacts.getItem(position);
            long identityId = cursor.getLong(cursor.getColumnIndexOrThrow(MIdentity.COL_ID));
            Intent intent = new Intent(mActivity, ViewProfileActivity.class);
            intent.putExtra(ViewProfileActivity.PROFILE_ID, identityId);
            mActivity.startActivity(intent);
        }
        
        private class ContactListCursorAdapter extends CursorAdapter {
            public ContactListCursorAdapter (Context context, Cursor c) {
                super(context, c);
            }
        
            @Override
            public View newView(Context context, Cursor c, ViewGroup parent) {
                final LayoutInflater inflater = LayoutInflater.from(context);
                View v = inflater.inflate(R.layout.contacts_item, parent, false);
                bindView(v, context, c);
                return v;
            }
        
            @Override
            public void bindView(View v, Context context, Cursor cursor) {
                TextView unreadCount = (TextView)v.findViewById(R.id.unread_count);
                unreadCount.setVisibility(View.GONE);
                TextView nameText = (TextView) v.findViewById(R.id.name_text);
                TextView statusText = (TextView) v.findViewById(R.id.status_text);
                TextView principalText = (TextView) v.findViewById(R.id.principal_text);
                final ImageView icon = (ImageView)v.findViewById(R.id.icon);
                final ImageView presenceIcon = (ImageView)v.findViewById(R.id.presence_icon);
                presenceIcon.setVisibility(View.GONE);
                final ImageView nearbyIcon = (ImageView)v.findViewById(R.id.nearby_icon);
                nearbyIcon.setVisibility(View.GONE);
                final ImageView more = (ImageView)v.findViewById(R.id.more);
        
                long identityId = cursor.getLong(0);
                final MIdentity member = mIdentitiesManager.getIdentityForId(identityId);
                if(member == null) {
                    unreadCount.setVisibility(View.INVISIBLE);
                    nameText.setText("Missing contact data...");
                    statusText.setText("");
                    icon.setImageResource(R.drawable.ic_contact_picture);
                    return;
                }
                
        
                nameText.setText(UiUtil.safeNameForIdentity(member));
                principalText.setText(UiUtil.safePrincipalForIdentity(member));
                icon.setImageBitmap(UiUtil.safeGetContactThumbnail(context, mIdentitiesManager, member));
        
                //nearbyIcon.setVisibility(c.nearby ? View.VISIBLE : View.GONE);
                more.setVisibility(View.GONE);

                ImageView musubiEnabled = (ImageView)v.findViewById(R.id.musubi_enabled);
                ImageView ignoredOverlay = (ImageView) v.findViewById(R.id.ignored_overlay);
                if (member.claimed_) {
                	ignoredOverlay.setVisibility(View.GONE);
                    nameText.setTextColor(Color.BLACK);
                    principalText.setTextColor(Color.BLACK);
                	musubiEnabled.setBackgroundResource(R.drawable.musubi_enabled);
                }
                else {
                	ignoredOverlay.setVisibility(View.GONE);
                    nameText.setTextColor(Color.BLACK);
                    principalText.setTextColor(Color.BLACK);
                	musubiEnabled.setBackgroundResource(R.drawable.musubi_disabled);
                }
                if(member.blocked_) {
                    nameText.setTextColor(sDeletedColor);
                    principalText.setTextColor(sDeletedColor);
                    ignoredOverlay.setVisibility(View.VISIBLE);
                }
            }
        }
        
        public boolean onCreateOptionsMenu(Menu menu){
            return true;
        }
        
        @Override
        public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
            long feedId = Long.parseLong(mFeedUri.getLastPathSegment());
            /*Uri memberlist = MusubiContentProvider.uriForItem(Provided.FEED_MEMBERS_ID, feedId);
            String[] projection = new String[] { MFeedMember.COL_IDENTITY_ID };
            String selection = null;
            String[] selectionArgs = null;
            String sortOrder = null;
            return new CursorLoader(mActivity, memberlist, projection, selection, selectionArgs, sortOrder);*/
            return new FeedMembersCursorLoader(mActivity, feedId);
        }
        
        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
            mContacts = new ContactListCursorAdapter(mActivity, cursor);
            setListAdapter(mContacts);
        }
        
        @Override
        public void onLoaderReset(Loader<Cursor> arg0) {
        
        }

        public static class FeedMembersCursorLoader extends SimpleCursorLoader {
        
            private FeedManager mManager;
            private long mFeedId;
            
            public FeedMembersCursorLoader(Context context, long feedId) {
                super(context);
                SQLiteOpenHelper helper = App.getDatabaseSource(context);
                mManager = new FeedManager(helper);
                mFeedId = feedId;
            }
        
            @Override
            public Cursor loadInBackground() {
                Cursor c = mManager.getKnownProfileFeedMembersCursor(mFeedId);
                c.setNotificationUri(getContext().getContentResolver(),
                		MusubiContentProvider.uriForItem(Provided.FEEDS_ID, mFeedId));
                return c;
            }
            
        }
	}
}