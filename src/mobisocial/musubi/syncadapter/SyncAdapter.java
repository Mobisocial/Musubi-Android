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

package mobisocial.musubi.syncadapter;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import mobisocial.musubi.App;
import mobisocial.musubi.R;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.ui.util.UiUtil;
import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.StatusUpdates;
import android.util.Log;

public class SyncAdapter extends AbstractThreadedSyncAdapter {
	public static final boolean DEBUG = false;
	public static final String TAG = "SyncAdapter";
	public static final String CUSTOM_PROTOCOL = "MusubiSyncAdapter";
    public static final String PREFS_NAME = "SyncAdapterPref";
    public static final String LAST_SYNC_MARKER = "lastSyncMarker";

    public static final int CLAIMED_GROUP_ID = 0;
    public static final int WHITELIST_GROUP_ID = 1;
    public static final int GROUP_NUM = 2;
    public static final String[] GROUP_TITLES = {"Claimed", "Whitelist"};
    
	private final String accountType;
	private final String accountName;
	
	private final Context mContext;
	private final IdentitiesManager mIdm;
	private final SQLiteOpenHelper mDatabaseSource;
	
	public SyncAdapter(Context context, boolean autoInitialize) {
		super(context, autoInitialize);
		mContext = context;
		accountType = context.getString(R.string.account_type);
		accountName = context.getString(R.string.account_name);
		mDatabaseSource = App.getDatabaseSource(mContext);
		mIdm = new IdentitiesManager(mDatabaseSource);
	}
	
	/**
	 * Constructor for test
	 * 
	 * @param context
	 * @param autoInitialize
	 * @param name
	 */
	public SyncAdapter(Context context, boolean autoInitialize, String type, String name) {
		super(context, autoInitialize);
		mContext = context;
		accountType = type;
		accountName = name;
		mDatabaseSource = App.getDatabaseSource(mContext);
		mIdm = new IdentitiesManager(mDatabaseSource);
	}

	@Override
	public void onPerformSync(Account account, Bundle extras, String authority,
			ContentProviderClient provider, SyncResult syncRresult) {
		Log.i(TAG, "perform sync");
		final long startTime = new Date().getTime();
		long lastSyncMarker = getSyncMarker(mContext);
		final long[] groupIds = createGroups();
		
		if(lastSyncMarker == 0) {
			//clear all old data if they click remove account, effectively this is our recovery tool
			//TODO: make this into a util that we can use from the upgrade handler for the db or 
			//something along those lines, so that we can deal with the bugs that inevitable exist
			//in the code
			mContext.getContentResolver().delete(ContactsContract.Data.CONTENT_URI, 
					ContactsContract.RawContacts.ACCOUNT_TYPE + "='" + accountType + "'", null);
			mContext.getContentResolver().delete(ContactsContract.RawContacts.CONTENT_URI, 
					ContactsContract.RawContacts.ACCOUNT_TYPE + "='" + accountType + "'", null);
		}
			
		
		// pull updates from identity caches and sync them to address book
		List<MIdentity> updatedContacts = getUpdatedContacts(lastSyncMarker);
		Log.i(TAG, "found " + String.valueOf(updatedContacts.size()) + " updated contacts");
		ContentProviderResult[] results = syncUpdatedContacts(updatedContacts, groupIds, lastSyncMarker);
		Log.i(TAG, "done " + String.valueOf(results.length) + " update operations in " 
				+ String.valueOf((new Date().getTime()-startTime)/1000) + "seconds");
		
	}
	
	public ContentProviderResult[] syncUpdatedContacts(List<MIdentity> updatedContacts, final long[] groupIds,
			final long lastSyncMarker) {
		final BatchOperation batchOp = new BatchOperation(mContext, mContext.getContentResolver());
		ContentProviderResult[] results = new ContentProviderResult[updatedContacts.size()*6];
		int last = 0;
		long rawId = 0;
		long nextSyncMarker = lastSyncMarker;
		if(updatedContacts.size() == 0)
			return results;
		
		List<MIdentity> try_status_updates = new LinkedList<MIdentity>();
		ArrayList<MIdentity> sub_identities = new ArrayList<MIdentity>(8);
		Iterator<MIdentity> it = updatedContacts.iterator();
		MIdentity next = it.next();
		while(next != null) {
			//TODO: this should be whitelisted, but that is the next phase of fixing up the support
			//for whitelisting identities that are only reachable through musubi
			if(next.androidAggregatedContactId_ == null) {
				//if the account isn't whitelisted, then we skip it
				//TODO: somewhere else we were supposed to have added one
				//if we whitelisted a gray list
				//TODO: that one will have a flag set on it that we can detect for handling
				//TODO: if it was removed from the white list, then
				//we need to do some clean up
				if(next.updatedAt_ > lastSyncMarker) {
					nextSyncMarker = next.updatedAt_;
				}
				next = it.hasNext() ? it.next() : null;
				continue;
			}

			//we have to batch by raw_contact we ant to add because otherwise the test below
			//that performs a query will fail.  this is because the batch of operations
			//hasnt actually executed yet, and so it will cause multiple raw_contacts to be inserted
			//for one logical contact that just has multiple profiles
			sub_identities.clear();
			sub_identities.add(next);
			long contact_aggregation_id = next.androidAggregatedContactId_;
			for(;;) {
				if(next.updatedAt_ > lastSyncMarker) {
					nextSyncMarker = next.updatedAt_;
				}
				next = it.hasNext() ? it.next() : null;
				if(next == null || next.androidAggregatedContactId_ == null || contact_aggregation_id != next.androidAggregatedContactId_)
					break;
				sub_identities.add(next);
			}
			
			assert(sub_identities.size() > 0);

			MIdentity id = sub_identities.get(0);
			if(id.updatedAt_ > lastSyncMarker) {
				nextSyncMarker = id.updatedAt_;
			}

			
			//TODO: not here, but this is a reason why some functionality is not here...
			//other actions, like sending a message should be done by tapping
			//the appropriate intent for "text message" or "email"

			boolean existMusubiProfile = false;

			final Uri qUri = RawContacts.CONTENT_URI;
			final String qSelection =  RawContacts.SOURCE_ID + "=? AND " + RawContacts.ACCOUNT_TYPE + "=?";
			final String[] qProjection = new String[] { 
					RawContacts._ID
			};

			final ContentResolver resolver = mContext.getContentResolver();
			Cursor c =
				resolver.query(qUri, qProjection, qSelection,
						new String[] {id.androidAggregatedContactId_.toString(), accountType}
				, null);
			try {
				while (c.moveToNext()) {
					existMusubiProfile = true;
					rawId = c.getLong(0);
				}
			} finally {
				c.close();
			}
			byte[] thumbnail = null;
			boolean any_claimed = false;
			for(MIdentity anid : sub_identities) {
				//copy one profile picture in per raw contact
				mIdm.getMusubiThumbnail(anid);
				if(anid.musubiThumbnail_ != null)
					thumbnail = anid.musubiThumbnail_;
				//TODO: one day if we don't hear from someone,
				//maybe they will become unclaimed... but right now that never happens
				if(anid.claimed_) {
					any_claimed = true;
					try_status_updates.add(anid);
				}
			}
			//if none of the sub identities are claimed, then we aren't going to
			//put this in the address book
			//TODO: deletes? unclaims
			if(!any_claimed)
				continue;

			if(!existMusubiProfile) {
				// insert new contact
				if(DEBUG) Log.i(TAG, "insert contact->" + id.principal_ + " name->" + id.name_ + " hashedPrincipal->"+id.principalHash_);
				final ContactOperations contactOp = 
					ContactOperations.createNewContact(mContext, id.androidAggregatedContactId_, accountName, true, batchOp);
				
				//we want to be aggregated, so specify the same text name
				if(id.androidAggregatedContactId_ != null && id.name_ != null) {
					contactOp.addName(id.name_);
				} else {
					contactOp.addName(UiUtil.safeNameForIdentity(id));
				}


				for(MIdentity anid : sub_identities) {
					//TODO: one day if we don't hear from someone,
					//maybe they will become unclaimed... but right now that never happens
					if(anid.claimed_) {
						contactOp.addProfileAction(anid);
						contactOp.addGroupMembership(groupIds[CLAIMED_GROUP_ID]);
					}
				}
				//TODO: what if it was deleted? maybe not in this path...
				if(thumbnail != null)
					contactOp.addPhoto(id.musubiThumbnail_);
			} else {
				if(DEBUG) Log.i(TAG, "update contact->" + id.principal_ + " name->" + id.name_ + " hashedPrincipal->"+id.principalHash_);
				for(MIdentity anid : sub_identities) {
					//copy one profile picture in per raw contact
					mIdm.getMusubiThumbnail(anid);
					if(anid.musubiThumbnail_ != null)
						thumbnail = anid.musubiThumbnail_;
					//TODO: one day if we don't hear from someone,
					//maybe they will become unclaimed... but right now that never happens
					if(anid.claimed_) {
						try_status_updates.add(anid);
					}
				}

				final ContactOperations contactOp = ContactOperations.updateExistingContact(mContext, rawId, true, batchOp);
				c = mContext.getContentResolver().query(Data.CONTENT_URI, 
					new String[]{
						Data._ID, 
						MusubiProfile.DATA_PID,
						StructuredName.DISPLAY_NAME, 
						Data.MIMETYPE,
					}, 
					Data.RAW_CONTACT_ID + "=?", 
					new String[]{String.valueOf(rawId)}, null
				);
				try {
					while(c.moveToNext()) {
						final long dataId = c.getLong(0);
						String mime = c.getString(3);
						final Uri uri = ContentUris.withAppendedId(Data.CONTENT_URI, dataId);
						if(mime.equals(Photo.CONTENT_ITEM_TYPE)) {
							if(DEBUG) Log.i(TAG, "update thumbnail");
							//TODO: what if it was deleted?
							if(thumbnail != null)
								contactOp.updatePhoto(thumbnail, uri);
						} else if(mime.equals(StructuredName.CONTENT_ITEM_TYPE)) {
							//we just use the first name since the high level android contact
							//merging algorithm is going to use that.
							final String name = c.getString(2);
							contactOp.updateName(id.name_, name, uri);
						} else if(mime.equals(MusubiProfile.MIME_PROFILE)) {
							long profile_id = c.getLong(1);
							for(Iterator<MIdentity> jt = sub_identities.iterator(); jt.hasNext();) {
								MIdentity possible = jt.next();
								if(possible.id_ == profile_id) {
									jt.remove();
									contactOp.updateProfile(possible, uri);
									break;
								}
							}
						}
					}
					
					for(MIdentity remaining : sub_identities) {
						//add new profile for people who became claimed
						if(remaining.claimed_) {
							contactOp.addProfileAction(remaining);
						}
					}
				} finally {
					c.close();
				}
			}
			
			if(batchOp.size() >= 50) {
				ContentProviderResult[] r = batchOp.execute();
				System.arraycopy(r, 0, results, last, r.length);
				last += r.length;
			} 
		}
		if(batchOp.size()>0) {
			ContentProviderResult[] r = batchOp.execute();
			System.arraycopy(r, 0, results, last, r.length);
		}
		
		final BatchOperation statusOp = new BatchOperation(mContext, mContext.getContentResolver());
		for(@SuppressWarnings("unused") MIdentity id : try_status_updates) {
			//TODO: actually grab a status message... when we have something appropriate.
			//is it a MOTD? away message? etc? latest feed item?
			//setStatus(id.id_, id.principal_, accountName, statusOp);
		}
		statusOp.execute();
		
		Log.i(TAG, "last sync at " + String.valueOf(lastSyncMarker));
		Log.i(TAG, "next sync will be after " + String.valueOf(nextSyncMarker));
		setSyncMarker(mContext, nextSyncMarker);
		
		return results;
	}
	
	public List<MIdentity> getUpdatedContacts(long lastSyncMarker) {
		List<MIdentity> updatedIds = new ArrayList<MIdentity>();
		MIdentity[] ids = null;
		try {
			ids = mIdm.getUpdatedIdentities(lastSyncMarker);
		} catch (SQLiteException e) {
			Log.e(TAG, e.toString());
			return updatedIds;
		}
		for(MIdentity id : ids) {
			if(id.updatedAt_ > lastSyncMarker) {
				if(DEBUG) Log.i(TAG, "Updated contact: email->"+id.principal_ + " name->" + id.name_ + 
						" musubiname->" + id.musubiName_ + " hashedPrincipal->" + id.principalHash_.toString());
				updatedIds.add(id);
			}
		}
		
		return updatedIds;
	}
	
	public static long getSyncMarker(Context context) {
		SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, 0);
		return settings.getLong(LAST_SYNC_MARKER, 0);
	}
	
	public static void setSyncMarker(Context context, long marker) {
		//TODO: This needs to move to the db, as all state needs to be there for testing isolation purposes
		SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, 0);
		SharedPreferences.Editor editor = settings.edit();
		editor.putLong(LAST_SYNC_MARKER, marker);
		editor.commit();
	}
	
	@SuppressWarnings("unused")
	private void setStatus(long id, String handle, String username, BatchOperation batchOp) {
		final ContentValues values = new ContentValues();
		final long profileId = lookupProfile(id);
    	if(handle != null && handle.length() > 0) {
    		values.put(StatusUpdates.DATA_ID, profileId);
    		values.put(StatusUpdates.STATUS, "status");
    		values.put(StatusUpdates.PROTOCOL, Im.PROTOCOL_CUSTOM);
    		values.put(StatusUpdates.CUSTOM_PROTOCOL, CUSTOM_PROTOCOL);
    		values.put(StatusUpdates.IM_ACCOUNT, username);
    		values.put(StatusUpdates.IM_HANDLE, handle);
    		values.put(StatusUpdates.STATUS_RES_PACKAGE, mContext.getPackageName());
    		values.put(StatusUpdates.STATUS_ICON, R.drawable.icon);
    		values.put(StatusUpdates.STATUS_LABEL, R.string.app_name);
    		batchOp.add(ContactOperations.newInsertCpo(StatusUpdates.CONTENT_URI,
                    false, true).withValues(values).build());
    	}
	}
	
	 private long lookupProfile(long userId) {

	        long profileId = 0;
	        final Cursor c =
	            mContext.getContentResolver().query(Data.CONTENT_URI,
	            		new String[]{Data._ID},
	            		Data.MIMETYPE + "='" + MusubiProfile.MIME_PROFILE + "' AND "
	            		+ MusubiProfile.DATA_PID + "=?",
	                new String[] {String.valueOf(userId)}, null);
	        try {
	            if ((c != null) && c.moveToFirst()) {
	                profileId = c.getLong(0);
	            }
	        } finally {
	            if (c != null) {
	                c.close();
	            }
	        }
	        return profileId;
	    }
        
	public long[] createGroups() {
		   // Lookup the sample group
        long[] groupIds = new long[2];
        int groupNum = 0;
        final Cursor cursor = mContext.getContentResolver().query(Groups.CONTENT_URI, new String[] { Groups.TITLE, Groups._ID },
                Groups.ACCOUNT_NAME + "=? AND " + Groups.ACCOUNT_TYPE + "=?",
                new String[] { accountName, accountType}, null);
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                	final String groupTitle = cursor.getString(0);
                	if(groupTitle.equals(GROUP_TITLES[CLAIMED_GROUP_ID])) {
                		groupIds[CLAIMED_GROUP_ID] = cursor.getLong(1);
                		++groupNum;
                	} else if(groupTitle.equals(GROUP_TITLES[WHITELIST_GROUP_ID])) {
                		groupIds[WHITELIST_GROUP_ID] = cursor.getLong(1);
                		++groupNum;
                	}

                }
            } finally {
                cursor.close();
            }
        }

        if (groupNum < GROUP_NUM) {
            // group doesn't exist yet, so create it
            final ContentValues contentValues = new ContentValues();
        	for(int i = 0; i < GROUP_NUM; i++) {
        		 contentValues.put(Groups.ACCOUNT_NAME, accountName);
                 contentValues.put(Groups.ACCOUNT_TYPE, accountType);
                 contentValues.put(Groups.TITLE, GROUP_TITLES[i]);
                 if(i == CLAIMED_GROUP_ID || i == WHITELIST_GROUP_ID)
                	 contentValues.put(Groups.GROUP_VISIBLE, 1);

                 final Uri newGroupUri = mContext.getContentResolver().insert(Groups.CONTENT_URI, contentValues);
                 groupIds[i] = ContentUris.parseId(newGroupUri);
        	}
        }
        return groupIds;
	}
	
    
}