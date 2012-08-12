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
import java.io.File;

import mobisocial.crypto.IBIdentity;
import mobisocial.musubi.BootstrapActivity;
import mobisocial.musubi.model.DbContactAttributes;
import mobisocial.musubi.model.DbLikeCache;
import mobisocial.musubi.model.DbObjCache;
import mobisocial.musubi.model.DbRelation;
import mobisocial.musubi.model.MApp;
import mobisocial.musubi.model.MAppAction;
import mobisocial.musubi.model.MContactDataVersion;
import mobisocial.musubi.model.MDevice;
import mobisocial.musubi.model.MEncodedMessage;
import mobisocial.musubi.model.MEncryptionUserKey;
import mobisocial.musubi.model.MFact;
import mobisocial.musubi.model.MFactType;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MFeed.FeedType;
import mobisocial.musubi.model.MFeedApp;
import mobisocial.musubi.model.MFeedMember;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MIncomingSecret;
import mobisocial.musubi.model.MMissingMessage;
import mobisocial.musubi.model.MMyAccount;
import mobisocial.musubi.model.MMyDeviceName;
import mobisocial.musubi.model.MObject;
import mobisocial.musubi.model.MOutgoingSecret;
import mobisocial.musubi.model.MPendingIdentity;
import mobisocial.musubi.model.MPendingUpload;
import mobisocial.musubi.model.MSequenceNumber;
import mobisocial.musubi.model.MSignatureUserKey;
import mobisocial.musubi.model.MSyncState;
import mobisocial.musubi.model.SKFeedMembers;
import mobisocial.musubi.model.SKIdentities;
import mobisocial.musubi.model.SKObjects;
import mobisocial.musubi.service.WizardStepHandler;
import mobisocial.musubi.util.Util;

import org.mobisocial.corral.ContentCorral;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQuery;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

/**
 * Utility methods for managing the database.
 *
 */
public class DatabaseFile extends SQLiteOpenHelper {
	public static final String TAG = "DBHelper";

	public static final String DEFAULT_DATABASE_NAME = "MUSUBI.db";

	public static final int VERSION = 24;
	public static final int SIZE_LIMIT = 480 * 1024;
	private BootstrapActivity mBootstrapActivity = null;
	private static boolean sDowngradeAlertUp = false;
	private boolean mDatabaseInitialized;
	private Context mContext;

	public DatabaseFile(Context context) {
		this(context, handleRestore(context, DEFAULT_DATABASE_NAME));
		mContext = context;
	}

	public DatabaseFile(Context context, String dbName) {
		super(
		    context, 
		    handleRestore(context, dbName), 
		    new SQLiteDatabase.CursorFactory() {
		    	@Override
		    	public Cursor newCursor(
                    SQLiteDatabase db,
                    SQLiteCursorDriver masterQuery,
                    String editTable,
                    SQLiteQuery query) {
		    		return new SQLiteCursor(db, masterQuery, editTable, query);
		    	}
		    }, 
		    VERSION);
	}

	@Override
	public synchronized SQLiteDatabase getReadableDatabase() {
	    initializeDatabase();
	    return super.getReadableDatabase();
	}

	@Override
	public synchronized SQLiteDatabase getWritableDatabase() {
	    initializeDatabase();
	    return super.getWritableDatabase();
	}

	void initializeDatabase() {
	    if (!mDatabaseInitialized) {
	        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
	            super.getWritableDatabase().enableWriteAheadLogging();
	        }
	        mDatabaseInitialized = true;
	    }
	}

	public DatabaseFile(Context context, String dbName, SQLiteDatabase.CursorFactory factory) {
        super(context, handleRestore(context, dbName), factory, VERSION);
    }

	private static String handleRestore(Context context, String dbName) {
		if(dbName == null)
			return dbName;
		File db = context.getDatabasePath(dbName);
		File backup = new File(db.getPath() + ".torestore");
		if(!backup.exists()) {
			return dbName;
		}
		boolean ok = backup.renameTo(db);
		if(!ok) {
			Toast.makeText(context, "Could not restore backup", Toast.LENGTH_SHORT).show();
		}
		return dbName;
	}

	@Override
	public void onOpen(SQLiteDatabase db) {
		super.onOpen(db);
	}

	/**
	 * Called whenever a database handle is requested on an outdated database.
	 */
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
	    db.beginTransaction();
        try {
        	//Wait for ever, pop up a notification
        	if(newVersion < oldVersion) {
        		blockDowngrade(oldVersion, newVersion);
        	}
            doUpgrade(db, oldVersion, newVersion);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
	}

	private void blockDowngrade(int oldVersion, int newVersion) {
		Log.e(TAG, "downgrade version disallowed: " + oldVersion + " to " + newVersion);;
		for(;;) {
			//only the bootstrap loader will pass a context in.
			if(mBootstrapActivity != null && !sDowngradeAlertUp) {
				new Handler(mBootstrapActivity.getMainLooper()).post(new Runnable() {
					@Override
					public void run() {
						if(sDowngradeAlertUp)
							return;
						sDowngradeAlertUp = true;
			            new AlertDialog.Builder(mBootstrapActivity)
				            .setTitle("Upgrade Required")
				            .setMessage("You cannot downgrade to a lower version of Musubi.  Update to a newer version.")
				            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
				                public void onClick(DialogInterface dialog, int which) {
				                	//we'll jsut show it again
				                	sDowngradeAlertUp = false;
				                }
				            }).create().show();
					}
				});
			}
			try {
				Thread.sleep(10000);
			} catch (InterruptedException e) {}
		}
	}

	private void doUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);

        if (oldVersion <= 1) {
        	//This update requires that we insert a local whitelist and provisional white list
        	//account.  An account is essentially a feed associated with an identity
    		Cursor c = db.query(MIdentity.TABLE, null,
    			MIdentity.COL_OWNED + "=1",
    			null,
    			null, null, null
    		);
    		try {
    			while(c.moveToNext()) {
    				int identity_column = c.getColumnIndexOrThrow(MIdentity.COL_ID);				
    				long identityId = c.getLong(identity_column);
					//create the whitelist feed immediately.
			        ContentValues cv = new ContentValues();
			        cv.put(MFeed.COL_NAME, MFeed.LOCAL_WHITELIST_FEED_NAME);
			        cv.put(MFeed.COL_ACCEPTED, 0);
			        cv.put(MFeed.COL_TYPE, FeedType.ASYMMETRIC.ordinal());
			        cv.put(MFeed.COL_NUM_UNREAD, 0);
			        long feed_id = db.insert(MFeed.TABLE, null, cv);

			        cv.clear();
			        cv.put(MMyAccount.COL_ACCOUNT_NAME, MMyAccount.LOCAL_WHITELIST_ACCOUNT);
			        cv.put(MMyAccount.COL_ACCOUNT_TYPE, MMyAccount.INTERNAL_ACCOUNT_TYPE);
			        cv.put(MMyAccount.COL_FEED_ID, feed_id);
			        cv.put(MMyAccount.COL_IDENTITY_ID, identityId);
			        db.insert(MMyAccount.TABLE, null, cv);

			        cv.clear();
			        cv.put(MFeed.COL_NAME, MFeed.PROVISONAL_WHITELIST_FEED_NAME);
			        cv.put(MFeed.COL_ACCEPTED, 0);
			        cv.put(MFeed.COL_TYPE, FeedType.ASYMMETRIC.ordinal());
			        cv.put(MFeed.COL_NUM_UNREAD, 0);
			        feed_id = db.insert(MFeed.TABLE, null, cv);
			        
			        cv.clear();
			        cv.put(MMyAccount.COL_ACCOUNT_NAME, MMyAccount.LOCAL_WHITELIST_ACCOUNT);
			        cv.put(MMyAccount.COL_ACCOUNT_TYPE, MMyAccount.INTERNAL_ACCOUNT_TYPE);
			        cv.put(MMyAccount.COL_FEED_ID, feed_id);
			        cv.put(MMyAccount.COL_IDENTITY_ID, identityId);
			        db.insert(MMyAccount.TABLE, null, cv);
    				
    			}
    		} finally {
    			c.close();
    		}
    		
        }

        
        if (oldVersion <= 2) {
            db.execSQL("ALTER TABLE " + MSyncState.TABLE + " ADD COLUMN " + MSyncState.COL_LAST_FACEBOOK_UPDATE_TIME + " INTEGER NOT NULL DEFAULT 0");
        }

        if (oldVersion <= 3) {
            db.execSQL("DROP INDEX " + MEncodedMessage.TABLE + "_lookup");
            db.execSQL("DELETE FROM " + MEncodedMessage.TABLE);
            db.execSQL("CREATE INDEX " + MEncodedMessage.TABLE + "_lookup ON " + MEncodedMessage.TABLE + "(" +
            		MEncodedMessage.COL_SHORT_HASH + ")");
        }

        if (oldVersion <= 5) {
        	db.execSQL("DELETE FROM " + MObject.TABLE + " WHERE " + MObject.COL_TYPE + "='delete_obj'");
        }

        if (oldVersion <= 6) {
            createTable(db, MFeedApp.TABLE,
                    MFeedApp.COL_ID, "INTEGER PRIMARY KEY",
                    MFeedApp.COL_FEED_ID, "INTEGER NOT NULL",
                    MFeedApp.COL_APP_ID, "INTEGER NOT NULL");
            db.execSQL("CREATE UNIQUE INDEX " + MFeedApp.TABLE + "_lookup ON " + MFeedApp.TABLE + "(" + 
                    MFeedApp.COL_FEED_ID + "," + MFeedApp.COL_APP_ID +
                    ")");            
        }

        if (oldVersion <= 7) {
            db.execSQL("ALTER TABLE " + MEncodedMessage.TABLE + " ADD COLUMN " + MEncodedMessage.COL_PROCESSED_TIME + " INTEGER NOT NULL DEFAULT 0");
        }

        if (oldVersion <= 8) {
        	createTable(db, WizardStepHandler.TABLE,
        			WizardStepHandler.COL_ID, "INTEGER PRIMARY KEY",
                    WizardStepHandler.COL_CURRENT_STEP, "INTEGER NOT NULL");
        	
        	ContentValues cv = new ContentValues();
	        cv.put(WizardStepHandler.COL_ID, 0);
	        cv.put(WizardStepHandler.COL_CURRENT_STEP, 0);
	        db.insert(WizardStepHandler.TABLE, null, cv);
        }

        if (oldVersion <= 12) {
            createSocialKitViews(db);
        }

        if (oldVersion <= 13) {
            db.execSQL("ALTER TABLE " + MApp.TABLE + " ADD COLUMN " + MApp.COL_NAME + " TEXT");
            db.execSQL("ALTER TABLE " + MApp.TABLE + " ADD COLUMN " + MApp.COL_ANDROID_PACKAGE + " TEXT");
            db.execSQL("ALTER TABLE " + MApp.TABLE + " ADD COLUMN " + MApp.COL_WEB_APP_URL + " TEXT");
        }

        if (oldVersion <= 14) {
            installBundledApps(db);
        }

        if (oldVersion <= 15) {
            createTable(db, MAppAction.TABLE,
            		MAppAction.COL_ID, "INTEGER PRIMARY KEY",
            		MAppAction.COL_APP_ID, "INTEGER NOT NULL",
            		MAppAction.COL_OBJ_TYPE, "TEXT",
            		MAppAction.COL_ACTION, "TEXT");
            //query by app id needs to be fast
            db.execSQL("CREATE INDEX " + MAppAction.TABLE + "_lookup_by_app ON " + MAppAction.TABLE + "(" + 
            		MAppAction.COL_APP_ID +
            		")");
            //query for obj type, action needs to be fast
            db.execSQL("CREATE INDEX " + MAppAction.TABLE + "_lookup_by_type_action ON " + MAppAction.TABLE + "(" + 
            		MAppAction.COL_OBJ_TYPE + "," + MAppAction.COL_ACTION +
            		")");
        }
        if (oldVersion <= 16) {
            db.execSQL("ALTER TABLE " + MApp.TABLE + " ADD COLUMN " + MApp.COL_DELETED + " INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion <= 17) {
            createTable(db, MPendingIdentity.TABLE,
                    MPendingIdentity.COL_ID, "INTEGER PRIMARY KEY",
                    MPendingIdentity.COL_IDENTITY_ID, "INTEGER NOT NULL",
                    MPendingIdentity.COL_KEY, "TEXT NOT NULL",
                    MPendingIdentity.COL_NOTIFIED, "INTEGER NOT NULL",
                    MPendingIdentity.COL_REQUEST_ID, "INTEGER NOT NULL",
                    MPendingIdentity.COL_TIMESTAMP, "INTEGER NOT NULL");
        }
        if (oldVersion <= 19) {
            db.execSQL("DROP INDEX IF EXISTS " + MObject.TABLE + "_encoded");
            db.execSQL("CREATE INDEX " + MObject.TABLE + "_encoded ON " + MObject.TABLE + "(" +
                    MObject.COL_ENCODED_ID + ")");
            db.execSQL("CREATE INDEX " + MObject.TABLE + "_processed ON " + MObject.TABLE + "(" +
                    MObject.COL_PROCESSED + ")");
        }

        if (oldVersion <= 20) {
            createTable(db, MPendingUpload.TABLE,
                    MPendingUpload.COL_ID, "INTEGER PRIMARY KEY",
                    MPendingUpload.COL_OBJECT_ID, "INTEGER NOT NULL");
            db.execSQL("CREATE INDEX " + MPendingUpload.TABLE + "_object ON "
                    + MPendingUpload.TABLE + "(" + MPendingUpload.COL_OBJECT_ID + ")");
        }

        if (oldVersion <= 21) {
            File dir = new File(Environment.getExternalStorageDirectory(), "Musubi/Pictures");
            if (dir.exists()) {
                File newPath = new File(Environment.getExternalStorageDirectory(), ContentCorral.PICTURE_SUBFOLDER);
                dir.renameTo(newPath);
                MediaScannerConnection.scanFile(mContext, new String[] { newPath.getAbsolutePath() }, null, null);
            }
        }

        if (oldVersion <= 22) {
        	db.execSQL("ALTER TABLE " + MFeed.TABLE + " ADD COLUMN " + MFeed.COL_THUMBNAIL + " BLOB");
        }

        if (oldVersion <= 23) {
        	db.execSQL("CREATE INDEX " + MEncodedMessage.TABLE + "_processed ON " + MEncodedMessage.TABLE + "(" +
            		MEncodedMessage.COL_PROCESSED +"," + MEncodedMessage.COL_PROCESSED_TIME + ")");
        }

        if (oldVersion <= 24) {
        	// etc
        }
        db.setVersion(VERSION);
    }

    private void createTable(SQLiteDatabase db, String tableName, String... cols){
        assert cols.length % 2 == 0;
        String s = "CREATE TABLE " + tableName + " (";
        for(int i = 0; i < cols.length; i += 2){
            s += cols[i] + " " + cols[i + 1];
            if(i < (cols.length - 2)){
                s += ", ";
            }
            else{
                s += " ";
            }
        }
        s += ")";
        Log.i(TAG, s);
        db.execSQL(s);
    }

    private void createIndex(SQLiteDatabase db, String type, String name, String tableName, String col){
        String s = "CREATE " + type + " " + name + " on " + tableName + " (" + col + ")";
        Log.i(TAG, s);
        db.execSQL(s);
    } 


	@Override
	public void onCreate(SQLiteDatabase db) {
	    db.beginTransaction();

	    createTable(db, MMyDeviceName.TABLE,
	            MMyDeviceName.COL_ID, "INTEGER PRIMARY KEY",
	            MMyDeviceName.COL_DEVICE_NAME, "INTEGER NOT NULL");
	    
	    
	    new DeviceManager(db).generateAndStoreLocalDeviceName();

	    createTable(db, MSyncState.TABLE,
	    		MSyncState.COL_ID, "INTEGER PRIMARY KEY",
	    		MSyncState.COL_MAX_DATA, "INTEGER NOT NULL",
	    		MSyncState.COL_MAX_CONTACT, "INTEGER NOT NULL",
	    		MSyncState.COL_LAST_FACEBOOK_UPDATE_TIME, "INTEGER NOT NULL");

	    ContentValues syncStateRow = new ContentValues();
	    syncStateRow.put(MSyncState.COL_MAX_DATA, -1);
	    syncStateRow.put(MSyncState.COL_MAX_CONTACT, -1);
	    syncStateRow.put(MSyncState.COL_LAST_FACEBOOK_UPDATE_TIME, -1);
	    db.insert(MSyncState.TABLE, null, syncStateRow);

	    createTable(db, MIdentity.TABLE, 
	            MIdentity.COL_ID, "INTEGER PRIMARY KEY",
	            MIdentity.COL_TYPE, "TEXT NOT NULL",
	            MIdentity.COL_PRINCIPAL, "TEXT",
	            MIdentity.COL_PRINCIPAL_HASH, "BLOB NOT NULL",
	            MIdentity.COL_PRINCIPAL_SHORT_HASH, "INTEGER NOT NULL",
	            MIdentity.COL_NAME, "TEXT",
	            MIdentity.COL_MUSUBI_NAME, "TEXT",
                MIdentity.COL_THUMBNAIL, "BLOB",
                MIdentity.COL_MUSUBI_THUMBNAIL, "BLOB",
	            MIdentity.COL_OWNED, "INTEGER NOT NULL",
	            MIdentity.COL_CLAIMED, "INTEGER NOT NULL",
	            MIdentity.COL_BLOCKED, "INTEGER NOT NULL",
	            MIdentity.COL_CONTACT_ID, "INTEGER",
	            MIdentity.COL_ANDROID_DATA_ID, "INTEGER",
	            MIdentity.COL_RECEIVED_PROFILE_VERSION, "INTEGER DEFAULT 0",
	            MIdentity.COL_SENT_PROFILE_VERSION, "INTEGER DEFAULT 0",
	            MIdentity.COL_NEXT_SEQUENCE_NUMBER, "INTEGER",
	            MIdentity.COL_CREATED_AT, "INTEGER NOT NULL",
	            MIdentity.COL_UPDATED_AT, "INTEGER NOT NULL",
	            MIdentity.COL_HAS_SENT_EMAIL, "INTEGER NOT NULL",
	            MIdentity.COL_WHITELISTED, "INTEGER NOT NULL");

        //membership test needs to be fast
        db.execSQL("CREATE INDEX " + MIdentity.TABLE + "_lookup ON " + MIdentity.TABLE + "(" + 
        		MIdentity.COL_TYPE + "," + MIdentity.COL_PRINCIPAL_SHORT_HASH +
        		")");

	    createTable(db, MDevice.TABLE, 
	    		MDevice.COL_ID, "INTEGER PRIMARY KEY",
	    		MDevice.COL_DEVICE_NAME, "INTEGER NOT NULL",
	    		MDevice.COL_IDENTITY_ID, "INTEGER NOT NULL",
	    		MDevice.COL_MAX_SEQUENCE_NUMBER, "INTEGER NOT NULL");

        //lookup id by user,device name needs to be fast
        db.execSQL("CREATE INDEX " + MDevice.TABLE + "_lookup ON " + MDevice.TABLE + "(" + 
        		MDevice.COL_IDENTITY_ID + "," + MDevice.COL_DEVICE_NAME +
        		")");

        createTable(db, MFeed.TABLE,
	            MFeed.COL_ID, "INTEGER PRIMARY KEY",
	            MFeed.COL_TYPE, "INTEGER NOT NULL",
	            MFeed.COL_CAPABILITY, "BLOB",
	            MFeed.COL_SHORT_CAPABILITY, "INTEGER",
	            MFeed.COL_LATEST_RENDERABLE_OBJ_ID, "INTEGER",
	            MFeed.COL_LATEST_RENDERABLE_OBJ_TIME, "INTEGER",
	            MFeed.COL_NUM_UNREAD, "INTEGER NOT NULL",
	            MFeed.COL_NAME, "TEXT",
	    		MFeed.COL_ACCEPTED, "INTEGER NOT NULL",
	    		MFeed.COL_THUMBNAIL, "BLOB");

        //lookup id by type,capability needs to be fast
        db.execSQL("CREATE INDEX " + MFeed.TABLE + "_lookup ON " + MFeed.TABLE + "(" + 
        		MFeed.COL_TYPE + "," + MFeed.COL_SHORT_CAPABILITY +
        		")");
        //list for renderables ordered in descending timestamp needs to be fast
        db.execSQL("CREATE INDEX " + MFeed.TABLE + "_list_renderable ON " + MFeed.TABLE + "(" + 
        		MFeed.COL_ACCEPTED + "," + MFeed.COL_LATEST_RENDERABLE_OBJ_TIME + ")");

        createTable(db, MFeedMember.TABLE,
	            MFeedMember.COL_ID, "INTEGER PRIMARY KEY",
	            MFeedMember.COL_FEED_ID, "INTEGER NOT NULL",
	            MFeedMember.COL_IDENTITY_ID, "INTEGER NOT NULL");
	    
        //membership test needs to be fast
        db.execSQL("CREATE UNIQUE INDEX " + MFeedMember.TABLE + "_lookup ON " + MFeedMember.TABLE + "(" + 
        		MFeedMember.COL_FEED_ID + "," + MFeedMember.COL_IDENTITY_ID +
        		")");

        createTable(db, MFeedApp.TABLE,
                MFeedApp.COL_ID, "INTEGER PRIMARY KEY",
                MFeedApp.COL_FEED_ID, "INTEGER NOT NULL",
                MFeedApp.COL_APP_ID, "INTEGER NOT NULL");
        db.execSQL("CREATE UNIQUE INDEX " + MFeedApp.TABLE + "_lookup ON " + MFeedApp.TABLE + "(" + 
                MFeedApp.COL_FEED_ID + "," + MFeedApp.COL_APP_ID +
                ")");

        createTable(db, MObject.TABLE,
                MObject.COL_ID, "INTEGER PRIMARY KEY",
                MObject.COL_FEED_ID, "INTEGER NOT NULL",
                MObject.COL_IDENTITY_ID, "INTEGER NOT NULL",
                MObject.COL_DEVICE_ID, "INTEGER NOT NULL",
                MObject.COL_PARENT_ID, "INTEGER",
                MObject.COL_APP_ID, "INTEGER NOT NULL",
                MObject.COL_TIMESTAMP, "INTEGER NOT NULL",
                MObject.COL_UNIVERSAL_HASH, "INTEGER",
                MObject.COL_SHORT_UNIVERSAL_HASH, "INTEGER",
                MObject.COL_TYPE, "TEXT NOT NULL",
                MObject.COL_JSON, "TEXT",
                MObject.COL_RAW, "BLOB",
                MObject.COL_INT_KEY, "INTEGER",
                MObject.COL_STRING_KEY, "TEXT",
                MObject.COL_LAST_MODIFIED_TIMESTAMP, "INTEGER NOT NULL",
                MObject.COL_ENCODED_ID, "INTEGER",
                MObject.COL_DELETED, "INTEGER DEFAULT 0",
                MObject.COL_RENDERABLE, "INTEGER DEFAULT 0",
                MObject.COL_PROCESSED, "INTEGER NOT NULL");
        
        //query by short universal hash need to be fast
        db.execSQL("CREATE INDEX " + MObject.TABLE + "_lookup ON " + MObject.TABLE + "(" + 
        		MObject.COL_SHORT_UNIVERSAL_HASH + ")");
        //query by type,feed id needs to be fast
        db.execSQL("CREATE INDEX " + MObject.TABLE + "_list_type ON " + MObject.TABLE + "(" + 
        		MObject.COL_TYPE + "," + MObject.COL_FEED_ID + ")");
        //list by feed id for renderables in last modified timestamp desc order
        db.execSQL("CREATE INDEX " + MObject.TABLE + "_list_renderable ON " + MObject.TABLE + "(" + 
        		MObject.COL_FEED_ID + "," + MObject.COL_PARENT_ID + "," + MObject.COL_RENDERABLE +
        		"," + MObject.COL_LAST_MODIFIED_TIMESTAMP + ")");
        // Finding objects by encoded_id must be fast
        db.execSQL("CREATE INDEX " + MObject.TABLE + "_encoded ON " + MObject.TABLE + "(" +
                MObject.COL_ENCODED_ID + ")");
        // Finding unprocessed objects should be fast
        db.execSQL("CREATE INDEX " + MObject.TABLE + "_processed ON " + MObject.TABLE + "(" +
                MObject.COL_PROCESSED + ")");

        createTable(db, MApp.TABLE,
                MApp.COL_ID, "INTEGER PRIMARY KEY",
                MApp.COL_APP_ID, "TEXT",
                MApp.COL_NAME, "TEXT",
                MApp.COL_ANDROID_PACKAGE, "TEXT",
                MApp.COL_WEB_APP_URL, "TEXT",
                MApp.COL_DELETED, "INTEGER NOT NULL DEFAULT 0");
        //query by app id needs to be fast
        db.execSQL("CREATE UNIQUE INDEX " + MApp.TABLE + "_lookup ON " + MApp.TABLE + "(" + 
        		MApp.COL_APP_ID +
        		")");
        installBundledApps(db);

        createTable(db, MAppAction.TABLE,
        		MAppAction.COL_ID, "INTEGER PRIMARY KEY",
        		MAppAction.COL_APP_ID, "INTEGER NOT NULL",
        		MAppAction.COL_OBJ_TYPE, "TEXT",
        		MAppAction.COL_ACTION, "TEXT");
        //query by app id needs to be fast
        db.execSQL("CREATE INDEX " + MAppAction.TABLE + "_lookup_by_app ON " + MAppAction.TABLE + "(" + 
        		MAppAction.COL_APP_ID +
        		")");
        //query for obj type, action needs to be fast
        db.execSQL("CREATE INDEX " + MAppAction.TABLE + "_lookup_by_type_action ON " + MAppAction.TABLE + "(" + 
        		MAppAction.COL_OBJ_TYPE + "," + MAppAction.COL_ACTION +
        		")");

        createTable(db, MMyAccount.TABLE,
                MMyAccount.COL_ID, "INTEGER PRIMARY KEY",
                MMyAccount.COL_ACCOUNT_NAME, "TEXT NOT NULL",
                MMyAccount.COL_ACCOUNT_TYPE, "TEXT NOT NULL",
                MMyAccount.COL_IDENTITY_ID, "INTEGER",
                MMyAccount.COL_FEED_ID, "INTEGER");
        //query by account name,type needs to be fast
        db.execSQL("CREATE INDEX " + MMyAccount.TABLE + "_lookup ON " + MMyAccount.TABLE + "(" + 
        		MMyAccount.COL_ACCOUNT_NAME + "," + MMyAccount.COL_ACCOUNT_NAME + "," + MMyAccount.COL_IDENTITY_ID +
        		")");
        //query by identitiy id needs to be fast (double check with profile push processor)
        db.execSQL("CREATE INDEX " + MMyAccount.TABLE + "_for_identity ON " + MMyAccount.TABLE + "(" + 
        		MMyAccount.COL_IDENTITY_ID +
        		")");

        createTable(db, MFactType.TABLE,
                MFactType.COL_ID, "INTEGER PRIMARY KEY",
                MFactType.COL_FACT_TYPE, "TEXT UNIQUE NOT NULL");
        //query by fact type needs to be fast
        db.execSQL("CREATE UNIQUE INDEX " + MFactType.TABLE + "_lookup ON " + MFactType.TABLE + "(" + 
        		MFactType.COL_FACT_TYPE +
        		")");
        
        
        createTable(db, MFact.TABLE,
                MFact.COL_ID, "INTEGER PRIMARY KEY",
                MFact.COL_APP_ID, "INTEGER NOT NULL",
                MFact.COL_FACT_TYPE_ID, "INTEGER NOT NULL",
                MFact.COL_V, "NONE",
                MFact.COL_A, "NONE",
                MFact.COL_B, "NONE",
                MFact.COL_C, "NONE",
                MFact.COL_D, "NONE");
        //lookup index for from musubi across all applications
        db.execSQL("CREATE UNIQUE INDEX " + MFact.TABLE + "_lookup ON " + MFact.TABLE + "(" + 
        		MFact.COL_FACT_TYPE_ID + "," + MFact.COL_A + "," + 
        		MFact.COL_B + "," + MFact.COL_C + "," +
        		MFact.COL_D +
        		")");
        //lookup index for within an application namespace of facts
        db.execSQL("CREATE UNIQUE INDEX " + MFact.TABLE + "_lookup_app ON " + MFact.TABLE + "(" + 
        		MFact.COL_APP_ID + "," + MFact.COL_FACT_TYPE_ID + "," + MFact.COL_A + "," + 
        		MFact.COL_B + "," + MFact.COL_C + "," +
        		MFact.COL_D +
        		")");

        createTable(db, MEncodedMessage.TABLE,
                MEncodedMessage.COL_ID, "INTEGER PRIMARY KEY",
                MEncodedMessage.COL_SENDER, "INTEGER",
                MEncodedMessage.COL_ENCODED, "BLOB NOT NULL",
                MEncodedMessage.COL_DEVICE_ID, "INTEGER",
                MEncodedMessage.COL_SHORT_HASH, "INTEGER",
                MEncodedMessage.COL_HASH, "INTEGER",
                MEncodedMessage.COL_OUTBOUND, "INTEGER NOT NULL",
                MEncodedMessage.COL_PROCESSED, "INTEGER NOT NULL",
        		MEncodedMessage.COL_PROCESSED_TIME, "INTEGER NOT NULL DEFAULT 0");
        //lookup by hash for device,seq number
        db.execSQL("CREATE INDEX " + MEncodedMessage.TABLE + "_lookup ON " + MEncodedMessage.TABLE + "(" +
        		MEncodedMessage.COL_SHORT_HASH + ")");
        
        //lookup non-processed outbound order by id 
        //  AND
        //lookup non-processed inbound order by id
        db.execSQL("CREATE INDEX " + MEncodedMessage.TABLE + "_list ON " + MEncodedMessage.TABLE + "(" + 
        		MEncodedMessage.COL_OUTBOUND + "," + MEncodedMessage.COL_PROCESSED + "," + MEncodedMessage.COL_ID + ")");
        //lookup old processed messages
        db.execSQL("CREATE INDEX " + MEncodedMessage.TABLE + "_processed ON " + MEncodedMessage.TABLE + "(" +
        		MEncodedMessage.COL_PROCESSED +"," + MEncodedMessage.COL_PROCESSED_TIME + ")");

        createTable(db, MEncryptionUserKey.TABLE,
                MEncryptionUserKey.COL_ID, "INTEGER PRIMARY KEY",
                MEncryptionUserKey.COL_IDENTITY_ID, "INTEGER NOT NULL",
                MEncryptionUserKey.COL_WHEN, "INTEGER NOT NULL",
                MEncryptionUserKey.COL_USER_KEY, "INTEGER NOT NULL");
        //lookup by identity id, when
        db.execSQL("CREATE INDEX " + MEncryptionUserKey.TABLE + "_lookup ON " + MEncryptionUserKey.TABLE + "(" + 
        		MEncryptionUserKey.COL_IDENTITY_ID + "," + MEncryptionUserKey.COL_WHEN + ")");

        createTable(db, MSignatureUserKey.TABLE,
                MSignatureUserKey.COL_ID, "INTEGER PRIMARY KEY",
                MSignatureUserKey.COL_IDENTITY_ID, "INTEGER NOT NULL",
                MSignatureUserKey.COL_WHEN, "INTEGER NOT NULL",
                MSignatureUserKey.COL_USER_KEY, "INTEGER NOT NULL");
        //lookup by identity id, when
        db.execSQL("CREATE INDEX " + MSignatureUserKey.TABLE + "_lookup ON " + MSignatureUserKey.TABLE + "(" + 
        		MSignatureUserKey.COL_IDENTITY_ID + "," + MSignatureUserKey.COL_WHEN + ")");

        createTable(db, MIncomingSecret.TABLE,
                MIncomingSecret.COL_ID, "INTEGER PRIMARY KEY",
                MIncomingSecret.COL_MY_IDENTITY_ID, "INTEGER NOT NULL",
                MIncomingSecret.COL_OTHER_IDENTITY_ID, "INTEGER NOT NULL",
                MIncomingSecret.COL_INCOMING_SIGNATURE_WHEN, "INTEGER NOT NULL",
                MIncomingSecret.COL_INCOMING_ENCRYPTION_WHEN, "INTEGER NOT NULL",
                MIncomingSecret.COL_INCOMING_ENCRYPTED_KEY, "BLOB NOT NULL",
                MIncomingSecret.COL_INCOMING_DEVICE_ID, "INTEGER NOT NULL",
                MIncomingSecret.COL_INCOMING_SIGNATURE, "BLOB NOT NULL",
                MIncomingSecret.COL_INCOMING_KEY, "BLOB NOT NULL");
        //look channel key from the wire
        db.execSQL("CREATE INDEX " + MIncomingSecret.TABLE + "_lookup ON " + MIncomingSecret.TABLE + "(" + 
        		MIncomingSecret.COL_MY_IDENTITY_ID + "," + MIncomingSecret.COL_OTHER_IDENTITY_ID + "," + 
    			MIncomingSecret.COL_INCOMING_ENCRYPTION_WHEN + "," + MIncomingSecret.COL_INCOMING_SIGNATURE_WHEN + "," +
    			MIncomingSecret.COL_INCOMING_DEVICE_ID +
        		")");
		
        createTable(db, MMissingMessage.TABLE,
                MMissingMessage.COL_ID, "INTEGER PRIMARY KEY",
                MMissingMessage.COL_DEVICE_ID, "INTEGER NOT NULL",
                MMissingMessage.COL_SEQUENCE_NUMBER, "INTEGER NOT NULL");
        //lookup by device id, sequence number
        db.execSQL("CREATE INDEX " + MMissingMessage.TABLE + "_lookup ON " + MMissingMessage.TABLE + "(" + 
        		MMissingMessage.COL_DEVICE_ID + "," + MMissingMessage.COL_SEQUENCE_NUMBER + ")");

        createTable(db, MOutgoingSecret.TABLE,
                MOutgoingSecret.COL_ID, "INTEGER PRIMARY KEY",
                MOutgoingSecret.COL_MY_IDENTITY_ID, "INTEGER NOT NULL",
                MOutgoingSecret.COL_OTHER_IDENTITY_ID, "INTEGER NOT NULL",
                MOutgoingSecret.COL_OUTGOING_SIGNATURE_WHEN, "INTEGER NOT NULL",
                MOutgoingSecret.COL_OUTGOING_ENCRYPTION_WHEN, "INTEGER NOT NULL",
                MOutgoingSecret.COL_OUTGOING_ENCRYPTED_KEY, "BLOB NOT NULL",
                MOutgoingSecret.COL_OUTGOING_SIGNATURE, "BLOB NOT NULL",
                MOutgoingSecret.COL_OUTGOING_KEY, "BLOB NOT NULL");
        //look channel key from the wire
        db.execSQL("CREATE INDEX " + MOutgoingSecret.TABLE + "_lookup ON " + MOutgoingSecret.TABLE + "(" + 
        		MOutgoingSecret.COL_MY_IDENTITY_ID + "," + MOutgoingSecret.COL_OTHER_IDENTITY_ID + "," + 
				MOutgoingSecret.COL_OUTGOING_ENCRYPTION_WHEN + "," + MOutgoingSecret.COL_OUTGOING_SIGNATURE_WHEN + "" +
        		")");

        createTable(db, MSequenceNumber.TABLE,
                MSequenceNumber.COL_ID, "INTEGER PRIMARY KEY",
                MSequenceNumber.COL_ENCODED_ID, "INTEGER NOT NULL",
                MSequenceNumber.COL_RECIPIENT, "INTEGER NOT NULL",
                MSequenceNumber.COL_SEQUENCE_NUMBER, "INTEGER NOT NULL");
        //TODO: when we look these up for retransmit, then we should
        //add an index

        //implict index
        createTable(db, MContactDataVersion.TABLE,
        		MContactDataVersion.COL_RAW_DATA_ID, "INTEGER PRIMARY KEY",
        		MContactDataVersion.COL_VERSION, "INTEGER NOT NULL");
        
        //store latest wizard state
    	createTable(db, WizardStepHandler.TABLE,
    			WizardStepHandler.COL_ID, "INTEGER PRIMARY KEY",
                WizardStepHandler.COL_CURRENT_STEP, "INTEGER NOT NULL");
    	
    	createTable(db, MPendingIdentity.TABLE,
    	        MPendingIdentity.COL_ID, "INTEGER PRIMARY KEY",
    	        MPendingIdentity.COL_IDENTITY_ID, "INTEGER NOT NULL",
    	        MPendingIdentity.COL_KEY, "TEXT NOT NULL",
    	        MPendingIdentity.COL_NOTIFIED, "INTEGER NOT NULL",
    	        MPendingIdentity.COL_REQUEST_ID, "INTEGER NOT NULL",
    	        MPendingIdentity.COL_TIMESTAMP, "INTEGER NOT NULL");

    	createTable(db, MPendingUpload.TABLE,
                MPendingUpload.COL_ID, "INTEGER PRIMARY KEY",
                MPendingUpload.COL_OBJECT_ID, "INTEGER NOT NULL");
        db.execSQL("CREATE INDEX " + MPendingUpload.TABLE + "_object ON "
                + MPendingUpload.TABLE + "(" + MPendingUpload.COL_OBJECT_ID + ")");
    	
    	ContentValues cv = new ContentValues();
        cv.put(WizardStepHandler.COL_ID, 0);
        cv.put(WizardStepHandler.COL_CURRENT_STEP, 0);
        db.insert(WizardStepHandler.TABLE, null, cv);

        insertBuiltinFeeds(db);
        insertBootStrapIdentity(db);
        addLocalWhitelistGroup(db);
        
        createRelationBaseTable(db);
        addRelationIndexes(db);
        createUserAttributesTable(db);
        createObjCacheBaseTable(db);
        createLikeCacheBaseTable(db);

        createSocialKitViews(db);

        db.setVersion(VERSION);
        db.setTransactionSuccessful();
        db.endTransaction();
	}

	void installBundledApps(SQLiteDatabase db) {
	    String SK_APPS = "http://mobisocial.stanford.edu/musubi/apps/SocialKit-JS/apps/";
        AppManager am = new AppManager(db);
    
        db.delete(MApp.TABLE, MApp.COL_APP_ID + "=?", new String[] { "musubi.sketch" });

        ContentValues cv = new ContentValues();
        cv.put(MApp.COL_NAME, "Sketch");
        cv.put(MApp.COL_WEB_APP_URL, SK_APPS + "musubi.sketch/index.html");
        cv.put(MApp.COL_APP_ID, "musubi.sketch");
        db.insert(MApp.TABLE, null, cv);
    
        db.delete(MApp.TABLE, MApp.COL_APP_ID + "=?", new String[] { "musubi.shout" });

        cv.clear();
        cv.put(MApp.COL_NAME, "Shout");
        cv.put(MApp.COL_WEB_APP_URL, SK_APPS + "musubi.shout/index.html");
        cv.put(MApp.COL_APP_ID, "musubi.shout");
        db.insert(MApp.TABLE, null, cv);
	}

	void createSocialKitViews(SQLiteDatabase db) {
        StringBuilder sql;

        // SocialKit view over the objects table
        db.execSQL("DROP VIEW IF EXISTS " + SKObjects.TABLE);
        sql = new StringBuilder()
            .append("CREATE VIEW ").append(SKObjects.TABLE)
            .append(" AS ").append("SELECT ");
        for (ViewColumn column : SKObjects.VIEW_COLUMNS) {
            sql.append(column.getTableColumn()).append(" ")
                .append(column.getViewColumn()).append(",");
        }
        sql.setLength(sql.length() - 1);
        sql.append(" FROM ").append(MObject.TABLE).append(",").append(MApp.TABLE)
            .append(" WHERE ").append(MObject.TABLE).append(".").append(MObject.COL_APP_ID)
            .append(" = ").append(MApp.TABLE).append(".").append(MApp.COL_ID);
        db.execSQL(sql.toString());

        // view over identities table
        db.execSQL("DROP VIEW IF EXISTS " + SKIdentities.TABLE);
        sql = new StringBuilder()
            .append("CREATE VIEW ").append(SKIdentities.TABLE)
            .append(" AS ").append("SELECT ");
        for (ViewColumn column : SKIdentities.VIEW_COLUMNS) {
            sql.append(column.getTableColumn()).append(" ")
                .append(column.getViewColumn()).append(",");
        }
        sql.setLength(sql.length() - 1);
        sql.append(" FROM ").append(MIdentity.TABLE);
        db.execSQL(sql.toString());

        // feed members view
        db.execSQL("DROP VIEW IF EXISTS " + SKFeedMembers.TABLE);
        sql = new StringBuilder()
            .append("CREATE VIEW ").append(SKFeedMembers.TABLE)
            .append(" AS ").append("SELECT ");
        for (ViewColumn column : SKFeedMembers.VIEW_COLUMNS) {
            sql.append(column.getTableColumn()).append(" ")
                .append(column.getViewColumn()).append(",");
        }
        sql.setLength(sql.length() - 1);
        sql.append(" FROM ").append(SKIdentities.TABLE).append(",").append(MFeedMember.TABLE)
            .append(" WHERE ").append(SKIdentities.TABLE).append(".").append(SKIdentities.COL_ID)
            .append(" = ").append(MFeedMember.TABLE).append(".").append(MFeedMember.COL_IDENTITY_ID);
        db.execSQL(sql.toString());
	}

	private void addLocalWhitelistGroup(SQLiteDatabase db) {
		MyAccountManager myAccountManager = new MyAccountManager(db);
		MMyAccount account = new MMyAccount();
		account.accountName_ = MMyAccount.NONIDENTITY_SPECIFIC_WHITELIST_ACCOUNT;
		account.accountType_ = MMyAccount.INTERNAL_ACCOUNT_TYPE;
		account.feedId_ = (long)MFeed.NONIDENTITY_SPECIFIC_WHITELIST_ID;
		myAccountManager.insertAccount(account);
		
	}

	private void insertBootStrapIdentity(SQLiteDatabase db) {
		IdentitiesManager identitiesManager = new IdentitiesManager(db);
		DeviceManager deviceManager = new DeviceManager(db);

		Log.w(TAG, "adding your id");
		IBIdentity my_id = IdentitiesManager.getPreInstallIdentity();
		MIdentity myId = new MIdentity();
		
		myId.claimed_ = true;
		myId.owned_ = true;
		myId.hasSentEmail_ = true;
		myId.principal_ = my_id.principal_;
		myId.principalHash_ = my_id.hashed_;
		myId.principalShortHash_ = Util.shortHash(my_id.hashed_);
		myId.type_ = my_id.authority_;
		myId.name_ = "Me";
		myId.receivedProfileVersion_ = 1L;
		myId.sentProfileVersion_ = 1L;
		identitiesManager.insertIdentity(myId);
		
		MDevice dev = new MDevice();
		dev.deviceName_ = deviceManager.getLocalDeviceName();
		dev.identityId_ = myId.id_;
		dev.maxSequenceNumber_ = 0;
		deviceManager.insertDevice(dev);
	}

	private void insertBuiltinFeeds(SQLiteDatabase db) {
		//the broadcast feed should never show up, we will use
		//its state internally
	    ContentValues cv = new ContentValues();
		cv.put(MFeed.COL_ACCEPTED, false);
		cv.put(MFeed.COL_NAME, "broadcast");
		cv.put(MFeed.COL_TYPE, FeedType.ASYMMETRIC.ordinal());
		cv.put(MFeed.COL_ID, MFeed.GLOBAL_BROADCAST_FEED_ID);
		cv.put(MFeed.COL_NUM_UNREAD, 0);
		db.insert(MFeed.TABLE, null, cv);

		cv = new ContentValues();
        cv.put(MFeed.COL_ID, MFeed.WIZ_FEED_ID);
        cv.put(MFeed.COL_NAME, "Your first Musubi Feed");
        cv.put(MFeed.COL_ACCEPTED, 1);
        cv.put(MFeed.COL_TYPE, FeedType.FIXED.ordinal());
        cv.put(MFeed.COL_NUM_UNREAD, 0);
        db.insert(MFeed.TABLE, null, cv);

        cv = new ContentValues();
        cv.put(MFeed.COL_ID, MFeed.NONIDENTITY_SPECIFIC_WHITELIST_ID);
        cv.put(MFeed.COL_NAME, MFeed.LOCAL_WHITELIST_FEED_NAME);
        cv.put(MFeed.COL_ACCEPTED, 0);
        cv.put(MFeed.COL_TYPE, FeedType.ASYMMETRIC.ordinal());
        cv.put(MFeed.COL_NUM_UNREAD, 0);
        db.insert(MFeed.TABLE, null, cv);
	}

	private final void createRelationBaseTable(SQLiteDatabase db) {
	    createTable(db, DbRelation.TABLE,
                DbRelation._ID, "INTEGER PRIMARY KEY",
                DbRelation.OBJECT_ID_A, "INTEGER",
                DbRelation.OBJECT_ID_B, "INTEGER",
                DbRelation.RELATION_TYPE, "TEXT"
                );
	    createIndex(db, "INDEX", "relations_by_type", DbRelation.TABLE, DbRelation.RELATION_TYPE);
	}

	private final void createObjCacheBaseTable(SQLiteDatabase db) {
        createTable(db, DbObjCache.TABLE,
                DbObjCache._ID, "INTEGER PRIMARY KEY",
                DbObjCache.PARENT_OBJ, "INTEGER",
                DbObjCache.LATEST_OBJ, "INTEGER"
                );
        createIndex(db, "INDEX", "obj_cache_latest", DbObjCache.TABLE, DbObjCache.PARENT_OBJ);
    }

	private final void createLikeCacheBaseTable(SQLiteDatabase db) {
        createTable(db, DbLikeCache.TABLE,
                DbLikeCache._ID, "INTEGER PRIMARY KEY",
                DbLikeCache.PARENT_OBJ, "INTEGER",
                DbLikeCache.COUNT, "INTEGER",
                DbLikeCache.LOCAL_LIKE, "INTEGER"
                );
        createIndex(db, "INDEX", "obj_cache_like_count", DbLikeCache.TABLE, DbLikeCache.PARENT_OBJ);
    }

	private final void createUserAttributesTable(SQLiteDatabase db) {
	    // contact_attributes: _id, contact_id, attr_name, attr_value
	    // TODO: genericize; createDbTable(DbTable table) { ... }
	    String[] colNames = DbContactAttributes.getColumnNames();
	    String[] colTypes = DbContactAttributes.getTypeDefs();
	    String[] colDefs = new String[colNames.length * 2];
	    int j = 0;
	    for (int i = 0; i < colNames.length; i += 1) {
	        colDefs[j++] = colNames[i];
	        colDefs[j++] = colTypes[i];
	    }
	    createTable(db, DbContactAttributes.TABLE, colDefs);
        createIndex(db, "INDEX", "attrs_by_contact_id", DbContactAttributes.TABLE, DbContactAttributes.CONTACT_ID);
	}

	private final void addRelationIndexes(SQLiteDatabase db) {
	    createIndex(db, "INDEX", "relation_obj_a", DbRelation.TABLE, DbRelation.OBJECT_ID_A);
	    createIndex(db, "INDEX", "relation_obj_b", DbRelation.TABLE, DbRelation.OBJECT_ID_B);
	}

	public void vacuum() {
		getWritableDatabase().execSQL("VACUUM");
	}

	public void setActivityForEmergencyUI(BootstrapActivity bootstrapActivity) {
		mBootstrapActivity = bootstrapActivity;
	}
 }
