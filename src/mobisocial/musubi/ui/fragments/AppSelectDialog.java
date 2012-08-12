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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import mobisocial.musubi.App;
import mobisocial.musubi.R;
import mobisocial.musubi.feed.iface.FeedAction;
import mobisocial.musubi.model.MApp;
import mobisocial.musubi.model.helpers.AppManager;
import mobisocial.musubi.model.helpers.FeedManager;
import mobisocial.musubi.objects.WebAppObj;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.provider.MusubiContentProvider.Provided;
import mobisocial.musubi.ui.MusubiBaseActivity;
import mobisocial.musubi.util.InstrumentedActivity;
import mobisocial.musubi.webapp.AppCorralActivity;
import mobisocial.musubi.webapp.WebAppActivity;
import mobisocial.socialkit.Obj;
import mobisocial.socialkit.musubi.Musubi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.LabeledIntent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.app.SupportActivity;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.text.TextUtils.TruncateAt;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class AppSelectDialog extends DialogFragment implements LoaderCallbacks<List<AppSelectDialog.IMusubiApp>> {
    private static final String TAG = "LaunchAppDialog";
    private static final boolean DBG = MusubiBaseActivity.DBG;
    private Uri mFeedUri;
    private MusubiAppAdapter mAppAdapter;
    private boolean mHomeScreen;
    private IMusubiApp mSelectedApp;
    private IMusubiApp mMusubiFeedApp;

    private Activity mActivity;
    
    private static final String ARG_FEED_URI = "feedUri";
    private static final String ARG_HOME_SCREEN = "homeScreen";

    static final String CATEGORY_MUSUBI_MENU = "musubi.intent.category.MENU";

    public static final String SKETCH_APP_ID = "musubi.sketch";
    public static final String SHOUT_APP_ID = "musubi.shout";

    public static AppSelectDialog newInstance(boolean forHomeScreen, Uri feedUri) {
        if (DBG) Log.d(TAG, "New AppSelectDialog.");

        AppSelectDialog frag = new AppSelectDialog();
        Bundle args = new Bundle();
        args.putParcelable(ARG_FEED_URI, feedUri);
        args.putBoolean(ARG_HOME_SCREEN, forHomeScreen);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onAttach(SupportActivity activity) {
        super.onAttach(activity);
        mActivity = activity.asActivity();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_TITLE, R.style.Theme_D1dialog);

        mFeedUri = getArguments().getParcelable(ARG_FEED_URI);
        mHomeScreen = getArguments().getBoolean(ARG_HOME_SCREEN);
        if (getTargetFragment() != null) {
            setRetainInstance(true);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.applaunch_dialog, container, false);
        final Activity context = mActivity;
        if (mHomeScreen) {
            view.findViewById(R.id.appbar).setVisibility(View.GONE);
            mMusubiFeedApp = new MusubiFeed(context);
            mSelectedApp = mMusubiFeedApp;
        } else {
            setActionItems((ViewGroup)view.findViewById(R.id.appbar));
        }

        ListView listView = (ListView)view.findViewById(android.R.id.list);
        mAppAdapter = new MusubiAppAdapter(context);
        listView.setAdapter(mAppAdapter);
        listView.setOnItemClickListener(mAppAdapter);
        listView.setOnItemLongClickListener(mAppAdapter);
        view.findViewById(R.id.content).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent webStore = new Intent(context, AppCorralActivity.class);
                webStore.putExtra(Musubi.EXTRA_FEED_URI, mFeedUri);
                startActivity(webStore);
                getDialog().dismiss();
            }
        });
        getLoaderManager().initLoader(0, null, this);
        return view;
    }

    private void setActionItems(ViewGroup view) {
        final Context context = mActivity;
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        List<FeedAction> actions = FeedAction.getFeedActions();
        for (final FeedAction a : actions) {
            if (a.isActive(context)) {
                ft.add(a, a.getName());
                ImageButton b = new ImageButton(context);
                b.setLayoutParams(new LayoutParams(
                        LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
                b.setImageDrawable(a.getIcon(mActivity));
                b.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        a.onClick(context, mFeedUri);
                        Dialog d = getDialog();
                        if (d != null) d.dismiss();
                    }
                });
                view.addView(b);
            }
        }
        ft.commit();
    }

    private class MusubiAppAdapter extends ArrayAdapter<IMusubiApp>
            implements OnItemClickListener, OnItemLongClickListener {
        private final Context mContext;

        public MusubiAppAdapter(Context context) {
        	super(context, 0, 0);
        	mContext = context;
        }

        public MusubiAppAdapter(Context context, List<IMusubiApp> objects) {
            super(context, 0, 0, objects);
            mContext = context;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = newView(parent);
            }

            IMusubiApp app = getItem(position);
            ((TextView)convertView.findViewById(R.id.text)).setText(app.getName());
            ((ImageView)convertView.findViewById(R.id.icon)).setImageDrawable(app.getIcon());
            return convertView;
        }

        private View newView(ViewGroup parent) {
            LinearLayout frame = new LinearLayout(mContext);
            AbsListView.LayoutParams lp = new AbsListView.LayoutParams(
                    AbsListView.LayoutParams.MATCH_PARENT, AbsListView.LayoutParams.MATCH_PARENT);
            frame.setLayoutParams(lp);
            frame.setOrientation(LinearLayout.HORIZONTAL);
            frame.setGravity(Gravity.CENTER_HORIZONTAL);

            ImageView icon = new ImageView(mContext);
            icon.setId(R.id.icon);
            icon.setLayoutParams(new LayoutParams(80, 80));
            icon.setPadding(6, 6, 6, 6);
            frame.addView(icon);

            TextView label = new TextView(mContext);
            label.setId(R.id.text);
            label.setLayoutParams(new LayoutParams(
                    LayoutParams.FILL_PARENT, LayoutParams.MATCH_PARENT));
            label.setTextSize(24);
            label.setEllipsize(TruncateAt.END);
            label.setGravity(Gravity.CENTER_VERTICAL);
            frame.addView(label);

            return frame;
        }

        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int item, long itemId) {
            mSelectedApp = getItem(item);
            Fragment target = getTargetFragment();
            if (target != null) {
                int requestCode = getTargetRequestCode();
                getTargetFragment().onActivityResult(requestCode, Activity.RESULT_OK, null);
            } else {
                Intent launch = new Intent(mSelectedApp.getLaunchIntent(mActivity, mFeedUri));
                String appIdStr = mSelectedApp.getAppId();
                long feedId = ContentUris.parseId(mFeedUri);

                SQLiteOpenHelper db = App.getDatabaseSource(getActivity());
                MApp app = new AppManager(db).ensureApp(appIdStr);
                new FeedManager(db).ensureFeedApp(feedId, app.id_);
                mActivity.startActivity(launch);
            }
            dismiss();
        }

        @Override
        public boolean onItemLongClick(AdapterView<?> adapterView, View view, int item, long itemId) {
            IMusubiApp app = getItem(item);
            if (!(app instanceof MusubiWebApp)) {
                return false;
            }
            MusubiWebApp web = (MusubiWebApp) app;
            String appId = web.getAppId();
            String name = web.getName();

            if (SKETCH_APP_ID.equals(appId) || SHOUT_APP_ID.equals(appId)) {
                return false;
            }

            //TODO: add an internal flag or something that we can consult when deciding if the 
            //delete option should be available
            ((InstrumentedActivity)getActivity()).showDialog(
                    WebAppContextMenu.newInstance(appId, name));
            return true;
        }
    }

    /**
     * Gets the app the user currently has selected.
     */
    public IMusubiApp getSelectedApp() {
        return mSelectedApp;
    }

    public interface IMusubiApp {
        public String getAppId();
        public String getName();
        public Drawable getIcon();
        public LabeledIntent getLaunchIntent(Context context, Uri feedUri);
    }

    /**
     * An "app" that is just a Musubi feed.
     */
    static class MusubiFeed implements IMusubiApp {
        final Context mContext;

        public MusubiFeed(Context c) {
            mContext = c;
        }

        @Override
        public String getName() {
            return "New Conversation...";
        }

        @Override
        public Drawable getIcon() {
            return mContext.getResources().getDrawable(R.drawable.ic_text_holo_light);
        }

        @Override
        public LabeledIntent getLaunchIntent(Context context, Uri feedUri) {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(feedUri, MusubiContentProvider.getType(Provided.FEEDS_ID));
            i.setPackage(context.getPackageName());
            LabeledIntent l = new LabeledIntent(i, context.getPackageName(), getName(),
                    R.drawable.ic_text_holo_light);
            return l;
        }

        @Override
        public String getAppId() {
            return MusubiContentProvider.SUPER_APP_ID;
        }
    }

    public static class MusubiWebApp implements IMusubiApp {
        final String mName;
        final String mWebAppUrl;
        final String mAppId;
        final Context mContext;
        final int mIconResource;

        public MusubiWebApp(Context context, String name, String appId, String webAppUrl, int iconResource) {
            mContext = context;
            mName = name;
            mAppId = appId;
            mWebAppUrl = webAppUrl;
            mIconResource = iconResource;
        }

        @Override
        public String getName() {
            return mName;
        }

        public Drawable getIcon() {
            return mContext.getResources().getDrawable(mIconResource);
        }

        public Uri getUri() {
            return Uri.parse(mWebAppUrl);
        }

        @Override
        public LabeledIntent getLaunchIntent(Context context, Uri feedUri) {
            Intent appIntent = new Intent(Intent.ACTION_VIEW);
            appIntent.setClass(context, WebAppActivity.class);
            appIntent.putExtra(Musubi.EXTRA_FEED_URI, feedUri);
            appIntent.putExtra(WebAppActivity.EXTRA_APP_NAME, mName);
            appIntent.putExtra(WebAppActivity.EXTRA_APP_URI, getUri());
            appIntent.putExtra(WebAppActivity.EXTRA_APP_ID, mAppId);
            LabeledIntent labeled = new LabeledIntent(appIntent, context.getPackageName(),
                    mName, mIconResource);
            return labeled;
        }

        @Override
        public String getAppId() {
            return mAppId;
        }
    }

    static class MusubiInstalledApp implements IMusubiApp {
        private final PackageManager mPackageManager;
        private final ResolveInfo mInfo;
        private final String mLabel;

        public MusubiInstalledApp(PackageManager pm, ResolveInfo info) {
            mPackageManager = pm;
            mInfo = info;
            mLabel = info.loadLabel(pm).toString();
        }

        public ResolveInfo  getAppInfo() {
            return mInfo;
        }

        @Override
        public String getName() {
            return mLabel;
        }

        @Override
        public Drawable getIcon() {
            return getAppInfo().loadIcon(mPackageManager);
        }

        @Override
        public LabeledIntent getLaunchIntent(Context context, Uri feedUri) {
            String sourcePkg = getAppInfo().activityInfo.packageName;
            int labelRes = getAppInfo().activityInfo.labelRes;
            int iconRes = getAppInfo().activityInfo.icon;

            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(CATEGORY_MUSUBI_MENU);
            intent.setClassName(sourcePkg, getAppInfo().activityInfo.name);
            intent.putExtra(Musubi.EXTRA_FEED_URI, feedUri);
            LabeledIntent labeled = new LabeledIntent(intent, sourcePkg, labelRes, iconRes);
            return labeled;
        }

        @Override
        public String getAppId() {
            return getAppInfo().activityInfo.packageName;
        }
    }

    public interface OnAppSelectedListener {
        public void onAppSelected(AppSelectDialog dialog, IMusubiApp app);
    }

    static class WebAppContextMenu extends DialogFragment {
        static final String APP_ID = "app_id";
        static final String NAME = "name";

        public static WebAppContextMenu newInstance(String appId, String name) {
            WebAppContextMenu d = new WebAppContextMenu();
            Bundle b = new Bundle();
            b.putString(APP_ID, appId);
            b.putString(NAME, name);
            d.setArguments(b);
            return d;
        }

        public WebAppContextMenu() {
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final String name = getArguments().getString(NAME);
            final String appId = getArguments().getString(APP_ID);
            
            return new AlertDialog.Builder(getActivity())
                .setTitle(name)
                .setItems(new String[] { "Remove", "Copy to clipboard" }, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            AppManager am = new AppManager(App.getDatabaseSource(getActivity()));
                            am.deleteAppWithId(appId);
                            getActivity().getContentResolver().notifyChange(
                                    MusubiContentProvider.uriForDir(Provided.OBJECTS), null);
                            Toast.makeText(getActivity(), "Removed app " + name + ".",
                                    Toast.LENGTH_SHORT).show();
                        } else if (which == 1) {
                            AppManager am = new AppManager(App.getDatabaseSource(getActivity()));
                            MApp app = am.lookupAppByAppId(appId);
                            if (app == null) {
                                Toast.makeText(getActivity(), "Error getting app.", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            if (app.webAppUrl_ == null) {
                                Toast.makeText(getActivity(), "Not a copyable app.", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            Obj obj = WebAppObj.forUri(Uri.parse(app.webAppUrl_));
                            new ClipboardKeeper(getActivity()).store(obj);
                            Toast.makeText(getActivity(), "Put content on your clipboard. Use the pin menu to paste it.", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .create();
        }
    }

	@Override
	public Loader<List<IMusubiApp>> onCreateLoader(int id, Bundle args) {
		return new MusubiAppLoader(getActivity());
	}

	@Override
	public void onLoadFinished(Loader<List<IMusubiApp>> loader,
			List<IMusubiApp> data) {
		mAppAdapter.clear();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			mAppAdapter.addAll(data);
		} else {
			for (IMusubiApp app : data) {
				mAppAdapter.add(app);
			}
		}
	}

	@Override
	public void onLoaderReset(Loader<List<IMusubiApp>> loader) {
	}

	static class MusubiAppLoader extends AsyncTaskLoader<List<IMusubiApp>> {
		private List<IMusubiApp> mData;

		public MusubiAppLoader(Context context) {
			super(context);
		}

		@Override
		protected void onStartLoading() {
			if (mData != null) {
				deliverResult(mData);
			} else {
				forceLoad();
			}
		}

		@Override
		public List<IMusubiApp> loadInBackground() {
	        mData = getAvailableApps(getContext());
	        return mData;
		}

		private List<IMusubiApp> getAvailableApps(Context context) {
	        final PackageManager mgr = context.getPackageManager();
	        final List<IMusubiApp> apps = new LinkedList<IMusubiApp>();

	        // Web Apps
	        apps.addAll(getWebApps(context));

	        // User installed apps
	        Intent i = new Intent();
	        List<ResolveInfo> infos;
	        i.setAction(Intent.ACTION_MAIN);
	        i.addCategory(CATEGORY_MUSUBI_MENU);
	        infos = mgr.queryIntentActivities(i, 0);
	        if (DBG) Log.d(TAG, "Queried " + infos.size() + " feed apps.");
	        for (int j = 0; j < infos.size(); j++) {
	            apps.add(new MusubiInstalledApp(mgr, infos.get(j)));
	        }

	        return apps;
	    }

	    List<MusubiWebApp> getWebApps(Context context) {
	        SQLiteDatabase db = App.getDatabaseSource(context).getReadableDatabase();
	        String table = MApp.TABLE;
	        String[] columns = new String[] { MApp.COL_ID, MApp.COL_APP_ID, MApp.COL_NAME,
	                MApp.COL_WEB_APP_URL };
	        String selection = MApp.COL_WEB_APP_URL + " is not null AND " + MApp.COL_DELETED + "=0";
	        String[] selectionArgs = null;
	        String groupBy = null, having = null, orderBy = null;
	        Cursor c = db.query(table, columns, selection, selectionArgs, groupBy, having, orderBy);

	        List<MusubiWebApp> apps = new ArrayList<MusubiWebApp>();
	        try {
	            while (c.moveToNext()) {
	                String appId = c.getString(1);
	                String name = c.getString(2);
	                String webUrl = c.getString(3);

	                int iconResource;
	                if (SKETCH_APP_ID.equals(appId)) {
	                    iconResource = R.drawable.sketch;
	                } else if (SHOUT_APP_ID.equals(appId)) {
	                    iconResource = R.drawable.shout;
	                } else {
	                    iconResource = R.drawable.ic_menu_globe;
	                }
	                apps.add(new MusubiWebApp(context, name, appId, webUrl, iconResource));
	            }
	            return apps;
	        } finally {
	            c.close();
	        }
	    }
	}
}