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

package mobisocial.musubi.model.helpers;

import mobisocial.musubi.App;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Access a collection of database access managers. All managers are
 * lazy-loaded and stored in an instance of this class.
 */
public class DatabaseManager {
	private final SQLiteOpenHelper mSqliteOpenHelper;

	private IdentitiesManager mIdentitiesManager;
	private EncodedMessageManager mEncodedMessageManager;
	private AppManager mAppManager;
	private ObjectManager mObjectManager;
	private FeedManager mFeedManager;
	private MyAccountManager mMyAccountManager;
	private DeviceManager mDeviceManager;
	private ContactDataVersionManager mContactDataVersionManager;

	public DatabaseManager(Context context) {
		mSqliteOpenHelper = App.getDatabaseSource(context);
	}

	public DatabaseManager(SQLiteOpenHelper dbHelper) {
		mSqliteOpenHelper = dbHelper;
	}

	public IdentitiesManager getIdentitiesManager() {
		if (mIdentitiesManager == null) {
			mIdentitiesManager = new IdentitiesManager(mSqliteOpenHelper);
		}
		return mIdentitiesManager;
	}

	public EncodedMessageManager getEncodedMessageManager() {
		if (mEncodedMessageManager == null) {
			mEncodedMessageManager = new EncodedMessageManager(mSqliteOpenHelper);
		}
		return mEncodedMessageManager;
	}

	public AppManager getAppManager() {
		if (mAppManager == null) {
			mAppManager = new AppManager(mSqliteOpenHelper);
		}
		return mAppManager;
	}

	public ObjectManager getObjectManager() {
		if (mObjectManager == null) {
			mObjectManager = new ObjectManager(mSqliteOpenHelper);
		}
		return mObjectManager;
	}

	public FeedManager getFeedManager() {
		if (mFeedManager == null) {
			mFeedManager = new FeedManager(mSqliteOpenHelper);
		}
		return mFeedManager;
	}

	public MyAccountManager getMyAccountManager() {
		if (mMyAccountManager == null) {
			mMyAccountManager = new MyAccountManager(mSqliteOpenHelper);
		}
		return mMyAccountManager;
	}

	public DeviceManager getDeviceManager() {
		if (mDeviceManager == null) {
			mDeviceManager = new DeviceManager(mSqliteOpenHelper);
		}
		return mDeviceManager;
	}

	public ContactDataVersionManager getContactDataVersionManager() {
		if (mContactDataVersionManager == null) {
			mContactDataVersionManager = new ContactDataVersionManager(mSqliteOpenHelper);
		}
		return mContactDataVersionManager;
	}

	public SQLiteDatabase getDatabase() {
		return mSqliteOpenHelper.getWritableDatabase();
	}

	public synchronized void close() {
		if (mIdentitiesManager != null) {
			mIdentitiesManager.close();
			mIdentitiesManager = null;
		}
		if (mEncodedMessageManager != null) {
			mEncodedMessageManager.close();
			mEncodedMessageManager = null;
		}
		if (mAppManager != null) {
			mAppManager.close();
			mAppManager = null;
		}
		if (mObjectManager != null) {
			mObjectManager.close();
			mObjectManager = null;
		}
		if (mFeedManager != null) {
			mFeedManager.close();
			mFeedManager = null;
		}
		if (mMyAccountManager != null) {
			mMyAccountManager.close();
			mMyAccountManager = null;
		}
		if (mDeviceManager != null) {
			mDeviceManager.close();
			mDeviceManager = null;
		}
		if (mContactDataVersionManager != null) {
			mContactDataVersionManager.close();
			mContactDataVersionManager = null;
		}
	}
}
