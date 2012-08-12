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

import gnu.trove.list.linked.TLongLinkedList;

import java.util.LinkedList;
import java.util.List;

import mobisocial.musubi.model.MApp;
import mobisocial.musubi.model.MAppAction;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;

public class AppManager extends ManagerBase {
    SQLiteStatement mSqlGetId;
    SQLiteStatement mSqlGetAppId;
    SQLiteStatement mSqlInsertApp;
    SQLiteStatement mSqlUpdateApp;
	SQLiteStatement mSqlInsertAppAction;
    String mSqlLookupAppByAppId;

    static final String[] STANDARD_FIELDS = new String[] { 
        MApp.COL_ID, MApp.COL_APP_ID, MApp.COL_NAME, MApp.COL_ANDROID_PACKAGE, MApp.COL_WEB_APP_URL, MApp.COL_DELETED
    };

    final static int id = 0;
    final static int appId = 1;
    final static int name = 2;
    final static int androidPackage = 3;
    final static int webAppUrl = 4;
    final static int deleted = 5;

    public AppManager(SQLiteDatabase db) {
        super(db);
    }

	public AppManager(SQLiteOpenHelper databaseSource) {
        super(databaseSource);
    }

    public String getAppIdentifier(long id) {
        SQLiteDatabase db = initializeDatabase();
		if(mSqlGetAppId == null) {
			synchronized(this) {
				if(mSqlGetAppId == null) {
					mSqlGetAppId = db.compileStatement(
						"SELECT " + MApp.COL_APP_ID + 
						" FROM " + MApp.TABLE + 
						" WHERE " + MApp.COL_ID + "=?"
					);
				}
			}
		}
		synchronized (mSqlGetAppId) {
			try {
				mSqlGetAppId.bindLong(1, id);
				return mSqlGetAppId.simpleQueryForString();
			} catch(SQLiteDoneException e) {
				return null;
			}
		}
    }

    /**
     * Ensures that an app record exists for the given app identifier,
     * creating one if necessary.
     */
    public MApp ensureApp(String appIdentifier) {
        SQLiteDatabase db = initializeDatabase();
		if(mSqlGetId == null) {
			synchronized(this) {
				if(mSqlGetId == null) {
					mSqlGetId = db.compileStatement(
						"SELECT " + MApp.COL_ID + 
						" FROM " + MApp.TABLE + 
						" WHERE " + MApp.COL_APP_ID + "=?"
					);
				}
			}
		}
		synchronized (mSqlGetId) {
			try {
				mSqlGetId.bindString(1, appIdentifier);
				long id = mSqlGetId.simpleQueryForLong();
				MApp app = new MApp();
				app.id_ = id;
				app.appId_ = appIdentifier;
				return app;
			} catch(SQLiteDoneException e) {
				//must insert
			}
		}
    	
        ContentValues values = new ContentValues();
        values.put(MApp.COL_APP_ID, appIdentifier);
        long rowId = db.insert(MApp.TABLE, null, values);

        MApp app = new MApp();
        app.id_ = rowId;
        app.appId_ = appIdentifier;
        return app;
    }

    public void insertApp(MApp app) {
        SQLiteDatabase db = initializeDatabase();
        if (mSqlInsertApp == null) {
            synchronized (this) {
                StringBuilder sql = new StringBuilder(100)
                    .append(" INSERT INTO ").append(MApp.TABLE).append("(")
                    .append(MApp.COL_APP_ID).append(",")
                    .append(MApp.COL_NAME).append(",")
                    .append(MApp.COL_ANDROID_PACKAGE).append(",")
                    .append(MApp.COL_WEB_APP_URL).append(",")
                    .append(MApp.COL_DELETED)
                    .append(") VALUES (?,?,?,?,?)");
                mSqlInsertApp = db.compileStatement(sql.toString());
            }
        }

        synchronized (mSqlInsertApp) {
            bindField(mSqlInsertApp, appId, app.appId_);
            bindField(mSqlInsertApp, name, app.name_);
            bindField(mSqlInsertApp, androidPackage, app.androidPackage_);
            bindField(mSqlInsertApp, webAppUrl, app.webAppUrl_);
            bindField(mSqlInsertApp, deleted, app.deleted_  ? 1 : 0);
            app.id_ = mSqlInsertApp.executeInsert();
        }
    }

    public void updateApp(MApp app) {
        SQLiteDatabase db = initializeDatabase();
        if (mSqlUpdateApp == null) {
            synchronized (this) {
                if (mSqlUpdateApp == null) {
                    StringBuilder sql = new StringBuilder("UPDATE ").append(MApp.TABLE)
                        .append(" SET ")
                        .append(MApp.COL_APP_ID).append("=?,")
                        .append(MApp.COL_NAME).append("=?,")
                        .append(MApp.COL_ANDROID_PACKAGE).append("=?,")
                        .append(MApp.COL_WEB_APP_URL).append("=?,")
                        .append(MApp.COL_DELETED).append("=?")
                        .append(" WHERE ").append(MApp.COL_ID).append("=?");
                    mSqlUpdateApp = db.compileStatement(sql.toString());
                }
            }
        }

        synchronized (mSqlUpdateApp) {
            bindField(mSqlUpdateApp, appId, app.appId_);
            bindField(mSqlUpdateApp, name, app.name_);
            bindField(mSqlUpdateApp, androidPackage, app.androidPackage_);
            bindField(mSqlUpdateApp, webAppUrl, app.webAppUrl_);
            bindField(mSqlUpdateApp, deleted, app.deleted_ ? 1 : 0);
            bindField(mSqlUpdateApp, 6, app.id_);
            mSqlUpdateApp.execute();
        }
    }

    /**
     * Returns a fully-populated MApp object for the given id, or
     * null if no such app.
     */
    public MApp lookupApp(long appId) {
        SQLiteDatabase db = initializeDatabase();
        String table = MApp.TABLE;
        String selection = MApp.COL_ID + "=?";
        String[] selectionArgs = new String[] { Long.toString(appId) };
        String groupBy = null, having = null, orderBy = null;
        Cursor c = db.query(table, STANDARD_FIELDS, selection, selectionArgs, groupBy, having, orderBy);
        try {
            if (c.moveToFirst()) {
                // Existing app
                MApp app = new MApp();
                app.id_ = appId;

                if (!c.isNull(AppManager.appId)) {
                    app.appId_ = c.getString(AppManager.appId);
                }
                if (!c.isNull(name)) {
                    app.name_ = c.getString(name);
                }
                if (!c.isNull(androidPackage)) {
                    app.androidPackage_ = c.getString(androidPackage);
                }
                if (!c.isNull(webAppUrl)) {
                    app.webAppUrl_ = c.getString(webAppUrl);
                }
                app.deleted_ = c.getInt(deleted) != 0;
                return app;
            } else {
                return null;
            }
        } finally {
            c.close();
        }
    }

    public MApp lookupAppByAppId(String appId) {
        SQLiteDatabase db = initializeDatabase();
        if (mSqlLookupAppByAppId == null) {
        	StringBuilder sql = new StringBuilder(80).append("SELECT ");
        	for (String f : STANDARD_FIELDS) {
        		sql.append(f).append(",");
        	}
        	sql.setLength(sql.length()-1);
        	sql.append(" FROM ").append(MApp.TABLE)
        		.append(" WHERE ").append(MApp.COL_APP_ID).append("=?");
        	mSqlLookupAppByAppId = sql.toString();
        }
        String[] selectionArgs = new String[] { appId };
        Cursor c = db.rawQuery(mSqlLookupAppByAppId, selectionArgs);
        try {
            if (c.moveToFirst()) {
                MApp app = new MApp();
                app.id_ = c.getLong(AppManager.id);

                if (!c.isNull(AppManager.appId)) {
                    app.appId_ = c.getString(AppManager.appId);
                }
                if (!c.isNull(name)) {
                    app.name_ = c.getString(name);
                }
                if (!c.isNull(androidPackage)) {
                    app.androidPackage_ = c.getString(androidPackage);
                }
                if (!c.isNull(webAppUrl)) {
                    app.webAppUrl_ = c.getString(webAppUrl);
                }
                app.deleted_ = c.getInt(deleted) != 0;
                return app;
            } else {
                return null;
            }
        } finally {
            c.close();
        }
    }

    /**
     * Returns the appId and id fields of an MApp if found in the database.
     */
    public MApp getAppBasics(long id) {
    	String appId = getAppIdentifier(id);
    	if(appId == null)
    		return null;
		MApp app = new MApp();
		app.id_ = id;
		app.appId_ = appId;
		return app;
    }

    /**
     * Returns true if a row was deleted.
     */
    public boolean deleteAppWithId(String appId) {
        SQLiteDatabase db = initializeDatabase();
        String whereClause = MApp.COL_APP_ID + "=?";
        String[] whereArgs = new String[] { appId };
        ContentValues cv = new ContentValues();
        cv.put(MApp.COL_DELETED, true);
        return db.update(MApp.TABLE, cv, whereClause, whereArgs) > 0;
    }

    //super simple interface for now, no updates.  in a transaaction delete
    //all the existing registered actions.  then add all of the newly discovered ones
	public MAppAction insertAppAction(MApp app, String type, String action) {
        SQLiteDatabase db = initializeDatabase();
        if (mSqlInsertAppAction == null) {
            synchronized (this) {
                StringBuilder sql = new StringBuilder(100)
                    .append(" INSERT INTO ").append(MAppAction.TABLE).append("(")
                    .append(MAppAction.COL_APP_ID).append(",")
                    .append(MAppAction.COL_OBJ_TYPE).append(",")
                    .append(MAppAction.COL_ACTION)
                    .append(") VALUES (?,?,?)");
                mSqlInsertAppAction = db.compileStatement(sql.toString());
            }
        }

        MAppAction app_action = new MAppAction();
        app_action.appId_ = app.id_;
        app_action.objType_ = type;
        app_action.action_ = action;
        synchronized (mSqlInsertAppAction) {
            bindField(mSqlInsertAppAction, 1, app_action.appId_);
            bindField(mSqlInsertAppAction, 2, app_action.objType_);
            bindField(mSqlInsertAppAction, 3, app_action.action_);
            app_action.id_ = mSqlInsertAppAction.executeInsert();
        }
        return app_action;
	}
    /**
     * Returns true if a row was deleted.
     */
    public boolean deleteAppActionWithForApp(MApp app) {
        SQLiteDatabase db = initializeDatabase();
        String whereClause = MAppAction.COL_APP_ID + "=?";
        String[] whereArgs = new String[] { String.valueOf(app.id_) };
        return db.delete(MAppAction.TABLE, whereClause, whereArgs) > 0;
    }
    public List<MAppAction> lookupActions(String objType) {
        SQLiteDatabase db = initializeDatabase();
        String[] columns = new String[] {
        		MAppAction.COL_ID,
        		MAppAction.COL_APP_ID,
        		MAppAction.COL_OBJ_TYPE,
        		MAppAction.COL_ACTION,
        };
        String whereClause = MAppAction.COL_OBJ_TYPE + "=? AND " + MApp.TABLE + "." + MApp.COL_DELETED +"=0";
        String[] whereArgs = new String[] { objType };
        Cursor c = db.query(MAppAction.TABLE, columns, whereClause, whereArgs, null, null, null);
        
        try {
	        LinkedList<MAppAction> actions = new LinkedList<MAppAction>();
	        while(c.moveToNext()) {
	        	MAppAction app_action = new MAppAction();
	        	app_action.id_ = c.getLong(0);
	        	app_action.appId_ = c.getLong(1);
	        	app_action.objType_ = c.getString(2);
	        	app_action.action_ = c.getString(3);
	        	actions.add(app_action);
	        }
	        return actions;
        } finally {
        	c.close();
        }
    }
    public List<MApp> lookupAppForAction(String objType, String action) {
        SQLiteDatabase db = initializeDatabase();
        String[] columns = new String[] {
    		MApp.TABLE + "." + MApp.COL_ID,
        };
        String whereClause = MAppAction.TABLE + "." + MAppAction.COL_OBJ_TYPE + "=? AND " + MAppAction.TABLE + "." + MAppAction.COL_ACTION + "=? AND " + MApp.TABLE + "." + MApp.COL_DELETED +"=0";
        String[] whereArgs = new String[] { objType, action };
        String orderBy = MApp.TABLE + "." + MApp.COL_NAME;
        String join = " JOIN " + MApp.TABLE + " ON " + MAppAction.TABLE + "." + MAppAction.COL_APP_ID + "=" + MApp.TABLE + "." + MApp.COL_ID;
        Cursor c = db.query(MAppAction.TABLE + join, columns, whereClause, whereArgs, null, null, orderBy);
        LinkedList<MApp> apps = new LinkedList<MApp>();
        try {
	        while(c.moveToNext()) {
	        	long app_id = c.getLong(0);
	        	apps.add(lookupApp(app_id));
	        }
	        return apps;
        } finally {
        	c.close();
        }
    }
    public long[] listApps() {
        SQLiteDatabase db = initializeDatabase();
        Cursor c = db.query(MApp.TABLE, new String[] {MApp.COL_ID}, MApp.COL_DELETED +"=0", null, null, null, null);
        try {
        	TLongLinkedList apps = new TLongLinkedList();
            while (c.moveToNext()) {
            	apps.add(c.getLong(0));
            }
            return apps.toArray();
        } finally {
            c.close();
        }
    }

    @Override
    public synchronized void close() {
    	if (mSqlGetId != null) {
    		mSqlGetId.close();
    		mSqlGetId = null;
    	}
    	if (mSqlGetAppId != null) {
    		mSqlGetAppId.close();
    		mSqlGetAppId = null;
    	}
    	if (mSqlInsertApp != null) {
    		mSqlInsertApp.close();
    		mSqlInsertApp = null;
    	}
    	if (mSqlUpdateApp != null) {
    		mSqlUpdateApp.close();
    		mSqlUpdateApp = null;
    	}
    	if (mSqlInsertAppAction != null) {
    		mSqlInsertAppAction.close();
    		mSqlInsertAppAction = null;
    	}
    }
}
