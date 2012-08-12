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

import java.util.ArrayList;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.Context;
import android.content.OperationApplicationException;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.util.Log;

/**
 * This class handles execution of batch mOperations on Contacts provider.
 */
final public class BatchOperation {

    private final String TAG = "BatchOperation";

    private final ContentResolver mResolver;

    // List for storing the batch mOperations
    private final ArrayList<ContentProviderOperation> mOperations;

    public BatchOperation(Context context, ContentResolver resolver) {
        mResolver = resolver;
        mOperations = new ArrayList<ContentProviderOperation>();
    }

    public int size() {
        return mOperations.size();
    }

    public void add(ContentProviderOperation cpo) {
        mOperations.add(cpo);
    }

    public ContentProviderResult[] execute() {
    	ContentProviderResult[] results = null;
        if (mOperations.size() == 0) {
            return results;
        }
        // Apply the mOperations to the content provider
        try {
            results = mResolver.applyBatch(ContactsContract.AUTHORITY,
                    mOperations);
            
        } catch (final OperationApplicationException e1) {
            Log.e(TAG, "storing contact data failed", e1);
        } catch (final RemoteException e2) {
            Log.e(TAG, "storing contact data failed", e2);
        }
        mOperations.clear();
        return results;
    }
}
