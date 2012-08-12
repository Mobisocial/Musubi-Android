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
import mobisocial.musubi.App;
import mobisocial.musubi.R;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.helpers.FeedManager;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.ui.ViewProfileActivity;
import mobisocial.musubi.ui.util.UiUtil;
import mobisocial.musubi.util.SimpleCursorLoader;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.SupportActivity;
import android.support.v4.content.Loader;
import android.util.Log;
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
 * Displays a list of contacts. If the intent used to create
 * this activity as Long extra "group_id", contacts are chosen
 * from this group. Otherwise, lists all known contacts.
 *
 */
public class EmailUnclaimedMembersFragment extends ListFragment implements OnItemClickListener,
        LoaderManager.LoaderCallbacks<Cursor> {
	private ContactListCursorAdapter mContacts;
	public static final String TAG = "EmailUnclaimedMembersFragment";
    private static final int sDeletedColor = Color.parseColor("#66FF3333");
    private Uri mFeedUri;
    private IdentitiesManager mIdentitiesManager;
    private Activity mActivity;
    
    @Override
    public void onAttach(SupportActivity activity) {
        super.onAttach(activity);
        mActivity = activity.asActivity();
        mFeedUri = getArguments().getParcelable(FeedViewFragment.ARG_FEED_URI);
        SQLiteOpenHelper helper = App.getDatabaseSource(mActivity);
        mIdentitiesManager = new IdentitiesManager(helper);
        Log.w(TAG, "feeduri=" + mFeedUri);
    }
    
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getLoaderManager().initLoader(0, null, this);
	}

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id){
    	Cursor cursor = (Cursor)mContacts.getItem(position);
    	long identityId = cursor.getLong(cursor.getColumnIndexOrThrow(MIdentity.COL_ID));
    	Intent intent = new Intent(mActivity, ViewProfileActivity.class);
        intent.putExtra(ViewProfileActivity.PROFILE_ID, identityId);
        mActivity.startActivity(intent);
        
        
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
            
            if (member.blocked_) {
                v.setBackgroundColor(sDeletedColor);
            } else {
                v.setBackgroundColor(Color.TRANSPARENT);
            }

            nameText.setText(UiUtil.safeNameForIdentity(member));
            principalText.setText(UiUtil.safePrincipalForIdentity(member));
        	icon.setImageBitmap(UiUtil.safeGetContactThumbnail(context, mIdentitiesManager, member));
            
        	//nearbyIcon.setVisibility(c.nearby ? View.VISIBLE : View.GONE);
            more.setVisibility(View.GONE);
        }
    }

    public boolean onCreateOptionsMenu(Menu menu){
        return true;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
        long feedId;
        if (mFeedUri != null) {
            feedId = Long.parseLong(mFeedUri.getLastPathSegment()); 
        } else {
            feedId = -1;
        }
        
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
			return mManager.getEmailReachableUnclaimedFeedMembersCursor(mFeedId);
		}
    	
    }
}