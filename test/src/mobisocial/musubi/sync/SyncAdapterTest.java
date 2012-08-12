package mobisocial.musubi.sync;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;

import mobisocial.crypto.IBIdentity;
import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.helpers.DatabaseFile;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.syncadapter.MusubiProfile;
import mobisocial.musubi.syncadapter.SyncAdapter;
import mobisocial.musubi.util.Util;
import mobisocial.test.TestBase;

public class SyncAdapterTest extends TestBase {
	private DelegatedMockContext mProviderContext;
	public static final String TAG = "SyncAdapterTest";
	public static final String ACCOUNT_NAME = "Me";
	public static final String ACCOUNT_TYPE = "edu.stanford.mobisocial";
	
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		mProviderContext = new DelegatedMockContext(getContext());
		this.setContext(mProviderContext);
		
		final Account account = new Account(ACCOUNT_NAME, ACCOUNT_TYPE);
		AccountManager am = AccountManager.get(getContext());
		Account[] acs = am.getAccountsByType(ACCOUNT_TYPE);
		if(acs.length == 0) {
			am.addAccountExplicitly(account, "", null);

			ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, true);
			ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 1);
		}
	}
	
	@Override
	protected void tearDown() throws Exception {
//		final Uri uri = RawContacts.CONTENT_URI.buildUpon()
//			.appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build();
//		final String selection = RawContacts.ACCOUNT_TYPE + "='edu.stanford.mobisocial'";
//		
//		final ContentResolver resolver = getContext().getContentResolver();
//        final int c = resolver.delete(uri, selection, null);
//        Log.i(TAG, "deleted " + String.valueOf(c) + " contacts");
	}

	public void testGetUpdates() throws InterruptedException {
		SyncAdapter syncAdapter = new SyncAdapter(getContext(), true,
				ACCOUNT_TYPE, ACCOUNT_NAME);
		long lastUpdatedTime = new Date().getTime()/1000;
		Thread.sleep(1000);
		insertTestIdentity();
		List<MIdentity> updated = syncAdapter.getUpdatedContacts(lastUpdatedTime);
		assertEquals(1, updated.size());
		
		updated = syncAdapter.getUpdatedContacts(Long.MAX_VALUE);
		assertEquals(0, updated.size());
	}
	
	public void testSyncUpdates() throws InterruptedException {
		SyncAdapter syncAdapter = new SyncAdapter(getContext(), true,
				ACCOUNT_TYPE, ACCOUNT_NAME);
		final long[] groupIds = syncAdapter.createGroups();
		
		// test insert
		long lastUpdatedTime = new Date().getTime()/1000;
		Thread.sleep(1000);
		MIdentity insertedId = insertTestIdentity();
		List<MIdentity> updated = syncAdapter.getUpdatedContacts(lastUpdatedTime);
		ContentProviderResult[] results = syncAdapter.syncUpdatedContacts(updated, groupIds, lastUpdatedTime);
		
		MIdentity retrievedId = getRawContact(ContentUris.parseId(results[0].uri), groupIds);
		assertTrue(retrievedId != null);
//		assertEquals(retrievedId.principal_, insertedId.principal_);
		assertEquals(retrievedId.name_, insertedId.name_);
		assertTrue(retrievedId.thumbnail_ != null);
		assertTrue(retrievedId.androidAggregatedContactId_ != null);
		assertEquals(retrievedId.blocked_, false);
		assertEquals(retrievedId.claimed_, true);
		for(int i = 0; i < retrievedId.principalHash_.length; i++) {
			assertEquals(retrievedId.principalHash_[i], insertedId.principalHash_[i]);
		}
		
		// test update
//		List<MIdentity> ids = new ArrayList<MIdentity>();
//		retrievedId.musubiThumbnail_ = "";
//		ids.add(retrievedId);
//		syncAdapter.syncUpdatedContacts(ids, groupIds);
//		
//		MIdentity updatedId = getRawContact(retrievedId);
//		assertTrue(updatedId != null);
//		assertEquals(retrievedId.principal_, updatedId.principal_);
//		assertEquals(retrievedId.name_, updatedId.name_);
//		assertTrue(updatedId.androidDataId_ != null);
		
//		// test delete
//		retrievedId.blocked_ = true;
//		syncAdapter.syncUpdatedContacts(ids);
//		MIdentity deletedId = getRawContact(retrievedId);
//		assertTrue(deletedId == null);
	}
	
	public void testInsertGroups() {
		SyncAdapter syncAdapter = new SyncAdapter(getContext(), true, ACCOUNT_TYPE, ACCOUNT_NAME);
		
		long[] groupIds = syncAdapter.createGroups();
		assertTrue(groupIds[0] != 0 && groupIds[1] != 0);
		
		long[] newGroupIds = syncAdapter.createGroups();
		assertEquals(groupIds[0], newGroupIds[0]);
		assertEquals(groupIds[1], newGroupIds[1]);
	}
	
	private MIdentity insertTestIdentity() {
		DatabaseFile dbh = new DatabaseFile(getContext());
		IdentitiesManager idm = new IdentitiesManager(dbh);

		IBIdentity ibid0 = randomIBIdentity();
		MIdentity id0 = new MIdentity();
		id0.name_ = ibid0.principal_.substring(0, ibid0.principal_.length()-10);
		id0.type_ = ibid0.authority_;
		id0.principal_ = ibid0.principal_;
		id0.principalHash_ = ibid0.hashed_;
		id0.principalShortHash_ = Util.shortHash(id0.principalHash_);
		id0.owned_ = true;
		id0.claimed_ = true;
		id0.blocked_ = false;
		idm.insertIdentity(id0);
		return id0;
	}
	
	private MIdentity getRawContact(long rawId, long[] groupIds) {
    	final Uri qUri = Data.CONTENT_URI;
    	final String qSelection = Data.RAW_CONTACT_ID + "=?";
    	final String[] qProjection = new String[] {MusubiProfile.DATA_PID, 
    			Photo.PHOTO,
    			StructuredName.DISPLAY_NAME,
    			Data.RAW_CONTACT_ID,
    			Data.MIMETYPE,
    			GroupMembership.GROUP_ROW_ID,
    			CommonDataKinds.Identity.IDENTITY
    			};
    	
    	final int COLUMN_ID = 0;
    	final int COLUMN_PHOTO = 1;
    	final int COLUMN_NAME = 2;
    	final int COLUMN_RAW_ID = 3;
    	final int COLUMN_MIME = 4;
    	final int COLUMN_GROUP_ID = 5;
    	final int COLUMN_PRINCIPAL_HASH = 6;
    	
    	long id = -1;
    	long groupId = -1;
        byte[] photo = null;
        byte[] principalHash = null;
        String name = null;
        boolean isFound = false;

        final ContentResolver resolver = getContext().getContentResolver();
        final Cursor c =
            resolver.query(qUri, qProjection, qSelection,
                new String[] {String.valueOf(rawId)}
            , null);
        try {
            while (c.moveToNext()) {
            	String mime = c.getString(COLUMN_MIME);
            	if(mime.equals(MusubiProfile.MIME_PROFILE)) {
            		isFound = true;
            		id = c.getLong(COLUMN_ID);
            	} else if(mime.equals(Photo.CONTENT_ITEM_TYPE)) {
            		photo = new byte[1];
            	} else if(mime.equals(StructuredName.CONTENT_ITEM_TYPE)) {
            		name = c.getString(COLUMN_NAME);
            	} else if(mime.equals(GroupMembership.CONTENT_ITEM_TYPE)) {
            		groupId = c.getLong(COLUMN_GROUP_ID);
            	} else if(mime.equals(CommonDataKinds.Identity.CONTENT_ITEM_TYPE)) {
            		principalHash = c.getBlob(COLUMN_PRINCIPAL_HASH);
            	}
            } // while
        } finally {
            c.close();
        }
        
        MIdentity mId = null;
        if(isFound) {
        	IBIdentity ibd = new IBIdentity(Authority.Email, "noemail", 0);
			mId = new MIdentity();
			mId.id_ = id;
			mId.androidAggregatedContactId_ = rawId;
			mId.name_ = name;
			mId.type_ = ibd.authority_;
			mId.principal_ = ibd.principal_;
			mId.principalHash_ = principalHash;
			mId.principalShortHash_ = ByteBuffer.wrap(ibd.hashed_).getLong();
			mId.thumbnail_ = photo;
			if(groupId == groupIds[SyncAdapter.CLAIMED_GROUP_ID]) {
				mId.claimed_ = true;
				mId.blocked_ = false;
			} else if(groupId == groupIds[SyncAdapter.WHITELIST_GROUP_ID]) {
				mId.claimed_ = false;
				mId.blocked_ = false;
			}
        }

        return mId;
    }
}
