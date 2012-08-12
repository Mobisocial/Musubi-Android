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
import android.util.Log;

public class AuthenticatorService extends Service {

	 private static final String TAG = "AuthenticationService";

	    private AccountAuthenticator mAuthenticator;

	    @Override
	    public void onCreate() {
	        if (Log.isLoggable(TAG, Log.VERBOSE)) {
	            Log.v(TAG, "Authentication Service started.");
	        }
	        mAuthenticator = new AccountAuthenticator(this);
	    }

	    @Override
	    public void onDestroy() {
	        if (Log.isLoggable(TAG, Log.VERBOSE)) {
	            Log.v(TAG, "Authentication Service stopped.");
	        }
	    }

	    @Override
	    public IBinder onBind(Intent intent) {
	        if (Log.isLoggable(TAG, Log.VERBOSE)) {
	            Log.v(TAG, "getBinder()...  returning the AccountAuthenticator binder for intent "
	                    + intent);
	        }
	        return mAuthenticator.getIBinder();
	    }

}
