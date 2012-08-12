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

package mobisocial.musubi.objects;

import java.util.List;

import mobisocial.musubi.App;
import mobisocial.musubi.R;
import mobisocial.musubi.feed.iface.Activator;
import mobisocial.musubi.feed.iface.DbEntryHandler;
import mobisocial.musubi.feed.iface.FeedRenderer;
import mobisocial.musubi.model.MApp;
import mobisocial.musubi.model.helpers.AppManager;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.provider.MusubiContentProvider.Provided;
import mobisocial.musubi.service.MusubiService;
import mobisocial.musubi.ui.fragments.FeedListFragment.FeedSummary;
import mobisocial.musubi.ui.widget.DbObjCursorAdapter.DbObjCursor;
import mobisocial.musubi.util.CommonLayouts;
import mobisocial.musubi.util.InstrumentedActivity;
import mobisocial.musubi.webapp.WebAppActivity;
import mobisocial.socialkit.Obj;
import mobisocial.socialkit.musubi.DbObj;
import mobisocial.socialkit.musubi.Musubi;
import mobisocial.socialkit.obj.MemObj;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class WebAppObj extends DbEntryHandler implements FeedRenderer, Activator {
    static final String TYPE = "webapp";

    static final String OBJ_URL = "url";

    @Override
    public String getType() {
        return TYPE;
    }

    public static Obj forUri(Uri uri) {
        try {
            JSONObject json = new JSONObject();
            json.put(OBJ_URL, uri.toString());
            return new MemObj(TYPE, json);
        } catch (JSONException e) {
            throw new IllegalStateException("bad json library", e);
        }
    }

    @Override
    public View createView(Context context, ViewGroup parent) {
    	LinearLayout frame = new LinearLayout(context);
    	frame.setLayoutParams(CommonLayouts.FULL_WIDTH);
    	frame.setOrientation(LinearLayout.VERTICAL);

    	LinearLayout appBar = new LinearLayout(context);
        appBar.setLayoutParams(CommonLayouts.FULL_WIDTH);
        appBar.setOrientation(LinearLayout.HORIZONTAL);
        frame.addView(appBar);

        Drawable icon = context.getResources().getDrawable(R.drawable.ic_menu_globe);
        ImageView iv = new ImageView(context);
        iv.setImageDrawable(icon);
        iv.setAdjustViewBounds(true);
        iv.setMaxWidth(60);
        iv.setMaxHeight(60);
        iv.setLayoutParams(CommonLayouts.WRAPPED);
        appBar.addView(iv);

        TextView tv = new TextView(context);
        tv.setLayoutParams(CommonLayouts.WRAPPED);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        appBar.addView(tv);

        LinearLayout actionBar = new LinearLayout(context);
        actionBar.setLayoutParams(CommonLayouts.WRAPPED);
        actionBar.setOrientation(LinearLayout.HORIZONTAL);
        frame.addView(actionBar);

        Button b = new Button(context);
        // required for listview long-press
        b.setLayoutParams(CommonLayouts.WRAPPED);
        b.setFocusable(false);
        b.setText("Run");
        b.setOnClickListener(getRunListener());
        actionBar.addView(b);

        b = new Button(context);
        // required for listview long-press
        b.setLayoutParams(CommonLayouts.WRAPPED);
        b.setFocusable(false);
        b.setOnClickListener(getAddListener());
        actionBar.addView(b);

    	return frame;
    }

    @Override
    public void activate(Context context, DbObj obj) {
    	// we have BUTTONS
    }

    @Override
    public void render(Context context, View view, DbObjCursor obj, boolean allowInteractions)
            throws Exception {
    	LinearLayout frame = (LinearLayout)view;

    	TextView tv = (TextView)((ViewGroup)frame.getChildAt(0)).getChildAt(1);
    	tv.setText("Webapp " + getAppName(obj));

        if (allowInteractions) {
        	Button b = (Button)((ViewGroup)frame.getChildAt(1)).getChildAt(0);
            long objId = obj.objId;
            b.setTag(objId);

            b = (Button)((ViewGroup)((ViewGroup)view).getChildAt(1)).getChildAt(1);
            b.setTag(objId);
            AppManager am = new AppManager(App.getDatabaseSource(context));
            MApp lookup = am.lookupAppByAppId(getAppId(obj));
            //deleted ones can be readded
            //ensure app may have put it in the table without a url;
            if (lookup == null || lookup.deleted_ == true || lookup.webAppUrl_ == null) {
                b.setText("Add");
                b.setEnabled(true);
            } else {
                b.setText("Added");
                b.setEnabled(false);
            }
        }
    }

    OnClickListener mAddListener;
    private OnClickListener getAddListener() {
    	if (mAddListener == null) {
    		mAddListener = new OnClickListener() {
    	        @Override
    	        public void onClick(View v) {
    	            Context context = v.getContext();
    	            if (context instanceof InstrumentedActivity) {
    	                InstrumentedActivity a = (InstrumentedActivity) context;
    	                try {
    	                    DbObj obj = App.getMusubi(context).objForId((Long)v.getTag());
    	                    String name = getAppName(obj);
    	                    Uri uri = Uri.parse(obj.getJson().optString(OBJ_URL));
    	                    String appId = getAppId(obj);
    	                    a.showDialog(AddWebAppDialog.newInstance(obj.getLocalId(), appId, name, uri));
    	                } catch (Throwable t) {
    	                    Toast.makeText(context, "Failed to load webapp", Toast.LENGTH_SHORT).show();
    	                }
    	            }
    	        }
    	    };
    	}
    	return mAddListener;
    }
    
    OnClickListener mRunListener;
    private OnClickListener getRunListener() {
    	if (mRunListener == null) {
    		mRunListener = new OnClickListener() {
    	        @Override
    	        public void onClick(View v) {
    	            Context context = v.getContext();
    	            DbObj obj = App.getMusubi(context).objForId((Long)v.getTag());
    	            Intent appIntent = new Intent(Intent.ACTION_VIEW);
    	            appIntent.setClass(context, WebAppActivity.class);
    	            appIntent.putExtra(Musubi.EXTRA_FEED_URI, obj.getContainingFeed().getUri());
    	            appIntent.putExtra(WebAppActivity.EXTRA_APP_NAME, getAppName(obj));
    	            appIntent.putExtra(WebAppActivity.EXTRA_APP_URI, Uri.parse(obj.getJson().optString(OBJ_URL)));
    	            appIntent.putExtra(WebAppActivity.EXTRA_APP_ID, getAppId(obj));
    	            context.startActivity(appIntent);
    	        }
    	    };
    	}
    	return mRunListener;
    }

    String getAppName(Obj obj) {
        Uri appUri = Uri.parse(obj.getJson().optString(OBJ_URL));
        String given = appUri.getQueryParameter("n");
        if (given == null) {
            given = appUri.getLastPathSegment();
            if (given == null || given.length() <= 1 || given.startsWith("index")) {
                List<String> segs = appUri.getPathSegments();
                if (segs.size() > 1) {
                    given = segs.get(segs.size() - 2);
                } else {
                    given = appUri.getAuthority();
                }
            }
        }
        return given;
    }

    String getAppId(Obj obj) {
        Uri appUri = Uri.parse(obj.getJson().optString(OBJ_URL));
        return appUri.getAuthority() + "/" + getAppName(obj);
    }

    static class AddWebAppDialog extends DialogFragment {
        public static AddWebAppDialog newInstance(long objId, String appId, String name, Uri uri) {
            Bundle b = new Bundle();
            b.putString("name", name);
            b.putParcelable("uri", uri);
            b.putString("appid", appId);
            b.putLong("objId", objId);
            AddWebAppDialog d = new AddWebAppDialog();
            d.setArguments(b);
            return d;
        }

        public AddWebAppDialog() {
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Uri appUri = getArguments().getParcelable("uri");
            final String name = getArguments().getString("name");
            final String appId = getArguments().getString("appid");
            final long objId = getArguments().getLong("objId");

            return new AlertDialog.Builder(getActivity()).setTitle("Add to app list?")
                    .setMessage("Add " + name + " to your list of applications?")
                    .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        	SQLiteOpenHelper databaseSource = App.getDatabaseSource(getActivity());
                            AppManager am = new AppManager(databaseSource);
                            SQLiteDatabase db = databaseSource.getWritableDatabase();
                            db.beginTransaction();
                            MApp app = null;
                            try {
	                            MApp lookup = am.lookupAppByAppId(appId);
	                            //could be re-adding
	                            //could have had this app discovered p2p
	                            if (lookup != null && lookup.deleted_ == false && lookup.webAppUrl_ != null) {
	                                Toast.makeText(getActivity(), "App already installed.",
	                                        Toast.LENGTH_SHORT).show();
	                                return;
	                            }
	
	                            app = lookup;
	                            if(app == null)
	                            	app = new MApp();
	                            app.appId_ = appId;
	                            app.name_ = name;
	                            app.webAppUrl_ = appUri.toString();
	                            app.deleted_ = false;
	
	                            if(lookup != null) {
	                            	am.updateApp(app);
	                            } else {
	                            	am.insertApp(app);
	                            }
	                            db.setTransactionSuccessful();
	                            
                            } finally {
                            	db.endTransaction();
                            }
                            if (app == null || app.id_ == -1) {
                                Toast.makeText(getActivity(), "Error installing webapp.",
                                        Toast.LENGTH_SHORT).show();
                            } else {
                            	//load the app manifest
                            	//TODO: just load this one...
                            	getActivity().getContentResolver().notifyChange(MusubiService.UPDATE_APP_MANIFESTS, null);
                                Toast.makeText(getActivity(),
                                        "Installed " + name + " to pin menu.", Toast.LENGTH_SHORT)
                                        .show();
                                getActivity().getContentResolver().notifyChange(
                                        MusubiContentProvider.uriForItem(Provided.OBJS_ID, objId), null);
                            }
                        }
                    }).setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // no harm no foul
                        }
                    }).create();
        }
    }

	@Override
	public void getSummaryText(Context context, TextView view, FeedSummary summary) {
		JSONObject obj = summary.getJson();
		view.setTypeface(null, Typeface.ITALIC);
		view.setText(summary.getSender() 
				  	 + " posted a new web app: " 
					 + obj.optString(OBJ_URL));
	}
}
