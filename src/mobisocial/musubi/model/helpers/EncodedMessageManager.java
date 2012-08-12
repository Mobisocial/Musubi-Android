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
import mobisocial.musubi.model.MEncodedMessage;
import mobisocial.musubi.model.MObject;
import mobisocial.musubi.model.MPendingUpload;
import mobisocial.musubi.util.Util;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;

public class EncodedMessageManager extends ManagerBase {
	SQLiteStatement sqlInsertEncoded_;
	SQLiteStatement sqlUpdateEncoded_;
	SQLiteStatement mSqlGetEncodedIdByHash;
	SQLiteStatement mSqlGetFeedIdForEncoded;
	String mSqlGetMetadataById;
	String mSqlObjectsToDecode;
	String mSqlGetEncodedDataById;

    public EncodedMessageManager(SQLiteOpenHelper databaseSource) {
        super(databaseSource);
    }
    public EncodedMessageManager(SQLiteDatabase db) {
        super(db);
    }
	
	public void insertEncoded(MEncodedMessage encoded) {
		SQLiteDatabase db = initializeDatabase();
		if(sqlInsertEncoded_ == null) {
			synchronized (this) {
				if(sqlInsertEncoded_ == null) {
					sqlInsertEncoded_ = db.compileStatement(
						"INSERT INTO " + MEncodedMessage.TABLE + 
						" (" +
						MEncodedMessage.COL_DEVICE_ID + "," +
						MEncodedMessage.COL_HASH + "," +
						MEncodedMessage.COL_OUTBOUND + "," +
						MEncodedMessage.COL_PROCESSED + "," +
						MEncodedMessage.COL_SENDER + "," +
						MEncodedMessage.COL_ENCODED + "," +
						MEncodedMessage.COL_SHORT_HASH + "," +
						MEncodedMessage.COL_PROCESSED_TIME +
						") " +
						"VALUES (?,?,?,?,?,?,?,?)"
					);
				}
			}
		}
		synchronized (sqlInsertEncoded_) {
			if(encoded.fromDevice_ == null) {
				sqlInsertEncoded_.bindNull(1);
			} else {
				sqlInsertEncoded_.bindLong(1, encoded.fromDevice_);
			}
			if(encoded.hash_ == null) {
				sqlInsertEncoded_.bindNull(2);
			} else {
				sqlInsertEncoded_.bindBlob(2, encoded.hash_);
			}
			sqlInsertEncoded_.bindLong(3, encoded.outbound_ ? 1 : 0);
			sqlInsertEncoded_.bindLong(4, encoded.processed_ ? 1 : 0);
			if(encoded.fromIdentityId_ == null) {
				sqlInsertEncoded_.bindNull(5);
			} else {
				sqlInsertEncoded_.bindLong(5, encoded.fromIdentityId_);
			}
			sqlInsertEncoded_.bindBlob(6, encoded.encoded_);
			if(encoded.shortHash_ == null) {
				sqlInsertEncoded_.bindNull(7);
			} else {
				sqlInsertEncoded_.bindLong(7, encoded.shortHash_);
			}
			sqlInsertEncoded_.bindLong(8, encoded.processedTime_);
			encoded.id_ = sqlInsertEncoded_.executeInsert();
		}
	}
	public void updateEncodedMetadata(MEncodedMessage encoded) {
		SQLiteDatabase db = initializeDatabase();
		if(sqlUpdateEncoded_ == null) {
			synchronized (this) {
				if(sqlUpdateEncoded_ == null) {
					sqlUpdateEncoded_ = db.compileStatement(
						"UPDATE " + MEncodedMessage.TABLE + 
						" SET " + MEncodedMessage.COL_DEVICE_ID + "=?," +
						MEncodedMessage.COL_HASH + "=?," +
						MEncodedMessage.COL_OUTBOUND + "=?," +
						MEncodedMessage.COL_PROCESSED + "=?," +
						MEncodedMessage.COL_SENDER + "=?," +
						MEncodedMessage.COL_SHORT_HASH + "=?, " +
						MEncodedMessage.COL_PROCESSED_TIME + "=? " +
						" WHERE " + MEncodedMessage.COL_ID + "=? "
					);
				}
			}
		}
		synchronized (sqlUpdateEncoded_) {
			if(encoded.fromDevice_ == null) {
				sqlUpdateEncoded_.bindNull(1);
			} else {
				sqlUpdateEncoded_.bindLong(1, encoded.fromDevice_);
			}
			if(encoded.hash_ == null) {
				sqlUpdateEncoded_.bindNull(2);
			} else {
				sqlUpdateEncoded_.bindBlob(2, encoded.hash_);
			}
			sqlUpdateEncoded_.bindLong(3, encoded.outbound_ ? 1 : 0);
			sqlUpdateEncoded_.bindLong(4, encoded.processed_ ? 1 : 0);
			if(encoded.fromIdentityId_ == null) {
				sqlUpdateEncoded_.bindNull(5);
			} else {
				sqlUpdateEncoded_.bindLong(5, encoded.fromIdentityId_);
			}
			if(encoded.shortHash_ == null) {
				sqlUpdateEncoded_.bindNull(6);
			} else {
				sqlUpdateEncoded_.bindLong(6, encoded.shortHash_);
			}
			sqlUpdateEncoded_.bindLong(7, encoded.processedTime_);
			sqlUpdateEncoded_.bindLong(8, encoded.id_);
			sqlUpdateEncoded_.execute();
		}
	}

	public byte[] lookupEncodedDataById(long id) {
		if (mSqlGetEncodedDataById == null) {
			StringBuilder sql = new StringBuilder(80);
			sql.append("SELECT ").append(MEncodedMessage.COL_ENCODED)
				.append(" FROM ").append(MEncodedMessage.TABLE)
				.append(" WHERE ").append(MEncodedMessage.COL_ID).append("=?");
			mSqlGetEncodedDataById = sql.toString();
		}
		SQLiteDatabase db = initializeDatabase();
		Cursor c = db.rawQuery(mSqlGetEncodedDataById, new String[] { Long.toString(id) });
		try {
			if (c.moveToFirst()) {
				return c.getBlob(0);
			}
			return null;
		} finally {
			c.close();
		}
	}

	public MEncodedMessage lookupById(long id) {
	    SQLiteDatabase db = initializeDatabase();
        String table = MEncodedMessage.TABLE;
        String[] columns = new String[] {
                MEncodedMessage.COL_SENDER,
                MEncodedMessage.COL_ENCODED,
                MEncodedMessage.COL_DEVICE_ID,
                MEncodedMessage.COL_SHORT_HASH,
                MEncodedMessage.COL_HASH,
                MEncodedMessage.COL_OUTBOUND,
                MEncodedMessage.COL_PROCESSED,
                MEncodedMessage.COL_PROCESSED_TIME,
                };
        String selection = MEncodedMessage.COL_ID + " = ?";
        String[] selectionArgs = new String[] { Long.toString(id) };
        String groupBy = null, having = null, orderBy = null;
        Cursor c = db.query(table, columns, selection, selectionArgs, groupBy, having, orderBy);
        if (!c.moveToFirst()) {
            return null;
        }
        MEncodedMessage encoded = new MEncodedMessage();
        encoded.id_ = id;
        encoded.fromIdentityId_ = c.isNull(0) ? null : c.getLong(0);
        encoded.encoded_ = c.getBlob(1);
        encoded.fromDevice_ = c.isNull(2) ? null : c.getLong(2);
        encoded.shortHash_ = c.isNull(3) ? null : c.getLong(3);
        encoded.hash_ = c.getBlob(4);
        encoded.outbound_ = c.getInt(5) != 0;
        encoded.processed_ = c.getInt(6) != 0;
        encoded.processedTime_ = c.getLong(7);
        try {
            return encoded;
        } finally {
            c.close();
        }
    }

	public MEncodedMessage lookupMetadataById(long id) {
	    SQLiteDatabase db = initializeDatabase();
	    if (mSqlGetMetadataById == null) {
	    	StringBuilder sql = new StringBuilder(100)
	    		.append("SELECT ")
	    		.append(MEncodedMessage.COL_SENDER).append(",")
	    		.append(MEncodedMessage.COL_DEVICE_ID).append(",")
	    		.append(MEncodedMessage.COL_SHORT_HASH).append(",")
	    		.append(MEncodedMessage.COL_HASH).append(",")
	    		.append(MEncodedMessage.COL_OUTBOUND).append(",")
	    		.append(MEncodedMessage.COL_PROCESSED).append(",")
	    		.append(MEncodedMessage.COL_PROCESSED_TIME);
	    	sql.append(" FROM ").append(MEncodedMessage.TABLE)
	    		.append(" WHERE ").append(MEncodedMessage.COL_ID).append("=?");
	    	mSqlGetMetadataById = sql.toString();
	    }
        String[] selectionArgs = new String[] { Long.toString(id) };
        Cursor c = db.rawQuery(mSqlGetMetadataById, selectionArgs);
        if (!c.moveToFirst()) {
            return null;
        }
        MEncodedMessage encoded = new MEncodedMessage();
        encoded.id_ = id;
        encoded.fromIdentityId_ = c.isNull(0) ? null : c.getLong(0);
        encoded.fromDevice_ = c.isNull(1) ? null : c.getLong(1);
        encoded.shortHash_ = c.isNull(2) ? null : c.getLong(2);
        encoded.hash_ = c.getBlob(3);
        encoded.outbound_ = c.getInt(4) != 0;
        encoded.processed_ = c.getInt(5) != 0;
        encoded.processedTime_ = c.getLong(6);
        try {
            return encoded;
        } finally {
            c.close();
        }
    }
	public boolean delete(long id) {
	    SQLiteDatabase db = initializeDatabase();
	    String whereClause = MEncodedMessage.COL_ID + " = ?";
	    String[] whereArgs = new String[] { Long.toString(id) };
	    return db.delete(MEncodedMessage.TABLE, whereClause, whereArgs) > 0;
	}

	/**
	 * Returns the encoded message ids that are ready to be sent--
	 * they are outbound, have been encoded, and are not pending uploads.
	 */
	public TLongLinkedList getUnsentOutboundIdsNotPending() {
		SQLiteDatabase db = initializeDatabase();
		StringBuilder pendingUploads = new StringBuilder(50)
		    .append("SELECT ").append(MObject.COL_ENCODED_ID)
		    .append(" FROM ").append(MObject.TABLE).append(",").append(MPendingUpload.TABLE)
		    .append(" WHERE ").append(MObject.TABLE).append(".").append(MObject.COL_ID)
		    .append("=").append(MPendingUpload.TABLE).append(".").append(MPendingUpload.COL_OBJECT_ID);
		StringBuilder selection = new StringBuilder(100)
		    .append(MEncodedMessage.COL_PROCESSED).append("=0 AND ")
		    .append(MEncodedMessage.COL_OUTBOUND + "=1 AND ")
		    .append(MEncodedMessage.COL_ID).append(" NOT IN ")
		    .append("(").append(pendingUploads).append(")");
		Cursor c = db.query(MEncodedMessage.TABLE, new String[] { MEncodedMessage.COL_ID }, 
			selection.toString(), null, null, null, MEncodedMessage.COL_ID + " ASC");
		TLongLinkedList ids = new TLongLinkedList();
		try {
			while(c.moveToNext()) {
				ids.add(c.getLong(0));
			}
			return ids;
		} finally {
			c.close();
		}
    }
	public int deleteProcessedOldItems(int daysOld) {
		SQLiteDatabase db = initializeDatabase();
		StringBuilder whereClause = new StringBuilder()
			.append(MEncodedMessage.COL_PROCESSED).append("=1 AND ")
			.append(MEncodedMessage.COL_PROCESSED_TIME).append("<?");
		long since = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000);
		String[] whereArgs = new String[] { String.valueOf(since) };
		return db.delete(MEncodedMessage.TABLE, whereClause.toString(), whereArgs);
    }

	public long[] getNonDecodedInboundIds() {
		if (mSqlObjectsToDecode == null) {
			StringBuilder sql = new StringBuilder(80);
			sql.append("SELECT ").append(MEncodedMessage.COL_ID)
				.append(" FROM ").append(MEncodedMessage.TABLE)
				.append(" WHERE ").append(MEncodedMessage.COL_PROCESSED).append("=0")
				.append(" AND ").append(MEncodedMessage.COL_OUTBOUND).append("=0")
				.append(" ORDER BY ").append(MEncodedMessage.COL_ID).append(" ASC");
			mSqlObjectsToDecode = sql.toString();
		}

		SQLiteDatabase db = initializeDatabase();
	    Cursor c = db.rawQuery(mSqlObjectsToDecode, null);
	    long[] ids = new long[c.getCount()];
	    int i = 0;
		try {
			while (c.moveToNext()) {
				ids[i++] = c.getLong(0);
			}
			return ids;
		} finally {
			c.close();
		}
	}

	public long getEncodedIdForHash(byte[] hash) {
        SQLiteDatabase db = initializeDatabase();
        if (mSqlGetEncodedIdByHash == null) {
            synchronized (this) {
                if(mSqlGetEncodedIdByHash == null) {
                    String sql = new StringBuilder()
                        .append("SELECT ").append(MEncodedMessage.COL_ID).append(" FROM ").append(MEncodedMessage.TABLE).append(" WHERE ")
                        .append(MEncodedMessage.COL_SHORT_HASH).append("=?").append(" AND ")
                        .append(MEncodedMessage.COL_HASH).append("=?").toString();
                    mSqlGetEncodedIdByHash = db.compileStatement(sql);
                }
            }
        }
                
        synchronized (mSqlGetEncodedIdByHash) {
        	mSqlGetEncodedIdByHash.bindLong(1, Util.shortHash(hash));
        	mSqlGetEncodedIdByHash.bindBlob(2, hash);
        	try {
        		return mSqlGetEncodedIdByHash.simpleQueryForLong();
        	} catch(SQLiteDoneException e) {
        		return -1;
        	}
        }
    }

	public long getFeedIdForEncoded(long encodedId) {
		SQLiteDatabase db = initializeDatabase();
		if (mSqlGetFeedIdForEncoded == null) {
			synchronized (this) {
				if (mSqlGetFeedIdForEncoded == null) {
					StringBuilder sql = new StringBuilder(50);
					sql.append(" SELECT ").append(MObject.COL_FEED_ID)
						.append(" FROM ").append(MObject.TABLE)
						.append(" WHERE ").append(MObject.COL_ENCODED_ID).append("=?")
						.append(" LIMIT 1");
					mSqlGetFeedIdForEncoded = db.compileStatement(sql.toString());
				}
			}
		}
		synchronized(mSqlGetFeedIdForEncoded) {
			mSqlGetFeedIdForEncoded.bindLong(1, encodedId);
			try {
				return mSqlGetFeedIdForEncoded.simpleQueryForLong();
			} catch (SQLiteDoneException e) {
				return -1;
			}
		}
	}

	@Override
	public synchronized void close() {
		if (sqlInsertEncoded_ != null) {
			sqlInsertEncoded_.close();
			sqlInsertEncoded_ = null;
    	}
		if (sqlUpdateEncoded_ != null) {
			sqlUpdateEncoded_.close();
			sqlUpdateEncoded_ = null;
    	}
		if (mSqlGetEncodedIdByHash != null) {
			mSqlGetEncodedIdByHash.close();
			mSqlGetEncodedIdByHash = null;
    	}
		if (mSqlGetFeedIdForEncoded != null) {
			mSqlGetFeedIdForEncoded.close();
			mSqlGetFeedIdForEncoded = null;
		}
	}
}
