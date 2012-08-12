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

package mobisocial.musubi.service;


import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mobisocial.musubi.App;
import mobisocial.musubi.model.MApp;
import mobisocial.musubi.model.helpers.AppManager;
import mobisocial.musubi.util.Util;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.javatuples.Pair;
import org.json.JSONArray;
import org.json.JSONObject;
import org.mobisocial.corral.ContentCorral;

import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;

public class AppUpdaterService extends Service {
	public static boolean DBG = false;
    public static final String TAG = AppUpdaterService.class.getName();

    HandlerThread mThread;
    AppManager mAppManager;
    SQLiteOpenHelper mDatabaseSource;
    Handler mUpdateHandler;
        
    public AppUpdaterService() { 
    	super(); 
    }
    
	class UpdateApps extends ContentObserver {
    	final Handler mHandler;
		public UpdateApps(Handler handler) {
			super(handler);
			mHandler = handler;
		}
		@Override
		public void onChange(boolean selfChange) {
			DefaultHttpClient hc = new DefaultHttpClient();
			SQLiteDatabase db = mDatabaseSource.getWritableDatabase();
			long[] apps = mAppManager.listApps();
			for(long app_id : apps) {
				try {
					MApp app = mAppManager.lookupApp(app_id);
					//just in case someone deletes it
					if(app == null) {
						continue;
					}
					//no manifests for native apps for now
					if(app.webAppUrl_ == null)
						continue;
					
					String name = null;
					String web_url = null;
					List<Pair<String, String>> type_action_list = new LinkedList<Pair<String,String>>();

					
					URL url = new URL(app.webAppUrl_);
					URL[] possible_urls = null;
					if(url.getQuery() == null) {
						possible_urls = new URL[2];
						//try appending /config.json
						String no_slash = app.webAppUrl_;
						if(no_slash.charAt(no_slash.length() - 1) == '/')
							no_slash = no_slash.substring(0, no_slash.length() - 1);
						possible_urls[0] = new URL(no_slash + "/config.json");
						//then try trimming
						String parent = app.webAppUrl_.substring(0, app.webAppUrl_.lastIndexOf('/'));
						possible_urls[1] = new URL(parent + "/config.json");
					} else {
						possible_urls = new URL[1];
						//try triming 
						String parent = app.webAppUrl_.substring(0, app.webAppUrl_.lastIndexOf('/'));
						possible_urls[0] = new URL(parent + "/config.json");
					}
					
					for(URL config_url : possible_urls) {
						HttpResponse res;
						try {
							HttpGet hg =  new HttpGet(config_url.toString());
							res = hc.execute(hg);
							if(res == null) {
								throw new Exception("HTTP no result");
							}
							StatusLine sl = res.getStatusLine();
							if(sl == null) {
								throw new Exception("HTTP never completed");
							} else if(sl.getStatusCode() < 200 || sl.getStatusCode() >= 400) {
								String body = "<no response>";
								try {
									HttpEntity he = res.getEntity();
									if(he != null) {
										InputStream in = he.getContent();
										if(in != null) {
											body = IOUtils.toString(in);
										}
									}
								} catch(IOException e) {}
								throw new Exception("HTTP returned " + sl.toString() + ":\n" + body);
							}
							HttpEntity he = res.getEntity();
							String manifest_string = IOUtils.toString(he.getContent());
							JSONObject manifest = new JSONObject(manifest_string);
							name = manifest.optString("name");
							if(name.equals(""))
								name = null;
							web_url = manifest.optString("web_url");
							if(web_url.equals(""))
								web_url = null;
							JSONObject obj_actions = manifest.optJSONObject("obj_actions");
							if(obj_actions != null) {
								for(Iterator type_it = obj_actions.keys(); type_it.hasNext();) {
									String type = (String)type_it.next();
									JSONArray array = obj_actions.getJSONArray(type);
									for(int i = 0; i < array.length(); ++i) {
										type_action_list.add(Pair.with(type, array.getString(i)));
									}
								}
							}
							//if we already fetched it, don't try the alternative urls
							if (DBG) Log.i(TAG, "fetch config json file from for app " + app_id);
							break;
						} catch (Exception e) {
							//TODO: reschedule fetch sometime?
							if (DBG) Log.e(TAG, "unable to fetch config json file from for app " + app_id, e);
							continue;
						}
					}

					if (web_url != null) {
						app.webAppUrl_ = web_url;
						ContentCorral.cacheWebApp(Uri.parse(web_url));
					}

					long startTime = System.currentTimeMillis();
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
						db.beginTransactionNonExclusive();
					} else {
						db.beginTransaction();
					}
					try {
						//double check the delete condition
						app = mAppManager.lookupApp(app_id);
						if(app == null) {
							continue;
						}
						mAppManager.deleteAppActionWithForApp(app);
						if(name != null)
							app.name_ = name;
						if(web_url != null) {
							Log.w(TAG, "hmm, trying to change app url... from " + app.webAppUrl_ + " to " + web_url);
							app.webAppUrl_ = web_url;
						}
						//TODO: other info? like icon... would have had to be fetched above outside this body
						mAppManager.updateApp(app);
						
						for(Pair<String, String> type_action : type_action_list) {
							String type = type_action.getValue0();
							String action = type_action.getValue1();
							mAppManager.insertAppAction(app, type, action);
						}
						
						db.setTransactionSuccessful();
					} finally {
						db.endTransaction();
					}
					long totalTime = System.currentTimeMillis() - startTime;
					Log.d(TAG, "++++ AppManifest transaction took " + totalTime + "ms.");
				} catch(Throwable t) {
					Log.e(TAG, "failed to update app " + app_id, t);
				}
			}
		}
	}

    @Override
    public void onCreate() {
		mDatabaseSource = App.getDatabaseSource(this);
		mThread = new HandlerThread("AppManifests");
		mThread.setPriority(Thread.MIN_PRIORITY);
		mThread.start();
		mAppManager = new AppManager(mDatabaseSource);
		mUpdateHandler = new Handler(mThread.getLooper());
		ContentResolver resolver = getContentResolver();
        resolver.registerContentObserver(MusubiService.UPDATE_APP_MANIFESTS, false, 
        		new UpdateApps(mUpdateHandler));
        
        //kick it off once per boot
        resolver.notifyChange(MusubiService.UPDATE_APP_MANIFESTS, null);
        Log.w(TAG, "service is now running");
    }
	
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Received start id " + startId + ": " + intent);
        return START_STICKY;
    }


    @Override
    public void onDestroy() {
    	//kick the background thread into shutdown mode
    	mUpdateHandler.post(new Runnable() {
			@Override
			public void run() {
    			mThread.getLooper().quit();
			}
		});
    	//wait for it to clean up
    	try {
    		mThread.join();
		} catch (InterruptedException e) {}
    }
	
    public class AppUpdateServiceBinder extends Binder {
    	public AppUpdaterService getService() {
            return AppUpdaterService.this;
        }
    }
    private final IBinder mBinder = new AppUpdateServiceBinder();
	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}
}
