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

import gnu.trove.iterator.TLongIterator;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.procedure.TLongProcedure;
import gnu.trove.set.hash.TLongHashSet;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.crypto.IBIdentity;
import mobisocial.musubi.App;
import mobisocial.musubi.BootstrapActivity;
import mobisocial.musubi.R;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MMyAccount;
import mobisocial.musubi.model.helpers.ContactDataVersionManager;
import mobisocial.musubi.model.helpers.DatabaseManager;
import mobisocial.musubi.model.helpers.FeedManager;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.model.helpers.MyAccountManager;
import mobisocial.musubi.model.helpers.SQLClauseHelper;
import mobisocial.musubi.util.IdentityCache;
import mobisocial.musubi.util.Util;

import org.apache.commons.io.IOUtils;
import org.javatuples.Pair;

import android.accounts.Account;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;
import android.util.Log;

public class AddressBookUpdateHandler extends ContentObserver {
    private static final int BATCH_SIZE = 50;

    public static int sAddressBookTotal = 0;
    public static int sAddressBookPosition = 0;

    //at most twice / minute
    private static final int ONCE_PER_PERIOD = 30 * 1000;
	private static final String TAG = "AddressBookUpdateHandler";
    private static final boolean DBG = false;
    private final Context mContext;

	private final IdentityCache mContactThumbnailCache;
	HandlerThread mThread;
        
    int mChangeCount = 0;
	private String mAccountType;
	private static final String NAME_OR_OTHER_SELECTION = 
		ContactsContract.Data.MIMETYPE + "='" + CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE + "' OR " ;
	private static final String FACEBOOK_MIMETYPE = "vnd.android.cursor.item/vnd.facebook.profile";
	private static final String TWITTER_MIMETYPE = "vnd.android.cursor.item/vnd.twitter.profile";
	private static final String BASE_ACCOUNT_TYPES_SELECTION =     			
		ContactsContract.Data.MIMETYPE + "='" + CommonDataKinds.Email.CONTENT_ITEM_TYPE + "' ";
	private static final String FACEBOOK_ACCOUNT_TYPES_SELECTION =     			
			" OR " + ContactsContract.Data.MIMETYPE + "='" + FACEBOOK_MIMETYPE + "' ";
	private static final String TWITTER_ACCOUNT_TYPES_SELECTION =     			
			" OR " + ContactsContract.Data.MIMETYPE + "='" + TWITTER_MIMETYPE + "' ";
	private static final String PHONE_ACCOUNT_TYPES_SELECTION =     			
			" OR " + ContactsContract.Data.MIMETYPE + "='" + CommonDataKinds.Phone.CONTENT_ITEM_TYPE + "' ";
	private long mLastRun;
	private boolean mScheduled;
	private long mSleepTime = 0;
	static final int SLEEP_SCALE = 14;

	//Note that if you change these, you will need to clear the contact data version
	//table and reset the sync state table in the upgrade process.
	private final static boolean SYNC_EMAIL = true; //must always be set, code will fail if you change this to false (because of query building logic)
	private final static boolean SYNC_PHONE = false;
	private final static boolean SYNC_TWITTER = false;
	private final static boolean SYNC_FACEBOOK = true;

    public AddressBookUpdateHandler(Context context, SQLiteOpenHelper dbh, HandlerThread thread, ContentResolver resolver) {
        super(new Handler(thread.getLooper()));
        mThread = thread;
    	mContext = context.getApplicationContext();
    	mContactThumbnailCache = App.getContactCache(context);
        mAccountType = mContext.getString(R.string.account_type);

        resolver.registerContentObserver(MusubiService.FORCE_RESCAN_CONTACTS, false, new ContentObserver(new Handler(thread.getLooper())) {
        	public void onChange(boolean selfChange) {
        		mLastRun = -1;
        		AddressBookUpdateHandler.this.dispatchChange(false);
        	}
		});

        dispatchChange(false);
    }

    static final Pattern getEmailPattern() {
    	return Pattern.compile("\\b[A-Z0-9._%-]+@[A-Z0-9.-]+\\.[A-Z]{2,4}\\b", Pattern.CASE_INSENSITIVE);
    }

    static final Pattern getNumberPattern() {
    	return Pattern.compile("[0-9]+");
    }

    @Override
    public void onChange(boolean selfChange) {
        final DatabaseManager dbManager = new DatabaseManager(mContext);
        if (!dbManager.getIdentitiesManager().hasConnectedAccounts()) {
            Log.w(TAG, "no connected accounts, skipping friend import");
            return;
        }

        //a new meta contact appears (and the previous ones disappear) if the user merges
        //or if a new entry is added, we can detect the ones that have changed by
        //this condition
        long highestContactIdAlreadySeen = dbManager.getContactDataVersionManager().getMaxContactIdSeen();
        //a new data item corresponds with a new contact, but its possible
        //that a users just adds a new contact method to an existing contact
        //and we need to detect that
        long highestDataIdAlreadySeen = dbManager.getContactDataVersionManager().getMaxDataIdSeen();

        // BJD -- this didn't end up being faster once all import features were added.
        /*if (highestContactIdAlreadySeen == -1) {
        	importFullAddressBook(mContext);
        	return;
        }*/
        long now = System.currentTimeMillis();
    	if(mLastRun + ONCE_PER_PERIOD > now) {
    		//wake up when the period expires
    		if(!mScheduled) {
	    		new Handler(mThread.getLooper()).postDelayed(new Runnable() {
					@Override
					public void run() {
				        mScheduled = false;
						dispatchChange(false);
					}
				}, ONCE_PER_PERIOD - (now - mLastRun) + 1);
    		}
    		mScheduled = true;
    		//skip this update
    		return;
    	}
    	Log.i(TAG, "waking up to handle contact changes...");
    	boolean identityAdded = false, profileDataChanged = false;
    	Date start = new Date();

        assert(SYNC_EMAIL);
        String account_type_selection = getAccountSelectionString();
    	
    	Cursor c = mContext.getContentResolver().query(ContactsContract.Data.CONTENT_URI, 
    		new String[] { 
    			ContactsContract.Data._ID,
    			ContactsContract.Data.DATA_VERSION,
    			ContactsContract.Data.CONTACT_ID
    		},
    		"(" + 
    				ContactsContract.Data.DATA_VERSION + ">0 OR " + //maybe updated
    				ContactsContract.Data.CONTACT_ID + ">? OR " + 	//definitely new or merged
    				ContactsContract.Data._ID + ">? " + 			//definitely added a data item
    		") AND (" +    				
    			ContactsContract.RawContacts.ACCOUNT_TYPE + "<>'" + mAccountType + "'" +
    		") AND (" +    				
    		NAME_OR_OTHER_SELECTION + account_type_selection +
    		")", // All known contacts.
    		new String[] {  String.valueOf(highestContactIdAlreadySeen), String.valueOf(highestDataIdAlreadySeen) }, 
    		null
        );

        if (c == null) {
            Log.e(TAG, "no valid cursor", new Throwable());
            mContext.getContentResolver().notifyChange(MusubiService.ADDRESS_BOOK_SCANNED, this);
            return;
        }
    	
    	HashMap<Pair<String,String>, MMyAccount> account_mapping = new HashMap<Pair<String,String>, MMyAccount>();
    	int max_changes = c.getCount();
    	TLongArrayList raw_data_ids = new TLongArrayList(max_changes);
    	TLongArrayList versions = new TLongArrayList(max_changes);
    	long new_max_data_id = highestDataIdAlreadySeen;
    	long new_max_contact_id = highestContactIdAlreadySeen;
    	TLongHashSet potentially_changed = new TLongHashSet();
    	try {
    		//the cursor points to a list of raw contact data items that may have changed
	    	//the items will include a type specific field that we are interested in updating
	    	//it is possible that multiple data item entries mention the same identifier
	    	//so we build a list of contacts to update and then perform synchronization
	    	//by refreshing given that we know the top level contact id.
    	    if (DBG) Log.d(TAG, "Scanning " + c.getCount() + " contacts...");
	    	while(c.moveToNext()) {
	    	    if (DBG) Log.v(TAG, "check for updates of contact " + c.getLong(0));
	
	    		long raw_data_id = c.getLong(0);
	    		long version = c.getLong(1);
	    		long contact_id = c.getLong(2);
	    		
	    		//if the contact was split or merged, then we get a higher contact id
	    		//so if we have a higher id, data version doesnt really matter
	    		if(contact_id <= highestContactIdAlreadySeen) {
		    		//the data associated with this contact may not be dirty
		    		//we just can't do the join against our table because thise
		    		//api is implmented over the content provider
		    		if(dbManager.getContactDataVersionManager().getVersion(raw_data_id) == version)
		    			continue;
	    		} else {
		    		new_max_contact_id = Math.max(new_max_contact_id, contact_id);
	    		}	    		
	    		raw_data_ids.add(raw_data_id);
	    		versions.add(version);
	    		potentially_changed.add(contact_id);
	    		new_max_data_id = Math.max(new_max_data_id, raw_data_id);
	    	}
	    	if (DBG) Log.d(TAG, "Finished iterating over " + c.getCount() + " contacts for " + potentially_changed.size() + " candidates.");
    	} finally {
    		c.close();
    	}
    	if(potentially_changed.size() == 0) {
    		Log.w(TAG, "possible bug, woke up to update contacts, but no change was detected; there are extra wakes so it could be ok");
    	}
    	
    	final SQLiteDatabase db = dbManager.getDatabase();

    	Pattern emailPattern = getEmailPattern();
    	Pattern numberPattern = getNumberPattern();
    	//slice it up so we don't use too much system resource on keeping a lot of state in memory
    	int total = potentially_changed.size();
    	sAddressBookTotal = total;
    	sAddressBookPosition = 0;

    	final TLongArrayList slice_of_changed = new TLongArrayList(BATCH_SIZE);
    	final StringBuilder to_fetch = new StringBuilder();
        final HashMap<Pair<String, String>, TLongHashSet> ids_for_account =
                new HashMap<Pair<String,String>, TLongHashSet>();
        final TLongObjectHashMap<String> names = new TLongObjectHashMap<String>();

    	TLongIterator it = potentially_changed.iterator();
    	for(int i = 0; i < total && it.hasNext();) {
    	    sAddressBookPosition = i;

    	    if (BootstrapActivity.isBootstrapped()) {
        		try {
        			Thread.sleep(mSleepTime * SLEEP_SCALE);
        		} catch(InterruptedException e) {}
    	    }

    	    slice_of_changed.clear();
    	    ids_for_account.clear();
    	    names.clear();

    		int max = i + BATCH_SIZE;
    		for(; i < max && it.hasNext(); ++i) {
    			slice_of_changed.add(it.next());
    		}
	    	

    		if(DBG) Log.v(TAG, "looking up names ");
    		to_fetch.setLength(0);
    		to_fetch.append(ContactsContract.Contacts._ID + " IN ");
    		SQLClauseHelper.appendArray(to_fetch, slice_of_changed.iterator());
    		//lookup the fields we care about from a user profile perspective
        	c = mContext.getContentResolver().query(ContactsContract.Contacts.CONTENT_URI, 
        		new String[] { 
        			ContactsContract.Contacts._ID,
        			ContactsContract.Contacts.DISPLAY_NAME,
        		},
        		to_fetch.toString(),
        		null,
        		null
        	);
        	try {
        		while(c.moveToNext()) {
            		long id = c.getLong(0);
    	    		String name = c.getString(1);
            		if(name == null)
            			continue;
            		//reject names that are just the email address or are just a number 
            		//the default for android is just to propagate this as the name
            		//if there is no name
    	    		if(emailPattern.matcher(name).matches() || numberPattern.matcher(name).matches())
    	    			continue;
	    			names.put(id, name);
	    		}
        	} finally {
        		c.close();
        	}

        	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
        		db.beginTransactionNonExclusive();
        	} else {
        		db.beginTransaction();
        	}

            long before = SystemClock.elapsedRealtime();
        	SliceUpdater updater = new SliceUpdater(dbManager, slice_of_changed, ids_for_account, names, account_type_selection);
            long after = SystemClock.elapsedRealtime();
            mSleepTime = (mSleepTime + after - before) / 2;
        	slice_of_changed.forEach(updater);
        	profileDataChanged |= updater.profileDataChanged;
        	identityAdded |= updater.identityAdded;
    		db.setTransactionSuccessful();
    		db.endTransaction();

    		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
        		db.beginTransactionNonExclusive();
        	} else {
        		db.beginTransaction();
        	}
        	//add all detected members to account feed
        	for(Entry<Pair<String, String>, TLongHashSet> e : ids_for_account.entrySet()) {
        		Pair<String, String> k = e.getKey();
        		TLongHashSet v = e.getValue();
        		MMyAccount cached_account = account_mapping.get(k);
        		if(cached_account == null) {
                    cached_account = lookupOrCreateAccount(dbManager, k.getValue0(), k.getValue1());
                    prepareAccountWhitelistFeed(dbManager.getMyAccountManager(), dbManager.getFeedManager(), cached_account);
                    account_mapping.put(k, cached_account);
        		}

        		final MMyAccount account = cached_account;
        		v.forEach(new TLongProcedure() {
    				@Override
    				public boolean execute(long id) {
    		    		dbManager.getFeedManager().ensureFeedMember(account.feedId_, id);
    		    		db.yieldIfContendedSafely(75);
    		    		return true;
    				}
    			});
        	}
        	db.setTransactionSuccessful();
        	db.endTransaction();
    	}

    	sAddressBookTotal = sAddressBookPosition = 0;

    	//TODO: handle deleted
    	//for all android data ids in our table, check if they still exist in the
    	//contacts table, probably in batches of 100 or something.  if they don't
    	//null them out.  this is annoyingly non-differential.
    	

    	//TODO: adding friend should update accepted feed status, however,
    	//if a crashe happens for whatever reason, then its possible that this may need to
    	//be run for identities which actually exist in the db.  so this update code
    	//needs to do the feed accepted status change for all users that were touched
    	//by the profile update process

		//update the version ids so we can be faster on subsequent runs
    	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
    		db.beginTransactionNonExclusive();
    	} else {
    		db.beginTransaction();
    	}
		int changed_data_rows = raw_data_ids.size();
		for(int i = 0; i < changed_data_rows; ++i) {
			dbManager.getContactDataVersionManager().setVersion(raw_data_ids.get(i), versions.get(i));
		}
    	db.setTransactionSuccessful();
    	db.endTransaction();
    	
    	dbManager.getContactDataVersionManager().setMaxDataIdSeen(new_max_data_id);
    	dbManager.getContactDataVersionManager().setMaxContactIdSeen(new_max_contact_id);
    	ContentResolver resolver = mContext.getContentResolver();

    	Date end = new Date();
    	double time = end.getTime() - start.getTime();
    	time /= 1000;
    	Log.w(TAG, "update address book " + mChangeCount++ + " took " + time + " seconds");
    	if (identityAdded) {
    		//wake up the profile push
    	    resolver.notifyChange(MusubiService.WHITELIST_APPENDED, this);
    	}
    	if (profileDataChanged) {
    		//refresh the ui...
    	    resolver.notifyChange(MusubiService.PRIMARY_CONTENT_CHANGED, this);
    	}
    	if (identityAdded || profileDataChanged) {
    		//update the our musubi address book as needed.
    	    String accountName = mContext.getString(R.string.account_name);
    		String accountType = mContext.getString(R.string.account_type);
    		Account account = new Account(accountName, accountType);
			ContentResolver.requestSync(account, ContactsContract.AUTHORITY, new Bundle());
    	}

    	dbManager.close();
        mLastRun = new Date().getTime();
        resolver.notifyChange(MusubiService.ADDRESS_BOOK_SCANNED, this);
   }

	private static MMyAccount lookupOrCreateAccount(DatabaseManager dbManager, String accountName,
			String accountType) {
    	IBIdentity ibid;
        //TODO: this needs to support handling other account types better
        ibid = new IBIdentity(Authority.Email, accountName, 0);
        MMyAccount cached_account = dbManager.getMyAccountManager()
        		.lookupAccount(accountName, accountType);
        if (cached_account == null) {
            cached_account = new MMyAccount();
            cached_account.accountName_ = accountName;
            cached_account.accountType_ = accountType;
            MIdentity existingId = dbManager.getIdentitiesManager()
            		.getIdentityForIBHashedIdentity(ibid);
            if (existingId != null) {
                cached_account.identityId_ = existingId.id_;
            }
            dbManager.getMyAccountManager().insertAccount(cached_account);
        }
        return cached_account;
	}

	private static void prepareAccountWhitelistFeed(MyAccountManager am,
			FeedManager fm, MMyAccount account) {
    	if(account.feedId_ == null) {
			MFeed feed = new MFeed();
			feed.accepted_ = false; //not visible
			feed.type_ = MFeed.FeedType.ASYMMETRIC;
			feed.name_ = MFeed.LOCAL_WHITELIST_FEED_NAME;
			fm.insertFeed(feed);
			account.feedId_ = feed.id_;
			am.updateAccount(account);
		}
	}

	private static String getAccountSelectionString() {
    	String account_type_selection = BASE_ACCOUNT_TYPES_SELECTION;
        if(SYNC_FACEBOOK)
        	account_type_selection += FACEBOOK_ACCOUNT_TYPES_SELECTION;
        if(SYNC_PHONE)
        	account_type_selection += PHONE_ACCOUNT_TYPES_SELECTION;
        if(SYNC_TWITTER)
        	account_type_selection += TWITTER_ACCOUNT_TYPES_SELECTION;
        return account_type_selection;
	}

	class SliceUpdater implements TLongProcedure {
        public boolean profileDataChanged;
        TLongObjectHashMap<String> names;
        TLongArrayList slice_of_changed;
        HashMap<Pair<String, String>, TLongHashSet> ids_for_account;
        public boolean identityAdded;
        private final DatabaseManager mDatabaseManager;
        int i = 0;

		// Member variables used to avoid allocations across calls to execute()
		private final String[] mAccountColumns;
		private final String mAccountSelection;
		private final String[] mAccountSelectionArgs;

        private SliceUpdater(DatabaseManager dbManager, TLongArrayList slice_of_changed,
                HashMap<Pair<String, String>, TLongHashSet> ids_for_account,
                TLongObjectHashMap<String> names, String accountSelection) {
            mDatabaseManager = dbManager;
            this.ids_for_account = ids_for_account;
            this.slice_of_changed = slice_of_changed;
            this.names = names;

            mAccountColumns = new String[] {
                    ContactsContract.Data.DATA1,
                    ContactsContract.RawContacts.ACCOUNT_NAME,
                    ContactsContract.RawContacts.ACCOUNT_TYPE,
                    ContactsContract.Data.MIMETYPE,
                };
            mAccountSelection = ContactsContract.Data.CONTACT_ID + "=?" + " AND (" +
                    accountSelection + ")";
            mAccountSelectionArgs = new String[1];
        }

        @Override
        public boolean execute(long contact_id) {
            //for all types of identity
            //- ensure the row exists
            //- ensure the linked android id equals the value of this contact
            //- update the profile fields
            mAccountSelectionArgs[0] = String.valueOf(contact_id);
            Cursor c = mContext.getContentResolver().query(ContactsContract.Data.CONTENT_URI, 
                mAccountColumns, mAccountSelection, mAccountSelectionArgs, null);
            try {
                while(c.moveToNext()) {
                    String type = c.getString(3);
                    String principal = c.getString(0);
                    String accountName = c.getString(1);
                    String accountType = c.getString(2);
                	if(accountName == null) {
                		accountName = "null-account-name";
                	}
                	if(accountType == null) {
                		accountType = "null-account-type";
                	}
                    IBIdentity id = ibIdentityForData(type, principal);
                    if (id == null) {
                        continue;
                    }

                    if(DBG) Log.v(TAG, "updating contact " + contact_id);
                    //lookup the fields we care about from a user profile perspective
                    String display_name = names.get(contact_id);
                    byte[] photo_thumbnail = null;
                    Uri uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contact_id);
                    InputStream is = ContactsContract.Contacts.openContactPhotoInputStream(mContext.getContentResolver(), uri);
                    if (is != null) {
                        if(DBG) Log.v(TAG, "importing photo for " + display_name);
                        try {
                            photo_thumbnail = IOUtils.toByteArray(is);
                        } catch (IOException e) {
                            Log.e(TAG, "photo thumbnail failed to serialize", e);
                        } finally {
                            try {
                                is.close();
                            } catch(IOException e) {}
                        }
                    }

                    MIdentity ident = ensureIdentity(contact_id, display_name, photo_thumbnail, id, mDatabaseManager);
                    Pair<String, String> k = Pair.with(accountName, accountType);
                    TLongHashSet ids = ids_for_account.get(k);
                    if(ids == null) {
                        ids = new TLongHashSet();
                        ids_for_account.put(k, ids);
                    }
                    ids.add(ident.id_);
                    mDatabaseManager.getDatabase().yieldIfContendedSafely(100);
                }
            } finally {
                c.close();
            }
            return true;
        }

        MIdentity ensureIdentity(long contact_id, String display_name, byte[] photo_thumbnail,
        		IBIdentity id, DatabaseManager dbManager) {
            MIdentity ident = dbManager.getIdentitiesManager().getIdentityForIBHashedIdentity(id);
            boolean changed = false;
            boolean insert = false;
            boolean picture_changed = false;
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
                ident.thumbnail_ = photo_thumbnail;
            }
            //This is a little weird because there may be several contacts that update this one
            //so its possible for it to go through a sequence of changes before it settles on
            //one.  We could defer all the updates until we knew for sure there was a change...
            //but this seems okay until we look much more closely later

            //if the identity is new, there couldnt possibly any feeds to accept
            boolean accept_feeds = false;
            //the main strategy here is to update the field we use for display/queries
            //if the musubi version hasnt been populated
            //TODO: in the future, maybe take the newest across all services or something
            //like that?
            if(!ident.whitelisted_) {
            	changed = true;
                //dont' change the blocked flag here, because it could only have
                //been set through explicit user interaction
                ident.whitelisted_ = true;
                accept_feeds = true;
                identityAdded = true;
            }
            if(ident.androidAggregatedContactId_ == null || contact_id != ident.androidAggregatedContactId_ || ident.androidAggregatedContactId_ != contact_id) {
                ident.androidAggregatedContactId_ = contact_id;
            	changed = true;
            }
            if(display_name != null && (ident.name_ == null || !ident.name_.equals(display_name))) {
                changed = true;
                ident.name_ = display_name;
            }
            //TODO: is there a way to detect if the thumbnail actually changed?
            if(photo_thumbnail != null && (ident.thumbnail_ == null || !Arrays.equals(ident.thumbnail_, photo_thumbnail))) {
                picture_changed = true;
                ident.thumbnail_ = photo_thumbnail;
            }
            if(insert) {
            	dbManager.getIdentitiesManager().insertIdentity(ident);
            } else if(picture_changed || changed) {
            	if(picture_changed) {
            		dbManager.getIdentitiesManager().updateThumbnail(ident);
                     mContactThumbnailCache.invalidate(ident.id_);
            	}
            	if(changed) {
            		dbManager.getIdentitiesManager().updateIdentity(ident);
            	}
                profileDataChanged = true;
            }
            if(accept_feeds) {
            	dbManager.getFeedManager().acceptFeedsFromMember(mContext, ident.id_);
            }
            return ident;
        }
    }
    

	public static AddressBookUpdateHandler newInstance(Context context, SQLiteOpenHelper dbh, ContentResolver resolver) {
        HandlerThread thread = new HandlerThread("AddressBookUpdateThread");
        Process.setThreadPriority(Process.THREAD_PRIORITY_LOWEST);
        thread.start();
        AddressBookUpdateHandler abuh = new AddressBookUpdateHandler(context, dbh, thread, resolver);
        return abuh;
    }

	static IBIdentity ibIdentityForData(String type, String principal) {
		if(type.equals(CommonDataKinds.Email.CONTENT_ITEM_TYPE)) {
        	if(!SYNC_EMAIL)
        		return null;
            //TODO: canonicalizing emails like gmail? e.g. 
            //. isn't really considered part of the address, 
            //+after either
            //TODO: filter out this data item if it looks like a 
            //mailing list or common corporate sending address
            return new IBIdentity(Authority.Email, principal, 0);
        } else if(type.equals(CommonDataKinds.Phone.CONTENT_ITEM_TYPE)){
        	if(!SYNC_PHONE)
        		return null;
        	//TODO: phone number/sms support for server
            //TODO: phone number must be canonicalized
            return new IBIdentity(Authority.PhoneNumber, principal, 0);
        } else if(type.equals(FACEBOOK_MIMETYPE)){
        	if(!SYNC_FACEBOOK)
        		return null;
            return new IBIdentity(Authority.Facebook, principal, 0);
        } else if(type.equals(SYNC_TWITTER)){
        	if(!SYNC_TWITTER)
        		return null;
        	//TODO: twitter support
            //TODO: sync doesnt really work on phone for the twitter app for me (TJ)
            return new IBIdentity(Authority.Twitter, principal, 0);
        } else {
            return null;
        }
	}

	public static class AddressBookImportTask extends AsyncTask<Void, String, Void> {
	    final String NOTICE = "\n\nYour privacy is important. Musubi never uploads your contacts from your device.";
	    final Context mContext;
	    boolean mNeedsFriends = true;
	    ProgressDialog mDialog;

	    public AddressBookImportTask(Context context) {
	        mContext = context;
	    }

	    @Override
	    protected void onPreExecute() {
	        /*mDialog = new ProgressDialog(mContext);
	        mDialog.setTitle("Preparing friend list...");
	        mDialog.setIndeterminate(true);
	        mDialog.setCancelable(true);
	        mDialog.show();*/
	    }

	    @Override
	    protected Void doInBackground(Void... params) {
	        publishProgress("Scanning address book for friends.");

	        ContentResolver resolver = mContext.getContentResolver();
	        Uri friendPoint = MusubiService.ADDRESS_BOOK_SCANNED;
            ContentObserver friends = new ContentObserver(new Handler(
                    mContext.getMainLooper())) {
                @Override
                public void onChange(boolean selfChange) {
                    mNeedsFriends = false;
                    mContext.getContentResolver().unregisterContentObserver(this);
                }
            };
            resolver.registerContentObserver(friendPoint, false, friends);

            resolver.notifyChange(MusubiService.REQUEST_ADDRESS_BOOK_SCAN, null);
            while (mNeedsFriends) {
                int contacts = AddressBookUpdateHandler.sAddressBookTotal
                        - AddressBookUpdateHandler.sAddressBookPosition;
                if (contacts > 0) {
                    publishProgress("Adding " + contacts
                            + " friends from address book...");
                }

                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                }
            }
	        return null;
	    }

	    @Override
	    protected void onProgressUpdate(String... values) {
	        //mDialog.setMessage(values[0] + NOTICE);
	    	//Log.d(TAG, "-- " + values[0]);
	    }

	    @Override
	    protected void onPostExecute(Void result) {
	        // XXX hack to reconnect accounts.
	        mContext.getContentResolver().notifyChange(MusubiService.AUTH_TOKEN_REFRESH, null);
	        //mDialog.dismiss();
	    }
	}

	public static void importFullAddressBook(Context context) {
		Log.d(TAG, "doing full import");
		SQLiteOpenHelper db = App.getDatabaseSource(context);
		DatabaseManager dbm = new DatabaseManager(context);
		IdentitiesManager idm = dbm.getIdentitiesManager();
		FeedManager fm = dbm.getFeedManager();
		MyAccountManager am = dbm.getMyAccountManager();
		long startTime = System.currentTimeMillis();
		String musubiAccountType = context.getString(R.string.account_type);
		long maxDataId = -1;
		long maxContactId = -1;
		assert(SYNC_EMAIL);
        String account_type_selection = getAccountSelectionString();
    	
    	Cursor c = context.getContentResolver().query(ContactsContract.Data.CONTENT_URI, 
    		new String[] { 
    			ContactsContract.Data.CONTACT_ID,
    			ContactsContract.Contacts.DISPLAY_NAME,
    			ContactsContract.Data._ID,
    			ContactsContract.Data.DATA_VERSION,
    			ContactsContract.Data.DATA1,
    			ContactsContract.Data.MIMETYPE,
    			ContactsContract.RawContacts.ACCOUNT_NAME,
    			ContactsContract.RawContacts.ACCOUNT_TYPE
    		},
    		"(" +    				
    			ContactsContract.RawContacts.ACCOUNT_TYPE + "<>'" + musubiAccountType + "'" +
    		") AND (" +    				
    		NAME_OR_OTHER_SELECTION + account_type_selection +
    		")", // All known contacts.
    		null, null
        );

        if (c == null) {
            Log.e(TAG, "no valid cursor", new Throwable());
            return;
        }

        sAddressBookTotal = c.getCount();
    	sAddressBookPosition = 0;
        Log.d(TAG, "Scanning contacts...");

        final Map<String, MMyAccount> myAccounts = new HashMap<String, MMyAccount>();
        final Pattern emailPattern = getEmailPattern();
    	final Pattern numberPattern = getNumberPattern();
        while (c.moveToNext()) {
        	sAddressBookPosition++;
        	String identityType = c.getString(5);
        	String identityPrincipal = c.getString(4);
        	long contactId = c.getLong(0);
        	long dataId = c.getLong(2);
        	String displayName = c.getString(1);
        	byte[] thumbnail = null;

        	String accountName = c.getString(6);
        	String accountType = c.getString(7);
        	if(accountName == null) {
        		accountName = "null-account-name";
        	}
        	if(accountType == null) {
        		accountType = "null-account-type";
        	}
        	String accountKey = accountName + "-" + accountType;
        	MMyAccount myAccount = myAccounts.get(accountKey);
        	if (myAccount == null) {
        		myAccount = lookupOrCreateAccount(dbm, accountName, accountType);
        		prepareAccountWhitelistFeed(am, fm, myAccount);
        		myAccounts.put(accountKey, myAccount);
        	}

        	if (displayName == null
        		|| emailPattern.matcher(displayName).matches()
        		|| numberPattern.matcher(displayName).matches()) {
    			continue;
        	}

        	IBIdentity ibid = ibIdentityForData(identityType, identityPrincipal);
        	if (ibid == null) {
        		//TODO: better selection
        		//Log.d(TAG, "skipping " + displayName + " // " + identityPrincipal);
        		continue;
        	}
        	Uri uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId);
            InputStream is = ContactsContract.Contacts.openContactPhotoInputStream(context.getContentResolver(), uri);
            if (is != null) {
                //Log.d(TAG, "importing photo for " + displayName);
                try {
                	thumbnail = IOUtils.toByteArray(is);
                } catch (IOException e) {
                	thumbnail = null;
                    //Log.e(TAG, "photo thumbnail failed to serialize", e);
                } finally {
                    try {
                        is.close();
                    } catch(IOException e) {}
                }
            } else {
            	thumbnail = null;
            }
            MIdentity ident = addIdentity(context, idm, contactId, displayName, thumbnail, ibid);
            if (ident != null) {
            	fm.ensureFeedMember(myAccount.feedId_, ident.id_);
            	fm.acceptFeedsFromMember(context, ident.id_);
            }

            maxDataId = Math.max(maxDataId, dataId);
            maxContactId = Math.max(maxContactId, contactId);
        }
        c.close();
        long timeTaken = System.currentTimeMillis() - startTime;

        ContactDataVersionManager cdvm = new ContactDataVersionManager(db);
        cdvm.setMaxDataIdSeen(maxDataId);
    	cdvm.setMaxContactIdSeen(maxContactId);
        Log.d(TAG, "full import took " + timeTaken / 1000 + " secs");
        context.getContentResolver().notifyChange(MusubiService.ADDRESS_BOOK_SCANNED, null);
	}

	/**
	 * Returns the newly created identity or null if no identity was created.
	 */
	static MIdentity addIdentity(Context context, IdentitiesManager idm,
			long contactId, String displayName, byte[] photoThumbnail, IBIdentity id) {
		// TODO: in memory lookup for full import?
        MIdentity ident = idm.getIdentityForIBHashedIdentity(id);
        if(ident != null) {
        	return null;
        }

        ident = new MIdentity();
        ident.whitelisted_ = true;
        ident.type_ = id.authority_;
        ident.principal_ = id.principal_;
        ident.principalHash_ = id.hashed_;
        ident.principalShortHash_ = Util.shortHash(id.hashed_);
        //stuff that makes them pretty
        ident.name_ = displayName;
        ident.thumbnail_ = photoThumbnail;
        ident.androidAggregatedContactId_ = contactId;
        idm.insertIdentity(ident);
        return ident;
	}
}
