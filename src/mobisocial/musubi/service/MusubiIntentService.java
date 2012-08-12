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

import mobisocial.musubi.App;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MObject;
import mobisocial.musubi.model.helpers.EncodedMessageManager;
import mobisocial.musubi.ui.SettingsActivity;
import mobisocial.musubi.ui.fragments.SettingsFragment;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public class MusubiIntentService extends IntentService {
	static final String TAG = "MusubiIntentService";

	static final String ACTION_FACEBOOK_REFRESH = "facebook-refresh";
	static final String ACTION_AUTH_TOKEN_REFRESH = "auth-token-refresh";
	static final String ACTION_ROLLING_DELETE = "rolling-delete";

	public MusubiIntentService() {
		super("MusubiIntentService");
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		String action = intent.getAction();
		Log.d(TAG, "Inside onHandleIntent " + intent);
		if (action == null) {
			Log.e(TAG, "Intent service called with no action");
			return;
		}

		if (action.equals(ACTION_FACEBOOK_REFRESH)) {
			// TODO Get rid of the FacebookUpdateHandler HandlerThread
			getContentResolver().notifyChange(MusubiService.FACEBOOK_FRIEND_REFRESH, null);
			return;
		}

		if (action.equals(ACTION_AUTH_TOKEN_REFRESH)) {
			// TODO Get rid of the auth token refresh HandlerThread
			getContentResolver().notifyChange(MusubiService.AUTH_TOKEN_REFRESH, null);
			return;
		}
		
		if (action.equals(ACTION_ROLLING_DELETE)) {
			doRollingDelete(this);
			return;
		}
	}

	public static void doRollingDelete(Context context) {
		SharedPreferences settings = context.getSharedPreferences(SettingsActivity.PREFS_NAME, 0);
		int sinceDays = settings.getInt(SettingsFragment.PREF_ROLLING_DELETE, SettingsFragment.INVALID_HISTORY);
		if (sinceDays != SettingsFragment.INVALID_HISTORY) {
			Log.w(TAG, SettingsFragment.PREF_ROLLING_DELETE + " : deleting " + sinceDays + " days");

			SQLiteDatabase db = App.getDatabaseSource(context.getApplicationContext())
					.getWritableDatabase();
			long sinceMillis = System.currentTimeMillis() - (sinceDays * 1000 * 60 * 60 * 24);

			// delete objects if before timestamp
			String table = MObject.TABLE;
			String whereClause = MObject.COL_LAST_MODIFIED_TIMESTAMP + " > " + sinceMillis;
			String[] whereArgs = null;
			db.delete(table, whereClause, whereArgs);
			
			// delete latest feed if before timestamp
			table = MFeed.TABLE;
			whereClause = MFeed.COL_LATEST_RENDERABLE_OBJ_TIME + " > " + sinceMillis;
			whereArgs = null;
			db.delete(table, whereClause, whereArgs);

			// delete encoded
			final EncodedMessageManager emm = new EncodedMessageManager(db);
			emm.deleteProcessedOldItems(sinceDays);
		}
	}
}
