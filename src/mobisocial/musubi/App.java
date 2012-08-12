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

import mobisocial.metrics.MusubiExceptionHandler;
import mobisocial.metrics.UsageMetrics;
import mobisocial.metrics.UsageMetrics.ReportingLevel;
import mobisocial.musubi.model.helpers.DatabaseFile;
import mobisocial.musubi.model.helpers.DatabaseManager;
import mobisocial.musubi.model.helpers.FeedManager;
import mobisocial.musubi.objects.ProfileObj;
import mobisocial.musubi.provider.DBProvider;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.provider.MusubiContentProvider.Provided;
import mobisocial.musubi.provider.MusubiProvider;
import mobisocial.musubi.provider.TestSettingsProvider;
import mobisocial.musubi.provider.UICacheProvider;
import mobisocial.musubi.service.MusubiService;
import mobisocial.musubi.ui.MusubiBaseActivity;
import mobisocial.musubi.ui.SettingsActivity;
import mobisocial.musubi.util.IdentityCache;
import mobisocial.socialkit.Obj;
import mobisocial.socialkit.User;
import mobisocial.socialkit.musubi.Musubi;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

public class App extends Application implements DBProvider, MusubiProvider, UICacheProvider {
    /**
     * The protocol version we speak, affecting things like wire protocol
     * format and physical network support, available features, app api, etc.
     */
    public static final String PREF_POSI_VERSION = "posi_version";
    public static final int POSI_VERSION = 5;

    public final static String TAG = "musubi";
    private IdentityCache mContactCache;
    private ScreenState mScreenState;

    private static Uri sCurrentFeedUri;
    private Musubi mMusubi;
    private SQLiteOpenHelper mDatabaseSource;
    private DatabaseManager mDatabaseManager;
    private Object mDatabaseCreationLog;

    public static Obj clipboardObject;

	@Override
	public synchronized SQLiteOpenHelper getDatabaseSource() {
	    if (mDatabaseSource == null) {
	    	synchronized (this) {
				if (mDatabaseSource == null) {
					mDatabaseSource = new DatabaseFile(getApplicationContext());
					mDatabaseCreationLog = new Throwable();
				}
	    	}
        }
		return mDatabaseSource;
	}

	DatabaseManager getDatabaseManager() {
		if (mDatabaseManager == null) {
			mDatabaseManager = new DatabaseManager(getDatabaseSource());
		}
		return mDatabaseManager;
	}

	public static SQLiteOpenHelper getDatabaseSource(Context c) {
		Context app_as_context = c.getApplicationContext();
		if(app_as_context instanceof DBProvider) {
			return ((DBProvider)app_as_context).getDatabaseSource();
		} else {
			throw new RuntimeException("application or mock missing database source");
		}
	}

	public static UsageMetrics getUsageMetrics(Context c) {
	    UsageMetrics m;
	    if (MusubiBaseActivity.isDeveloperModeEnabled(c)) {
	        User u = getMusubi(c).userForLocalDevice(null);
	        m = new UsageMetrics(c, u);
        } else {
            m = new UsageMetrics(c);
        }

	    SharedPreferences p = c.getSharedPreferences(SettingsActivity.PREFS_NAME, 0);
	    if (!p.getBoolean(SettingsActivity.PREF_ANONYMOUS_STATS, true)) {
	        m.setReportingLevel(ReportingLevel.DISABLED);
	    }
	    
	    return m;
	}
	
	public static Musubi getMusubi(Context c) {
		Context app_as_context = c.getApplicationContext();
		if(app_as_context instanceof MusubiProvider) {
			return ((MusubiProvider)app_as_context).getMusubi();
		} else {
			throw new RuntimeException("application or mock missing musubi source");
		}
	}

	public static Uri getCurrentFeed() {
	    return sCurrentFeedUri;
	}

	public static void setCurrentFeed(Context context, Uri feedUri) {
	    sCurrentFeedUri = feedUri;
	    if (MusubiBaseActivity.DBG) {
	    	Log.d(TAG, "Setting current feed " + feedUri);
	    }
	    if (feedUri != null) {
	    	new UnreadMessageUpdateTask(context).execute(feedUri);
	    }
	}

	static class UnreadMessageUpdateTask extends AsyncTask<Uri, Void, Void> {
    	final DatabaseManager mDbManager;
    	final ContentResolver mResolver;

    	public UnreadMessageUpdateTask(Context context) {
    		Context appContext = context.getApplicationContext();
    		if (appContext instanceof App) {
    			mDbManager = ((App)appContext).getDatabaseManager();
    		} else {
    			throw new RuntimeException("UnreadMessageUpdateTask from non-App context");
    		}
			mResolver = context.getContentResolver();
		}

    	@Override
    	protected Void doInBackground(Uri... params) {
    		// Strongly correlated with UI activity. Delay a bit with hopes of
    		// letting UI-driven db queries go first.
    		/*try {
    			Thread.sleep(2000);
    		} catch (InterruptedException e) {}*/

    		FeedManager fm = mDbManager.getFeedManager();
    		for (Uri uri : params) {
    			long feedId;
    			try {
    				feedId = Long.parseLong(uri.getLastPathSegment());
    			} catch (NumberFormatException e) {
    				Log.w(TAG, "bad feed url " + uri);
    				continue;
    			}
    			if (fm.getUnreadMessageCount(feedId) > 0) {
    				if (fm.resetUnreadMessageCount(uri)) {
        				mResolver.notifyChange(MusubiContentProvider.uriForDir(Provided.FEEDS), null);
                        mResolver.notifyChange(uri, null);
        			}
    			}
    		}
    		return null;
    	}

    	@Override
    	protected void onPostExecute(Void result) {
    		mDbManager.close();
    	}
    }

	public static IdentityCache getContactCache(Context c) {
		Context app_as_context = c.getApplicationContext();
		if(app_as_context instanceof UICacheProvider) {
			return ((UICacheProvider)app_as_context).getContactCache();
		} else {
			throw new RuntimeException("application or mock missing ui cache source");
		}
	}

	public static TestSettingsProvider.Settings getTestSettings(Context c) {
		Context app_as_context = c.getApplicationContext();
		if(app_as_context instanceof TestSettingsProvider) {
			Log.w(TAG, "using test settings from context");
			return ((TestSettingsProvider)app_as_context).getSettings();
		}
		return null;
	}

	public boolean isScreenOn(){
        return !mScreenState.isOff;
    }

	@Override
	public void onCreate() {
		super.onCreate();
		if (mDatabaseCreationLog != null) {
		    synchronized(this) {
		        Log.e(TAG, "Database created prior to App.onCreate(). This is a serious error.",
		                (Throwable)mDatabaseCreationLog);
		        mDatabaseCreationLog = new Object();
		    }
		}
		mMusubi = new Musubi(getApplicationContext());

		// Exception handler
		MusubiExceptionHandler.installHandler(getApplicationContext());

        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        mScreenState = new ScreenState();
        getApplicationContext().registerReceiver(mScreenState, filter);

        // Sync profile information.
        SharedPreferences prefs = getSharedPreferences("main", 0);
        int oldVersion = prefs.getInt(PREF_POSI_VERSION, 0);
        if (oldVersion < POSI_VERSION) {
            prefs.edit().putInt(PREF_POSI_VERSION, POSI_VERSION).commit();
            Obj updateObj = ProfileObj.getUserAttributesObj(this);
            Log.d(TAG, "Broadcasting new profile attributes: " + updateObj.getJson());
            getContentResolver().notifyChange(MusubiService.MY_PROFILE_UPDATED, null);
        }
	}

	@Override
	public void onTerminate() {
		super.onTerminate();
	}

    private class ScreenState extends BroadcastReceiver {
        public boolean isOff = false;
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                isOff = true;
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                isOff = false;
            }
        }

    }

    public Musubi getMusubi() {
        return mMusubi;
    }

	public IdentityCache getContactCache() {
		if (mContactCache == null) {
			mContactCache = new IdentityCache(this);
		}
		return mContactCache;
	}

	@Override
	public void onLowMemory() {
		Log.d(TAG, "++++ low system memory ++++");
		if (mContactCache != null) {
			mContactCache.evictAll();
		}
	}
}
