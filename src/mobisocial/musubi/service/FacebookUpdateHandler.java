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

import gnu.trove.list.linked.TLongLinkedList;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.procedure.TLongProcedure;

import java.util.ArrayList;
import java.util.Date;

import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.crypto.IBIdentity;
import mobisocial.musubi.R;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MMyAccount;
import mobisocial.musubi.model.PresenceAwareNotify;
import mobisocial.musubi.model.helpers.FeedManager;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.model.helpers.MyAccountManager;
import mobisocial.musubi.social.FacebookFriendFetcher;
import mobisocial.musubi.ui.SettingsActivity;
import mobisocial.musubi.ui.fragments.AccountLinkDialog;
import mobisocial.musubi.util.Util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.ContactsContract;
import android.util.Log;

import com.facebook.android.Facebook;

public class FacebookUpdateHandler extends ContentObserver {
    public static final String ACCOUNT_TYPE_FACEBOOK = "com.facebook.auth.login";
	public final static String TAG = "FacebookUpdateHandler";
	public static final boolean DEBUG = false;
	private final Context mContext;
	private final SQLiteOpenHelper mHelper;
	private final HandlerThread mThread;
    private final IdentitiesManager mIdentityManager;
    private final MyAccountManager mAccountManager;
    //private final ContactDataVersionManager mContactDataVersionManager;
	private FeedManager mFeedManager;
    private boolean mProfileDataChanged;
    private boolean mIdentityAdded;

	private static final int BATCH_SIZE = 50;

    private static final long MINIMUM_BACKOFF = 10 * 1000;
    private static final long MAXIMUM_BACKOFF = 30 * 60 * 1000;
    private Long mBackoff;

	public static FacebookUpdateHandler newInstance(Context context, SQLiteOpenHelper dbh) {
        HandlerThread thread = new HandlerThread("FacebookUpdateThread");
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
        return new FacebookUpdateHandler(context, dbh, thread);
    }
	
	private FacebookUpdateHandler(Context context, SQLiteOpenHelper dbh, HandlerThread thread) {
		super(new Handler(thread.getLooper()));
		mContext = context;
		mHelper = dbh;
		mThread = thread;
		mIdentityManager = new IdentitiesManager(dbh);
        mAccountManager = new MyAccountManager(dbh);
        mFeedManager = new FeedManager(dbh);
        mBackoff = 0L;
        context.getContentResolver().registerContentObserver(MusubiService.NETWORK_CHANGED, false, 
                new ResetBackOffAndReconnectIfNotConnected(new Handler(thread.getLooper())));
	}
    
    public class ResetBackOffAndReconnectIfNotConnected extends ContentObserver {
        Handler mHandler;
        public ResetBackOffAndReconnectIfNotConnected(Handler handler) {
            super(handler);
            mHandler = handler;
        }
        @Override
        public void onChange(boolean selfChange) {
            mBackoff = 0L;
            FacebookUpdateHandler.this.dispatchChange(false);
        }
    }
    
    private void retryAfterBackoff() {
        Long backoff = MINIMUM_BACKOFF;
        synchronized (mBackoff) {
            backoff = mBackoff * 2;
            backoff = (backoff > MAXIMUM_BACKOFF) ? MAXIMUM_BACKOFF : backoff;
            mBackoff = backoff;
        }
        new Handler(mThread.getLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                mContext.getContentResolver().notifyChange(
                        MusubiService.FACEBOOK_FRIEND_REFRESH,
                        FacebookUpdateHandler.this);
            }
        }, mBackoff);
    }
	
	private void notifyOnAuthError() {
        Intent launch = new Intent(mContext, SettingsActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(mContext, 0,
                launch, PendingIntent.FLAG_CANCEL_CURRENT);
        (new PresenceAwareNotify(mContext)).notify("Sign-In Required",
                "Facebook account failed to connect", contentIntent);
	}
	
	 @Override
	 public void onChange(boolean selfChange) {
		 Facebook fb = AccountLinkDialog.getFacebookInstance(mContext);
		 FacebookFriendFetcher fetcher = new FacebookFriendFetcher(fb);
		 final Date start = new Date();
		 
		 // only get friends' info after last updated time
		 // TODO: find another way to keep track of data version
		 /*long lastUpdateTime = mContactDatenaVersionManager.getLastFacebookUpdateTime();
		 long nextUpdateTime = lastUpdateTime;*/
		 JSONArray friendList = null;
		 try {
		     friendList = fetcher.getFriendInfo();
		 } catch (Exception e) {
		     Log.i(TAG, "Non-auth facebook error. Retrying.");
		     retryAfterBackoff();
		     return;
		 }
		 mBackoff = 0L;
		 
		 if(friendList == null) {
			 Log.i(TAG, "not connected to facebook. cannot get updates");
			 if (fb.getAccessToken() != null) {
			     notifyOnAuthError();
			 }
			 return;
		 } else if (friendList.length() == 0) {
			 Log.i(TAG, "no friends found");
			 //AccountLinkDialog.refreshFacebookToken(mContext);
			 return;
		 }
		 Log.i(TAG, "found " + friendList.length() + " friends");
		 TLongLinkedList ids = new TLongLinkedList();
		 
		 // split up list and do batch update to our database
		 final SQLiteDatabase db = mHelper.getWritableDatabase();
		 try {
			 TLongObjectHashMap<String> photo_uris = new TLongObjectHashMap<String>(BATCH_SIZE);
			 ArrayList<MIdentity> idents = new ArrayList<MIdentity>(BATCH_SIZE);
			 for(int i = 0; i < friendList.length(); ) {
				 int max = i + BATCH_SIZE;
				 photo_uris.clear();
				 idents.clear();

				 if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
					 db.beginTransactionNonExclusive();
				 } else {
					 db.beginTransaction();
				 }
				 for(; i < max && i < friendList.length(); ++i) {
					 JSONObject friend = friendList.getJSONObject(i);
					 long fb_id = friend.getLong("uid");
	 	             IBIdentity ibid = new IBIdentity(Authority.Facebook, String.valueOf(fb_id), 0);
	 	             MIdentity ident = ensureIdentity(fb_id, friend.getString("name"), ibid);
 		             idents.add(ident);
 		             ids.add(ident.id_);
 		             photo_uris.put(ident.id_, friend.getString("pic_square"));
				 }
				 db.setTransactionSuccessful();
				 db.endTransaction(); 

				 //TODO: update profile photos?
				 for(MIdentity ident : idents) {
					 if(mIdentityManager.hasThumbnail(ident)) {
						 continue;
					 }
					 ident.thumbnail_ = FacebookFriendFetcher.getImageFromURL(photo_uris.get(ident.id_));
					 if(ident.thumbnail_  != null) {
						 mIdentityManager.updateThumbnail(ident);
						 mProfileDataChanged = true;
					 }
				 }
			 } 
		 } catch(JSONException e) {
			 Log.e(TAG, e.toString());
		 }
		 
		 
		 if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			 db.beginTransactionNonExclusive();
		 } else {
			 db.beginTransaction();
		 }
		 //add all detected members to account feed
		 final String email = fetcher.getLoggedinUserEmail();
		 final String fb_id = fetcher.getLoggedinUserId();
		 if (email != null && fb_id != null) {
    		 MMyAccount cached_account = mAccountManager.lookupAccount(email, ACCOUNT_TYPE_FACEBOOK);
    		 if(cached_account == null) {
    			 IBIdentity ibid;
    			 ibid = new IBIdentity(Authority.Facebook, fb_id, 0);
    			 cached_account = new MMyAccount();
    			 cached_account.accountName_ = email;
    			 cached_account.accountType_ = ACCOUNT_TYPE_FACEBOOK;
    			 MIdentity existingId = mIdentityManager.getIdentityForIBHashedIdentity(ibid);
    			 if (existingId != null) {
    				 cached_account.identityId_ = existingId.id_;
    			 }
    			 mAccountManager.insertAccount(cached_account);
    		 }
    		 final MMyAccount account = cached_account;
    		 if(account.feedId_ == null) {
    			 MFeed feed = new MFeed();
    			 feed.accepted_ = false; //not visible
    			 feed.type_ = MFeed.FeedType.ASYMMETRIC;
    			 feed.name_ = MFeed.LOCAL_WHITELIST_FEED_NAME;
    			 mFeedManager.insertFeed(feed);
    			 account.feedId_ = feed.id_;
    			 mAccountManager.updateAccount(account);
    		 }
    		 
    		 ids.forEach(new TLongProcedure() {
    			 @Override
    			 public boolean execute(long id) {
    				 MIdentity ident = mIdentityManager.getIdentityForIBHashedIdentity(
    						 new IBIdentity(Authority.Facebook, id + "", 0));
    				 if (ident != null && ident.id_ != account.identityId_) {
    					 mFeedManager.ensureFeedMember(account.feedId_, ident.id_);
    				 }
    				 return true;
    			 }
    		 });
		 }

		 db.setTransactionSuccessful();
		 db.endTransaction();
		 
		 // set last update time the id that has the lastest profile_update_time
		 //mContactDataVersionManager.setLastFacebookUpdateTime(nextUpdateTime);
		 
		 Date end = new Date();
		 double time = end.getTime() - start.getTime();
		 time /= 1000;
		 Log.i(TAG, "update address book took " + time + " seconds");
		 
		 // wake up content observers
		 if (mIdentityAdded) {
			 //wake up the profile push
			 mContext.getContentResolver().notifyChange(MusubiService.WHITELIST_APPENDED, this);
		 }
		 if (mProfileDataChanged) {
			 //refresh the ui...
			 mContext.getContentResolver().notifyChange(MusubiService.PRIMARY_CONTENT_CHANGED, this);
		 }
		 if(mIdentityAdded | mProfileDataChanged) {
			 //update the our musubi address book as needed.
			 String accountName = mContext.getString(R.string.account_name);
			 String accountType = mContext.getString(R.string.account_type);
			 Account ac = new Account(accountName, accountType);
			 ContentResolver.requestSync(ac, ContactsContract.AUTHORITY, new Bundle());
		 }
         
         // Refresh Facebook token in case it has expired
		 // Do this last because the token should still be good at this point, but 
		 // may be close to expiring.
         //AccountLinkDialog.refreshFacebookToken(mContext);
	 }
	 MIdentity ensureIdentity(long contact_id, String display_name, IBIdentity id) {
         MIdentity ident = mIdentityManager.getIdentityForIBHashedIdentity(id);
         boolean changed = false;
         boolean insert = false;
         if(ident == null) {
             ident = new MIdentity();
             insert = true;
             //stuff that lets us reach them
             ident.type_ = id.authority_;
             ident.principal_ = id.principal_;
             ident.principalHash_ = id.hashed_;
             ident.principalShortHash_ = Util.shortHash(id.hashed_);
             //stuff that makes them pretty
             ident.name_ = display_name;
             mIdentityAdded = true;
         }
         if(!ident.whitelisted_) {
        	 changed = true;
        	 ident.whitelisted_ = true;
             //dont' change the blocked flag here, because it could only have
             //been set through explicit user interaction
        	 mIdentityAdded = true;
         }

         if(display_name != null && ident.name_ == null) {
             changed = true;
             ident.name_ = display_name;
         }
         if(insert) {
        	 if(DEBUG) Log.i(TAG, "insert facebook user " + display_name);
        	 ident.whitelisted_ = true;
             mIdentityManager.insertIdentity(ident);
             mFeedManager.acceptFeedsFromMember(mContext, ident.id_);
         } else if(changed) {
        	 if(DEBUG) Log.i(TAG, "update facebook user " + display_name);
             mIdentityManager.updateIdentity(ident);
             mProfileDataChanged = true;
         }
         return ident;
     }
}
