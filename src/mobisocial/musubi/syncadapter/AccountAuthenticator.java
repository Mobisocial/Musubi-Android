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

import mobisocial.musubi.R;
import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;

public class AccountAuthenticator extends AbstractAccountAuthenticator {
	private static final String TAG = "AccountAuthenticator";
    private final Context mContext;
    private String mAccountName;
    private String mAccountType;
    private AccountManager mAccountManager;

	public AccountAuthenticator(Context context) {
		super(context);
		mContext = context;
		
		mAccountManager = AccountManager.get(mContext);
		mAccountName = mContext.getString(R.string.account_name);
		mAccountType = mContext.getString(R.string.account_type);
		
		//TODO: this accesses the DB/FS so should be done in a background thread.
		internalAddAccount();
	}

	@Override
	public Bundle addAccount(AccountAuthenticatorResponse response,
			String accountType, String authTokenType,
			String[] requiredFeatures, Bundle options)
			throws NetworkErrorException {
		if(!accountType.equals(mAccountType)) {
			Log.e(TAG, "trying to add an account type other than our own " + accountType);
			return null;
			
		}
		Log.v(TAG, "add account for " + mAccountName + " with type " + accountType);
		internalAddAccount();

		Bundle result = new Bundle();
		result.putString(AccountManager.KEY_ACCOUNT_NAME, mAccountName);
		result.putString(AccountManager.KEY_ACCOUNT_TYPE, accountType);
		response.onResult(result);
		
		//TODO: wtf??
		return null;
	}
	void internalAddAccount() {
    	//TODO: this does DB-access, so it needs to actually happen in a background thread.
		final Account account = new Account(mAccountName, mAccountType);
		Account[] acs = mAccountManager.getAccountsByType(mAccountType);
		if(acs.length == 0) {
			Log.v(TAG, "actually adding account for " + mAccountName + " with type " + mAccountType);
			SyncAdapter.setSyncMarker(mContext, 0);
			mAccountManager.addAccountExplicitly(account, "", null);

			ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, true);
			ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 1);
			
			//also force it to sync once
			ContentResolver.requestSync(account, ContactsContract.AUTHORITY, new Bundle());
		}
	}

	@Override
	public Bundle confirmCredentials(AccountAuthenticatorResponse response,
			Account account, Bundle options) throws NetworkErrorException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Bundle editProperties(AccountAuthenticatorResponse response,
			String accountType) {
		Log.v(TAG, "editProperties()");
        throw new UnsupportedOperationException();
	}

	@Override
	public Bundle getAuthToken(AccountAuthenticatorResponse response,
			Account account, String authTokenType, Bundle options)
			throws NetworkErrorException {
		return null;
	}

	@Override
	public String getAuthTokenLabel(String authTokenType) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Bundle hasFeatures(AccountAuthenticatorResponse response,
			Account account, String[] features) throws NetworkErrorException {
		Log.v(TAG, "hasFeatures()");
		final Bundle result = new Bundle();
		result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false);
		return result;
	}

	@Override
	public Bundle updateCredentials(AccountAuthenticatorResponse response,
			Account account, String authTokenType, Bundle options)
			throws NetworkErrorException {
		// TODO Auto-generated method stub
		Log.v(TAG, "updateCredentials()");
		return null;
	}

}
