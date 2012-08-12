/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package mobisocial.musubi.syncadapter;

import mobisocial.musubi.R;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.ui.util.UiUtil;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;

/**
 * Helper class for storing data in the platform content providers.
 */
public class ContactOperations {
	
    private final ContentValues mValues;
    private final BatchOperation mBatchOperation;
    private final Context mContext;
    private boolean mIsSyncOperation;
    private long mRawContactId;
    private int mBackReference;
    private boolean mIsNewContact;
    private String mAccountType;

    /**
     * Since we're sending a lot of contact provider operations in a single
     * batched operation, we want to make sure that we "yield" periodically
     * so that the Contact Provider can write changes to the DB, and can
     * open a new transaction.  This prevents ANR (application not responding)
     * errors.  The recommended time to specify that a yield is permitted is
     * with the first operation on a particular contact.  So if we're updating
     * multiple fields for a single contact, we make sure that we call
     * withYieldAllowed(true) on the first field that we update. We use
     * mIsYieldAllowed to keep track of what value we should pass to
     * withYieldAllowed().
     */
    private boolean mIsYieldAllowed;

    /**
     * Returns an instance of ContactOperations instance for adding new contact
     * to the platform contacts provider.
     *
     * @param context the Authenticator Activity context
     * @param userId the userId of the sample SyncAdapter user object
     * @param accountName the username for the SyncAdapter account
     * @param isSyncOperation are we executing this as part of a sync operation?
     * @return instance of ContactOperations
     */
    public static ContactOperations createNewContact(Context context, long userId,
            String accountName, boolean isSyncOperation, BatchOperation batchOperation) {
        return new ContactOperations(context, userId, accountName, isSyncOperation, batchOperation);
    }

    /**
     * Returns an instance of ContactOperations for updating existing contact in
     * the platform contacts provider.
     *
     * @param context the Authenticator Activity context
     * @param rawContactId the unique Id of the existing rawContact
     * @param isSyncOperation are we executing this as part of a sync operation?
     * @return instance of ContactOperations
     */
    public static ContactOperations updateExistingContact(Context context, long rawContactId,
            boolean isSyncOperation, BatchOperation batchOperation) {
        return new ContactOperations(context, rawContactId, isSyncOperation, batchOperation);
    }

    public ContactOperations(Context context, boolean isSyncOperation,
            BatchOperation batchOperation) {
        mValues = new ContentValues();
        mIsYieldAllowed = true;
        mIsSyncOperation = isSyncOperation;
        mContext = context;
        mBatchOperation = batchOperation;
        mAccountType = mContext.getString(R.string.account_type);
    }

    public ContactOperations(Context context, long userId, String accountName,
            boolean isSyncOperation, BatchOperation batchOperation) {
        this(context, isSyncOperation, batchOperation);
        mBackReference = mBatchOperation.size();
        mIsNewContact = true;
        mAccountType = mContext.getString(R.string.account_type);
        mValues.put(RawContacts.SOURCE_ID, userId);
        mValues.put(RawContacts.ACCOUNT_TYPE, mAccountType);
        mValues.put(RawContacts.ACCOUNT_NAME, accountName);
        ContentProviderOperation.Builder builder =
                newInsertCpo(RawContacts.CONTENT_URI, mIsSyncOperation, true).withValues(mValues);
        mBatchOperation.add(builder.build());
    }

    public ContactOperations(Context context, long rawContactId, boolean isSyncOperation,
            BatchOperation batchOperation) {
        this(context, isSyncOperation, batchOperation);
        mIsNewContact = false;
        mRawContactId = rawContactId;
        mAccountType = mContext.getString(R.string.account_type);
    }

    /**
     * Adds a profile action
     *
     * @param id.i the userId of the sample SyncAdapter user object
     * @return instance of ContactOperations
     */
    public ContactOperations addProfileAction(MIdentity id) {
        mValues.clear();
        if (id != null) {
            mValues.put(MusubiProfile.DATA_PID, id.id_);
            mValues.put(MusubiProfile.DATA_SUMMARY, "Musubi");
            mValues.put(MusubiProfile.DATA_DETAIL, UiUtil.safePrincipalForIdentity(id));
            mValues.put(MusubiProfile.DATA_TYPE, id.type_.ordinal());
            mValues.put(MusubiProfile.DATA_PRINCIPAL, id.principal_);
            mValues.put(MusubiProfile.DATA_IDENTITY, id.principalHash_);
            mValues.put(Data.MIMETYPE, MusubiProfile.MIME_PROFILE);
            addInsertOp();
        }
        return this;
    }
    
    public ContactOperations addName(String name) {
    	 mValues.clear();
         if (name != null && name.length()>0) {
             mValues.put(StructuredName.DISPLAY_NAME, name);
             mValues.put(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
             addInsertOp();
         }
         return this;
    }
    
    public ContactOperations addPhoto(byte[] photo) {
    	mValues.clear();
    	if(photo != null && photo.length > 0) {
    		mValues.put(Photo.PHOTO, photo);
    		mValues.put(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE);
    		addInsertOp();
    	}
    	return this;
    }
    
    public ContactOperations addGroupMembership(long groupId) {
        mValues.clear();
        mValues.put(GroupMembership.GROUP_ROW_ID, groupId);
        mValues.put(GroupMembership.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE);
        addInsertOp();
        return this;
    }
    
    public ContactOperations updateGroupMembership(long groupId, long existingGroupId, Uri uri) {
    	if(groupId != existingGroupId) {
    		mValues.clear();
    		mValues.put(GroupMembership.GROUP_ROW_ID, groupId);
    		addUpdateOp(uri);
    	}
    	return this;
    }
    
    public ContactOperations updatePhoto(byte[] photo, Uri uri) {
    	mValues.clear();
    	if(photo != null) {
    		mValues.put(Photo.PHOTO, photo);
    		addUpdateOp(uri);
    	}
    	return this;
    }
    
    public ContactOperations updateName(String name, String existingName, Uri uri) {
    	mValues.clear();
    	if(!TextUtils.equals(name, existingName)) {
    		mValues.put(StructuredName.DISPLAY_NAME, name);
    		addUpdateOp(uri);
    	}
    	return this;
    }
    
    public ContactOperations updateProfile(MIdentity id, Uri uri) {
		mValues.clear();
		mValues.put(MusubiProfile.DATA_PID, id.id_);
        mValues.put(MusubiProfile.DATA_DETAIL, UiUtil.safePrincipalForIdentity(id));
        mValues.put(MusubiProfile.DATA_PRINCIPAL, id.principal_);
        mValues.put(Data.MIMETYPE, MusubiProfile.MIME_PROFILE);
		addUpdateOp(uri);
    	return this;
    }
    
    /**
     * Adds an insert operation into the batch
     */
    private void addInsertOp() {

        if (!mIsNewContact) {
            mValues.put(Phone.RAW_CONTACT_ID, mRawContactId);
        }
        ContentProviderOperation.Builder builder =
                newInsertCpo(Data.CONTENT_URI, mIsSyncOperation, mIsYieldAllowed);
        builder.withValues(mValues);
        if (mIsNewContact) {
            builder.withValueBackReference(Data.RAW_CONTACT_ID, mBackReference);
        }
        mIsYieldAllowed = false;
        mBatchOperation.add(builder.build());
    }

    /**
     * Adds an update operation into the batch
     */
    private void addUpdateOp(Uri uri) {
        ContentProviderOperation.Builder builder =
                newUpdateCpo(uri, mIsSyncOperation, mIsYieldAllowed).withValues(mValues);
        mIsYieldAllowed = false;
        mBatchOperation.add(builder.build());
    }

    public static ContentProviderOperation.Builder newInsertCpo(Uri uri,
            boolean isSyncOperation, boolean isYieldAllowed) {
        return ContentProviderOperation
                .newInsert(addCallerIsSyncAdapterParameter(uri, isSyncOperation))
                .withYieldAllowed(isYieldAllowed);
    }

    public static ContentProviderOperation.Builder newUpdateCpo(Uri uri,
            boolean isSyncOperation, boolean isYieldAllowed) {
        return ContentProviderOperation
                .newUpdate(addCallerIsSyncAdapterParameter(uri, isSyncOperation))
                .withYieldAllowed(isYieldAllowed);
    }

    public static ContentProviderOperation.Builder newDeleteCpo(Uri uri,
            boolean isSyncOperation, boolean isYieldAllowed) {
        return ContentProviderOperation
                .newDelete(addCallerIsSyncAdapterParameter(uri, isSyncOperation))
                .withYieldAllowed(isYieldAllowed);
    }

    private static Uri addCallerIsSyncAdapterParameter(Uri uri, boolean isSyncOperation) {
        if (isSyncOperation) {
            // If we're in the middle of a real sync-adapter operation, then go ahead
            // and tell the Contacts provider that we're the sync adapter.  That
            // gives us some special permissions - like the ability to really
            // delete a contact, and the ability to clear the dirty flag.
            //
            // If we're not in the middle of a sync operation (for example, we just
            // locally created/edited a new contact), then we don't want to use
            // the special permissions, and the system will automagically mark
            // the contact as 'dirty' for us!
            return uri.buildUpon()
                    .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
                    .build();
        }
        return uri;
    }
}
