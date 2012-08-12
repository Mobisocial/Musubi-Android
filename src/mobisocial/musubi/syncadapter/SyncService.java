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

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * Service to handle Account sync. It creates our musubi account in the account manager if 
 * necessary.  The sync adapter provides its own binder service, this one is really just
 * a stub for us to launch in our own app.
 */
public class SyncService extends Service {
	public static final String TAG = SyncService.class.getName();
    private SyncAdapter mSyncAdapter;
    //we instantiate this so that it will automatically register our account
	@SuppressWarnings("unused")
	private AccountAuthenticator mAccountAuthenticator;

    @Override
    public void onCreate() {
    	mAccountAuthenticator = new AccountAuthenticator(this);
		//this is the good stuff, makes our adapter
    	mSyncAdapter = new SyncAdapter(this, true);
    }

    @Override
    public IBinder onBind(Intent intent) {
    	//the service exposes the sync interface not the typical service
    	//binder that has no function in our other services.
        return mSyncAdapter.getSyncAdapterBinder();
    }
}
