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

import java.io.FileDescriptor;

import mobisocial.musubi.model.DbLikeCache;
import mobisocial.musubi.model.MDevice;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MObject;
import mobisocial.musubi.util.Util;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.os.Build;
import android.os.ParcelFileDescriptor;

public class ObjectManager extends ManagerBase {
    private SQLiteStatement mSqlInsertObj;
    private SQLiteStatement mSqlUpdateObj;
    private SQLiteStatement mSqlGetObjIdByHash;
	private SQLiteStatement mSqlObjectCount;
	private SQLiteStatement mSqlGetLatestRenderableId;
	private String mSqlQueryObjectForId;
	private String mSqlQueryObjectWithoutRawForId;
	private String mSqlGetObjectsToEncode;
	private SQLiteStatement mSqlLikeCount;
	private SQLiteStatement mSqlUpdateObjPipelineMetadata;
	private SQLiteStatement mSqlUpdateObjEncodeMetadata;

    static String[] STANDARD_FIELDS = new String[] {
        MObject.COL_ID,
        MObject.COL_FEED_ID,
        MObject.COL_IDENTITY_ID,
        MObject.COL_DEVICE_ID,
        MObject.COL_PARENT_ID,
        MObject.COL_APP_ID,
        MObject.COL_TIMESTAMP,
        MObject.COL_UNIVERSAL_HASH,
        MObject.COL_SHORT_UNIVERSAL_HASH,
        MObject.COL_TYPE,
        MObject.COL_JSON,
        MObject.COL_RAW,
        MObject.COL_INT_KEY,
        MObject.COL_STRING_KEY,
        MObject.COL_LAST_MODIFIED_TIMESTAMP,
        MObject.COL_ENCODED_ID,
        MObject.COL_DELETED,
        MObject.COL_RENDERABLE,
        MObject.COL_PROCESSED
    };

    final int _id = 0;
    final int feedId = 1;
    final int identityId = 2;
    final int deviceId = 3;
    final int parentId = 4;
    final int appId = 5;
    final int timestamp = 6;
    final int universalHash = 7;
    final int shortHash = 8;
    final int type = 9;
    final int json = 10;
    final int raw = 11;
    final int intKey = 12;
    final int stringKey = 13;
    final int lastModified = 14;
    final int encodedId = 15;
    final int deleted = 16;
    final int renderable = 17;
    final int processed = 18;


    public ObjectManager(SQLiteOpenHelper databaseSource) {
        super(databaseSource);
    }

    public void insertObject(MObject obj) {
        SQLiteDatabase db = initializeDatabase();
        if (mSqlInsertObj == null) {
            synchronized (this) {
                if(mSqlInsertObj == null) {
                    String sql = new StringBuilder()
                        .append("INSERT INTO ").append(MObject.TABLE).append("(")
                        .append(MObject.COL_FEED_ID).append(",")
                        .append(MObject.COL_IDENTITY_ID).append(",")
                        .append(MObject.COL_DEVICE_ID).append(",")
                        .append(MObject.COL_PARENT_ID).append(",")
                        .append(MObject.COL_APP_ID).append(",")
                        .append(MObject.COL_TIMESTAMP).append(",")
                        .append(MObject.COL_UNIVERSAL_HASH).append(",")
                        .append(MObject.COL_SHORT_UNIVERSAL_HASH).append(",")
                        .append(MObject.COL_TYPE).append(",")
                        .append(MObject.COL_JSON).append(",")
                        .append(MObject.COL_RAW).append(",")
                        .append(MObject.COL_INT_KEY).append(",")
                        .append(MObject.COL_STRING_KEY).append(",")
                        .append(MObject.COL_LAST_MODIFIED_TIMESTAMP).append(",")
                        .append(MObject.COL_ENCODED_ID).append(",")
                        .append(MObject.COL_DELETED).append(",")
                        .append(MObject.COL_RENDERABLE).append(",")
                        .append(MObject.COL_PROCESSED)
                        .append(") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)").toString();
                    mSqlInsertObj = db.compileStatement(sql);
                }
            }
        }
                
        synchronized (mSqlInsertObj) {
            bindStandardFields(mSqlInsertObj, obj);
            obj.id_ = mSqlInsertObj.executeInsert();
        }
    }

    public void updateObject(MObject obj) {
        SQLiteDatabase db = initializeDatabase();
        if (mSqlUpdateObj == null) {
            synchronized (this) {
                if(mSqlUpdateObj == null) {
                    String sql = new StringBuilder()
                        .append("UPDATE ").append(MObject.TABLE).append(" SET ")
                        .append(MObject.COL_FEED_ID).append("=?,")
                        .append(MObject.COL_IDENTITY_ID).append("=?,")
                        .append(MObject.COL_DEVICE_ID).append("=?,")
                        .append(MObject.COL_PARENT_ID).append("=?,")
                        .append(MObject.COL_APP_ID).append("=?,")
                        .append(MObject.COL_TIMESTAMP).append("=?,")
                        .append(MObject.COL_UNIVERSAL_HASH).append("=?,")
                        .append(MObject.COL_SHORT_UNIVERSAL_HASH).append("=?,")
                        .append(MObject.COL_TYPE).append("=?,")
                        .append(MObject.COL_JSON).append("=?,")
                        .append(MObject.COL_RAW).append("=?,")
                        .append(MObject.COL_INT_KEY).append("=?,")
                        .append(MObject.COL_STRING_KEY).append("=?,")
                        .append(MObject.COL_LAST_MODIFIED_TIMESTAMP).append("=?,")
                        .append(MObject.COL_ENCODED_ID).append("=?,")
                        .append(MObject.COL_DELETED).append("=?,")
                        .append(MObject.COL_RENDERABLE).append("=?,")
                        .append(MObject.COL_PROCESSED).append("=?")
                        .append(" WHERE ").append(MObject.COL_ID).append("=?").toString();
                    mSqlUpdateObj = db.compileStatement(sql);
                }
            }
        }
                
        synchronized (mSqlUpdateObj) {
            bindStandardFieldsThenId(mSqlUpdateObj, obj);
            mSqlUpdateObj.execute();
        }
    }

    public void updateObjectPipelineMetadata(MObject obj) {
        SQLiteDatabase db = initializeDatabase();
        if (mSqlUpdateObjPipelineMetadata == null) {
            synchronized (this) {
                if(mSqlUpdateObjPipelineMetadata == null) {
                    String sql = new StringBuilder()
                        .append("UPDATE ").append(MObject.TABLE).append(" SET ")
                        .append(MObject.COL_PARENT_ID).append("=?,")
                        .append(MObject.COL_RENDERABLE).append("=?,")
                        .append(MObject.COL_PROCESSED).append("=?")
                        .append(" WHERE ").append(MObject.COL_ID).append("=?").toString();
                    mSqlUpdateObjPipelineMetadata = db.compileStatement(sql);
                }
            }
        }
                
        synchronized (mSqlUpdateObjPipelineMetadata) {
        	if (obj.parentId_ == null) {
        		mSqlUpdateObjPipelineMetadata.bindNull(1);
        	} else {
        		mSqlUpdateObjPipelineMetadata.bindLong(1, obj.parentId_);
        	}
        	mSqlUpdateObjPipelineMetadata.bindLong(2, obj.renderable_ ? 1 : 0);
        	mSqlUpdateObjPipelineMetadata.bindLong(3, obj.processed_ ? 1 : 0);
        	mSqlUpdateObjPipelineMetadata.bindLong(4, obj.id_);
            mSqlUpdateObjPipelineMetadata.execute();
        }
    }

    public void updateObjectEncodedMetadata(MObject obj) {
        SQLiteDatabase db = initializeDatabase();
        if (mSqlUpdateObjEncodeMetadata == null) {
            synchronized (this) {
                if(mSqlUpdateObjEncodeMetadata == null) {
                    String sql = new StringBuilder()
                        .append("UPDATE ").append(MObject.TABLE).append(" SET ")
                        .append(MObject.COL_ENCODED_ID).append("=?,")
                        .append(MObject.COL_UNIVERSAL_HASH).append("=?,")
                        .append(MObject.COL_SHORT_UNIVERSAL_HASH).append("=?")
                        .append(" WHERE ").append(MObject.COL_ID).append("=?").toString();
                    mSqlUpdateObjEncodeMetadata = db.compileStatement(sql);
                }
            }
        }
                
        synchronized (mSqlUpdateObjEncodeMetadata) {
        	if (obj.encodedId_ == null) {
        		mSqlUpdateObjEncodeMetadata.bindNull(1);
        	} else {
        		mSqlUpdateObjEncodeMetadata.bindLong(1, obj.encodedId_);
        	}
        	if (obj.universalHash_ == null) {
        		mSqlUpdateObjEncodeMetadata.bindNull(2);
        	} else {
        		mSqlUpdateObjEncodeMetadata.bindBlob(2, obj.universalHash_);
        	}
        	if (obj.shortUniversalHash_ == null) {
        		mSqlUpdateObjEncodeMetadata.bindNull(3);
        	} else {
        		mSqlUpdateObjEncodeMetadata.bindLong(3, obj.shortUniversalHash_);
        	}
        	mSqlUpdateObjEncodeMetadata.bindLong(4, obj.id_);
            mSqlUpdateObjEncodeMetadata.execute();
        }
    }

    public MObject getObjectForId(long id) {
        SQLiteDatabase db = initializeDatabase();
        if (mSqlQueryObjectForId == null) {
            synchronized (this) {
                StringBuilder sql = new StringBuilder(100).append("SELECT ");
                for (String c : STANDARD_FIELDS) {
                    sql.append(c).append(",");
                }
                sql.setLength(sql.length() - 1);
                sql.append(" FROM ").append(MObject.TABLE)
                    .append(" WHERE ").append(MObject.COL_ID).append("=?");
                mSqlQueryObjectForId = sql.toString();
            }
        }

        String[] selectionArgs = new String[] { String.valueOf(id) };
        Cursor c = db.rawQuery(mSqlQueryObjectForId, selectionArgs);
        try {
            if (c.moveToNext()) {
                return fillInStandardFields(c);
            }
            return null;
        } finally {
            c.close();
        }
    }

    String mSqlQueryForRawById;
    public byte[] getRawForId(long id) {
    	SQLiteDatabase db = initializeDatabase();
    	if (mSqlQueryForRawById == null) {
    		synchronized (this) {
    			StringBuilder sql = new StringBuilder(100).append("SELECT ")
    					.append(MObject.COL_RAW)
    					.append(" FROM ").append(MObject.TABLE)
    					.append(" WHERE ").append(MObject.COL_ID).append("=?");
    			mSqlQueryForRawById = sql.toString();
    		}
    	}

    	String[] selectionArgs = new String[] { Long.toString(id) };
    	Cursor c = db.rawQuery(mSqlQueryForRawById, selectionArgs);
    	try {
    		if (c.moveToFirst()) {
    			if (!c.isNull(0)) return c.getBlob(0);
    		}
    		return null;
    	} finally {
    		c.close();
    	}
    }

    SQLiteStatement mSqlGetFdForRaw;
    public FileDescriptor getFileDescriptorForRaw(long objId) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			return null;
		}
		SQLiteDatabase db = initializeDatabase();
		if (mSqlGetFdForRaw == null) {
			synchronized (this) {
				if (mSqlGetFdForRaw == null) {
					StringBuilder sql = new StringBuilder(100)
						.append("SELECT ").append(MObject.COL_RAW)
						.append(" FROM ").append(MObject.TABLE)
						.append(" WHERE ").append(MObject.COL_ID).append("=?");
					mSqlGetFdForRaw = db.compileStatement(sql.toString());
				}
			}
		}

		synchronized (mSqlGetFdForRaw) {
			mSqlGetFdForRaw.bindLong(1, objId);
			try {
				ParcelFileDescriptor pfd = mSqlGetFdForRaw.simpleQueryForBlobFileDescriptor();
				if (pfd == null) {
					return null;
				}
				return pfd.getFileDescriptor();
			} catch (SQLiteDoneException e) {
				return null;
			}
		}
	}

    public MObject getObjectWithoutRawForId(long id) {
        SQLiteDatabase db = initializeDatabase();
        if (mSqlQueryObjectWithoutRawForId == null) {
            synchronized (this) {
                StringBuilder sql = new StringBuilder(100).append("SELECT ");
                for (String c : STANDARD_FIELDS) {
                	if (c.equals(MObject.COL_RAW)) {
                		sql.append("NULL,");
                	} else {
                		sql.append(c).append(",");
                	}
                }
                sql.setLength(sql.length() - 1);
                sql.append(" FROM ").append(MObject.TABLE)
                    .append(" WHERE ").append(MObject.COL_ID).append("=?");
                mSqlQueryObjectWithoutRawForId = sql.toString();
            }
        }

        String[] selectionArgs = new String[] { String.valueOf(id) };
        Cursor c = db.rawQuery(mSqlQueryObjectWithoutRawForId, selectionArgs);
        try {
            if (c.moveToNext()) {
                return fillInStandardFields(c);
            }
            return null;
        } finally {
            c.close();
        }
    }

    public boolean delete(long id) {
        SQLiteDatabase db = initializeDatabase();
        String whereClause = MObject.COL_ID + " = ?";
        String[] whereArgs = new String[] { Long.toString(id) };
        return db.delete(MObject.TABLE, whereClause, whereArgs) > 0;
    }

    private MObject fillInStandardFields(Cursor c) {
        MObject obj = new MObject();
        obj.id_ = c.getLong(_id);
        obj.feedId_ = c.getLong(feedId);
        obj.identityId_ = c.getLong(identityId);
        obj.deviceId_ = c.getLong(deviceId);
        obj.parentId_ = c.isNull(parentId) ? null : c.getLong(parentId);
        obj.appId_ = c.getLong(appId);
        obj.timestamp_ = c.getLong(timestamp);
        obj.universalHash_ = c.getBlob(universalHash);
        obj.shortUniversalHash_ = c.isNull(shortHash) ? null : c.getLong(shortHash);
        obj.type_ = c.getString(type);
        obj.json_ = c.getString(json);
        obj.raw_ = c.getBlob(raw);
        obj.intKey_ = c.isNull(intKey) ? null : c.getInt(intKey);
        obj.stringKey_ = c.getString(stringKey);
        obj.lastModifiedTimestamp_ = c.isNull(lastModified) ? null : c.getLong(lastModified);
        obj.encodedId_ = c.isNull(encodedId) ? null : c.getLong(encodedId);
        obj.deleted_ = c.getInt(deleted) == 1;
        obj.renderable_ = c.getInt(renderable) == 1;
        obj.processed_ = c.getInt(processed) == 1;
        return obj;
    }

    private void bindStandardFields(SQLiteStatement statement, MObject obj) {
        assert(obj.type_ != null);

        statement.bindLong(feedId, obj.feedId_);
        statement.bindLong(identityId, obj.identityId_);
        statement.bindLong(deviceId, obj.deviceId_);
        if (obj.parentId_ == null) statement.bindNull(parentId);
            else statement.bindLong(parentId, obj.parentId_);
        statement.bindLong(appId, obj.appId_);
        statement.bindLong(timestamp, obj.timestamp_);
        if (obj.universalHash_ == null) statement.bindNull(universalHash);
            else statement.bindBlob(universalHash, obj.universalHash_);
        if (obj.shortUniversalHash_ == null) statement.bindNull(shortHash);
            else statement.bindLong(shortHash, obj.shortUniversalHash_);
        statement.bindString(type, obj.type_);
        if (obj.json_ == null) statement.bindNull(json);
            else statement.bindString(json, obj.json_);
        if (obj.raw_ == null) statement.bindNull(raw);
        	else statement.bindBlob(raw, obj.raw_);
        if (obj.intKey_ == null) statement.bindNull(intKey);
    		else statement.bindLong(intKey, obj.intKey_);
        if (obj.stringKey_ == null) statement.bindNull(stringKey);
    		else statement.bindString(stringKey, obj.stringKey_);
        statement.bindLong(lastModified, obj.lastModifiedTimestamp_); 
        if (obj.encodedId_ == null) statement.bindNull(encodedId);
        	else statement.bindLong(encodedId, obj.encodedId_);
        statement.bindLong(deleted, obj.deleted_ ? 1 : 0);
        statement.bindLong(renderable, obj.renderable_ ? 1 : 0);
        statement.bindLong(processed, obj.processed_ ? 1 : 0);
    }

    private void bindStandardFieldsThenId(SQLiteStatement statement, MObject obj) {
        bindStandardFields(statement, obj);
        statement.bindLong(processed + 1, obj.id_);
    }

	public long getObjectIdForHash(byte[] hash) {
        SQLiteDatabase db = initializeDatabase();
        if (mSqlGetObjIdByHash == null) {
            synchronized (this) {
                if(mSqlGetObjIdByHash == null) {
                    String sql = new StringBuilder()
                        .append("SELECT ").append(MObject.COL_ID).append(" FROM ").append(MObject.TABLE).append(" WHERE ")
                        .append(MObject.COL_SHORT_UNIVERSAL_HASH).append("=?").append(" AND ")
                        .append(MObject.COL_UNIVERSAL_HASH).append("=?").toString();
                    mSqlGetObjIdByHash = db.compileStatement(sql);
                }
            }
        }
                
        synchronized (mSqlGetObjIdByHash) {
        	mSqlGetObjIdByHash.bindLong(1, Util.shortHash(hash));
        	mSqlGetObjIdByHash.bindBlob(2, hash);
        	try {
        		return mSqlGetObjIdByHash.simpleQueryForLong();
        	} catch(SQLiteDoneException e) {
        		return -1;
        	}
        }
    }

	public long getTotalCountOfObjects() {
        SQLiteDatabase db = initializeDatabase();
        if (mSqlObjectCount == null) {
            synchronized (this) {
                if(mSqlObjectCount == null) {
                    String sql = new StringBuilder()
                        .append("SELECT COUNT(*) FROM ").append(MObject.TABLE).toString();
                    mSqlObjectCount = db.compileStatement(sql);
                }
            }
        }
                
        synchronized (mSqlObjectCount) {
    		return mSqlObjectCount.simpleQueryForLong();
        }
	}

	public boolean isObjectFromLocalDevice(long objectId) {
	    MObject object = this.getObjectForId(objectId);
	    DeviceManager deviceMan = new DeviceManager(mDatabase);
	    MDevice device = deviceMan.getDeviceForId(object.deviceId_);
	    long myDeviceName = deviceMan.getLocalDeviceName();
	    if (device.deviceName_ != myDeviceName) {
	        return false;
	    }
	    IdentitiesManager idMan = new IdentitiesManager(mDatabase);
	    MIdentity senderId = idMan.getIdentityForId(device.identityId_);
	    return senderId != null && senderId.owned_;
	}

	public Long getLatestFeedRenderable(long feed_id) {
        SQLiteDatabase db = initializeDatabase();
        if (mSqlGetLatestRenderableId == null) {
            synchronized (this) {
                if(mSqlGetLatestRenderableId == null) {
                    String sql = new StringBuilder()
                        .append("SELECT ").append(MObject.COL_ID).append(" FROM ").append(MObject.TABLE).append(" WHERE ")
                        .append(MObject.COL_RENDERABLE).append("=1 AND ").append(MObject.COL_FEED_ID).append("=? ORDER BY ")
                        .append(MObject.COL_LAST_MODIFIED_TIMESTAMP).append(" DESC LIMIT 1") .toString();
                    mSqlGetLatestRenderableId = db.compileStatement(sql);
                }
            }
        }
                
        synchronized (mSqlGetLatestRenderableId) {
        	mSqlGetLatestRenderableId.bindLong(1, feed_id);
        	try {
        		return mSqlGetLatestRenderableId.simpleQueryForLong();
        	} catch(SQLiteDoneException e) {
        		return null;
        	}
        }
	}

	public Cursor getIdCursorForFeed(long feedId) {
        SQLiteDatabase db = initializeDatabase();
            StringBuilder sql = new StringBuilder(100).append("SELECT ").append(MObject.COL_ID)
                .append(" FROM ").append(MObject.TABLE)
                .append(" WHERE ").append(MObject.COL_FEED_ID).append("=?");
        String query = sql.toString();

        String[] selectionArgs = new String[] { String.valueOf(feedId) };
        return db.rawQuery(query, selectionArgs);
    }

	public Cursor getTypedIdCursorForFeed(String type, long feedId) {
        SQLiteDatabase db = initializeDatabase();
        StringBuilder sql = new StringBuilder(100).append("SELECT ").append(MObject.COL_ID)
            .append(" FROM ").append(MObject.TABLE)
            .append(" WHERE ").append(MObject.COL_FEED_ID).append("=? AND ").append(MObject.COL_TYPE).append("=?")
            .append(" ORDER BY ").append(MObject.COL_LAST_MODIFIED_TIMESTAMP).append(" ASC");
	    String query = sql.toString();
	
	    String[] selectionArgs = new String[] { String.valueOf(feedId), type };
	    return db.rawQuery(query, selectionArgs);
	}

	public int getLikeCount(long objId) {
		SQLiteDatabase db = initializeDatabase();
		if (mSqlLikeCount == null) {
			synchronized(this) {
				if (mSqlLikeCount == null) {
					StringBuilder sql = new StringBuilder(80)
						.append("SELECT ").append(DbLikeCache.COUNT)
						.append(" FROM ").append(DbLikeCache.TABLE)
						.append(" WHERE ").append(DbLikeCache.PARENT_OBJ).append("=?");
					mSqlLikeCount = db.compileStatement(sql.toString());
				}
			}
		}

		synchronized(mSqlLikeCount) {
			mSqlLikeCount.bindLong(1, objId);
			try {
				return (int)mSqlLikeCount.simpleQueryForLong();
			} catch (SQLiteDoneException e) {
				return 0;
			}
		}
	}

    public long[] objectsToEncode() {
    	if (mSqlGetObjectsToEncode == null) {
    		mSqlGetObjectsToEncode = new StringBuilder(80)
    			.append("SELECT ").append(MObject.COL_ID)
    			.append(" FROM ").append(MObject.TABLE)
    			.append(" WHERE ").append(MObject.COL_ENCODED_ID).append(" is null").toString();
    	}
        Cursor c = initializeDatabase().rawQuery(mSqlGetObjectsToEncode, null);
        int i = 0;
        long[] ids = new long[c.getCount()];
        while (c.moveToNext()) {
            ids[i++] = c.getLong(0);
        }
        try {
            return ids;
        } finally {
            c.close();
        }
    }

	@Override
	public synchronized void close() {
		if (mSqlInsertObj != null) {
			mSqlInsertObj.close();
			mSqlInsertObj = null;
		}
		if (mSqlUpdateObj != null) {
			mSqlUpdateObj.close();
			mSqlUpdateObj = null;
		}
		if (mSqlGetObjIdByHash != null) {
			mSqlGetObjIdByHash.close();
			mSqlGetObjIdByHash = null;
		}
		if (mSqlObjectCount != null) {
			mSqlObjectCount.close();
			mSqlObjectCount = null;
		}
		if (mSqlGetLatestRenderableId != null) {
			mSqlGetLatestRenderableId.close();
			mSqlGetLatestRenderableId = null;
		}
		if (mSqlLikeCount != null) {
			mSqlLikeCount.close();
			mSqlLikeCount = null;
		}
		if (mSqlUpdateObjPipelineMetadata != null) {
			mSqlUpdateObjPipelineMetadata.close();
			mSqlUpdateObjPipelineMetadata = null;
		}
		if (mSqlUpdateObjEncodeMetadata != null) {
			mSqlUpdateObjEncodeMetadata.close();
			mSqlUpdateObjEncodeMetadata = null;
		}
	}
}
