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
import gnu.trove.procedure.TLongProcedure;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;

import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.musubi.App;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MFeed.FeedType;
import mobisocial.musubi.model.MFeedApp;
import mobisocial.musubi.model.MFeedMember;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MMyAccount;
import mobisocial.musubi.model.MObject;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.provider.MusubiContentProvider.Provided;
import mobisocial.musubi.ui.FeedPannerActivity;
import mobisocial.musubi.util.Util;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;
import android.util.Log;

/**
 * Manages a feed with access beyond what is allowable in SocialKit.
 * 
 * Note that none of these methods manage whitelists.  The expectation is that
 * higher level code less frequently handles updating them.  The whitelist
 * itself is a feed and we don't really want a circular dependency.
 * @see MFeed
 * @see MFeedMember
 */
public final class FeedManager extends ManagerBase {
    static final String TAG = "FeedType";

    // A feed display is really a view over a query of objects
    public static final String MIME_TYPE = MusubiContentProvider.getType(Provided.FEEDS_ID);

    private SecureRandom mSecureRandom;
    private IdentitiesManager mIdentitiesManager;

    private SQLiteStatement mSqlUpdateFeed;
    private SQLiteStatement mSqlEnsureMember;
    private SQLiteStatement mSqlEnsureApp;
    private SQLiteStatement mSqlQueryAllowed;
	private SQLiteStatement mSqlDeleteFeedMember;
	private SQLiteStatement mSqlDeleteFeedApp;
	private SQLiteStatement mSqlMembersForObject;
    private SQLiteStatement mSqlGetUnreadMessageCount;

	private String mSqlUnacceptedFeedsWithMember;
    String mSqlThumbnailForFeed;

    static String[] STANDARD_FIELDS = new String[] {
    	MFeed.COL_ID,
    	MFeed.COL_TYPE,
    	MFeed.COL_CAPABILITY,
    	MFeed.COL_SHORT_CAPABILITY,
    	MFeed.COL_LATEST_RENDERABLE_OBJ_ID,
    	MFeed.COL_LATEST_RENDERABLE_OBJ_TIME,
    	MFeed.COL_NUM_UNREAD,
    	MFeed.COL_NAME,
    	MFeed.COL_ACCEPTED
    };

    final int _id = 0;
    final int type = 1;
    final int capability = 2;
    final int shortCapability = 3;
    final int latestRenderableObjId = 4;
    final int latestRenderableObjTime = 5;
    final int numUnread = 6;
    final int name = 7;
    final int accepted = 8;

	private SQLiteStatement mSqlCheckMembership;

    public FeedManager(SQLiteOpenHelper databaseSource) {
        super(databaseSource);
        mSecureRandom = new SecureRandom();
    }

    public FeedManager(SQLiteDatabase db) {
        super(db);
	}

    private SecureRandom getSecureRandom() {
    	if (mSecureRandom == null) {
    		mSecureRandom = new SecureRandom();
    	}
    	return mSecureRandom;
    }

    private IdentitiesManager getIdentitiesManager() {
    	if (mIdentitiesManager == null) {
    		mIdentitiesManager = new IdentitiesManager(initializeDatabase());
    	}
    	return mIdentitiesManager;
    }

    public static Intent getViewingIntent(Context context, Uri feedUri) {
        Intent launch = new Intent(Intent.ACTION_VIEW);
        launch.setDataAndType(feedUri, MIME_TYPE);
        launch.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        launch.setClass(context, FeedPannerActivity.class);
        return launch;
    }

    /**
     * Returns a stable byte array for the given list of identities.
     * The identifier is constant regardless of the order of the identities and
     * is derived from the identifier's principalHash.
     */
    public static byte[] computeFixedIdentifier(MIdentity... identities) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("your platform does not support sha256", e);
        }

        Comparator<MIdentity> comparator = new Comparator<MIdentity>() {
            @Override
            public int compare(MIdentity lhs, MIdentity rhs) {
            	if(lhs.type_.ordinal() < rhs.type_.ordinal())
            		return -1;
            	if(lhs.type_.ordinal() > rhs.type_.ordinal())
            		return 1;
                ByteBuffer buffer2 = ByteBuffer.wrap(rhs.principalHash_);
                return ByteBuffer.wrap(lhs.principalHash_).compareTo(buffer2);
            }
        };

        Arrays.sort(identities, comparator);
        byte lastType = 0x0;
        byte[] lastHash = new byte[0];
        for (MIdentity identity : identities) {
            byte type = (byte)identity.type_.ordinal();
            byte[] hash = identity.principalHash_;
            if (type == lastType && Arrays.equals(hash, lastHash)) {
                continue;
            }
            md.update(type);
            md.update(hash);
            lastType = type;
            lastHash = hash;
        }
        return md.digest();
    }

    public MFeed lookupFeed(FeedType type, byte[] capability) {
        long shortCapability = Util.shortHash(capability);

        SQLiteDatabase db = initializeDatabase();
        String table = MFeed.TABLE;
        String[] columns = new String[] { MFeed.COL_ID };
        String selection = MFeed.COL_TYPE + " = ? and " + MFeed.COL_SHORT_CAPABILITY + " = ?";
        String[] selectionArgs = new String[] {
                Integer.toString(type.ordinal()), Long.toString(shortCapability) };
        String groupBy = null, having = null, orderBy = null;
        Cursor c = db.query(table, columns, selection, selectionArgs, groupBy, having, orderBy);
        try {
            while (c.moveToNext()) {
                MFeed lookup = lookupFeed(c.getLong(0));
                if (Arrays.equals(lookup.capability_, capability)) {
                    return lookup;
                }
            }
            return null;
        } finally {
            c.close();
        }
    }

    /**
     * Returns a fixed feed, creating it if necessary. A fixed feed has static
     * membership with a capability derived deterministically from the list of
     * participants.
     *
     * This method creates and inserts a new fixed feed and inserts the feed
     * membership.
     *
     */
    public MFeed getOrCreateFixedFeed(MIdentity... participants) {
        if (!hasOwner(participants)) {
            IdentitiesManager im = new IdentitiesManager(initializeDatabase());
            MIdentity me = im.getMyDefaultIdentity(participants);
            if (me == null) {
                throw new IllegalArgumentException("Feed creation needs owned identity");
            }
            MIdentity[] us = new MIdentity[participants.length + 1];
            System.arraycopy(participants, 0, us, 1, participants.length);
            us[0] = me;
            participants = us;
        }

        byte[] capability = computeFixedIdentifier(participants);
        MFeed feed = lookupFeed(FeedType.FIXED, capability);
        if (feed != null) {
            return feed;
        }

    	MFeed created = new MFeed();
        created = new MFeed();
        created.type_ = FeedType.FIXED;
        created.capability_ = capability;
        created.shortCapability_ = Util.shortHash(capability);
        created.accepted_ = true;
        return createFeedWithMembers(created, participants);
    }

    /**
     * Creates a new feed with expandable membership with the given membership. If the feed
     * does not have an owned identity, one is inserted automatically. If no owned identities
     * are available, an exception is thrown.
     * @param list
     * @return
     */
    public MFeed createExpandingFeed(MIdentity... participants) {
        if (!hasOwner(participants)) {
            IdentitiesManager im = new IdentitiesManager(initializeDatabase());
            MIdentity me = im.getMyDefaultIdentity(participants);
            if (me == null) {
                throw new IllegalArgumentException("Feed creation needs owned identity");
            }
            MIdentity[] us = new MIdentity[participants.length + 1];
            System.arraycopy(participants, 0, us, 1, participants.length);
            us[0] = me;
            participants = us;
        }

        MFeed created = new MFeed();
        created = new MFeed();
        created.type_ = FeedType.EXPANDING;
        created.capability_ = new byte[32];
        getSecureRandom().nextBytes(created.capability_);
        created.shortCapability_ = Util.shortHash(created.capability_);
        created.accepted_ = true;
        return createFeedWithMembers(created, participants);
    }

    /**
     * Creates a new feed with expandable membership with the given membership.
     * @param list
     * @return
     */
    public MFeed createOneShotFeed(MIdentity... participants) {
        MFeed created = new MFeed();
        created = new MFeed();
        created.type_ = FeedType.ONE_TIME_USE;
        createFeedWithMembers(created, participants);
        return created;
    }

    static boolean hasOwner(MIdentity[] participants) {
        for (MIdentity id : participants) {
            if (id.owned_) {
                return true;
            }
        }
        return false;
    }
    public static LinkedList<MIdentity> getOwners(MIdentity[] participants) {
    	LinkedList<MIdentity> owners = new LinkedList<MIdentity>();
        for (MIdentity id : participants) {
            if (id.owned_) {
            	owners.add(id);
            }
        }
        return owners;
    }

    /**
     * Creates a feed with the given parameters and members. If the feed
     * fails insertion, the database is unchanged and feed.id_ is set to -1.
     */
    MFeed createFeedWithMembers(MFeed feed, MIdentity[] participants) {
        SQLiteDatabase db = initializeDatabase();
        db.beginTransaction();
        try {
            insertFeed(feed);
            if (feed.id_ == -1) {
                return feed;
            }

            for (MIdentity id : participants) {
                ensureFeedMember(feed.id_, id.id_);
            }
            db.setTransactionSuccessful();
            return feed;
        } finally {
            db.endTransaction();
        }
    }

    public void updateFeed(MFeed feed) {
        SQLiteDatabase db = initializeDatabase();
        if (mSqlUpdateFeed == null) {
            synchronized (this) {
                if(mSqlUpdateFeed == null) {
                    String sql = new StringBuilder()
                        .append("UPDATE ").append(MFeed.TABLE).append(" SET ")
                		.append(MFeed.COL_TYPE).append("=?,")
                		.append(MFeed.COL_CAPABILITY).append("=?,")
                		.append(MFeed.COL_SHORT_CAPABILITY).append("=?,")
                		.append(MFeed.COL_LATEST_RENDERABLE_OBJ_ID).append("=?,")
                		.append(MFeed.COL_LATEST_RENDERABLE_OBJ_TIME).append("=?,")
                		.append(MFeed.COL_NUM_UNREAD).append("=?,")
                		.append(MFeed.COL_NAME).append("=?,")
                		.append(MFeed.COL_ACCEPTED).append("=?")
                		.append(" WHERE ").append(MFeed.COL_ID).append("=?").toString();
                    mSqlUpdateFeed = db.compileStatement(sql);
                }
            }
        }
                
        synchronized (mSqlUpdateFeed) {
            bindStandardFields(mSqlUpdateFeed, feed);
            mSqlUpdateFeed.bindLong(STANDARD_FIELDS.length, feed.id_);
            mSqlUpdateFeed.execute();
        }
    }

    public boolean updateFeedDetails(long feedId, String feedName, byte[] thumbnail) {
    	ContentValues cv = new ContentValues();
    	if (feedName != null) {
    		cv.put(MFeed.COL_NAME, feedName);
    	}
    	if (thumbnail != null) {
    		cv.put(MFeed.COL_THUMBNAIL, thumbnail);
    	}
    	String whereClause = MFeed.COL_ID + "=?";
    	String[] whereArgs = new String[] { Long.toString(feedId) };
    	return initializeDatabase().update(MFeed.TABLE, cv, whereClause, whereArgs) > 0;
    }

    public byte[] getFeedThumbnailForId(long feedId) {
    	if (mSqlThumbnailForFeed == null) {
    		synchronized(this) {
    			mSqlThumbnailForFeed = new StringBuilder(100)
    				.append("SELECT ").append(MFeed.COL_THUMBNAIL)
    				.append(" FROM ").append(MFeed.TABLE)
    				.append(" WHERE ").append(MFeed.COL_ID).append("=?")
    				.toString();
    		}
    	}
    	String[] selectionArgs = new String[] { Long.toString(feedId) };
    	Cursor c = initializeDatabase().rawQuery(mSqlThumbnailForFeed, selectionArgs);
    	try {
    		if (c.moveToFirst()) {
    			if (!c.isNull(0)) {
    				return c.getBlob(0);
    			}
    		}
    		return null;
    	} finally {
    		c.close();
    	}
    }

    public long insertFeed(MFeed feed) {
        SQLiteDatabase db = initializeDatabase();
        ContentValues values = new ContentValues();

        values.put(MFeed.COL_TYPE, feed.type_.ordinal());
        if (feed.capability_ != null) {
            values.put(MFeed.COL_CAPABILITY, feed.capability_);
            values.put(MFeed.COL_SHORT_CAPABILITY, feed.shortCapability_);
        }
        if (feed.latestRenderableObjId_ != null) {
            values.put(MFeed.COL_LATEST_RENDERABLE_OBJ_ID, feed.latestRenderableObjId_);
        }
        if (feed.latestRenderableObjTime_ != null) {
            values.put(MFeed.COL_LATEST_RENDERABLE_OBJ_TIME, feed.latestRenderableObjTime_);
        }
        values.put(MFeed.COL_NUM_UNREAD, feed.numUnread_);
        if (feed.name_ != null) {
            values.put(MFeed.COL_NAME, feed.name_);
        }
        values.put(MFeed.COL_ACCEPTED, feed.accepted_);
        feed.id_ = db.insert(MFeed.TABLE, null, values);
        return feed.id_;
    }

    /**
     * Sets a feed's unread message count to 0
     * @return true if the database was updated.
     */
    public boolean resetUnreadMessageCount(Uri feedUri) {
        try {
            SQLiteDatabase db = initializeDatabase();
            String table = MFeed.TABLE;
            ContentValues values = new ContentValues();
            values.put(MFeed.COL_NUM_UNREAD, 0);
            String whereClause = MFeed.COL_ID + " = ? AND " + MFeed.COL_NUM_UNREAD + " > 0";
            String[] whereArgs = new String[] {
                feedUri.getLastPathSegment()
            };
            return (db.update(table, values, whereClause, whereArgs) > 0);
        } catch (Exception e) {
            Log.e(App.TAG, "Error clearing unread messages", e);
            return false;
        }
    }

    public int getUnreadMessageCount(long feedId) {
    	SQLiteDatabase db = initializeDatabase();
    	if (mSqlGetUnreadMessageCount == null) {
    		synchronized (this) {
    			if (mSqlGetUnreadMessageCount == null) {
    				StringBuilder sql = new StringBuilder(50)
    					.append("SELECT ").append(MFeed.COL_NUM_UNREAD)
    					.append(" FROM ").append(MFeed.TABLE)
    					.append(" WHERE ").append(MFeed.COL_ID).append("=?");
    				mSqlGetUnreadMessageCount = db.compileStatement(sql.toString());
    			}
    		}
    	}
    	synchronized (mSqlGetUnreadMessageCount) {
    		mSqlGetUnreadMessageCount.bindLong(1, feedId);
    		try {
    			return (int)mSqlGetUnreadMessageCount.simpleQueryForLong();
    		} catch (SQLiteDoneException e) {
    			return 0;
    		}
    	}
    }

    /**
     * Increments a feed's unread message count by 1.
     */
    public void incrementUnreadMessageCount(Context context, long feedId) {
        try {
            SQLiteDatabase db = initializeDatabase();
            // Can't do db.update() with a computed column.
            String sql = new StringBuilder("UPDATE ").append(MFeed.TABLE).append(" SET ")
                    .append(MFeed.COL_NUM_UNREAD).append(" = ").append(MFeed.COL_NUM_UNREAD)
                    .append("+1 WHERE ").append(MFeed.COL_ID).append(" = ").append(feedId)
                    .toString();

            db.rawQuery(sql, null);
            context.getContentResolver().notifyChange(
                    MusubiContentProvider.uriForDir(Provided.FEEDS), null);
        } catch (Exception e) {
            Log.e(App.TAG, "Error clearing unread messages", e);
        }
    }

    public boolean isLatestFeedNameSuggestion(MObject obj) {
        SQLiteDatabase db = initializeDatabase();
        String table = MObject.TABLE;
        String[] columns = new String[] {MObject.COL_ID};
        String selection = MObject.COL_FEED_ID + " = ? AND " + MObject.COL_LAST_MODIFIED_TIMESTAMP + " > ?";
        String[] selectionArgs = new String[] { Long.toString(obj.feedId_), Long.toString(obj.lastModifiedTimestamp_) };
        String groupBy = null, having = null, orderBy = null;
        Cursor c = db.query(table, columns, selection, selectionArgs, groupBy, having, orderBy);
    	return c.getCount() == 0;
    }
    
    public MFeed lookupFeed(long id) {
        SQLiteDatabase db = initializeDatabase();
        String table = MFeed.TABLE;
        String[] columns = STANDARD_FIELDS;
        String selection = MFeed.COL_ID + " = ?";
        String[] selectionArgs = new String[] { Long.toString(id) };
        String groupBy = null, having = null, orderBy = null;
        Cursor c = db.query(table, columns, selection, selectionArgs, groupBy, having, orderBy);
        try {
            if (c.moveToFirst()) {
                MFeed feed = new MFeed();
                feed.id_ = id;
                feed.type_ = FeedType.values()[c.getInt(type)];
                if (!c.isNull(capability)) {
                    feed.capability_ = c.getBlob(capability);
                    feed.shortCapability_ = c.getLong(shortCapability);
                }
                if (!c.isNull(latestRenderableObjId)) {
                    feed.latestRenderableObjId_ = c.getLong(latestRenderableObjId);
                }
                if (!c.isNull(latestRenderableObjTime)) {
                    feed.latestRenderableObjTime_ = c.getLong(latestRenderableObjTime);
                }
                feed.numUnread_ = c.getLong(numUnread);
                feed.name_ = c.getString(name);
                feed.accepted_ = c.getLong(accepted) != 0;
                return feed;
            } else {
                return null;
            }
        } finally {
            c.close();
        }
    }

    /**
     * Returns the _id of an identity owned by the local user in a given feed.
     */
    public Long getOwnedIdentityForFeed(long feedId) {
        SQLiteDatabase db = initializeDatabase();
        StringBuilder sql = new StringBuilder();
        sql.append("select ").append(MIdentity.TABLE).append(".").append(MIdentity.COL_ID)
            .append(" from ").append(MFeedMember.TABLE).append(",").append(MIdentity.TABLE)
            .append(" where ").append(MFeedMember.TABLE).append(".")
            .append(MFeedMember.COL_IDENTITY_ID).append("=")
            .append(MIdentity.TABLE).append(".").append(MIdentity.COL_ID).append(" and ")
            .append(MIdentity.COL_OWNED).append(" = 1 and ").append(MFeedMember.TABLE)
            .append(".").append(MFeedMember.COL_FEED_ID).append(" = ?");
        String[] selectionArgs = new String[] { Long.toString(feedId) };
        Cursor cursor = db.rawQuery(sql.toString(), selectionArgs);
        try {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return cursor.getLong(0);
        } finally {
            cursor.close();
        }
    }

    /**
     * select count(*) from feed_members where feed_id = ? and identity_id in ?
     */
    int countIdentitiesInFeed(long feedId, MIdentity[] identities) {
        StringBuilder members = new StringBuilder(100).append("(");
        for (MIdentity id : identities) {
            members.append(id.id_).append(",");
        }
        members.setLength(members.length() - 1);
        members.append(")");
        StringBuilder sql = new StringBuilder(100)
            .append("SELECT count(*) FROM ").append(MFeedMember.TABLE)
            .append(" WHERE ").append(MFeedMember.COL_FEED_ID).append("=?")
            .append(" AND ").append(MFeedMember.COL_IDENTITY_ID).append(" in ").append(members);
        String[] selectionArgs = new String[] { Long.toString(feedId) };
        Cursor c = initializeDatabase().rawQuery(sql.toString(), selectionArgs);
        try {
            if (c.moveToFirst()) {
                return c.getInt(0);
            }
            throw new IllegalStateException("Query failed.");
        } finally {
            c.close();
        }
    }
    
    public void ensureFeedMember(long feed, long ident) {
        SQLiteDatabase db = initializeDatabase();
        if (mSqlEnsureMember == null) {
            synchronized (this) {
                if(mSqlEnsureMember == null) {
                    String sql = new StringBuilder()
                        .append("INSERT OR IGNORE INTO ").append(MFeedMember.TABLE).append("(")
                        .append(MFeedMember.COL_FEED_ID)
                        .append(",")
                        .append(MFeedMember.COL_IDENTITY_ID)
                        .append(") VALUES (?,?)").toString();
                    mSqlEnsureMember = db.compileStatement(sql);
                }
            }
        }
                
        synchronized (mSqlEnsureMember) {
        	mSqlEnsureMember.bindLong(1, feed);
        	mSqlEnsureMember.bindLong(2, ident);
        	mSqlEnsureMember.execute();
        }
    }

    public void deleteFeedMember(long feed, long ident) {
        SQLiteDatabase db = initializeDatabase();
        if (mSqlDeleteFeedMember == null) {
            synchronized (this) {
                if(mSqlDeleteFeedMember == null) {
                    String sql = new StringBuilder()
                        .append("DELETE FROM ").append(MFeedMember.TABLE).append(" WHERE ")
                        .append(MFeedMember.COL_FEED_ID).append("=?").append(" AND ")
                        .append(MFeedMember.COL_IDENTITY_ID).append("=?").toString();
                    mSqlDeleteFeedMember = db.compileStatement(sql);
                }
            }
        }
                
        synchronized (mSqlDeleteFeedMember) {
        	mSqlDeleteFeedMember.bindLong(1, feed);
        	mSqlDeleteFeedMember.bindLong(2, ident);
        	mSqlDeleteFeedMember.execute();
        }
    }

    public void ensureFeedApp(long feed, long app) {
        SQLiteDatabase db = initializeDatabase();
        if (mSqlEnsureApp == null) {
            synchronized (this) {
                if(mSqlEnsureApp == null) {
                    String sql = new StringBuilder()
                        .append("INSERT OR IGNORE INTO ").append(MFeedApp.TABLE).append("(")
                        .append(MFeedApp.COL_FEED_ID)
                        .append(",")
                        .append(MFeedApp.COL_APP_ID)
                        .append(") VALUES (?,?)").toString();
                    mSqlEnsureApp = db.compileStatement(sql);
                }
            }
        }
                
        synchronized (mSqlEnsureApp) {
            mSqlEnsureApp.bindLong(1, feed);
            mSqlEnsureApp.bindLong(2, app);
            mSqlEnsureApp.execute();
        }
    }

    public void deleteFeedApp(long feed, long app) {
        SQLiteDatabase db = initializeDatabase();
        if (mSqlDeleteFeedApp == null) {
            synchronized (this) {
                if(mSqlDeleteFeedApp == null) {
                    String sql = new StringBuilder()
                        .append("DELETE FROM ").append(MFeedApp.TABLE).append(" WHERE ")
                        .append(MFeedApp.COL_FEED_ID).append("=?").append(" AND ")
                        .append(MFeedApp.COL_APP_ID).append("=?").toString();
                    mSqlDeleteFeedApp = db.compileStatement(sql);
                }
            }
        }
                
        synchronized (mSqlDeleteFeedApp) {
            mSqlDeleteFeedApp.bindLong(1, feed);
            mSqlDeleteFeedApp.bindLong(2, app);
            mSqlDeleteFeedApp.execute();
        }
    }

    /**
     * Returns a list of accepted feed id's for a given user id
     */
    public long[] getFeedsForIdentityId(long identityId) {
    	SQLiteDatabase db = initializeDatabase();
        String table = MFeedMember.TABLE + " inner join " + MFeed.TABLE + " on " +
                MFeedMember.TABLE + "." + MFeedMember.COL_FEED_ID + " = " +
                MFeed.TABLE + "." + MFeed.COL_ID;
        String[] columns = new String[] { MFeedMember.COL_FEED_ID };
        String selection = MFeedMember.COL_IDENTITY_ID + " = ? AND " + MFeed.COL_ACCEPTED + "=1";
        String[] selectionArgs = new String[] { Long.toString(identityId) };
        String groupBy = null, having = null;
        String orderBy = MFeedMember.COL_FEED_ID + " desc";

        Cursor c = db.query(table, columns, selection, selectionArgs, groupBy, having, orderBy);
        long[] feedIds = new long[c.getCount()];
        int i = 0;
        try {
            while (c.moveToNext()) {
                feedIds[i++] = c.getLong(0);
            }
            return feedIds;
        } finally {
            c.close();
        }
    }
    
    /**
     * Returns the known members of the given feed.
     */
    public MIdentity[] getFeedMembers(MFeed feed) {
        long[] ids = getFeedMembers(feed.id_);
        return getIdentitiesManager().getIdentitiesForIds(ids);
    }

    private String feedMemberIdsQuery;
    private String getFeedMemberIdsQuery() {
    	if (feedMemberIdsQuery == null) {
    		feedMemberIdsQuery = new StringBuilder(80)
    			.append("SELECT ").append(MFeedMember.COL_IDENTITY_ID)
    			.append(" FROM ").append(MFeedMember.TABLE)
    			.append(" WHERE ").append(MFeedMember.COL_FEED_ID).append("=?").toString();
    	}
    	return feedMemberIdsQuery;
    }

    public long[] getFeedMembers(long feedId) {
    	SQLiteDatabase db = initializeDatabase();
        Cursor c = db.rawQuery(getFeedMemberIdsQuery(), new String[] { Long.toString(feedId) });
        long[] identityIds = new long[c.getCount()];
        int i = 0;
        while (c.moveToNext()) {
        	identityIds[i++] = c.getLong(0);
        }
        c.close();
        return identityIds;
    }

    /**
     * Returns the known members of the given feed.
     */
    public int getFeedMemberCount(long feedId) {
        SQLiteDatabase db = initializeDatabase();
        String table = MFeedMember.TABLE;
        String[] columns = new String[] { "count(*)" };
        String selection = MFeedMember.COL_FEED_ID + " = ?";
        String[] selectionArgs = new String[] { Long.toString(feedId) };
        String groupBy = null, having = null, orderBy = null;
        Cursor c = db.query(table, columns, selection, selectionArgs, groupBy, having, orderBy);
        try {
            if (c.moveToFirst()) {
                return c.getInt(0);
            } else {
                return 0;
            }
        } finally {
            c.close();
        }
    }

    /**
     * Returns the known members of the given feed.
     */
    public MIdentity[] getFeedMembersGroupedByVisibleName(MFeed feed) {
        SQLiteDatabase db = initializeDatabase();
        String table = MFeedMember.TABLE;
        String[] columns = new String[] { MFeedMember.COL_IDENTITY_ID };
        String selection = MFeedMember.COL_FEED_ID + " = ?";
        String[] selectionArgs = new String[] { Long.toString(feed.id_) };
        String groupBy = null, having = null, orderBy = null;
        Cursor c = db.query(table, columns, selection, selectionArgs, groupBy, having, orderBy);
        long[] identityIds = new long[c.getCount()];
        int i = 0;
        try {
            if (c.moveToFirst()) {
                do {
                    identityIds[i++] = c.getLong(0);
                } while (c.moveToNext());
                return getIdentitiesManager().getIdentitiesForIdsGroupedByVisibleName(identityIds);
            } else {
                return new MIdentity[0];
            }
        } finally {
            c.close();
        }
    }
    
    /**
     * Returns a cursor for the known members of the given feed;
     */
    
    public Cursor getKnownProfileFeedMembersCursor(long feedId) {
		//TODO: in developer mode, show don't supress unknown users.
    	SQLiteDatabase db = initializeDatabase();
    	String[] selectionArgs = new String[] {Long.toString(feedId)};
    	
        String sql = "SELECT " + MIdentity.TABLE + "." + MIdentity.COL_ID
        			+ " FROM " + MFeedMember.TABLE + " JOIN " + MIdentity.TABLE + " ON "
        			+ MFeedMember.TABLE + "." + MFeedMember.COL_IDENTITY_ID + " = " + MIdentity.TABLE + "." + MIdentity.COL_ID
        			+ " WHERE " 
        				+ MFeedMember.TABLE + "." + MFeedMember.COL_FEED_ID + " = ? AND " 
        				+ "(" 
        					+ MIdentity.TABLE + "." + MIdentity.COL_MUSUBI_NAME + " IS NOT NULL OR " 
							+ MIdentity.TABLE + "." + MIdentity.COL_NAME + " IS NOT NULL OR "
							+ MIdentity.TABLE + "." + MIdentity.COL_THUMBNAIL + " IS NOT NULL OR "
							+ MIdentity.TABLE + "." + MIdentity.COL_MUSUBI_THUMBNAIL + " IS NOT NULL OR "
							+ MIdentity.TABLE + "." + MIdentity.COL_PRINCIPAL + " IS NOT NULL" 
	        			+ ")"
    				+ " ORDER BY " + MIdentity.TABLE + "." + MIdentity.COL_NAME + " COLLATE NOCASE ASC";
        return db.rawQuery(sql, selectionArgs);
    }
    
    /**
     * Returns a cursor for the unclaimed members of the given feed;
     */
    
    public Cursor getEmailReachableUnclaimedFeedMembersCursor(long feedId) {
		//TODO: in developer mode, show don't supress unknown users.
    	SQLiteDatabase db = initializeDatabase();
    	String[] selectionArgs = new String[] {Long.toString(feedId), "0", "0", "0"};
    	
        String sql = "SELECT " 
        				+ MIdentity.TABLE + "." + MIdentity.COL_ID
        			+ " FROM " + MFeedMember.TABLE + ", " + MIdentity.TABLE
        			+ " WHERE " 
        				+ MFeedMember.TABLE + "." + MFeedMember.COL_FEED_ID + " = ? AND " 
        				+ MFeedMember.TABLE + "." + MFeedMember.COL_IDENTITY_ID + " = " + MIdentity.TABLE + "." + MIdentity.COL_ID + " AND "
        				+ MIdentity.TABLE + "." + MIdentity.COL_CLAIMED + " = ? " + " AND "
        				+ MIdentity.TABLE + "." + MIdentity.COL_HAS_SENT_EMAIL + " = ? " + " AND "
        				+ MIdentity.TABLE + "." + MIdentity.COL_OWNED + " = ? " + " AND "
        				+ MIdentity.TABLE + "." + MIdentity.COL_PRINCIPAL + " IS NOT NULL "
        				+ " AND " + MIdentity.TABLE + "." + MIdentity.COL_TYPE + "=" + Authority.Email.ordinal()
        				//TODO: if we can get the an email address @ facebook for our friends on facebook, then uncomment this...
        				//+ " AND " + MIdentity.TABLE + "." + MIdentity.COL_TYPE + "=" + Authority.Facebook.ordinal()
    				+ " ORDER BY " + MIdentity.TABLE + "." + MIdentity.COL_NAME + " COLLATE NOCASE ASC";
        Log.w(TAG, sql);
        return db.rawQuery(sql, selectionArgs);
    }

    public void deleteFeedAndMembers(MFeed feed) {
        SQLiteDatabase db = initializeDatabase();
        String whereClause = MFeedMember.COL_FEED_ID + "=?";
        String[] whereArgs = new String[] { Long.toString(feed.id_) };
        db.delete(MFeedMember.TABLE, whereClause, whereArgs);

        whereClause = MFeed.COL_ID + "=?";
        // whereArgs same as above
        db.delete(MFeed.TABLE, whereClause, whereArgs);
    }
    
    /**
     * Rename a feed locally
     *
    public void renameFeed(MFeed feed, String name) {
    	 try {
             SQLiteDatabase db = initializeDatabase();
             String table = MFeed.TABLE;
             ContentValues values = new ContentValues();
             values.put(MFeed.COL_NAME, name);
             String whereClause = MFeed.COL_ID + " = ?";
             String[] whereArgs = new String[] { Long.toString(feed.id_) };
             db.update(table, values, whereClause, whereArgs);
         } catch (Exception e) {
             Log.e(App.TAG, "Error renaming feed", e);
         }
    }*/
    
    
    private void bindStandardFields(SQLiteStatement statement, MFeed feed) {
        statement.bindLong(type, feed.type_.ordinal());
        if (feed.capability_ == null) {
            statement.bindNull(capability);
            statement.bindNull(shortCapability);
        } else {
            statement.bindBlob(capability, feed.capability_);
            statement.bindLong(shortCapability, feed.shortCapability_);
        }
        if (feed.latestRenderableObjId_ == null) {
            statement.bindNull(latestRenderableObjId);
        } else {
            statement.bindLong(latestRenderableObjId, feed.latestRenderableObjId_);
        }
        if (feed.latestRenderableObjTime_ == null) {
            statement.bindNull(latestRenderableObjTime);
        } else {
            statement.bindLong(latestRenderableObjTime, feed.latestRenderableObjTime_);
        }
        statement.bindLong(numUnread, feed.numUnread_);
        if (feed.name_ == null) {
            statement.bindNull(name);
        } else {
            statement.bindString(name, feed.name_);
        }
        statement.bindLong(accepted, feed.accepted_ ? 1 : 0);
    }

	public MFeed getGlobal() {
		return lookupFeed(MFeed.GLOBAL_BROADCAST_FEED_ID);
	}

	public MFeed getWizFeed() {
	    return lookupFeed(MFeed.WIZ_FEED_ID);
	}

    public boolean isInAllowedFeed(MIdentity owner) {
        SQLiteDatabase db = initializeDatabase();
        if (mSqlQueryAllowed == null) {
            synchronized (this) {
                StringBuilder sql = new StringBuilder(100)
                    .append("SELECT count(*)")
                    .append(" FROM ").append(MFeedMember.TABLE)
                    .append(" INNER JOIN ").append(MFeed.TABLE).append(" ON ")
                    .append(MFeedMember.TABLE).append(".").append(MFeedMember.COL_FEED_ID)
                    .append("=").append(MFeed.TABLE).append(".").append(MFeed.COL_ID)
                    .append(" WHERE ").append(MFeedMember.COL_IDENTITY_ID).append("=?")
                    .append(" AND " + MFeed.COL_ACCEPTED + "=1");
                mSqlQueryAllowed = db.compileStatement(sql.toString());
            }
        }
        synchronized (mSqlQueryAllowed) {
            mSqlQueryAllowed.bindLong(1, owner.id_);
            return mSqlQueryAllowed.simpleQueryForLong() > 0;
        }
    }
    
    //TODO: this almost certainly has a race with the pipeline processor.... because
    //they both update the feed and the pipeline processor doesn't hold a transaction
    //the whole time
    public void acceptFeedsFromMember(Context context, long identityId) {
    	TLongLinkedList ids = getUnacceptedFeedsWithMember(identityId);
    	if(ids.size() == 0)
    		return;
    	ids.forEach(new TLongProcedure() {
			@Override
			public boolean execute(long feedId) {
				MFeed feed = lookupFeed(feedId);
				feed.accepted_ = true;
				feed.latestRenderableObjTime_ = new Date().getTime();
				updateFeed(feed);
				return true;
			}
		});
    	context.getContentResolver().notifyChange(MusubiContentProvider.uriForDir(Provided.FEEDS), null);
    }
    public TLongLinkedList getUnacceptedFeedsWithMember(long identityId) {
        if (mSqlUnacceptedFeedsWithMember == null) {
            mSqlUnacceptedFeedsWithMember = "SELECT " + MFeed.TABLE + "." + MFeed.COL_ID + " FROM " + MFeed.TABLE + " JOIN " + MObject.TABLE + " ON " +
                    MFeed.TABLE + "." + MFeed.COL_ID + "=" + MObject.TABLE + "." + MObject.COL_FEED_ID + " JOIN " +
                    MFeedMember.TABLE + " ON " + MFeed.TABLE + "." + MFeed.COL_ID + "=" + MFeedMember.TABLE + "." + MFeedMember.COL_FEED_ID + 
                    " WHERE " + MObject.TABLE + "." + MObject.COL_IDENTITY_ID + "=? AND " + MObject.TABLE + "." + MObject.COL_IDENTITY_ID +
                    "=" + MFeedMember.TABLE + "." + MFeedMember.COL_IDENTITY_ID + " AND " +
                    MFeed.TABLE + "." + MFeed.COL_TYPE + " IN (" + MFeed.FeedType.FIXED.ordinal() + "," +
                    MFeed.FeedType.EXPANDING.ordinal() + ") AND " + MFeed.TABLE + "." + MFeed.COL_ACCEPTED + "=0 " +
                    "GROUP BY " + MFeed.TABLE + "." + MFeed.COL_ID;
        }

        SQLiteDatabase db = initializeDatabase();
        Cursor c = db.rawQuery(mSqlUnacceptedFeedsWithMember, new String[] { String.valueOf(identityId) });
        TLongLinkedList ids = new TLongLinkedList(c.getCount());
    	try {
	    	while(c.moveToNext()) {
	    		ids.add(c.getLong(0));
	    	}
	    	return ids;
    	} finally {
    		c.close();
    	}
    }

	public boolean isFeedMember(long feedId, long identityId) {
        SQLiteDatabase db = initializeDatabase();
        if (mSqlCheckMembership == null) {
            synchronized (this) {
                if(mSqlCheckMembership == null) {
                    String sql = new StringBuilder()
                        .append("SELECT COUNT(").append(MFeedMember.COL_ID).append(") FROM ").append(MFeedMember.TABLE).append(" WHERE ")
                        .append(MFeedMember.COL_FEED_ID).append("=?").append(" AND ")
                        .append(MFeedMember.COL_IDENTITY_ID).append("=?").toString();
                    mSqlCheckMembership = db.compileStatement(sql);
                }
            }
        }
                
        synchronized (mSqlCheckMembership) {
        	mSqlCheckMembership.bindLong(1, feedId);
        	mSqlCheckMembership.bindLong(2, identityId);
        	return mSqlCheckMembership.simpleQueryForLong() != 0;
        }
	}
	public boolean addToWhitelistsIfNecessary(MMyAccount provisionalAccount, MMyAccount whitelistAccount, MIdentity persona, MIdentity recipient) {
		if(recipient.blocked_) {
			return false;
		}

		//TODO: if things are flaky or we want to work better in the face of
		//clear data, then we could also reset the sentProfileVersion for the identity
		//but this is probably good enough...
		assert(provisionalAccount.feedId_  != null);
		assert(whitelistAccount.feedId_  != null);
		boolean changed = false;
		//if they are not whitelisted, then they need to be provisionally 
		//whitelisted because this feed was accepted
		if(!recipient.whitelisted_) {
			if(!isFeedMember(provisionalAccount.feedId_, recipient.id_)) {
				ensureFeedMember(provisionalAccount.feedId_, recipient.id_);
				changed = true;
			}
		} else {
			//but they may be whitelisted and contacting you via an identity that you did not think
			//they knew you at.  in which case we need to track this so that we ensure we send a profile
			//to them using this specific identity.
			if(!isFeedMember(whitelistAccount.feedId_, recipient.id_)) {
				ensureFeedMember(whitelistAccount.feedId_, recipient.id_);
				changed = true;
			}
		}
		return changed;
	}

    public static final String VISIBLE_FEED_SELECTION = SQLClauseHelper.andClauses(MFeed.COL_LATEST_RENDERABLE_OBJ_ID + " is not null", MFeed.COL_ACCEPTED + "=1");

    public ArrayList<Long> getFeedIdsForDisplay() {
    	SQLiteDatabase db = initializeDatabase();
        String table = MFeed.TABLE;
        String[] columns = new String[] { MFeed.COL_ID };
        String selection = VISIBLE_FEED_SELECTION;
        String groupBy = null, having = null;
        String orderBy = MFeed.COL_LATEST_RENDERABLE_OBJ_TIME + " DESC";

        Cursor c = db.query(table, columns, selection, null, groupBy, having, orderBy);
        ArrayList<Long> feedIds = new ArrayList<Long>(c.getCount());
        try {
            while (c.moveToNext()) {
                feedIds.add(c.getLong(0));
            }
            return feedIds;
        } finally {
            c.close();
        }
	}

    public Long getCachedLatestRenderable(long feedId) {
    	SQLiteDatabase db = initializeDatabase();
        String table = MFeed.TABLE;
        String[] columns = new String[] { MFeed.COL_LATEST_RENDERABLE_OBJ_ID };
        String selection = MFeed.COL_ID + "=? AND " + MFeed.COL_LATEST_RENDERABLE_OBJ_ID + " is not null";
        String[] selectionArgs = new String[] { Long.toString(feedId) };
        String groupBy = null, having = null, orderBy = null;

        Cursor c = db.query(table, columns, selection, selectionArgs, groupBy, having, orderBy);
        try {
        	if (c.moveToFirst()) {
        		return c.getLong(0);
        	} else {
        		return null;
        	}
        } finally {
        	c.close();
        }
	}

	public static final String visibleFeedSelection(long[] feedIds) {
	    StringBuilder selection = new StringBuilder(80);
	    selection.append(MFeed.COL_LATEST_RENDERABLE_OBJ_ID).append(" is not null");
	    if (feedIds.length > 0) {
	    	selection.append(" AND ").append(MFeed.TABLE).append(".").append(MFeed.COL_ID)
	    		.append(" IN (").append(feedIds[0]);
	    	for (int i = 1; i < feedIds.length; i++) {
	    		selection.append(",").append(feedIds[i]);
	    	}
	    	selection.append(")");
	    } else {
	        selection.append(" AND 1=0"); // heh
	    }
	    selection.append(" AND ").append(MFeed.COL_ACCEPTED).append("=1");
	    return selection.toString();
    }

	public long membersInObjectFeed(long objectId) {
		SQLiteDatabase db = initializeDatabase();
        if (mSqlMembersForObject == null) {
                synchronized (this) {
                    StringBuilder sql = new StringBuilder(80)
                        .append("SELECT count(*) from ").append(MFeedMember.TABLE)
                        .append(" WHERE ").append(MFeedMember.COL_FEED_ID).append("= ")
                        .append(" (SELECT ").append(MObject.COL_FEED_ID).append(" FROM ")
                        .append(MObject.TABLE).append(" WHERE ").append(MObject.COL_ID)
                        .append("=?)");
                    mSqlMembersForObject = db.compileStatement(sql.toString());
                }
        }
        synchronized (mSqlMembersForObject) {
            mSqlMembersForObject.bindLong(1, objectId);
            return mSqlMembersForObject.simpleQueryForLong();
        }
    }

	@Override
    public synchronized void close() {
    	if (mIdentitiesManager != null) {
    		mIdentitiesManager.close();
    		mIdentitiesManager = null;
    	}
    	if (mSqlUpdateFeed != null) {
    		mSqlUpdateFeed.close();
    		mSqlUpdateFeed = null;
    	}
    	if (mSqlEnsureMember != null) {
    		mSqlEnsureMember.close();
    		mSqlEnsureMember = null;
    	}
    	if (mSqlEnsureApp != null) {
    		mSqlEnsureApp.close();
    		mSqlEnsureApp = null;
    	}
    	if (mSqlQueryAllowed != null) {
    		mSqlQueryAllowed.close();
    		mSqlQueryAllowed = null;
    	}
    	if (mSqlDeleteFeedMember != null) {
    		mSqlDeleteFeedMember.close();
    		mSqlDeleteFeedMember = null;
    	}
    	if (mSqlDeleteFeedApp != null) {
    		mSqlDeleteFeedApp.close();
    		mSqlDeleteFeedApp = null;
    	}
    	if (mSqlMembersForObject != null) {
    		mSqlMembersForObject.close();
    		mSqlMembersForObject = null;
    	}
    	if (mSqlGetUnreadMessageCount != null) {
    		mSqlGetUnreadMessageCount.close();
    		mSqlGetUnreadMessageCount = null;
    	}
    }
}
