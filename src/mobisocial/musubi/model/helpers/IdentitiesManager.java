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

import gnu.trove.list.array.TLongArrayList;
import gnu.trove.map.TLongIntMap;
import gnu.trove.map.hash.TLongIntHashMap;
import gnu.trove.procedure.TLongIntProcedure;

import java.math.BigInteger;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import mobisocial.crypto.IBHashedIdentity;
import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.crypto.IBIdentity;
import mobisocial.musubi.App;
import mobisocial.musubi.BJDNotImplementedException;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MMyAccount;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.provider.MusubiContentProvider.Provided;
import mobisocial.musubi.service.MusubiService;
import mobisocial.musubi.util.Util;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;

public class IdentitiesManager extends ManagerBase {
    public static final String PRE_INSTALL_IDENTITY_PRINCIPAL = "me@nowhere.com";
    public static final String PRE_INSTALL_MUSUBI_PRINCIPAL = "musubi-feedback@lists.stanford.edu";
    /**
     * Interval at which private keys are refreshed.
     */
	public static final long KEY_REFRESH_SECONDS = 30 * 24 * 60 * 60;

	SQLiteStatement sqlInsertIdentity_;
	SQLiteStatement sqlUpdateIdentity_;
	SQLiteStatement sqlGetIdentityId_;
	SQLiteStatement sqlIncrementSequenceNumber_;
	SQLiteStatement sqlIsBlacklisted_;
	SQLiteStatement sqlIsMe_;
	SQLiteStatement sqlHasThumbnail_;
	SQLiteStatement sqlHasMusubiThumbnail_;
	SQLiteStatement sqlGetThumbnail_;
	SQLiteStatement sqlGetMusubiThumbnail_;
	SQLiteStatement sqlUpdateThumbnail_;
	SQLiteStatement sqlUpdateMusubiThumnail_;
	String sqlGetIdentityForId_;
	String sqlIdentityWithThumbnails_;

    /**
     * Mime type that can be used to launch a VIEW intent for an identity.
     */
    public static final String MIME_TYPE = MusubiContentProvider.getType(Provided.IDENTITIES_ID);
	
	public IdentitiesManager(SQLiteOpenHelper databaseSource) {
		super(databaseSource);
	}
	
	public IdentitiesManager(SQLiteDatabase db) {
		super(db);
	}

	/* this is supposed to return a globally valid uri for sharing over wifi, etc */
	public static Uri uriForMyIBHashedIdentity() {
	    // return a long id instead; generate uri as MusubiContentProvider.uriForItem(Provided.IDENTITY, id)
	    BJDNotImplementedException.except("TODO: discard  me");
        return null;
	}

	/* this extracts a hash from the globally valid uri */
	public static IBHashedIdentity ibHashedIdentityForUri(Uri uri) {
		BJDNotImplementedException.except("TODO: discard  me");
		return null;
	}
	
	static final String[] STANDARD_FIELDS = new String[] {
		MIdentity.COL_ID,
		MIdentity.COL_TYPE,
		MIdentity.COL_PRINCIPAL_SHORT_HASH,
		MIdentity.COL_PRINCIPAL_HASH,
		MIdentity.COL_PRINCIPAL,
		MIdentity.COL_CLAIMED,
		MIdentity.COL_BLOCKED,
		MIdentity.COL_OWNED,
		MIdentity.COL_ANDROID_DATA_ID,
		MIdentity.COL_CONTACT_ID,
		MIdentity.COL_RECEIVED_PROFILE_VERSION,
		MIdentity.COL_SENT_PROFILE_VERSION,
		MIdentity.COL_NAME,
		MIdentity.COL_MUSUBI_NAME,
		MIdentity.COL_CREATED_AT,
		MIdentity.COL_UPDATED_AT,
		MIdentity.COL_NEXT_SEQUENCE_NUMBER,
		MIdentity.COL_HAS_SENT_EMAIL,
		MIdentity.COL_WHITELISTED
	};
	static final String[] WITH_THUMBNAILS = joinArrays(STANDARD_FIELDS, new String[] {
		MIdentity.COL_THUMBNAIL,
		MIdentity.COL_MUSUBI_THUMBNAIL,
	});

	static final int _id = 0;
	static final int type = 1;
	static final int principalShortHash = 2;
	static final int principalHash = 3;
	static final int principal = 4;
	static final int claimed = 5;
	static final int blocked = 6;
	static final int owned = 7;
	static final int androidDataId = 8;
	static final int contactId = 9;
	static final int receivedProfileVersion = 10;
	static final int sentProfileVersion = 11;
	static final int name = 12;
	static final int musubiName = 13;
	static final int createdAt = 14;
	static final int updatedAt = 15;
	static final int nextSequenceNumber = 16;
	static final int hasSentEmail = 17;
	static final int whitelisted = 18;
	static final int thumbnail = 19;
	static final int musubiThumbnail = 20;

	public List<MIdentity> getOwnedIdentities() {
		SQLiteDatabase db = initializeDatabase();
		Cursor c = db.query(MIdentity.TABLE, STANDARD_FIELDS,
			MIdentity.COL_OWNED + "=1",
			null,
			null, null, null
		);
		try {
			LinkedList<MIdentity> ids = new LinkedList<MIdentity>();
			while(c.moveToNext()) {
				ids.add(fillInStandardFields(c));
			}
			return ids;
		} finally {
			c.close();
		}	
	}

	public MIdentity getMyDefaultIdentity() {
	    List<MIdentity> mine = getOwnedIdentities();
	    if (mine.size() == 0) return null;
        return mine.get(mine.size() > 1 ? 1 : 0);
	}

	/**
	 * Gets a default identity "best-suited" for the given list of identities.
	 * @param party the identities to use in matching.
	 * @return
	 */
	public MIdentity getMyDefaultIdentity(MIdentity... party) {
	    if (party.length == 0) {
	        return getMyDefaultIdentity();
	    }
	    for (MIdentity identity : party) {
	        if (identity.owned_) {
	            return identity;
	        }
	    }

	    FeedManager fm = new FeedManager(initializeDatabase());
	    MyAccountManager am = new MyAccountManager(initializeDatabase());
	    MMyAccount[] accounts = am.getClaimedAccounts(null);
	    if (accounts.length == 0) {
	        return getMyDefaultIdentity();
	    }
	    if (accounts.length == 1) {
	        return getIdentityForId(accounts[0].identityId_);
	    }

	    TLongIntMap map = new TLongIntHashMap();
	    for (MMyAccount account : accounts) {
	        // Sum up the number of identities in myIdentitys' account feeds.
	        if (account.feedId_ == null) {
                continue;
            }

	        int count;
	        if (map.containsKey(account.identityId_)) {
	            count = map.get(account.identityId_);
	        } else {
	            count = 0;
	        }

	        count += fm.countIdentitiesInFeed(account.feedId_, party);
	        map.put(account.identityId_, count);
	    }

	    GetBestIdentity gbi = new GetBestIdentity();
	    map.forEachEntry(gbi);
	    if (gbi.bestIdentity == -1) {
	        return getMyDefaultIdentity();
	    }
	    return getIdentityForId(gbi.bestIdentity);
    }

	class GetBestIdentity  implements TLongIntProcedure {
	    long bestIdentity = -1;
        int bestCount = -1;

        @Override
        public boolean execute(long id, int count) {
            if (count > bestCount) {
                bestIdentity = id;
                bestCount = count;
            }
            return true;
        }
    };

	private MIdentity fillInStandardFields(Cursor c) {
		MIdentity id = new MIdentity();
		id.id_ = c.getLong(_id);
		id.type_ = Authority.values()[(int) c.getLong(type)];
		id.principalShortHash_ = c.getLong(principalShortHash);
		id.principalHash_ = c.getBlob(principalHash);
		id.principal_ = c.getString(principal);
		id.claimed_ = c.getLong(claimed) != 0;
		id.blocked_ = c.getLong(blocked) != 0;
		id.owned_ = c.getLong(owned) != 0;
		id.androidAggregatedContactId_ = c.isNull(androidDataId) ? null : c.getLong(androidDataId);
		id.contactId_ = c.isNull(contactId) ? null : c.getLong(contactId);
		id.receivedProfileVersion_ = c.getLong(receivedProfileVersion);
		id.sentProfileVersion_ = c.getLong(sentProfileVersion);
		id.name_ = c.getString(name);
		id.musubiName_ = c.getString(musubiName);
		id.createdAt_ = c.getLong(createdAt);
		id.updatedAt_ = c.getLong(updatedAt);
		id.nextSequenceNumber_ = c.getLong(nextSequenceNumber);
		id.hasSentEmail_ = c.getLong(hasSentEmail) != 0;
		id.whitelisted_ = c.getLong(whitelisted) != 0;
		return id;
	}
	
	public MIdentity getIdentityForIBHashedIdentity(IBHashedIdentity hid) {
		long id = getIdForIBHashedIdentity(hid);
		if (id <= 0) {
		    return null;
		}
		return getIdentityForId(id);
	}

	public MIdentity getIdentityForId(long id) {
	    if (sqlGetIdentityForId_ == null) {
	        synchronized (this) {
                StringBuilder sql = new StringBuilder(100).append("SELECT ");
                for (String c : STANDARD_FIELDS) {
                    sql.append(c).append(",");
                }
                sql.setLength(sql.length() - 1);
                sql.append(" FROM ").append(MIdentity.TABLE)
                    .append(" WHERE ").append(MIdentity.COL_ID).append("=?");
                sqlGetIdentityForId_ = sql.toString();
            }
	    }
		SQLiteDatabase db = initializeDatabase();
		String[] selectionArgs = new String[] { String.valueOf(id) };
        Cursor c = db.rawQuery(sqlGetIdentityForId_, selectionArgs);
        try {
            if (c.moveToNext()) {
                return fillInStandardFields(c);
            }
            return null;
        } finally {
            c.close();
        }
	}
	
	public TLongArrayList getIdentityIdsForAggregateContactId(long id) {
		SQLiteDatabase db = initializeDatabase();
		Cursor c = db.query(MIdentity.TABLE, new String[] { MIdentity.COL_ID },
			MIdentity.COL_ANDROID_DATA_ID + "=?",
			new String[] { 
				String.valueOf(id),
			},
			null, null, null
		);
		TLongArrayList ids = new TLongArrayList(4);
		try {
			while(c.moveToNext()) {
				ids.add(c.getLong(0));
			}
			return ids;
		} finally {
			c.close();
		}
	}
	
	public MIdentity[] getUpdatedIdentities(long lastUpdatedTime) {
		SQLiteDatabase db = initializeDatabase();
		Cursor c = db.query(MIdentity.TABLE, STANDARD_FIELDS,
			MIdentity.COL_UPDATED_AT + ">?", 
			new String[] {
				String.valueOf(lastUpdatedTime)
			},
			null, null, MIdentity.COL_ANDROID_DATA_ID //don't index by this, index by time if at all
		);
		MIdentity[] ids = new MIdentity[c.getCount()];
		int i = 0;
		try {
			while(c.moveToNext()) {
				ids[i++] = fillInStandardFields(c);
			}
		} finally {
			c.close();
		}
		
		return ids;
	}
	public MIdentity[] getIdentitiesForIds(long[] identityIds) {
		StringBuilder subselect = new StringBuilder(identityIds.length * 2 + 2);
	    for (long id : identityIds) {
	        subselect.append(",").append(id);
	    }
	    subselect.setCharAt(0, '(');
	    subselect.append(')');
	    SQLiteDatabase db = initializeDatabase();
	    String table = MIdentity.TABLE;
	    String selection = MIdentity.COL_ID + " in " + subselect;
	    String[] selectionArgs = null;
	    String having = null, groupBy = null, orderBy = null;
        Cursor c = db.query(table, STANDARD_FIELDS,
                selection, selectionArgs, groupBy, having, orderBy);

        MIdentity[] identities = new MIdentity[c.getCount()];
        int i = 0;
        while(c.moveToNext()) {
            identities[i++] = fillInStandardFields(c);
        }
        try {
            return identities;
        } finally {
            c.close();
        }
	}

	public MIdentity[] getIdentitiesForIdsGroupedByVisibleName(long[] identityIds) {
		StringBuilder sql = new StringBuilder();
		sql.append("SELECT ");
		for (String field : STANDARD_FIELDS) {
			sql.append(field).append(",");
		}
		sql.setLength(sql.length() - 1);
		sql.append(" FROM ").append(MIdentity.TABLE).append(" WHERE ")
			.append(MIdentity.COL_ID).append(" IN (");
		boolean first = true;
		for (long id : identityIds) {
			if (!first) sql.append(",");
			sql.append(id);
			first = false;
		}
		sql.append(") GROUP BY ").append(NAME_EXPR);
		sql.append(" ORDER BY ").append(NAME_EXPR);

		SQLiteDatabase db = initializeDatabase();
        Cursor c = db.rawQuery(sql.toString(), null);

        MIdentity[] identities = new MIdentity[c.getCount()];
        int i = 0;
        while(c.moveToNext()) {
            identities[i++] = fillInStandardFields(c);
        }
        try {
            return identities;
        } finally {
        	sql.setLength(0);
            c.close();
        }
	}

	static String[] joinArrays(String[] a, String[] b) {
		String[] c = new String[a.length + b.length];
		int i = 0;
		for(int j = 0; j < a.length; ++j)
			c[i++] = a[j];
		for(int j = 0; j < b.length; ++j)
			c[i++] = b[j];
		return c;
	}

	public MIdentity getIdentityWithThumbnailsForId(long id) {
		SQLiteDatabase db = initializeDatabase();
		if (sqlIdentityWithThumbnails_ == null) {
			StringBuilder sql = new StringBuilder(50);
			sql.append("SELECT ");
			for (String col : WITH_THUMBNAILS) {
				sql.append(col).append(",");
			}
			sql.setLength(sql.length() - 1);
			sql.append(" FROM ").append(MIdentity.TABLE)
				.append(" WHERE ").append(MIdentity.COL_ID).append("=?");
			sqlIdentityWithThumbnails_ = sql.toString();
		}
		Cursor c = db.rawQuery(sqlIdentityWithThumbnails_, new String[] { Long.toString(id) });
		try {
			if (c.moveToNext()) {
				MIdentity ident = fillInStandardFields(c);
				ident.thumbnail_ = c.getBlob(thumbnail);
				ident.musubiThumbnail_ = c.getBlob(musubiThumbnail);
				return ident;
			}
			return null;
		} finally {
			c.close();
		}
	}
	/** returns 0 if no id matches */
	public long getIdForIBHashedIdentity(IBHashedIdentity hid) {
		SQLiteDatabase db = initializeDatabase();
		if(sqlGetIdentityId_ == null) {
			synchronized(this) {
				if(sqlGetIdentityId_ == null) {
					sqlGetIdentityId_ = db.compileStatement(
						"SELECT " + MIdentity.COL_ID + " FROM " + MIdentity.TABLE + " WHERE " + MIdentity.COL_TYPE + "=? AND " + 
								MIdentity.COL_PRINCIPAL_SHORT_HASH + "=? AND " + MIdentity.COL_PRINCIPAL_HASH + "=?"
					);
				}
			}
		}
		synchronized (sqlGetIdentityId_) {
			sqlGetIdentityId_.bindLong(1, hid.authority_.ordinal());
			sqlGetIdentityId_.bindLong(2, Util.shortHash(hid.hashed_));
			sqlGetIdentityId_.bindBlob(3, hid.hashed_);
			try {
				return sqlGetIdentityId_.simpleQueryForLong();
			} catch(SQLiteDoneException e) {
				return 0;
			}
		}
	}
	public long insertIdentity(MIdentity id) {
		assert(id.principalHash_ != null && Util.shortHash(id.principalHash_) == id.principalShortHash_);
		SQLiteDatabase db = initializeDatabase();
		if(sqlInsertIdentity_ == null) {
			synchronized (this) {
				if(sqlInsertIdentity_ == null) {
					sqlInsertIdentity_ = db.compileStatement(
						"INSERT INTO " + MIdentity.TABLE + 
						" (" +
						standardColumnsForInsert() + "," +
						MIdentity.COL_THUMBNAIL + "," + 
						MIdentity.COL_MUSUBI_THUMBNAIL + 
						") " +
						"VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
					);
				}
			}
		}
		synchronized (sqlInsertIdentity_) {
			id.createdAt_ = new Date().getTime()/1000;
			id.updatedAt_ = new Date().getTime()/1000;
			setAllButThumbnailIdentityColumns(sqlInsertIdentity_, id);
			if(id.thumbnail_ == null) {
				sqlInsertIdentity_.bindNull(thumbnail);
			} else {
				sqlInsertIdentity_.bindBlob(thumbnail, id.thumbnail_);
			}
			if(id.musubiThumbnail_ == null) {
				sqlInsertIdentity_.bindNull(musubiThumbnail);
			} else {
				sqlInsertIdentity_.bindBlob(musubiThumbnail, id.musubiThumbnail_);
			}
			id.id_ = sqlInsertIdentity_.executeInsert();
			return id.id_;
		}
	}

	private static String standardColumnsForInsert() {
		String v = STANDARD_FIELDS[1];
		for(int i = 2; i < STANDARD_FIELDS.length; ++i) {
			v += "," + STANDARD_FIELDS[i];
		}
		return v;
	}

	public void updateIdentity(MIdentity id) {
		assert(id.id_ != 0);
		assert(id.principalHash_ != null && Util.shortHash(id.principalHash_) == id.principalShortHash_);
		SQLiteDatabase db = initializeDatabase();
		if(sqlUpdateIdentity_ == null) {
			synchronized (this) {
				if(sqlUpdateIdentity_ == null) {
					sqlUpdateIdentity_ = db.compileStatement(
						"UPDATE  " + MIdentity.TABLE + 
						" SET " +
						MIdentity.COL_TYPE + "=?," +
						MIdentity.COL_PRINCIPAL_SHORT_HASH + "=?," +
						MIdentity.COL_PRINCIPAL_HASH + "=?," +
						MIdentity.COL_PRINCIPAL + "=?," +
						MIdentity.COL_CLAIMED + "=?," +
						MIdentity.COL_BLOCKED + "=?," +
						MIdentity.COL_OWNED + "=?," +
						MIdentity.COL_ANDROID_DATA_ID + "=?," +
						MIdentity.COL_CONTACT_ID + "=?," +
						MIdentity.COL_RECEIVED_PROFILE_VERSION + "=?," +
						MIdentity.COL_SENT_PROFILE_VERSION + "=?," +
						MIdentity.COL_NAME + "=?, " +
						MIdentity.COL_MUSUBI_NAME + "=?, " +
						MIdentity.COL_CREATED_AT + "=?," +
						MIdentity.COL_UPDATED_AT + "=?," +
						MIdentity.COL_NEXT_SEQUENCE_NUMBER + "=?," +
						MIdentity.COL_HAS_SENT_EMAIL + "=?," +
						MIdentity.COL_WHITELISTED + "=?" +
						" WHERE " + MIdentity.COL_ID + "=?"
					);
				}
			}
		}
		id.updatedAt_ = new Date().getTime()/1000;
		synchronized (sqlUpdateIdentity_) {
			setAllButThumbnailIdentityColumns(sqlUpdateIdentity_, id);
			sqlUpdateIdentity_.bindLong(whitelisted+1, id.id_);
			sqlUpdateIdentity_.execute();
		}
	}

	private static void setAllButThumbnailIdentityColumns(SQLiteStatement statement, MIdentity id) {
		statement.bindLong(type, id.type_.ordinal());
		statement.bindLong(principalShortHash, id.principalShortHash_);
		statement.bindBlob(principalHash, id.principalHash_);
		if(id.principal_ == null) { 
			statement.bindNull(principal); 
		} else {
			statement.bindString(principal, id.principal_);
		}
		statement.bindLong(claimed, id.claimed_ ? 1 : 0);
		statement.bindLong(blocked, id.blocked_ ? 1 : 0);
		statement.bindLong(owned, id.owned_ ? 1 : 0);
		if(id.androidAggregatedContactId_ == null) {
			statement.bindNull(androidDataId);
		} else {
			statement.bindLong(androidDataId, id.androidAggregatedContactId_);
		}
		if(id.contactId_ == null) {
			statement.bindNull(contactId);
		} else {	
			statement.bindLong(contactId, id.contactId_);
		}
		statement.bindLong(receivedProfileVersion, id.receivedProfileVersion_);
		statement.bindLong(sentProfileVersion, id.sentProfileVersion_);
		if(id.name_ == null) { 
			statement.bindNull(name);
		} else {
			statement.bindString(name, id.name_);
		}
		if(id.musubiName_ == null) { 
			statement.bindNull(musubiName);
		} else {
			statement.bindString(musubiName, id.musubiName_);
		}
		if(id.createdAt_ != 0) {
			statement.bindLong(createdAt, id.createdAt_);
		}
		if(id.updatedAt_ != 0) {
			statement.bindLong(updatedAt, id.updatedAt_);
		}
		statement.bindLong(nextSequenceNumber, id.nextSequenceNumber_);
		statement.bindLong(hasSentEmail, id.hasSentEmail_ ? 1 : 0);
		statement.bindLong(whitelisted, id.whitelisted_ ? 1 : 0);
	}

	/** MIdentities row must have populated id and sequence number */
	public void incrementSequenceNumber(MIdentity to) {
		SQLiteDatabase db = initializeDatabase();
		if(sqlIncrementSequenceNumber_ == null) {
			synchronized(this) {
				if(sqlIncrementSequenceNumber_ == null) {
					sqlIncrementSequenceNumber_ = db.compileStatement(
						"UPDATE " + MIdentity.TABLE +
						" SET " + MIdentity.COL_NEXT_SEQUENCE_NUMBER + "=" + MIdentity.COL_NEXT_SEQUENCE_NUMBER + "+1" +
						" WHERE " + MIdentity.COL_ID + "=? " 
					);
				}
			}
		}
		
		synchronized (sqlIncrementSequenceNumber_) {
			sqlIncrementSequenceNumber_.bindLong(1, to.id_);
			sqlIncrementSequenceNumber_.execute();
		}
		//if they had a valid sequence number before, they still will... otherwise undefined
		++to.nextSequenceNumber_;
	}
	public boolean isBlacklisted(MIdentity from) {
		SQLiteDatabase db = initializeDatabase();
		if(sqlIsBlacklisted_ == null) {
			synchronized(this) {
				if(sqlIsBlacklisted_ == null) {
					sqlIsBlacklisted_ = db.compileStatement(
						"SELECT COUNT(*) FROM " + MIdentity.TABLE + " WHERE " + MIdentity.COL_ID + "=? AND " + MIdentity.COL_BLOCKED + "=1"
					);
				}
			}
		}
		synchronized (sqlIsBlacklisted_) {
			sqlIsBlacklisted_.bindLong(1, from.id_);
			return sqlIsBlacklisted_.simpleQueryForLong() > 0;
		}
	}
	public boolean isMe(IBHashedIdentity ibHashedIdentity) {
		SQLiteDatabase db = initializeDatabase();
		if(sqlIsMe_ == null) {
			synchronized(this) {
				if(sqlIsMe_ == null) {
					sqlIsMe_ = db.compileStatement(
						"SELECT COUNT(*) FROM " + MIdentity.TABLE + " WHERE " + MIdentity.COL_TYPE + "=? AND " +
								MIdentity.COL_PRINCIPAL_SHORT_HASH + "=? AND " +
						        MIdentity.COL_PRINCIPAL_HASH + "=? AND " + MIdentity.COL_OWNED + "=1"
					);
				}
			}
		}
		synchronized (sqlIsMe_) {
			sqlIsMe_.bindLong(1, ibHashedIdentity.authority_.ordinal());
			sqlIsMe_.bindLong(2, Util.shortHash(ibHashedIdentity.hashed_));
			sqlIsMe_.bindBlob(3, ibHashedIdentity.hashed_);
			return sqlIsMe_.simpleQueryForLong() > 0;
		}
	}
	public boolean hasThumbnail(MIdentity id) {
		SQLiteDatabase db = initializeDatabase();
		if(sqlHasThumbnail_ == null) {
			synchronized(this) {
				if(sqlHasThumbnail_ == null) {
					sqlHasThumbnail_ = db.compileStatement(
						"SELECT " + MIdentity.COL_THUMBNAIL + " IS NOT NULL FROM " + MIdentity.TABLE + " WHERE " + MIdentity.COL_ID + "=?"
					);
				}
			}
		}
		synchronized (sqlHasThumbnail_) {
			sqlHasThumbnail_.bindLong(1, id.id_);
			try {
				return sqlHasThumbnail_.simpleQueryForLong() != 0;
			} catch(SQLiteDoneException e) {
				return false;
			}
		}
	}
	public boolean hasMusubiThumbnail(MIdentity id) {
		SQLiteDatabase db = initializeDatabase();
		if(sqlHasMusubiThumbnail_ == null) {
			synchronized(this) {
				if(sqlHasMusubiThumbnail_ == null) {
					sqlHasMusubiThumbnail_ = db.compileStatement(
						"SELECT " + MIdentity.COL_MUSUBI_THUMBNAIL + " IS NOT NULL FROM " + MIdentity.TABLE + " WHERE " + MIdentity.COL_ID + "=?"
					);
				}
			}
		}
		synchronized (sqlHasMusubiThumbnail_) {
			sqlHasMusubiThumbnail_.bindLong(1, id.id_);
			try {
				return sqlHasMusubiThumbnail_.simpleQueryForLong() != 0;
			} catch(SQLiteDoneException e) {
				return false;
			}
		}
	}
	public byte[] getThumbnail(MIdentity id) {
		SQLiteDatabase db = initializeDatabase();
		Cursor c = db.rawQuery("SELECT " + MIdentity.COL_THUMBNAIL + " FROM " + MIdentity.TABLE + " WHERE " + MIdentity.COL_ID + "=?",
					new String[] {String.valueOf(id.id_)});
		try {
			while(c.moveToNext()) {
				id.thumbnail_ = c.getBlob(0);
				return id.thumbnail_;
			}
			return null;
		} finally {
			c.close();
		}
	}
	public byte[] getMusubiThumbnail(MIdentity id) {
		SQLiteDatabase db = initializeDatabase();
		Cursor c = db.rawQuery("SELECT " + MIdentity.COL_MUSUBI_THUMBNAIL + " FROM " + MIdentity.TABLE + " WHERE " + MIdentity.COL_ID + "=?",
					new String[] {String.valueOf(id.id_)});
		try {
			while(c.moveToNext()) {
				id.musubiThumbnail_ = c.getBlob(0);
				return id.musubiThumbnail_;
			}
			return null;
		} finally {
			c.close();
		}
	}
	public void updateThumbnail(MIdentity id) {
		SQLiteDatabase db = initializeDatabase();
		if(sqlUpdateThumbnail_ == null) {
			synchronized(this) {
				if(sqlUpdateThumbnail_ == null) {
					sqlUpdateThumbnail_ = db.compileStatement(
						"UPDATE " + MIdentity.TABLE + " SET " + MIdentity.COL_THUMBNAIL + "=? WHERE " + MIdentity.COL_ID + "=?"
					);
				}
			}
		}
		synchronized (sqlUpdateThumbnail_) {
			if(id.thumbnail_ == null) {
				sqlUpdateThumbnail_.bindNull(1);
			} else {
				sqlUpdateThumbnail_.bindBlob(1, id.thumbnail_);
			}
			sqlUpdateThumbnail_.bindLong(2, id.id_);
			sqlUpdateThumbnail_.execute();
		}
	}
	public void updateMusubiThumbnail(MIdentity id) {
		SQLiteDatabase db = initializeDatabase();
		if(sqlUpdateMusubiThumnail_ == null) {
			synchronized(this) {
				if(sqlUpdateMusubiThumnail_ == null) {
					sqlUpdateMusubiThumnail_ = db.compileStatement(
							"UPDATE " + MIdentity.TABLE + " SET " + MIdentity.COL_MUSUBI_THUMBNAIL + "=? WHERE " + MIdentity.COL_ID + "=?"
					);
				}
			}
		}
		synchronized (sqlUpdateMusubiThumnail_) {
			if(id.musubiThumbnail_ == null) {
				sqlUpdateMusubiThumnail_.bindNull(1);
			} else {
				sqlUpdateMusubiThumnail_.bindBlob(1, id.musubiThumbnail_);
			}
			sqlUpdateMusubiThumnail_.bindLong(2, id.id_);
			sqlUpdateMusubiThumnail_.execute();
		}
	}
	public static long computeTemporalFrameFromHash(byte[] hashed) {
	    long offset = new BigInteger(hashed)
	        .mod(BigInteger.valueOf(KEY_REFRESH_SECONDS)).longValue();
	    long interval = (new Date().getTime() / 1000 - offset) / KEY_REFRESH_SECONDS;
	    return (interval * KEY_REFRESH_SECONDS) + offset;
	}

	public static long computeTemporalFrameFromPrincipal(String principal) {
	    return computeTemporalFrameFromHash(IBIdentity.digestPrincipal(principal));
	}

	public static IBHashedIdentity toIBHashedIdentity(MIdentity i, long temporalFrame) {
		return new IBHashedIdentity(i.type_, i.principalHash_, temporalFrame);
	}

	public static IBIdentity toIBIdentity(MIdentity i, long temporalFrame) {
		if(i.principal_ == null)
			return null;
		return new IBIdentity(i.type_, i.principal_, temporalFrame);
	}
	
	static final String NAME_EXPR =
			"COALESCE(" + MIdentity.COL_MUSUBI_NAME + "," + MIdentity.COL_NAME + "," + MIdentity.COL_PRINCIPAL + "," + MIdentity.COL_PRINCIPAL_SHORT_HASH + ")";

	/**
	 * Returns a cursor of whitelisted identities
	 */
	public Cursor getWhiteListIdentitiesCursor() {
		SQLiteDatabase db = initializeDatabase();
		String table = MIdentity.TABLE;
        String[] columns = new String[] { MIdentity.COL_ID, NAME_EXPR + " AS displayname" };
        String selection = MIdentity.COL_WHITELISTED + "=1 AND " + MIdentity.COL_OWNED + " = 0";
        String[] selectionArgs = null;
        String groupBy = null, having = null;
        String orderBy = MIdentity.COL_CLAIMED + " DESC, displayname COLLATE NOCASE ASC";
        Cursor c = db.query(
                table, columns, selection, selectionArgs, groupBy, having, orderBy);
		
		return c;
	}
	
	/**
	 * Returns a cursor of graylisted identities
	 */
	public Cursor getGrayListIdentitiesCursor() {
		//TODO: in developer mode, show don't supress unknown users.
		SQLiteDatabase db = initializeDatabase();
		int localType = IBHashedIdentity.Authority.Local.ordinal();
		String table = MIdentity.TABLE; 
        String[] columns = new String[] { MIdentity.COL_ID, NAME_EXPR + " AS displayname" };
        String selection = MIdentity.COL_WHITELISTED + "=0 AND " + MIdentity.COL_OWNED + " = 0 AND " + MIdentity.COL_TYPE + " <> " + localType + " AND "
				+ "(" 
        			+ MIdentity.TABLE + "." + MIdentity.COL_MUSUBI_NAME + " IS NOT NULL OR " 
					+ MIdentity.TABLE + "." + MIdentity.COL_NAME + " IS NOT NULL OR "
					+ MIdentity.TABLE + "." + MIdentity.COL_THUMBNAIL + " IS NOT NULL OR "
					+ MIdentity.TABLE + "." + MIdentity.COL_MUSUBI_THUMBNAIL + " IS NOT NULL OR "
					+ MIdentity.TABLE + "." + MIdentity.COL_PRINCIPAL + " IS NOT NULL" 
				+ ")";
        String[] selectionArgs = null;
        String groupBy = null, having = null;
        String orderBy = MIdentity.COL_BLOCKED + " ASC," + MIdentity.COL_CLAIMED + " DESC, displayname COLLATE NOCASE ASC";
        Cursor c = db.query(
                table, columns, selection, selectionArgs, groupBy, having, orderBy);
		
		return c;
	}
	/**
	 * Returns a count of graylisted identities that need to be dealt with by the user
	 */
	public int getPendingGraylistCount() {
		//TODO: in developer mode, show don't supress unknown users.
		SQLiteDatabase db = initializeDatabase();
		String table = MIdentity.TABLE;
        String[] columns = new String[] { "COUNT(" + MIdentity.COL_ID + ")" };
        String selection = MIdentity.COL_WHITELISTED + "=0 AND " + MIdentity.COL_OWNED + " = 0" + " AND " + MIdentity.COL_BLOCKED +"=0" +
        		 " AND " + MIdentity.COL_TYPE +"<>" + Authority.Local.ordinal() + " AND "
			+ "(" 
    			+ MIdentity.TABLE + "." + MIdentity.COL_MUSUBI_NAME + " IS NOT NULL OR " 
				+ MIdentity.TABLE + "." + MIdentity.COL_NAME + " IS NOT NULL OR "
				+ MIdentity.TABLE + "." + MIdentity.COL_THUMBNAIL + " IS NOT NULL OR "
				+ MIdentity.TABLE + "." + MIdentity.COL_MUSUBI_THUMBNAIL + " IS NOT NULL OR "
				+ MIdentity.TABLE + "." + MIdentity.COL_PRINCIPAL + " IS NOT NULL" 
			+ ")";

        String[] selectionArgs = null;
        String groupBy = null, having = null;
        String orderBy = null;
        Cursor c = db.query(
                table, columns, selection, selectionArgs, groupBy, having, orderBy);
		try {
			while(c.moveToNext()) {
				return c.getInt(0);
			}
			return 0;
		} finally {
			c.close();
		}
	}
	
	
	/**
	 * Returns a subquery of all of this device's owned identities, in the form:
	 * (2,3,9)
	 */
	public String getOwnedIdentitiesSubquery() {
		SQLiteDatabase db = initializeDatabase();
	    String table = MIdentity.TABLE;
        String[] columns = new String[] { MIdentity.COL_ID };
        String selection = MIdentity.COL_OWNED + " = 1";
        String[] selectionArgs = null;
        String groupBy = null, having = null, orderBy = null;
        Cursor c = db.query(
                table, columns, selection, selectionArgs, groupBy, having, orderBy);
        if (!c.moveToFirst()) {
            try {
                return "(-1)";
            } finally {
                c.close();
            }
        }
        StringBuilder b = new StringBuilder("(");
        do {
            b.append(c.getLong(0)).append(",");
        } while (c.moveToNext());
        b.setCharAt(b.length() - 1, ')');
        try {
            return b.toString();
        } finally {
            c.close();
        }
	}

	public IBIdentity getIBIdentityForIBHashedIdentity(IBHashedIdentity hid) {
		MIdentity ident = getIdentityForIBHashedIdentity(hid);
		if(ident == null || ident.principal_ == null)
			return null;
		return new IBIdentity(hid.authority_, ident.principal_, hid.temporalFrame_);
	}

	/**
	 * A stub identity Musubi ships with.
	 */
	public static IBIdentity getPreInstallIdentity() {
        return new IBIdentity(IBHashedIdentity.Authority.Local,
                IdentitiesManager.PRE_INSTALL_IDENTITY_PRINCIPAL, 0);
    }
	/**
	 * A stub identity Musubi ships with.
	 */
	public static IBIdentity getPreInstallMusubiIdentity() {
        return new IBIdentity(IBHashedIdentity.Authority.Local,
                IdentitiesManager.PRE_INSTALL_MUSUBI_PRINCIPAL, 0);
    }

	/**
	 * Sets the thumbnail for all of this user's owned identities.
	 * @See ProfilePushProcessor
	 * @See MusubiService
	 */
    public void updateMyProfileThumbnail(Context context, byte[] data, boolean notify) {
    	Date now = new Date();
    	SQLiteDatabase db = initializeDatabase();
    	db.beginTransaction();
    	try {
	        for (MIdentity me : getOwnedIdentities()) {
	            me.musubiThumbnail_ = data;
	            me.receivedProfileVersion_ = now.getTime();
	            updateIdentity(me);
	            updateMusubiThumbnail(me);
	            App.getContactCache(context).invalidate(me.id_);
	        }
	        db.setTransactionSuccessful();
    	} finally {
    		db.endTransaction();
    	}
        if (notify) {
            context.getContentResolver().notifyChange(MusubiService.MY_PROFILE_UPDATED, null);
        	context.getContentResolver().notifyChange(MusubiService.PRIMARY_CONTENT_CHANGED, null);
        }
    }
    /**
     * Sets the name for all of this user's owned identities.
     * @See ProfilePushProcessor
     * @See MusubiService
     */
    public void updateMyProfileName(Context context, String name, boolean notify) {
    	Date now = new Date();
    	SQLiteDatabase db = initializeDatabase();
    	db.beginTransaction();
    	try {
	        for (MIdentity me : getOwnedIdentities()) {
	            me.musubiName_ = name;
	            me.receivedProfileVersion_ = now.getTime();
	            updateIdentity(me);
	        }
	        db.setTransactionSuccessful();
    	} finally {
    		db.endTransaction();
    	}
        if (notify) {
            context.getContentResolver().notifyChange(MusubiService.MY_PROFILE_UPDATED, null);
        	context.getContentResolver().notifyChange(MusubiService.PRIMARY_CONTENT_CHANGED, null);
        }
    }

    public boolean isWhitelisted(MIdentity owner) {
        return (owner.owned_ || owner.androidAggregatedContactId_ != null);
    }

    public MIdentity ensureClaimedIdentity(IBHashedIdentity hid) {
        MIdentity id = getIdentityForIBHashedIdentity(hid);
        if(id != null) {
            if(!id.claimed_) {
                id.claimed_ = true;
                updateIdentity(id);
            }
            return id;
        }
        id = new MIdentity();
        id.claimed_ = true;
        id.principalHash_ = hid.hashed_;
        id.principalShortHash_ = Util.shortHash(hid.hashed_);
        id.type_ = hid.authority_;
        id.hasSentEmail_ = true;
        insertIdentity(id);
        return id;
    }

    public static String androidLookupKeyForIdentitiy(Context context, MIdentity person) {
        if (person.androidAggregatedContactId_ == null) {
            return null;
        }

        Uri uri = Contacts.CONTENT_URI;
        String[] projection = new String[] { Contacts.LOOKUP_KEY };
        String selection = Contacts._ID + " = ?";
        String[] selectionArgs = new String[] { Long.toString(person.androidAggregatedContactId_) };
        String sortOrder = null;
        Cursor c = context.getContentResolver().query(uri, projection, selection, selectionArgs, sortOrder);
        try {
            if (c.moveToFirst()) {
                return c.getString(0);
            } else {
                return null;
            }
        } finally {
            c.close();
        }
    }

    public boolean hasConnectedAccounts() {
        List<MIdentity> owned = getOwnedIdentities();
        // we create a dummy account for the wizard.
        return owned.size() > 1;
    }

    @Override
    public synchronized void close() {
    	if (sqlInsertIdentity_ != null) {
    		sqlInsertIdentity_.close();
    		sqlInsertIdentity_ = null;
    	}
    	if (sqlUpdateIdentity_ != null) {
    		sqlUpdateIdentity_.close();
    		sqlUpdateIdentity_ = null;
    	}
    	if (sqlGetIdentityId_ != null) {
    		sqlGetIdentityId_.close();
    		sqlGetIdentityId_ = null;
    	}
    	if (sqlIncrementSequenceNumber_ != null) {
    		sqlIncrementSequenceNumber_.close();
    		sqlIncrementSequenceNumber_ = null;
    	}
    	if (sqlIsBlacklisted_ != null) {
    		sqlIsBlacklisted_.close();
    		sqlIsBlacklisted_ = null;
    	}
    	if (sqlIsMe_ != null) {
    		sqlIsMe_.close();
    		sqlIsMe_ = null;
    	}
    	if (sqlHasThumbnail_ != null) {
    		sqlHasThumbnail_.close();
    		sqlHasThumbnail_ = null;
    	}
    	if (sqlHasMusubiThumbnail_ != null) {
    		sqlHasMusubiThumbnail_.close();
    		sqlHasMusubiThumbnail_ = null;
    	}
    	if (sqlGetThumbnail_ != null) {
    		sqlGetThumbnail_.close();
    		sqlGetThumbnail_ = null;
    	}
    	if (sqlGetMusubiThumbnail_ != null) {
    		sqlGetMusubiThumbnail_.close();
    		sqlGetMusubiThumbnail_ = null;
    	}
    	if (sqlUpdateThumbnail_ != null) {
    		sqlUpdateThumbnail_.close();
    		sqlUpdateThumbnail_ = null;
    	}
    	if (sqlUpdateMusubiThumnail_ != null) {
    		sqlUpdateMusubiThumnail_.close();
    		sqlUpdateMusubiThumnail_ = null;
    	}
    }
}
