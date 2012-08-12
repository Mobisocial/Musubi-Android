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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import mobisocial.metrics.MusubiMetrics;
import mobisocial.musubi.App;
import mobisocial.musubi.Helpers;
import mobisocial.musubi.R;
import mobisocial.musubi.feed.iface.FeedRenderer;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MFeedMember;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MObject;
import mobisocial.musubi.model.helpers.DatabaseManager;
import mobisocial.musubi.model.helpers.EncodedMessageManager;
import mobisocial.musubi.model.helpers.FeedManager;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.model.helpers.MyAccountManager;
import mobisocial.musubi.model.helpers.ObjectManager;
import mobisocial.musubi.obj.ObjHelpers;
import mobisocial.musubi.objects.IntroductionObj;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.provider.MusubiContentProvider.Provided;
import mobisocial.musubi.service.MusubiService;
import mobisocial.musubi.ui.FeedDetailsActivity;
import mobisocial.musubi.ui.FeedListActivity;
import mobisocial.musubi.ui.MusubiBaseActivity;
import mobisocial.musubi.ui.NearbyActivity;
import mobisocial.musubi.ui.util.EmojiSpannableFactory;
import mobisocial.musubi.ui.util.FeedHTML;
import mobisocial.musubi.ui.util.UiUtil;
import mobisocial.musubi.ui.util.UiUtil.PeopleDetails;
import mobisocial.musubi.ui.widget.CompositeImageView;
import mobisocial.musubi.ui.widget.MultiIdentitySelector;
import mobisocial.musubi.ui.widget.MultiIdentitySelector.OnIdentitiesUpdatedListener;
import mobisocial.musubi.ui.widget.MultiIdentitySelector.OnRequestAddIdentityListener;
import mobisocial.musubi.util.IdentityCache;
import mobisocial.musubi.util.IdentityCache.CachedIdentity;
import mobisocial.musubi.util.RelativeDate;
import mobisocial.socialkit.Obj;

import org.json.JSONException;
import org.json.JSONObject;
import org.mobisocial.corral.ContentCorral;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Intents.Insert;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.SupportActivity;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.support.v4.util.LruCache;
import android.support.v4.view.Menu;
import android.support.v4.view.MenuItem;
import android.text.Editable;
import android.text.Spannable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.widget.SingleTopPinnedHeaderListAdapter;

/**
 * Displays a list of all user-accessible threads (feeds).
 *
 */
public class FeedListFragment extends ListFragment implements LoaderManager.LoaderCallbacks<Cursor> {
    private static final String TAG = "FeedListFragment";
    private static final boolean DBG = MusubiBaseActivity.DBG;
	private static final int REQUEST_ADD_CONTACT = 1;
    private FeedListAdapter mFeeds;

    public static final String ARG_IDENTITY_ID = "identity_id";
	public static final String DUAL_PANE = "dual_pane";
    
    private OnFeedSelectedListener mFeedSelectedListener;
    private MultiIdentitySelector mPeople;
    private SQLiteOpenHelper mDatabaseSource;
	private ContentObserver mObserver;
	private Activity mActivity;
	public static final int DAYS_TO_SHOW = 7;
	public static int ONE_DAY = 1000*60*60*24;

    static final String sFeedSortOrder = MFeed.COL_LATEST_RENDERABLE_OBJ_TIME + " desc";
	
    public FeedListFragment() {
        if (DBG) Log.d(TAG, "Instantiating new FeedListFragment");
    }

    public interface OnFeedSelectedListener {
        public void onFeedSelected(Uri feedUri);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DBG) Log.d(TAG, "Creating new FeedListFragment");

        mFeeds = new FeedListAdapter(mActivity);
        for (int i = 0; i < DAYS_TO_SHOW+1; i++) {
        	mFeeds.addPartition(false, true);
        }
        setListAdapter(mFeeds);

        mObserver = new ContentObserver(new Handler(mActivity.getMainLooper())) {
        	@Override
        	public void onChange(boolean arg0) {
        		if(mFeeds.isEmpty() || !isAdded()) {
        			return;
                }
        		initLoaders(true);
        	}
		};
    }

    @Override
    public void onAttach(SupportActivity activity) {
        super.onAttach(activity);
        mActivity = activity.asActivity();
        if (DBG) Log.d(TAG, "Attaching FeedListFragment.");
        mFeedSelectedListener = (OnFeedSelectedListener) activity;
        mDatabaseSource = App.getDatabaseSource(mActivity);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_feed_list, container, false);
        Bundle args = getArguments();
        if(args != null && args.containsKey("no_nearby") && args.getBoolean("no_nearby"))
        	v.findViewById(R.id.nearby).setVisibility(View.GONE);
        return v;
    }

	@Override
	public void onPause() {
    	super.onPause();
    	mActivity.getContentResolver().unregisterContentObserver(mObserver);
	}
    
    @Override
    public void onResume() {
        super.onResume();
        IdentitiesManager identitiesManager = new IdentitiesManager(mDatabaseSource);
        if (!identitiesManager.hasConnectedAccounts()) {
        	mActivity.findViewById(R.id.go).setOnClickListener(mNoAccountsListener);
        	mActivity.findViewById(R.id.people).setOnClickListener(mNoAccountsListener);
        	mActivity.findViewById(R.id.nearby).setOnClickListener(mNoAccountsListener);
        }
        else {
            mActivity.findViewById(R.id.go).setOnClickListener(mStartListener);
        	mActivity.findViewById(R.id.people).setOnClickListener(null);
            mActivity.findViewById(R.id.nearby).setOnClickListener(mJoinNearbyListener);
        }
        /*if (!MusubiBaseActivity.isDeveloperModeEnabled(mActivity)) {
        	getActivity().findViewById(R.id.nearby).setVisibility(View.GONE);
        }*/
    	mActivity.getContentResolver().registerContentObserver(MusubiService.PRIMARY_CONTENT_CHANGED, false, mObserver);
    	mObserver.dispatchChange(false);
    }

    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (null != mActivity.findViewById(R.id.feed_view)) {
            getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        }
        ListView lv = (ListView)getView().findViewById(android.R.id.list);
        registerForContextMenu(lv);

        /** Prepare the autocompleting dropdown **/
        // TODO: background
        
        mPeople = (MultiIdentitySelector) mActivity.findViewById(R.id.people);
        mPeople.setOnRequestAddIdentityListener(mOnRequestAddIdentityListener);
        mPeople.setOnIdentitiesUpdatedListener(mIdentitiesUpdatedListener);
        mPeople.addTextChangedListener(new TextWatcher() {
			
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {	
			}
			
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}
			
			@Override
			public void afterTextChanged(Editable s) {
				if (mPeople.getSelectedIdentities().size() == 0) {
					initLoaders(true);
				} else {
					// defer to OnIdentitiesUpdatedListener
				}
			}
		});
        mActivity.findViewById(R.id.go).setOnClickListener(mStartListener);

        /** Load the latest feeds in the background **/
        initLoaders(false);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == REQUEST_ADD_CONTACT) {
			if(resultCode == Activity.RESULT_OK) {
				UiUtil.addedContact(mActivity, data, mPeople);
			}
		}
    }

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

	private OnIdentitiesUpdatedListener mIdentitiesUpdatedListener = new OnIdentitiesUpdatedListener() {
        @Override
        public void onIdentitiesUpdated() {
            initLoaders(true);
        }
    };


    static class ViewHolder {
    	PeopleDetails peopleDetails;
    	FeedSummary feedSummary;
    	CompositeImageView icon;
    	TextView feedLabel;
    	TextView time;
    	TextView text;
    	TextView unreadCount;
    }


    public static class FeedListAdapter extends SingleTopPinnedHeaderListAdapter {
    	private final DatabaseManager mmDatabaseManager;
    	private final Context mmContext;
    	private final LayoutInflater mmLayoutInflater;
    	private final IdentityCache mmIdentityCache;
    	private final FeedIconCache mmFeedIconCache;
    	private EmojiSpannableFactory mEmojiSpannableFactory;

        public FeedListAdapter (Context context) {
            super(context);
            setPinnedPartitionHeadersEnabled(true);
            mmContext = context;
            mEmojiSpannableFactory = EmojiSpannableFactory.getInstance(mmContext);
            mmLayoutInflater = LayoutInflater.from(context);
            SQLiteOpenHelper helper = App.getDatabaseSource(context);
            mmDatabaseManager = new DatabaseManager(helper);
            mmIdentityCache = App.getContactCache(context);
            mmFeedIconCache = new FeedIconCache(context, mmDatabaseManager, 30, 160);
        }

        @Override
        protected View newHeaderView(Context context, int partition,
        		Cursor cursor, ViewGroup parent) {
        	TextView tv = new TextView(context);
        	tv.setPadding(6, 2, 2, 2);
        	tv.setBackgroundColor(Color.rgb(101, 159, 229));
        	tv.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.FILL_PARENT,
        			AbsListView.LayoutParams.WRAP_CONTENT));
        	tv.setTextAppearance(context, android.R.style.TextAppearance_Medium_Inverse);
        	return tv;
        }

        final Map<Integer, String> mHeaderLabels = new HashMap<Integer, String>(10);

        @Override
        protected void bindHeaderView(View view, int partition, Cursor cursor) {
        	TextView tv = (TextView)view;
        	String text;
        	if (partition == 0) {
        		text = "Today";
        	} else if (partition == 1) {
        		text = "Yesterday";
        	} else if (partition < DAYS_TO_SHOW) {
        		if (mHeaderLabels.containsKey(partition)) {
        			text = mHeaderLabels.get(partition);
        		} else {
        			text = partition + " days ago";
        			mHeaderLabels.put(partition, text);
        		}
        	} else {
        		text = "Older Conversations";
        	}
        	tv.setText(text);
        }

        @Override
        public View newView(Context context, int partition, Cursor c, int position,
        		ViewGroup parent) {
            View v = mmLayoutInflater.inflate(R.layout.feed_entry, parent, false);

            ViewHolder holder = new ViewHolder();
            holder.peopleDetails = new PeopleDetails();
            holder.feedSummary = new FeedSummary(mmContext);
            holder.icon = (CompositeImageView)v.findViewById(R.id.image);
            holder.feedLabel = ((TextView)v.findViewById(R.id.feed_label));
            holder.time = ((TextView)v.findViewById(R.id.time_text));
            holder.text = ((TextView)v.findViewById(R.id.text));
            holder.unreadCount = (TextView)v.findViewById(R.id.unread_count);

            //holder.icon.setOnClickListener(mmOnIconClickListener);
            v.setTag(R.id.holder, holder);
            return v;
        }

        @Override
        public void bindView(final View v, int partition, final Cursor c, int position) {
            ViewHolder holder = (ViewHolder)v.getTag(R.id.holder);
            PeopleDetails details = holder.peopleDetails;
            FeedSummary feedSummary = holder.feedSummary;
            feedSummary.populate(c);

            String timeString = RelativeDate.getRelativeDate(feedSummary.timestamp);
            long[] identityIds = mmDatabaseManager.getFeedManager().getFeedMembers(feedSummary.feedId);
            UiUtil.populatePeopleDetails(mmContext, mmDatabaseManager.getIdentitiesManager(),
            		identityIds, mmIdentityCache, details);
            if (feedSummary.feedName == null) feedSummary.feedName = details.name;

            List<Bitmap> images = null;
            if (feedSummary.hasThumbnail) {
            	Bitmap bm = mmFeedIconCache.get(feedSummary.feedId);
            	if (bm != null) {
        			images = new ArrayList<Bitmap>(1);
        			images.add(bm);
        		}
            }
            if (images == null) {
            	images = details.images;
            }
            if (images.size() == 0) {
            	images.add(BitmapFactory.decodeResource(mmContext.getResources(), R.drawable.ic_contact_picture));
            }

            // TODO: thumbnail view for all feed types
            v.setTag(feedSummary.feedId);
            holder.icon.setImageBitmaps(images);
            Spannable span = mEmojiSpannableFactory.newSpannable(feedSummary.feedName);
            holder.feedLabel.setText(span);
            holder.time.setText(timeString);
            /*if (holder.text.getText() != null) {
            	mSpannableFactory.recycleSpans(holder.text.getText());
            }*/

            holder.text.setTypeface(null, Typeface.NORMAL);
        	FeedRenderer renderer = ObjHelpers.getFeedRenderer(feedSummary.objType);
        	renderer.getSummaryText(mmContext, holder.text, holder.feedSummary);

        	if (feedSummary.numUnread == 0) {
            	holder.unreadCount.setVisibility(View.GONE);
            } else {
            	holder.unreadCount.setText("(" + feedSummary.numUnread + " new)");
            	holder.unreadCount.setVisibility(View.VISIBLE);
            }
        }

        View.OnClickListener mmOnIconClickListener = new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				long feedId = (Long)((View)v.getParent()).getTag();
				Intent i = new Intent(v.getContext(), FeedDetailsActivity.class);
				i.setData(MusubiContentProvider.uriForItem(Provided.FEEDS_ID, feedId));
				v.getContext().startActivity(i);
			}
		};
    }

    static class FeedIconCache extends LruCache<Long, Bitmap> {
    	final int mImageSize;
    	final DatabaseManager mDatabaseManager;
    	final Matrix mScaleMatrix;

    	public FeedIconCache(Context context, DatabaseManager databaseManager, int maxCount, int imageSize) {
			super(maxCount);
			mImageSize = imageSize;
			mDatabaseManager = databaseManager;
			mScaleMatrix = new Matrix();
		}

		@Override
    	protected Bitmap create(Long feedId) {
			byte[] thumbnailBytes = mDatabaseManager.getFeedManager().getFeedThumbnailForId(feedId);
        	if (thumbnailBytes != null) {
        		Bitmap bm = UiUtil.decodeSampledBitmapFromByteArray(thumbnailBytes, mImageSize, mImageSize);
        		int bw = bm.getWidth();
        		int bh = bm.getHeight();
        		float dx = 0, dy = 0;
        		if (bw > mImageSize || bh > mImageSize) {
        			float scale;
        			if (bw > bh) {
        				scale = (float) mImageSize / (float) bw;
        				dx = (mImageSize - bw * scale) * 0.5f;
        			} else {
        				scale = (float) mImageSize / (float) bh;
        				dy = (mImageSize - bh * scale) * 0.5f;
        			}
        			mScaleMatrix.reset();
        			mScaleMatrix.setScale(scale, scale);
        			mScaleMatrix.postTranslate((int) (dx + 0.5f), (int) (dy + 0.5f));
        			return Bitmap.createBitmap(bm, 0, 0, bw, bh, mScaleMatrix, true);
        		}
        	}
        	return null;
    	}
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        Long feedId = (Long)v.getTag();
        Uri feedUri = MusubiContentProvider.uriForItem(Provided.FEEDS, feedId);
        selectFeed(position, feedUri);
        mPeople.clearSelectedIdentities();
    }

    class DeleteFeedAndContent extends AsyncTask<Void, Void, Void> {
    	long mFeedId;
		private ProgressDialog mProgressDialog;
        private boolean mCanceled = false;
    	public DeleteFeedAndContent(long feedId) {
			mFeedId = feedId;
            mProgressDialog = new ProgressDialog(mActivity);
            mProgressDialog.setTitle("Deleting Feed");
            mProgressDialog.setMessage("Feed is being deleted.  You will still receive new messages sent to the group.");
            mProgressDialog.setCancelable(true);
            mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
				@Override
                public void onCancel(DialogInterface dialog) {
                	mCanceled = true;
                }
            });
		}
    	@Override
    	protected void onPreExecute() {
    		mProgressDialog.show();
    	}
    	@Override
    	protected Void doInBackground(Void... params) {
        	SQLiteDatabase db = mDatabaseSource.getWritableDatabase();
        	db.beginTransaction();
        	FeedManager feedManager = new FeedManager(mDatabaseSource);
        	ObjectManager objectManager = new ObjectManager(mDatabaseSource);
        	EncodedMessageManager encodedMessageManager = new EncodedMessageManager(mDatabaseSource);
        	Cursor c = objectManager.getIdCursorForFeed(mFeedId);
        	try {
        		while(c.moveToNext() && !mCanceled) {
        			long id = c.getLong(0);
        			MObject object = objectManager.getObjectForId(id);
        			if(object.encodedId_ != null) {
        				encodedMessageManager.delete(object.encodedId_);
        				objectManager.delete(id);
        			}
        		}
        	} finally {
        		c.close();
        	}
        	MFeed feed = feedManager.lookupFeed(mFeedId);
    		feedManager.deleteFeedAndMembers(feed);
        	if(!mCanceled)
        		db.setTransactionSuccessful();
        	db.endTransaction();
        	return null;
    	}
    	
    	@Override
    	protected void onPostExecute(Void result) {
    		initLoaders(true);
        	mProgressDialog.dismiss();
    	}
    }
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		if (v.getId() == android.R.id.list) {
			menu.setHeaderTitle("Feed...");
			menu.add(Menu.NONE, 0, 0, "Delete");
			menu.add(Menu.NONE, 1, 0, "Send HTML");
		}
    }
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
        int menuItemIndex = item.getItemId();
        Cursor cursor = (Cursor)mFeeds.getItem(info.position);
 
        switch(menuItemIndex) {
	        case 0:
	        	//pass the feed id in
	        	handleDelete(cursor.getLong(0));
	        	break;
	        case 1:
	        	//pass the feed id in
	        	handleExport(cursor.getLong(0));
	        	break;
        }
        return true;
    }
    public void handleDelete(final long feedId) {
    	if(feedId == MFeed.WIZ_FEED_ID)
    		return;
        new AlertDialog.Builder(mActivity)
        .setIcon(android.R.drawable.ic_dialog_alert)
        .setTitle(R.string.delete_feed)
        .setMessage(R.string.delete_feed_message)
        .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            	FeedManager feedManager = new FeedManager(mDatabaseSource);
            	MFeed feed = feedManager.lookupFeed(feedId);
            	if(feed != null)
            		new DeleteFeedAndContent(feedId).execute();
            }
        })
        .setNegativeButton(R.string.no, null)
        .show();        
    }
    public void handleExport(final long feedId) {
    	FeedManager feedManager = new FeedManager(mDatabaseSource);
    	MFeed feed = feedManager.lookupFeed(feedId);
    	if(feed != null)
    		new ExportFeedContent(feedId).execute();
    }

    class ExportFeedContent extends AsyncTask<Void, Void, Void> {
    	long mFeedId;
		private ProgressDialog mProgressDialog;
        private boolean mCanceled = false;
		private String mFilename;
		private String mName;
    	public ExportFeedContent(long feedId) {
			mFeedId = feedId;
            mProgressDialog = new ProgressDialog(mActivity);
            mProgressDialog.setTitle("Send Feed as HTML");
            mProgressDialog.setMessage("Converting...");
            mProgressDialog.setCancelable(true);
            mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
				@Override
                public void onCancel(DialogInterface dialog) {
                	mCanceled = true;
                }
            });
		}
    	@Override
    	protected void onPreExecute() {
    		mProgressDialog.show();
    	}
    	public String encodeFilename(String s)
    	{
    		return s.replaceAll("[^A-Za-z0-9]+", " ");
    	}
    	
    	@Override
    	protected Void doInBackground(Void... params) {
        	FeedManager feedManager = new FeedManager(mDatabaseSource);
        	IdentitiesManager identitiesManager = new IdentitiesManager(mDatabaseSource);
        	MFeed feed = feedManager.lookupFeed(mFeedId);
        	if(feed == null)
        		return null;
        	ObjectManager objectManager = new ObjectManager(mDatabaseSource);
            String selection = new StringBuilder(100).append(MObject.COL_RENDERABLE).append(" = 1 AND ")
                    .append(MObject.COL_PARENT_ID).append(" is null AND ")
                    .append(MObject.COL_FEED_ID).append(" =?").toString();
            String[] selectionArgs = new String[] { Long.toString(mFeedId) };
        	Cursor c = getActivity().getContentResolver().query(MusubiContentProvider.uriForDir(Provided.OBJECTS), new String[] { MObject.COL_ID }, selection, selectionArgs, MObject.COL_LAST_MODIFIED_TIMESTAMP + " DESC");
        	try { 
	            File contentDir = new File(Environment.getExternalStorageDirectory(), ContentCorral.HTML_SUBFOLDER);
				MObject newest_object = null;
				while(c.moveToNext() && newest_object == null) {
					long newest_id = c.getLong(0);
					newest_object = objectManager.getObjectForId(newest_id);
				}

				//could only happen if someone delets while we export
				if(newest_object == null) {
					return null;
				}
	            mName = UiUtil.getFeedNameFromMembersList(feedManager, feed);
	            if(!contentDir.exists() && !contentDir.mkdirs()) {
	            	Log.e(TAG, "failed to create musubi html directory");
	            	return null;
	            }
	            String filename = contentDir.getAbsolutePath() + "/" + encodeFilename(mName) + "." + newest_object.timestamp_ + ".html";
	            FileOutputStream fo;
				try {
					fo = new FileOutputStream(filename);
				} catch (FileNotFoundException e) {
	            	Log.e(TAG, "failed to open HTML export file", e);
	            	return null;
				}
	
	            FeedHTML.writeHeader(fo, feedManager, feed);
	            
	        	c.moveToPosition(-1);
	        	try {
	        		while(c.moveToNext() && !mCanceled) {
	        			long id = c.getLong(0);
	        			MObject object = objectManager.getObjectForId(id);
	        			if(object == null)
	        				continue;
	                    FeedHTML.writeObj(fo, mActivity, identitiesManager, object);
	        		}
	        	} finally {
	        		c.close();
	        	}
	            FeedHTML.writeFooter(fo);
	            try {
					fo.close();
				} catch (IOException e) {
	            	Log.e(TAG, "failed to close HTML export file", e);
	            	return null;
				}
	            mFilename = filename;
        	} finally {
        		c.close();
        	}
        	return null;
    	}
    	
    	@Override
    	protected void onPostExecute(Void result) {
    		if(mFilename != null) {
    			Intent share = new Intent(Intent.ACTION_SEND);
    			share.setType("text/html");
    			share.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + mFilename));
    			share.putExtra(Intent.EXTRA_SUBJECT, "Musubi Feed: " + mName);
    			share.putExtra(Intent.EXTRA_TEXT, "Here is a fun group activity that I was a part of using Musubi. http://play.google.com/store/apps/details?id=mobisocial.musubi");
    			startActivity(Intent.createChooser(share, "Send Feed Snapshot"));
    		}
        	mProgressDialog.dismiss();
    	}
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
    	for (int i = 1; i < DAYS_TO_SHOW; i++) {
    		long time = cal.getTimeInMillis();
    		args = new Bundle();
    		args.putLong("start", time);
    		args.putLong("end", time + ONE_DAY);
    		if (restart) {
        		lm.restartLoader(i, args, this);
        	} else {
        		lm.initLoader(i, args, this);
        	}
    		cal.add(Calendar.DAY_OF_MONTH, -1);
    	}
    	args = new Bundle();
    	args.putLong("end", cal.getTimeInMillis() + ONE_DAY);
    	if (restart) {
    		lm.restartLoader(DAYS_TO_SHOW, args, this);
    	} else {
    		lm.initLoader(DAYS_TO_SHOW, args, this);
    	}
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    	Set<MIdentity> ids = mPeople.getSelectedIdentities();
    	String filterText = mPeople.getText().toString();
    	StringBuilder constraints = new StringBuilder(100);
    	constraints.append("1=1");
    	if (args != null) {
        	if (args.containsKey("start")) {
        		constraints.append(" AND ").append(MFeed.COL_LATEST_RENDERABLE_OBJ_TIME)
        			.append(">").append(args.getLong("start"));
        	}
        	if (args.containsKey("end")) {
        		constraints.append(" AND ").append(MFeed.COL_LATEST_RENDERABLE_OBJ_TIME)
        			.append("<=").append(args.getLong("end"));
        	}
        }
    	if (filterText.length() > 0) {
    		if (ids.size() > 0) {
    			for (MIdentity p : ids) {
    	            constraints.append(" AND ").append(MFeed.TABLE).append(".").append(MFeed.COL_ID).append(" in (SELECT ")
    	                .append(MFeedMember.COL_FEED_ID).append(" FROM ").append(MFeedMember.TABLE)
    	                .append(" WHERE ").append(MFeedMember.COL_IDENTITY_ID).append("=")
    	                .append(p.id_).append(")");
    	                    
    	        }
    		} else {
    			constraints.append(" AND ").append(MFeed.TABLE).append(".").append(MFeed.COL_NAME).append(" LIKE ");
        		DatabaseUtils.appendEscapedSQLString(constraints, "%" + filterText + "%");
    		}
    	}
        FeedSummaryLoader cl = new FeedSummaryLoader(getActivity(),
        		constraints.toString());
        cl.setUpdateThrottle(1000);
        return cl;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        mFeeds.changeCursor(loader.getId(), cursor);
        /*if (cursor.moveToFirst()) {
            long feedId = cursor.getLong(cursor.getColumnIndexOrThrow(MFeed.COL_ID));
            final Uri feedUri = MusubiContentProvider.uriForItem(Provided.FEEDS, feedId);
            if (getArguments() != null && getArguments().containsKey(DUAL_PANE) && isAdded()) {
                new Handler(getActivity().getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        selectFeed(0, feedUri);
                    }
                });
            }
        }*/
    }

    @Override
    public void onLoaderReset(Loader<Cursor> arg0) {
    }

    void selectFeed(int position, Uri feedUri) {
        mFeedSelectedListener.onFeedSelected(feedUri);
    }
    
    private OnClickListener mNoAccountsListener = new OnClickListener() {
		
		@Override
		public void onClick(View v) {
			getActivity().showDialog(FeedListActivity.DIALOG_PLZ_LINK_ACCCOUNT);
		}
	};

    private View.OnClickListener mJoinNearbyListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
        	startActivity(new Intent(mActivity, NearbyActivity.class));
        }
    };

	
    private View.OnClickListener mStartListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            LinkedHashSet<MIdentity> identities = mPeople.getSelectedIdentities();
            //mAppSelectFragment.getSelectedApp();
            if (identities.size() == 0) {
                Toast.makeText(mActivity,
                        "You have to add people to start something!", Toast.LENGTH_SHORT).show();
            } else {
            	new CreateFeedAsyncTask().execute(identities.toArray(new MIdentity[identities.size()]));
            }
        }
    };

    class CreateFeedAsyncTask extends AsyncTask<MIdentity, Void, Uri> {
    	DialogFragment mCreatingFeedDialog;

    	@Override
    	protected void onPreExecute() {
    		mCreatingFeedDialog = new CreateFeedDialogFragment();
    		((MusubiBaseActivity)getActivity()).showDialog(mCreatingFeedDialog);
    	}

    	@Override
    	protected Uri doInBackground(MIdentity... identities) {
    		//explicit user control of identity is handled by putting yourself in the feed list
            FeedManager fm = new FeedManager(mDatabaseSource);
            MyAccountManager am = new MyAccountManager(mDatabaseSource);

            MFeed feed = fm.createExpandingFeed(identities);
            Uri feedUri = MusubiContentProvider.uriForItem(Provided.FEEDS, feed.id_);

            UiUtil.addToWhitelistsIfNecessary(fm, am, fm.getFeedMembers(feed), true);

            //introduce your buddies so they have names for each other
            Obj invitedObj = IntroductionObj.from(Arrays.asList(identities), true);
            Helpers.sendToFeed(mActivity, invitedObj, feedUri);
            App.getUsageMetrics(mActivity).report(MusubiMetrics.FEED_CREATED_EXPANDING);

            Long objId = fm.getCachedLatestRenderable(feed.id_);
            while (objId == null) {
            	try {
            		Thread.sleep(50);
            	} catch (InterruptedException e) {}
            	objId = fm.getCachedLatestRenderable(feed.id_);
            }
            return feedUri;
    	}

    	@Override
    	protected void onPostExecute(Uri result) {
            mPeople.clearSelectedIdentities();
    		mCreatingFeedDialog.dismiss();
    		mFeedSelectedListener.onFeedSelected(result);
    	}
    }

    public static class CreateFeedDialogFragment extends DialogFragment {
    	@Override
    	public Dialog onCreateDialog(Bundle savedInstanceState) {
    		ProgressDialog d = new ProgressDialog(getActivity());
    		d.setTitle("Just a Moment");
    		d.setMessage("Starting conversation...");
    		d.setIndeterminate(true);
    		return d;
    	}
    }

    /**
     * Static library support version of the framework's {@link android.content.CursorLoader}.
     * Used to write apps that run on platforms prior to Android 3.0.  When running
     * on Android 3.0 or above, this implementation is still used; it does not try
     * to switch to the framework's implementation.  See the framework SDK
     * documentation for a class overview.
     */
    public static class FeedSummaryLoader extends AsyncTaskLoader<Cursor> {
    	static final String TAG = "FeedObjectsCursorLoader";
        final ForceLoadContentObserver mObserver;

        final SQLiteDatabase mDb;
        final String mConstraints;
        Cursor mCursor;

        /* Runs on a worker thread */
        @Override
        public Cursor loadInBackground() {
            Cursor cursor = initCursor();
            if (cursor != null) {
                // Ensure the cursor window is filled
                cursor.getCount();
                registerContentObserver(cursor, mObserver);
            }
            return cursor;
        }

        /**
         * Registers an observer to get notifications from the content provider
         * when the cursor needs to be refreshed.
         */
        void registerContentObserver(Cursor cursor, ContentObserver observer) {
            cursor.registerContentObserver(observer);
        }

        /* Runs on the UI thread */
        @Override
        public void deliverResult(Cursor cursor) {
            if (isReset()) {
                // An async query came in while the loader is stopped
                if (cursor != null) {
                    cursor.close();
                }
                return;
            }
            Cursor oldCursor = mCursor;
            mCursor = cursor;

            if (isStarted()) {
                super.deliverResult(cursor);
            }

            if (oldCursor != null && oldCursor != cursor && !oldCursor.isClosed()) {
                oldCursor.close();
            }
        }

        /**
         * Creates an empty unspecified CursorLoader.  You must follow this with
         * calls to {@link #setUri(Uri)}, {@link #setSelection(String)}, etc
         * to specify the query to perform.
         */
        public FeedSummaryLoader(Context context, String constraints) {
            super(context);
            mConstraints = constraints;
            mDb = App.getDatabaseSource(context).getReadableDatabase();
            mObserver = new ForceLoadContentObserver();
        }

        /**
         * Starts an asynchronous load of the contacts list data. When the result is ready the callbacks
         * will be called on the UI thread. If a previous load has been completed and is still valid
         * the result may be passed to the callbacks immediately.
         *
         * Must be called from the UI thread
         */
        @Override
        protected void onStartLoading() {
            if (mCursor != null) {
                deliverResult(mCursor);
            }
            if (takeContentChanged() || mCursor == null) {
                forceLoad();
            }
        }

        /**
         * Must be called from the UI thread
         */
        @Override
        protected void onStopLoading() {
            // Attempt to cancel the current load task if possible.
            cancelLoad();
        }

        @Override
        public void onCanceled(Cursor cursor) {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }

        @Override
        protected void onReset() {
            super.onReset();

            // Ensure the loader is stopped
            onStopLoading();

            if (mCursor != null && !mCursor.isClosed()) {
                mCursor.close();
            }
            mCursor = null;
        }

        Cursor initCursor() {
    		Cursor c = mDb.rawQuery(getFeedSummariesQuery(), null);
    		c.setNotificationUri(getContext().getContentResolver(),
    				MusubiContentProvider.uriForDir(Provided.FEEDS));
    		return c;
        }

        String getFeedSummariesQuery() {
        	StringBuilder sql = new StringBuilder(100);
        	sql.append("SELECT ")
	        		.append(MFeed.TABLE).append(".").append(MFeed.COL_ID).append(",")
	        		.append(MFeed.TABLE).append(".").append(MFeed.COL_NAME).append(",")
	        		.append(MFeed.TABLE).append(".").append(MFeed.COL_NUM_UNREAD).append(",")
	        		.append(MFeed.TABLE).append(".").append(MFeed.COL_LATEST_RENDERABLE_OBJ_TIME).append(",")
	        		.append(MObject.TABLE).append(".").append(MObject.COL_TYPE).append(",")
	        		.append(MObject.TABLE).append(".").append(MObject.COL_JSON).append(",")
	        		.append(MObject.TABLE).append(".").append(MObject.COL_IDENTITY_ID).append(",")
	        		.append(MFeed.TABLE).append(".").append(MFeed.COL_THUMBNAIL).append(" IS NOT NULL AS feed_thumbnail")
        		.append(" FROM ").append(MFeed.TABLE)
        		.append(" LEFT JOIN ").append(MObject.TABLE)
        			.append(" ON ").append(MFeed.TABLE).append(".").append(MFeed.COL_LATEST_RENDERABLE_OBJ_ID)
        			.append("=").append(MObject.TABLE).append(".").append(MObject.COL_ID)
    			//.append(" LEFT JOIN ").append(MIdentity.TABLE)
    			//	.append(" ON ").append(MObject.TABLE).append(".").append(MObject.COL_IDENTITY_ID)
    			//	.append("=").append(MIdentity.TABLE).append(".").append(MIdentity.COL_ID)
        		.append(" WHERE ").append(FeedManager.VISIBLE_FEED_SELECTION);
            if (mConstraints != null && mConstraints.length() > 0) {
            	sql.append(" AND ").append(mConstraints);
            }
            sql.append(" ORDER BY ").append(sFeedSortOrder);
            return sql.toString();
        }
    }

    public static class FeedSummary {
    	final Context mContext;

    	public long feedId;
    	public String feedName;
    	public int numUnread;
    	public long timestamp;
    	public String objType;
    	public String objJsonSrc;
    	public long identityId;
    	public boolean hasThumbnail;

    	private JSONObject mJson;

    	public FeedSummary(Context context) {
    		mContext = context;
    	}

    	public void populate(Cursor c) {
    		feedId = c.getLong(0);
    		feedName = (c.isNull(1)) ? null : c.getString(1);
    		numUnread = c.getInt(2);
    		timestamp = c.getLong(3);
    		objType = c.getString(4);
    		objJsonSrc = (c.isNull(5)) ? null : c.getString(5);
    		identityId = c.getLong(6);
    		hasThumbnail = c.getInt(7) != 0;
    		mJson = null;
    	}

    	public JSONObject getJson() {
    		if (mJson == null && objJsonSrc != null) {
    			try {
    				mJson = new JSONObject(objJsonSrc);
    			} catch (JSONException e) {}
    		}
    		return mJson;
    	}

    	public CachedIdentity getSender() {
    		return App.getContactCache(mContext).get(identityId);
    	}
    }
}