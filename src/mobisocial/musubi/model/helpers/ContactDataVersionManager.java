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

import mobisocial.musubi.model.MContactDataVersion;
import mobisocial.musubi.model.MSyncState;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;

/**
 * manages the state that tracks which email addresses, etc. have
 * been modified in the address book.  also deals with the sync state 
 * table which tracks things like the max contact id seen, etc
 */
public class ContactDataVersionManager extends ManagerBase {
    public ContactDataVersionManager(SQLiteOpenHelper databaseSource) {
        super(databaseSource);
    }
    
    // for address book update handler
    private SQLiteStatement mGetVersionStatement;
    private SQLiteStatement mSetVersionStatement;
	private SQLiteStatement mGetMaxContactIdStatement;
	private SQLiteStatement mGetMaxDataIdStatement;
	private SQLiteStatement mSetMaxDataIdSeenStatement;
	private SQLiteStatement mSetMaxContactIdSeenStatement;
	
	// for facebook update handler
	private SQLiteStatement mGetLastFacebookUpdateTime;
	private SQLiteStatement mSetLastFacebookUpdateTime;
	
	/**
	 * @return the latest updated profile received from facebook or -1
	 */
	public long getLastFacebookUpdateTime() {
		SQLiteDatabase db = initializeDatabase();
		if(mGetLastFacebookUpdateTime == null) {
			synchronized(this) {
				if(mGetLastFacebookUpdateTime == null) {
					mGetLastFacebookUpdateTime = db.compileStatement(
						"SELECT " + MSyncState.COL_LAST_FACEBOOK_UPDATE_TIME + 
						" FROM " + MSyncState.TABLE
					);
				}
			}
		}
		synchronized (mGetLastFacebookUpdateTime) {
			try {
				return mGetLastFacebookUpdateTime.simpleQueryForLong();
			} catch(SQLiteDoneException e) {
				return -1;
			}
		}
	}
	 /**
     * set the latest facebook profile update time
     * 
     * @param time
     */
	public void setLastFacebookUpdateTime(long time) {
		SQLiteDatabase db = initializeDatabase();
		if(mSetLastFacebookUpdateTime == null) {
			synchronized(this) {
				if(mSetLastFacebookUpdateTime == null) {
					mSetLastFacebookUpdateTime = db.compileStatement(
						"UPDATE " + MSyncState.TABLE + 
						" SET " + MSyncState.COL_LAST_FACEBOOK_UPDATE_TIME + "=?"
					);
				}
			}
		}
		synchronized (mSetLastFacebookUpdateTime) {
			mSetLastFacebookUpdateTime.bindLong(1, time);
			mSetLastFacebookUpdateTime.execute();
		}
    }

	
    /**
     * get the version of the last raw contact data item that
     * was incorporated into musubi's address book
     * @param rawDataId
     * @return -1 version last synced
     */
	public long getVersion(long rawDataId) {
		SQLiteDatabase db = initializeDatabase();
		if(mGetVersionStatement == null) {
			synchronized(this) {
				if(mGetVersionStatement == null) {
					mGetVersionStatement = db.compileStatement(
						"SELECT " + MContactDataVersion.COL_VERSION + 
						" FROM " + MContactDataVersion.TABLE + 
						" WHERE " + MContactDataVersion.COL_RAW_DATA_ID + "=?"
					);
				}
			}
		}
		synchronized (mGetVersionStatement) {
			try {
				mGetVersionStatement.bindLong(1, rawDataId);
				return mGetVersionStatement.simpleQueryForLong();
			} catch(SQLiteDoneException e) {
				return -1;
			}
		}
    }
	/**
	 * @return the highest id seen in the sync process or -1
	 */
    public long getMaxContactIdSeen() {
		SQLiteDatabase db = initializeDatabase();
		if(mGetMaxContactIdStatement == null) {
			synchronized(this) {
				if(mGetMaxContactIdStatement == null) {
					mGetMaxContactIdStatement = db.compileStatement(
						"SELECT " + MSyncState.COL_MAX_CONTACT + 
						" FROM " + MSyncState.TABLE
					);
				}
			}
		}
		synchronized (mGetMaxContactIdStatement) {
			try {
				return mGetMaxContactIdStatement.simpleQueryForLong();
			} catch(SQLiteDoneException e) {
				return -1;
			}
		}
    }
	/**
	 * @return the highest id seen in the sync process or -1
	 */
	public long getMaxDataIdSeen() {
		SQLiteDatabase db = initializeDatabase();
		if(mGetMaxDataIdStatement == null) {
			synchronized(this) {
				if(mGetMaxDataIdStatement == null) {
					mGetMaxDataIdStatement = db.compileStatement(
							"SELECT " + MSyncState.COL_MAX_DATA + 
							" FROM " + MSyncState.TABLE
					);
				}
			}
		}
		synchronized (mGetMaxDataIdStatement) {
			try {
				return mGetMaxDataIdStatement.simpleQueryForLong();
			} catch(SQLiteDoneException e) {
				return -1;
			}
		}
    }
	/**
	 * Set the version of a data item that has already been 
	 * applied to Musubi's contact list
	 * @param rawDataId
	 * @param version
	 */
    public void setVersion(long rawDataId, long version) {
		SQLiteDatabase db = initializeDatabase();
		if(mSetVersionStatement == null) {
			synchronized(this) {
				if(mSetVersionStatement == null) {
					mSetVersionStatement = db.compileStatement(
						"INSERT OR REPLACE INTO " + MContactDataVersion.TABLE + 
						" (" + MContactDataVersion.COL_RAW_DATA_ID + "," + MContactDataVersion.COL_VERSION + ")" +
						" VALUES (?, ?)"
					);
				}
			}
		}
		synchronized (mSetVersionStatement) {
			mSetVersionStatement.bindLong(1, rawDataId);
			mSetVersionStatement.bindLong(2, version);
			mSetVersionStatement.execute();
		}
    }
	/**
	 * Set the highest contact id that has been seen for addr book sync
	 */
    public void setMaxContactIdSeen(long max_id) {
		SQLiteDatabase db = initializeDatabase();
		if(mSetMaxContactIdSeenStatement == null) {
			synchronized(this) {
				if(mSetMaxContactIdSeenStatement == null) {
					mSetMaxContactIdSeenStatement = db.compileStatement(
						"UPDATE " + MSyncState.TABLE + 
						" SET " + MSyncState.COL_MAX_CONTACT + "=?"
					);
				}
			}
		}
		synchronized (mSetMaxContactIdSeenStatement) {
			mSetMaxContactIdSeenStatement.bindLong(1, max_id);
			mSetMaxContactIdSeenStatement.execute();
		}
    }
	/**
	 * Set the highest data id that has been seen for addr book sync
	 */
    public void setMaxDataIdSeen(long max_id) {
		SQLiteDatabase db = initializeDatabase();
		if(mSetMaxDataIdSeenStatement == null) {
			synchronized(this) {
				if(mSetMaxDataIdSeenStatement == null) {
					mSetMaxDataIdSeenStatement = db.compileStatement(
						"UPDATE " + MSyncState.TABLE + 
						" SET " + MSyncState.COL_MAX_DATA + "=?"
					);
				}
			}
		}
		synchronized (mSetMaxDataIdSeenStatement) {
			mSetMaxDataIdSeenStatement.bindLong(1, max_id);
			mSetMaxDataIdSeenStatement.execute();
		}
    }

    @Override
    public synchronized void close() {
    	if (mGetVersionStatement != null) {
    		mGetVersionStatement.close();
    		mGetVersionStatement = null;
    	}
    	if (mSetVersionStatement != null) {
    		mSetVersionStatement.close();
    		mSetVersionStatement = null;
    	}
    	if (mGetMaxContactIdStatement != null) {
    		mGetMaxContactIdStatement.close();
    		mGetMaxContactIdStatement = null;
    	}
    	if (mGetMaxDataIdStatement != null) {
    		mGetMaxDataIdStatement.close();
    		mGetMaxDataIdStatement = null;
    	}
    	if (mSetMaxDataIdSeenStatement != null) {
    		mSetMaxDataIdSeenStatement.close();
    		mSetMaxDataIdSeenStatement = null;
    	}
    	if (mSetMaxContactIdSeenStatement != null) {
    		mSetMaxContactIdSeenStatement.close();
    		mSetMaxContactIdSeenStatement = null;
    	}
    	if (mGetLastFacebookUpdateTime != null) {
    		mGetLastFacebookUpdateTime.close();
    		mGetLastFacebookUpdateTime = null;
    	}
    	if (mSetLastFacebookUpdateTime != null) {
    		mSetLastFacebookUpdateTime.close();
    		mSetLastFacebookUpdateTime = null;
    	}
    }
}
