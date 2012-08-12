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

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.musubi.App;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MFeedMember;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MMyAccount;
import mobisocial.musubi.model.helpers.FeedManager;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.model.helpers.MyAccountManager;
import mobisocial.musubi.objects.ProfileObj;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.provider.MusubiContentProvider.Provided;
import mobisocial.musubi.ui.SettingsActivity;
import mobisocial.socialkit.Obj;
import mobisocial.socialkit.obj.MemObj;

import org.javatuples.Pair;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentValues;
import android.content.Context;
import android.database.ContentObserver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

//TODO: this probably doesn't deal with the case where we need to send
//a profile to an identity twice (since it tracks the sentProfile time and
//after sending it via one persona, the other persona will seem to not need 
//an update
/**
 * Scans the list of identities for entries that need this user's latest
 * profile.
 * 
 * @See MusubiService
 */
public class ProfilePushProcessor extends ContentObserver {
    //at most twice / minute
    private static final int ONCE_PER_PERIOD = 30 * 1000;
	private final boolean DBG = MusubiService.DBG;
    private final String TAG = "ProfilePushProcessor";
    private final Context mContext;
    private final SQLiteOpenHelper mHelper;

    private final FeedManager mFeedManager;
    private final MyAccountManager mAccountManager;
    private final IdentitiesManager mIdentityManager;
    private final ProfileUpdateObserver mProfileUpdatedObserver;
    final HandlerThread mThread;

    private SQLiteStatement mSqlPrepareProfile;
    private SQLiteStatement mSqlCheckRecipients;
    private SQLiteStatement mSqlMarkIdentitiesSynced;
	private Date mLastRun;
	private boolean mScheduled;

    public static ProfilePushProcessor newInstance(Context context, SQLiteOpenHelper dbh) {
        HandlerThread thread = new HandlerThread("ProfileSyncThread");
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
        return new ProfilePushProcessor(context, dbh, thread, new Handler(thread.getLooper()));
    }

    private ProfilePushProcessor(Context context, SQLiteOpenHelper dbh, HandlerThread thread, Handler handler) {
        super(handler);
        mContext = context;
        mThread = thread;
        mHelper = dbh;
        mAccountManager = new MyAccountManager(mHelper);
        mFeedManager = new FeedManager(mHelper);
        mIdentityManager = new IdentitiesManager(mHelper);
        mProfileUpdatedObserver = new ProfileUpdateObserver(handler);
        context.getContentResolver().registerContentObserver(MusubiService.FORCE_PROFILE_PUSH, false, new ContentObserver(new Handler(thread.getLooper())) {
        	@Override
        	public void onChange(boolean selfChange) {
        		mLastRun = null;
        		ProfilePushProcessor.this.dispatchChange(false);
        	}
		});
    }

    @Override
    public void onChange(boolean selfChange) {
    	if(mLastRun != null && mLastRun.getTime() + ONCE_PER_PERIOD > new Date().getTime()) {
    		//wake up when the period expires
    		if(!mScheduled) {
	    		new Handler(mThread.getLooper()).postDelayed(new Runnable() {
					@Override
					public void run() {
				        mScheduled = false;
						dispatchChange(false);
					}
				}, mLastRun.getTime() + ONCE_PER_PERIOD - mLastRun.getTime());
    		}
    		mScheduled = true;
    		//skip this update
    		return;
    	}
    	boolean includePrincipal = mContext.getSharedPreferences(SettingsActivity.PREFS_NAME, 0)
    			.getBoolean(SettingsActivity.PREF_SHARE_CONTACT_ADDRESS, SettingsActivity.PREF_SHARE_CONTACT_ADDRESS_DEFAULT);
    	
    	LinkedList<Pair<long[], Long>> ids_synced = new LinkedList<Pair<long[], Long>>();
    	try {
	        MMyAccount[] accounts = mAccountManager.getMyAccounts();
	        for (MMyAccount account : accounts) {
	            if (MusubiService.DBG)
	                Log.d(TAG, "Pushing profile to " + account.accountName_);
	        	MIdentity sendAs = null;
	            if(account.identityId_ != null) {
	            	sendAs = mIdentityManager.getIdentityForId(account.identityId_);
	            }
	            if(sendAs != null && sendAs.owned_ == false) {
	            	//if we have claimed we have to just use the default identity.
	            	sendAs = null;
	            }
	            if (sendAs == null) {
	            	List<MIdentity> idents = mIdentityManager.getOwnedIdentities();
	            	for(MIdentity ident : idents) {
	            		if(ident.type_ != Authority.Local) {
	            			sendAs = ident;
	            			break;
	            		}
	            	}
	            	if(sendAs == null) {
		                Log.w(TAG, "  No identity linked to account. And no claimed identity, skipping push");
		                continue;
	            	} else {
	                    Log.w(TAG, "  No identity linked to account. Using default identity " + sendAs.principal_);
	            	} 
	            }
	            //no profile was set
	            if(sendAs.receivedProfileVersion_ == 0) {
	            	continue;
	            }
	
	            for (boolean sync : new boolean[] { true, false}) {
	                Obj myProfile = profileObjForLocalUser(sendAs, sync, includePrincipal);
	                long version = myProfile.getJson().optLong(ProfileObj.VERSION);
	                MFeed feed = prepareFeedForSync(account, sendAs, version, sync);
	                if (feed != null) {
	                    if (DBG) Log.d(TAG, "Syncing profiles with replyRequest: " + sync);
	                    // this isn't done in the db, because encoding deletes the one shot feed
	                    // so we have to store the members and the version they should see
	                	long[] ids = mFeedManager.getFeedMembers(feed.id_);
	                	ids_synced.add(Pair.with(ids, version));
	                    Uri feedUri = MusubiContentProvider.uriForItem(Provided.FEEDS, feed.id_);
	                    App.getMusubi(mContext).getFeed(feedUri).postObj(myProfile);
	                }
	            }
	        }
    	} finally {
    		//in case we crash in the above process, prefer losing a profile obj over resending for ever.
	        for(Pair<long[], Long> people_version : ids_synced) {
	            markIdentitiesSynced(people_version.getValue0(), people_version.getValue1());
	        }
	        mLastRun = new Date();
    	}
    }

    /**
     * Creates a one-time-use feed with membership of the account's known
     * identities that require a profile sync
     * 
     * @param account The account whose profile is to be synced.
     * @param onlyUnsynced true to only select accounts that have not been
     * sent a profile from this account.
     */
    MFeed prepareFeedForSync(MMyAccount account, MIdentity sendAs, long version, boolean onlyUnsynced) {
        assert (account.feedId_ != null);
        if (account.feedId_ == null) {
            return null;
        }

        SQLiteDatabase db = mHelper.getWritableDatabase();
        if (mSqlCheckRecipients == null) {
            synchronized (this) {
            	if (mSqlCheckRecipients == null) {
	                /**
	                 * INSERT INTO feed_members(feed_id,identity_id)
	                 *   SELECT #newFeedId#, identity_id
	                 *   FROM feed_members
	                 *   INNER JOIN identities ON identities._id = feed_members.identity_id
	                 *   WHERE 1=1
	                 *   --AND identities.claimed=1
	                 *   AND sent_profile_version #syncConstraint#
	                 *   AND feed_members.feed_id = #accountFeedId# 
	                 */
	                StringBuilder sql = new StringBuilder(100).append("INSERT INTO ")
	                        .append(MFeedMember.TABLE).append("(").append(MFeedMember.COL_FEED_ID)
	                        .append(",").append(MFeedMember.COL_IDENTITY_ID).append(")")
	                        .append(" SELECT ?,").append(MFeedMember.COL_IDENTITY_ID)
	                        .append(" FROM ").append(MFeedMember.TABLE)
	                        .append(" INNER JOIN ").append(MIdentity.TABLE).append(" ON ")
	                        .append(MFeedMember.TABLE).append(".").append(MFeedMember.COL_IDENTITY_ID).append("=")
	                        .append(MIdentity.TABLE).append(".").append(MIdentity.COL_ID)
	                        .append(" WHERE 1=1")
	                        //.append(" AND ").append(MIdentity.TABLE).append(".").append(MIdentity.COL_CLAIMED).append("=1")
	                        .append(" AND ").append(MIdentity.COL_SENT_PROFILE_VERSION)
	                        .append("< ?").append(" AND ").append(MFeedMember.COL_FEED_ID)
	                        .append("=?");
	                mSqlPrepareProfile = db.compileStatement(sql.toString());
	
	                sql.setLength(0);
	                sql.append(" SELECT ").append(MFeedMember.COL_ID).append(" FROM ")
	                        .append(MFeedMember.TABLE).append(" WHERE ")
	                        .append(MFeedMember.COL_FEED_ID + " = ? LIMIT 1");
	                mSqlCheckRecipients = db.compileStatement(sql.toString());
            	}
            }
        }

        try {
        	MFeed newFeed = new MFeed();
            newFeed.type_ = MFeed.FeedType.ONE_TIME_USE;
            newFeed.id_ = -1;

            db.beginTransaction();
            mFeedManager.insertFeed(newFeed);
            assert (newFeed.id_ != -1);
            long accountFeedId = account.feedId_;
            long newFeedId = newFeed.id_;

            synchronized (mSqlPrepareProfile) {
                mSqlPrepareProfile.bindLong(1, newFeedId);
                if (onlyUnsynced) {
                    // Unsynced profiles have version 0 < 1L.
                    mSqlPrepareProfile.bindLong(2, 1L);
                } else {
                    // Otherwise look for version # < currentVersion.
                    mSqlPrepareProfile.bindLong(2, version);
                }
                mSqlPrepareProfile.bindLong(3, accountFeedId);
                mSqlPrepareProfile.execute();
            }

            try {
                synchronized (mSqlCheckRecipients) {
                    mSqlCheckRecipients.bindLong(1, newFeedId);
                    mSqlCheckRecipients.simpleQueryForLong();
                }

                // At least one recipient:
                mFeedManager.ensureFeedMember(newFeedId, sendAs.id_);
                db.setTransactionSuccessful();
                return newFeed;
            } catch (SQLiteDoneException e) {
                // No recipients:
                // unsuccessful transaction discards feed
                return null;
            }
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Marks the members of the given feed as synced inefficiently.
     */
    void markIdentitiesSynced(long[] identityIds, long profileVersion) {
    	long start = System.currentTimeMillis();
        SQLiteDatabase db = mHelper.getWritableDatabase();
        if (mSqlMarkIdentitiesSynced == null) {
            synchronized (this) {
                String sql = new StringBuilder(100).append(" UPDATE ").append(MIdentity.TABLE)
                        .append(" SET ").append(MIdentity.COL_SENT_PROFILE_VERSION).append("=?")
                        .append(" WHERE ").append(MIdentity.COL_ID).append("=?")
                        .append(" AND ").append(MIdentity.COL_SENT_PROFILE_VERSION).append("<?").toString();
                mSqlMarkIdentitiesSynced = db.compileStatement(sql.toString());
            }
        }

        synchronized (mSqlMarkIdentitiesSynced) {
        	final int batch = 20;
        	for(int i = 0; i < identityIds.length; ++i) {
        		if (i % batch == 0) {
        			if (i > 0) {
        				db.setTransactionSuccessful();
        				db.endTransaction();
        			}
        			db.beginTransaction();
        		}
	            mSqlMarkIdentitiesSynced.bindLong(1, profileVersion);
	            mSqlMarkIdentitiesSynced.bindLong(2, identityIds[i]);
	            mSqlMarkIdentitiesSynced.bindLong(3, profileVersion);
	            mSqlMarkIdentitiesSynced.execute();
        	}
            db.setTransactionSuccessful();
        	db.endTransaction();
        }
        long time = System.currentTimeMillis() - start;
        if (DBG) Log.d(TAG, "Synced " + identityIds.length + " profiles in " + time);
    }

    /**
     * Marks the members of the given feed as synced.
     */
    void markIdentitiesSyncedOneShot(long[] identityIds, long profileVersion) {
    	long start = System.currentTimeMillis();
        SQLiteDatabase db = mHelper.getWritableDatabase();
        StringBuilder sql = new StringBuilder(200).append(MIdentity.COL_ID).append(" in (");
    	boolean first = true;
    	for (long id : identityIds) {
    		if (!first) sql.append(",");
    		sql.append(id);
    		first = false;
    	}
    	sql.append(")").append(" AND ").append(MIdentity.COL_SENT_PROFILE_VERSION).append("<").append(profileVersion);
    	ContentValues values = new ContentValues();
    	values.put(MIdentity.COL_SENT_PROFILE_VERSION, profileVersion);
    	int count = db.update(MIdentity.TABLE, values, sql.toString(), null);

        Log.d(TAG, "marked " + count + " profiles as synced");
        long time = System.currentTimeMillis() - start;
        Log.d(TAG, "synced profiles in " + time);
    }

    /**
     * @See ProfileObj
     */
    Obj profileObjForLocalUser(MIdentity sendAs, boolean replyRequested, boolean includePrincipal) {
        assert (sendAs != null);
        JSONObject json = new JSONObject();
        byte[] thumbnail = null;
        try {
            mIdentityManager.getMusubiThumbnail(sendAs);
            json.put(ProfileObj.NAME, sendAs.musubiName_);
            //TODO: reuse of this field could cause something weird one day...
            json.put(ProfileObj.VERSION, sendAs.receivedProfileVersion_);
            json.put(ProfileObj.REPLY, replyRequested);
            if(includePrincipal)
            	json.put(ProfileObj.PRINCIPAL, sendAs.principal_);
            thumbnail = sendAs.musubiThumbnail_;
            // TODO: Add local device properties like bluetooth address
            /** @see ProfileObj **/
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return new MemObj(ProfileObj.TYPE, json, thumbnail);
    }

    public ContentObserver getProfileUpdateObserver() {
        return mProfileUpdatedObserver;
    }

    /**
     * Listens for changes to the user's local profile and flags all identities
     * as needing a profile update.
     *
     */
    class ProfileUpdateObserver extends ContentObserver {
        public ProfileUpdateObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            flagIdentitiesForProfileUpdate();
            ProfilePushProcessor.this.dispatchChange(false);
        }

        /**
         * Flags the user's account identities as having been just updated.
         */
        void flagIdentitiesForProfileUpdate() {
            MMyAccount[] accounts = mAccountManager.getMyAccounts();
            StringBuilder builder = new StringBuilder();
            for (MMyAccount a : accounts) {
                if (a.identityId_ != null) {
                    builder.append(",").append(a.identityId_);
                }
            }
            if (builder.length() == 0) {
                Log.w(TAG, "No linked identities for profile update");
                return;
            }
            String myAccountIdentities = builder.substring(1);
            SQLiteDatabase db = App.getDatabaseSource(mContext).getWritableDatabase();
            String table = MIdentity.TABLE;
            ContentValues values = new ContentValues();
            values.put(MIdentity.COL_RECEIVED_PROFILE_VERSION, new Date().getTime());
            String whereClause = MIdentity.COL_ID + " in (" + myAccountIdentities + ")";
            String[] whereArgs = null;
            db.update(table, values, whereClause, whereArgs);
        }
    }

	public static Obj getProfileRequestObj() {
        JSONObject json = new JSONObject();
        try {
            json.put(ProfileObj.REPLY, true);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return new MemObj(ProfileObj.TYPE, json, null);
	};
}
