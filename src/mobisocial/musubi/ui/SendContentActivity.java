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

import java.util.Set;
import java.util.regex.Pattern;

import mobisocial.musubi.App;
import mobisocial.musubi.Helpers;
import mobisocial.musubi.R;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MFeedMember;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.helpers.FeedManager;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.model.helpers.MyAccountManager;
import mobisocial.musubi.objects.IntroductionObj;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.provider.MusubiContentProvider.Provided;
import mobisocial.musubi.service.MusubiService;
import mobisocial.musubi.ui.fragments.ClipboardKeeper;
import mobisocial.musubi.ui.util.UiUtil;
import mobisocial.musubi.ui.widget.MultiIdentitySelector;
import mobisocial.musubi.ui.widget.MultiIdentitySelector.OnIdentitiesUpdatedListener;
import mobisocial.musubi.ui.widget.MultiIdentitySelector.OnRequestAddIdentityListener;
import mobisocial.musubi.ui.widget.ObjView;
import mobisocial.musubi.util.ObjFactory;
import mobisocial.musubi.util.SimpleCursorLoader;
import mobisocial.socialkit.Obj;
import mobisocial.socialkit.musubi.DbFeed;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Intents.Insert;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Toast;

public class SendContentActivity extends MusubiBaseActivity {
    static final int LOAD_FEEDS = 0;

	private static final int REQUEST_ADD_CONTACT = 1;

    public static final String EXTRA_CALLING_APP = "caller";

    Obj mObj;
    MultiIdentitySelector mIdentitySelector;
    SQLiteDatabase mDatabaseSource;
    IdentitiesManager mIdentitiesManager;
    FeedManager mFeedManager;
    Button mSendButton;
    Button mClipboardButton;
    
    RelativeLayout mWindow;
    ProgressBar mProgress;

    class BuildObjTask extends AsyncTask<Void, Void, Void> {
    	@Override
    	protected Void doInBackground(Void... params) {
            mObj = ObjFactory.objForSendIntent(SendContentActivity.this, getIntent());
    		return null;
    	}
    	@Override
    	protected void onPostExecute(Void result) {
            if (mObj == null) {
                Toast.makeText(SendContentActivity.this, "Unsupported content type.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            Set<MIdentity> idSet = mIdentitySelector.getSelectedIdentities();
            mSendButton.setEnabled(idSet.size() > 0 && mObj != null);
            mClipboardButton.setEnabled(mObj != null);
            mProgress.setVisibility(View.GONE);
            // Preview pane
            ObjView preview = new ObjView(SendContentActivity.this, mObj);
            preview.setId(R.id.object_entry);
            RelativeLayout.LayoutParams previewParams = new RelativeLayout.LayoutParams(
                    LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
            previewParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            previewParams.addRule(RelativeLayout.BELOW, R.id.submit);
            preview.setLayoutParams(previewParams);
            preview.setPadding(6, 16, 6, 6);
            preview.setMinimumHeight(150);
            preview.setBackgroundResource(R.drawable.sharebox);
            mWindow.addView(preview);
    	}
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //TODO: this begs for being in util, huh.  i wonder where else it is used.
        //check that we aren't going to send a message using the local authority
        //to our friends.  This is similar to the initial hidden state of the 
        //person picker on the feed list
        mDatabaseSource = App.getDatabaseSource(this).getWritableDatabase();
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
        mWindow = new RelativeLayout(this);
        LayoutParams fill = new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
        mWindow.setLayoutParams(fill);

        // Identity multi-select
        mIdentitySelector = new MultiIdentitySelector(this);
        mIdentitySelector.setOnIdentitiesUpdatedListener(mIdentitiesUpdatedListener);
        mIdentitySelector.setOnRequestAddIdentityListener(mRequestAddIdentityListener);
        RelativeLayout.LayoutParams selectorParams = new RelativeLayout.LayoutParams(
                LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
        selectorParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        mIdentitySelector.setLayoutParams(selectorParams);
        mIdentitySelector.setId(R.id.people);
        mWindow.addView(mIdentitySelector);

        // Send button
        mSendButton = new Button(this);
        mSendButton.setText(R.string.send);
        RelativeLayout.LayoutParams sendButtonParams = new RelativeLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        mSendButton.setId(R.id.submit);
        sendButtonParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        sendButtonParams.addRule(RelativeLayout.BELOW, R.id.people);
        mSendButton.setLayoutParams(sendButtonParams);
        mSendButton.setOnClickListener(mNewFeedClickListener);
        mSendButton.setEnabled(false);
        mWindow.addView(mSendButton);

        // Clipboard button
        mClipboardButton = new Button(this);
        mClipboardButton.setEnabled(false);
        //mClipboardButton.setImageResource(R.drawable.ic_action_pin);
        mClipboardButton.setText("Copy to clipboard");
        RelativeLayout.LayoutParams clipboardParams = new RelativeLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        clipboardParams.addRule(RelativeLayout.LEFT_OF, R.id.submit);
        clipboardParams.addRule(RelativeLayout.BELOW, R.id.people);
        mClipboardButton.setLayoutParams(clipboardParams);
        mClipboardButton.setOnClickListener(mClipboardListener);
        int dp = (int)(getResources().getDisplayMetrics().density * 10);
        mClipboardButton.setPadding(dp, dp, dp, dp);
        mWindow.addView(mClipboardButton);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
        layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        layoutParams.addRule(RelativeLayout.BELOW, R.id.submit);
        layout.setLayoutParams(layoutParams);
        layout.setPadding(6, 16, 6, 6);
        layout.setGravity(Gravity.CENTER);
        layout.setMinimumHeight(150);
        layout.setBackgroundResource(R.drawable.sharebox);

        mProgress = new ProgressBar(this);
        mProgress.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        mProgress.setIndeterminate(true);
        layout.addView(mProgress);
        mWindow.addView(layout);

        setContentView(mWindow, fill);
        new BuildObjTask().execute();
    }


    OnIdentitiesUpdatedListener mIdentitiesUpdatedListener = new OnIdentitiesUpdatedListener() {
        public void onIdentitiesUpdated() {
            Set<MIdentity> idSet = mIdentitySelector.getSelectedIdentities();
            mSendButton.setEnabled(idSet.size() > 0 && mObj != null);
            mClipboardButton.setEnabled(mObj != null);
        }
    };
    OnRequestAddIdentityListener mRequestAddIdentityListener = new OnRequestAddIdentityListener() {
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
	
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(requestCode == REQUEST_ADD_CONTACT) {
			//reread the contact list so that its possible for us to fill in what they typed
			getContentResolver().notifyChange(MusubiService.FORCE_RESCAN_CONTACTS, null);
		}
	};

    View.OnClickListener mNewFeedClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            boolean ownedId = false;
            Set<MIdentity> ids = mIdentitySelector.getSelectedIdentities();
            StringBuilder selection = new StringBuilder(FeedManager.VISIBLE_FEED_SELECTION);
            for (MIdentity p : ids) {
                ownedId |= p.owned_;
                selection.append(" AND ").append(MFeed.COL_ID).append(" in (SELECT ")
                    .append(MFeedMember.COL_FEED_ID).append(" FROM ").append(MFeedMember.TABLE)
                    .append(" WHERE ").append(MFeedMember.COL_IDENTITY_ID).append("=")
                    .append(p.id_).append(")");
                        
            }
            String table = MFeed.TABLE;
            String[] projection = new String[] { MFeed.COL_ID };
            String sortOrder = MFeed.COL_LATEST_RENDERABLE_OBJ_TIME + " desc";
            String[] selectionArgs = null;
            String groupBy = null, having = null;
            Cursor c = mDatabaseSource.query(table, projection, selection.toString(),
                    selectionArgs, groupBy, having, sortOrder);
            try {
                int size = ids.size();
                if (!ownedId) size++;
                while (c.moveToNext()) {
                    long feedId = c.getLong(0);
                    int count = mFeedManager.getFeedMemberCount(feedId);
                    if (count == size) {
                        Uri feedUri = MusubiContentProvider.uriForItem(Provided.FEEDS, feedId);
                        DbFeed feed = App.getMusubi(SendContentActivity.this).getFeed(feedUri);
                        feed.postObj(mObj);

                        String text = (ids.size() == 1) ? "Shared with 1 person." :
                            "Shared with " + ids.size() + " people.";
                        Toast.makeText(SendContentActivity.this, text, Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                }
            } finally {
                c.close();
            }

            MIdentity[] buddies = new MIdentity[ids.size() + 1];
            int i = 0;
            for (MIdentity id : ids) {
                buddies[i++] = id;
            }
            buddies[i] = mIdentitiesManager.getMyDefaultIdentity();
            MFeed f = mFeedManager.createExpandingFeed(buddies);
            
            MyAccountManager am = new MyAccountManager(mDatabaseSource);
            UiUtil.addToWhitelistsIfNecessary(mFeedManager, am, mFeedManager.getFeedMembers(f), true);

            long feedId = f.id_;
            Uri feedUri = MusubiContentProvider.uriForItem(Provided.FEEDS, feedId);
            DbFeed feed = App.getMusubi(SendContentActivity.this).getFeed(feedUri);            
            Obj invitedObj = IntroductionObj.from(mIdentitySelector.getSelectedIdentities(), true);
            Helpers.sendToFeed(SendContentActivity.this, invitedObj, feedUri);
            feed.postObj(mObj);
            
            String text = (ids.size() == 1) ? "Shared with 1 person." :
                "Shared with " + ids.size() + " people.";
            Toast.makeText(SendContentActivity.this, text, Toast.LENGTH_SHORT).show();
            finish();
        }
    };

    View.OnClickListener mClipboardListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            new ClipboardKeeper(SendContentActivity.this).store(mObj);
            Intent home = new Intent(SendContentActivity.this, FeedListActivity.class);
            startActivity(home);
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
}
