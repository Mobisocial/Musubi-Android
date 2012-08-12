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
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import mobisocial.metrics.UsageMetrics;
import mobisocial.musubi.App;
import mobisocial.musubi.Helpers;
import mobisocial.musubi.R;
import mobisocial.musubi.VoiceRecordActivity;
import mobisocial.musubi.feed.iface.DbEntryHandler;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MObject;
import mobisocial.musubi.model.helpers.AppManager;
import mobisocial.musubi.model.helpers.FeedManager;
import mobisocial.musubi.model.helpers.ObjectManager;
import mobisocial.musubi.obj.ObjActions;
import mobisocial.musubi.obj.ObjHelpers;
import mobisocial.musubi.obj.iface.ObjAction;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.provider.MusubiContentProvider.Provided;
import mobisocial.musubi.service.MusubiService;
import mobisocial.musubi.service.WizardStepHandler;
import mobisocial.musubi.ui.FeedDetailsActivity;
import mobisocial.musubi.ui.MusubiBaseActivity;
import mobisocial.musubi.ui.util.EmojiSpannableFactory;
import mobisocial.musubi.ui.widget.DbObjCursorAdapter;
import mobisocial.musubi.util.InstrumentedActivity;
import mobisocial.musubi.util.ObjFactory;
import mobisocial.socialkit.Obj;
import mobisocial.socialkit.musubi.DbObj;
import mobisocial.socialkit.musubi.Musubi;

import org.apache.commons.io.IOUtils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.SupportActivity;
import android.support.v4.content.Loader;
import android.support.v4.view.Menu;
import android.support.v4.view.MenuItem;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

/**
 * Shows a series of posts from a feed.
 */
public class FeedViewFragment extends ListFragment implements OnScrollListener,
        OnEditorActionListener, TextWatcher, LoaderManager.LoaderCallbacks<Cursor>,
        KeyEvent.Callback {

    public static final String TAG = "ObjectsActivity";
    private boolean DBG = true;

    public static final String ARG_FEED_URI = "feed_uri";
    public static final String ARG_DUAL_PANE = "dual_pane";
    private static final String EXTRA_NUM_ITEMS = "total";
    private static final int BATCH_SIZE = getBestBatchSize();

    private InputMethodManager mInputMethodManager;
    private View mInputBar;
    private ListView mListView;
    private DbObjCursorAdapter mObjects;
	private Uri mFeedUri;
    private EditText mStatusText;
    private Button mSendTextButton;
	private long loaderStartTime;
	private Musubi mMusubi;
	private Activity mActivity;
	private int mTotal = BATCH_SIZE;
	int mPreviousTotal = -1;
	int mResumeToPosition = -1;

	@Override
    public void onAttach(SupportActivity activity) {
        super.onAttach(activity);
        mFeedUri = getArguments().getParcelable(ARG_FEED_URI);
        if (DBG) {
            Log.w(TAG, getArguments().toString());
            Log.d(TAG, "Attached fragment to feed " + mFeedUri);
        }
        mMusubi = App.getMusubi(activity.asActivity());
        mActivity = activity.asActivity();

        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_feed_view, container, false);
		return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        WizardStepHandler.accomplishTask(mActivity, WizardStepHandler.TASK_OPEN_FEED);
        mSendTextButton = (Button)view.findViewById(R.id.send_text);
        mSendTextButton.setOnClickListener(mSendStatus);
        mSendTextButton.setEnabled(false);

        mStatusText = (EditText)view.findViewById(R.id.status_text);
        mStatusText.setOnEditorActionListener(FeedViewFragment.this);
        mStatusText.addTextChangedListener(FeedViewFragment.this);

        view.findViewById(R.id.pick_app).setOnClickListener(mPickApp);

        mInputBar = (View)view.findViewById(R.id.input_bar);
        mInputBar.setBackgroundColor(Color.WHITE);

        mListView = getListView();
        mListView.setFastScrollEnabled(true);
        mListView.setOnItemClickListener(mItemClickListener);
        mListView.setOnItemLongClickListener(mItemLongClickListener);
        mListView.setOnScrollListener(this);
        mListView.setFocusable(true);

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(0, RelativeLayout.LayoutParams.FILL_PARENT);
        params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);

        ((MusubiBaseActivity)getActivity()).setOnKeyListener(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mListView = null;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (mFeedUri == null) {
            Throwable e = new Throwable("No uri when setting options menu");
            UsageMetrics.getUsageMetrics(mActivity).report(e);
            return;
        }

        long id = Long.parseLong(mFeedUri.getLastPathSegment());
        FeedManager fm = new FeedManager(App.getDatabaseSource(mActivity));
        MFeed f = fm.lookupFeed(id);
        assert(f != null);
        if (f.type_ == MFeed.FeedType.EXPANDING) {
            inflater.inflate(R.menu.feed_group, menu);
        } else {
            inflater.inflate(R.menu.feed_fixed, menu);
        }
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        	case R.id.menu_feed_details:
	        	Intent intent = new Intent(getActivity(), FeedDetailsActivity.class);
	            intent.setDataAndType(mFeedUri, MusubiContentProvider.getType(Provided.FEEDS_ID));
	            startActivity(intent);
	            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_SEND ||
                actionId == EditorInfo.IME_ACTION_DONE) {
            mSendStatus.onClick(v);
        }
        return true;
    }

    Bundle getLoaderArgs(int max) {
    	Bundle b = new Bundle();
    	b.putInt("max", max);
    	return b;
    }

    final View.OnClickListener mSendStatus = new OnClickListener() {
        public void onClick(View v) {
            new SendTextTask().execute();
        }
    };

    class SendTextTask extends AsyncTask<Void, String, Obj> {
        String mText;
        Editable mEditor;

        @Override
        protected void onPreExecute() {
            mEditor = mStatusText.getText();
            mText = mEditor.toString();
            mEditor.clear();
        }

        @Override
        protected Obj doInBackground(Void... params) {
            Obj obj = null;
            if (mText.length() > 0) {
                if (Patterns.WEB_URL.matcher(mText.trim()).matches()) {
                    // TODO: proper progress notification using async task..
                    publishProgress("Fetching web story...");
                }
                obj = ObjFactory.objForText(mText);
                Helpers.sendToFeed(mActivity, obj, mFeedUri);
            }
            return obj;
        }

        @Override
        protected void onPostExecute(Obj result) {
            if (result != null) {
                Helpers.emailUnclaimedMembers(mActivity, result, mFeedUri);
            }
        }

        @Override
        protected void onProgressUpdate(String... values) {
            Toast.makeText(mActivity, values[0], Toast.LENGTH_LONG).show();
        }
    }

    final View.OnClickListener mPickApp = new OnClickListener() {
        @Override
        public void onClick(View v) {
            ((InstrumentedActivity)mActivity)
                .showDialog(AppSelectDialog.newInstance(false, mFeedUri));
        }
    };

    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
    @Override
    public void afterTextChanged(Editable s) {
        mSendTextButton.setEnabled(s.length() > 0);
        EmojiSpannableFactory.getInstance(mActivity).updateSpannable(s);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        final long feedId = Long.parseLong(mFeedUri.getLastPathSegment());
        loaderStartTime = new Date().getTime();

        //if max is too low things get very screwy
        int max = Math.max(25, args.getInt("max"));
        Loader<Cursor> cl = DbObjCursorAdapter.getLoaderForFeed(mActivity, feedId, max);
        //cl.setUpdateThrottle(2000);
        return cl;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        if (DBG) Log.d(TAG, "Query took " + (System.currentTimeMillis() - loaderStartTime) + "ms");
    	//the mObjects field is accessed by the ui thread as well
        int previousTotal = -1;
        if (mObjects == null) {
            mObjects = new DbObjCursorAdapter(mActivity, cursor);
            setListAdapter(mObjects);
		} else {
    	    previousTotal = mObjects.getCount();
		    mObjects.changeCursor(cursor);
		}

        mTotal = cursor.getCount();
        if (mResumeToPosition != -1) {
            int position = mTotal - mResumeToPosition;
            getListView().setSelection(position);
            mResumeToPosition = -1;
            mPreviousTotal = previousTotal;
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
    	loaderStartTime = new Date().getTime();
    }

    private static int getBestBatchSize() {
        Runtime runtime = Runtime.getRuntime();
        if(runtime.availableProcessors() > 1)
            return 100;

        FileInputStream in = null;
        try {
            File max_cpu_freq = new File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq");
            in = new FileInputStream(max_cpu_freq);
            byte[] freq_bytes = IOUtils.toByteArray(in);
            String freq_string = new String(freq_bytes);
            double freq = Double.valueOf(freq_string);
            if(freq > 950000) {
                return 50;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if(in != null) in.close();
            } catch (IOException e) {
                Log.e(FeedViewFragment.TAG, "failed to close frequency counter file", e);
            }
        }
        return 15;
    }

	private ContentObserver mObserver;
	@Override
	public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
        mInputMethodManager = (InputMethodManager)getActivity()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        mObserver = new ContentObserver(new Handler(mActivity.getMainLooper())) {
        	@Override
        	public void onChange(boolean selfChange) {
        		if (mObjects == null || mObjects.getCursor() == null || !isAdded()) {
        			return;
        		}

        		// XXX Move this to WizardStepHandler-- register a content observer
        		// there only when required.
        		if (WizardStepHandler.isCurrentTask(mActivity, WizardStepHandler.TASK_EDIT_PICTURE)) {
        			Cursor c = mObjects.getCursor();
        			ObjectManager om = new ObjectManager(App.getDatabaseSource(mActivity));
        			AppManager am = new AppManager(App.getDatabaseSource(mActivity));
        			if (c.moveToFirst()) {
        				while (!c.isAfterLast()) {
        			        long objId = c.getLong(c.getColumnIndexOrThrow(MObject.COL_ID));
        			        MObject obj = om.getObjectForId(objId);
        			        if (obj != null && am.getAppIdentifier(obj.appId_).startsWith("musubi.sketch")) {
        			        	WizardStepHandler.accomplishTask(mActivity, WizardStepHandler.TASK_EDIT_PICTURE);
        			        	break;
        			        }
        					c.moveToNext();
        				}
        			}
        		}

        		if (DBG) Log.d(TAG, "-- contentObserver observed change");
        		getLoaderManager().restartLoader(0, getLoaderArgs(mTotal), FeedViewFragment.this);
        	}
		};

		if (savedInstanceState != null) {
            mTotal = savedInstanceState.getInt(EXTRA_NUM_ITEMS);
            Log.d(TAG, "setting total from instance: " + mTotal);
        } else {
            Log.d(TAG, "using total " + mTotal);
        }

        if (DBG) Log.d(TAG, "-- onCreated");
        getLoaderManager().initLoader(0, getLoaderArgs(mTotal), this);
    }
	
	@Override
	public void onResume() {
    	super.onResume();

		if (WizardStepHandler.isCurrentTask(mActivity, WizardStepHandler.TASK_EDIT_PICTURE)) {
			mActivity.getContentResolver().registerContentObserver(MusubiService.APP_OBJ_READY, false, mObserver);
		}
    	mActivity.getContentResolver().registerContentObserver(MusubiService.WHITELIST_APPENDED, false, mObserver);
    	mActivity.getContentResolver().registerContentObserver(MusubiService.COLORLIST_CHANGED, false, mObserver);
    	mActivity.getContentResolver().registerContentObserver(MusubiService.PRIMARY_CONTENT_CHANGED, false, mObserver);
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
		
    void showMenuForObj(int position) {
    	//this first cursor is the internal one
        Cursor cursor = (Cursor)mObjects.getItem(position);
        long objId = cursor.getLong(0);

        DbObj obj = mMusubi.objForId(objId);
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        Fragment prev = getFragmentManager().findFragmentByTag("dialog");
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);

        // Create and show the dialog.
        DialogFragment newFragment = new ObjMenuDialogFragment(obj);
        newFragment.show(ft, "dialog");
    }

    public class ObjMenuDialogFragment extends DialogFragment {
        String mType;
        private DbObj mObj;
		byte[] mRaw;
		Uri mFeedUri;
		long mHash;
		long mContactId;

        // Required by framework; fields populated from savedInstanceState.
        public ObjMenuDialogFragment() {
            
        }

        private ObjMenuDialogFragment(DbObj obj) {
            loadFromObj(obj);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            if (savedInstanceState != null) {
                long objId = savedInstanceState.getLong("objId");
                loadFromObj(mMusubi.objForId(objId));
            }

            final DbEntryHandler dbType = ObjHelpers.forType(mType);
            final List<ObjAction> actions = new ArrayList<ObjAction>();
            for (ObjAction action : ObjActions.getObjActions()) {
                if (action.isActive(mActivity, dbType, mObj)) {
                    actions.add(action);
                }
            }
            final String[] actionLabels = new String[actions.size()];
            int i = 0;
            for (ObjAction action : actions) {
                actionLabels[i++] = action.getLabel(mActivity);
            }
            Dialog dialog = new AlertDialog.Builder(mActivity)
                    .setTitle("Handle...")
                    .setItems(actionLabels, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            actions.get(which).actOn(mActivity, dbType, mObj);
                        }
                    }).create();
            return dialog;
        }

        @Override
        public void onSaveInstanceState(Bundle bundle) {
            super.onSaveInstanceState(bundle);
            bundle.putLong("objId", mObj.getLocalId());
        }

        private void loadFromObj(DbObj obj) {
            mFeedUri = obj.getContainingFeed().getUri();
            mType = obj.getType();
            mObj = obj;
            mRaw = obj.getRaw();
            mHash = obj.getHash();
            mContactId = obj.getSender().getLocalId();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK) {
            Intent record = new Intent(mActivity, VoiceRecordActivity.class);
            record.putExtra("feed_uri", mFeedUri);
            startActivity(record);
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        return false;
    }

    @Override
    public boolean onKeyMultiple(int keyCode, int count, KeyEvent event) {
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return false;
    }

	@Override
	public void onScroll(AbsListView view, int firstVisible,
			int visibleCount, int totalCount) {

 		if (mObjects == null) {
			return;		
 		}

		boolean loadMore = (firstVisible == 0 && mResumeToPosition == -1 && mPreviousTotal != mTotal);
    	if (loadMore) {
    		mResumeToPosition = mTotal;
    		getLoaderManager().restartLoader(0, getLoaderArgs(totalCount + BATCH_SIZE), this);
    	}
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
	    if (scrollState != SCROLL_STATE_IDLE) {
	        mInputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
	    }
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
	    super.onSaveInstanceState(outState);
	    if (mListView != null) {
	        outState.putInt(EXTRA_NUM_ITEMS, mTotal);
	    }
	}

	private OnItemClickListener mItemClickListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
            ObjHelpers.ItemClickListener.getInstance().onClick(view);
        }
	};

	private OnItemLongClickListener mItemLongClickListener = new OnItemLongClickListener() {
        @Override
        public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long id) {
            ObjHelpers.ItemLongClickListener.getInstance(mActivity).onLongClick(view);
            return true;
        }
    };
}
