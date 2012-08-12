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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import mobisocial.crypto.IBEncryptionScheme;
import mobisocial.crypto.IBHashedIdentity;
import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.crypto.IBIdentity;
import mobisocial.crypto.IBSignatureScheme;
import mobisocial.musubi.encoding.NeedsKey;
import mobisocial.musubi.identity.IdentityProvider;
import mobisocial.musubi.identity.IdentityProviderException;
import mobisocial.musubi.model.MEncryptionUserKey;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MPendingIdentity;
import mobisocial.musubi.model.MSignatureUserKey;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.model.helpers.PendingIdentityManager;
import mobisocial.musubi.model.helpers.UserKeyManager;
import android.content.Context;
import android.database.ContentObserver;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

public class KeyUpdateHandler extends ContentObserver {
	public static final String TAG = "KeyUpdateHandler";
	
    private final Context mContext;
    private final SQLiteOpenHelper mHelper;
    private final IdentitiesManager mIdentitiesManager;
    private final IdentityProvider mIdentityProvider;
    private final UserKeyManager mUserKeyManager;
    private final PendingIdentityManager mPendingIdentityManager;

    private final HashSet<IBHashedIdentity> mRequestedClaimKeys;
    private final HashSet<IBHashedIdentity> mRequestedEncryptionKeys;
    private final HashSet<IBHashedIdentity> mRequestedSignatureKeys;
	HandlerThread mThread;
	
	/*
	 * Support exponential backoff for retries. Only retry if there is a failure
	 * not related to authentication.
	 * 
	 * Backoff ranges from 10 seconds to 30 minutes, and doubles on each successive
	 * failure until the maximum wait is reached.
	 */
	private static final long MINIMUM_BACKOFF = 10 * 1000;
	private static final long MAXIMUM_BACKOFF = 30 * 60 * 1000;
    private final HashMap<IBHashedIdentity, Long> mSigBackoff;
	private final HashMap<IBHashedIdentity, Long> mEncBackoff;
	private final HashMap<IBHashedIdentity, Long> mClaimBackoff;
    
	public static KeyUpdateHandler newInstance(Context context, SQLiteOpenHelper dbh, IdentityProvider identityProvider) {
        HandlerThread thread = new HandlerThread("KeyUpdateThread");
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
        return new KeyUpdateHandler(context, dbh, thread, identityProvider);
    }

    private KeyUpdateHandler(Context context, SQLiteOpenHelper dbh, HandlerThread thread, IdentityProvider identityProvider) {
        super(new Handler(thread.getLooper()));
        mThread = thread;
        mContext = context;
        mHelper = dbh;
        mIdentitiesManager = new IdentitiesManager(mHelper);
        mIdentityProvider = identityProvider;
        mUserKeyManager = new UserKeyManager(mIdentityProvider.getEncryptionScheme(),
				mIdentityProvider.getSignatureScheme(), mHelper);
        mPendingIdentityManager = new PendingIdentityManager(mHelper);
        mRequestedClaimKeys = new HashSet<IBHashedIdentity>();
        mRequestedEncryptionKeys = new HashSet<IBHashedIdentity>();
        mRequestedSignatureKeys = new HashSet<IBHashedIdentity>();
        mSigBackoff = new HashMap<IBHashedIdentity, Long>();
        mEncBackoff = new HashMap<IBHashedIdentity, Long>();
        mClaimBackoff = new HashMap<IBHashedIdentity, Long>();
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
    		synchronized(mSigBackoff) {
    			mSigBackoff.clear();
        	}
            synchronized (mEncBackoff) {
                mEncBackoff.clear();
            }
            synchronized (mClaimBackoff) {
                mClaimBackoff.clear();
            }
        	KeyUpdateHandler.this.dispatchChange(false);
    	}
	}


	@Override
    public void onChange(boolean selfChange) {
    	Log.i(TAG, "KeyUpdateHandler onChange reached");
    	
    	// Determine which identities the user currently owns
    	List<MIdentity> ids = mIdentitiesManager.getOwnedIdentities();
    	Set<IBIdentity> idsToUpdate = new HashSet<IBIdentity>();
    	
    	// Convert MIdentity objects to IBIdentity objects
    	// TODO: Might want to use MMyAccount here
    	for (MIdentity id : ids) {
    		IBIdentity ident = IdentitiesManager.toIBIdentity(id,
    				IdentitiesManager.computeTemporalFrameFromPrincipal(id.principal_));
    		if (ident.authority_ == Authority.PhoneNumber) {
    		    ident = new IBIdentity(ident.authority_, ident.principal_, 0L);
    		}
    		idsToUpdate.add(ident);
    		Log.i(TAG, "Identity principal: " + id.principal_);
    	}
    	
    	// Also get identities to notify with the 2-stage process
    	List<MPendingIdentity> pids = mPendingIdentityManager.getUnnotifiedIdentities();
    	for (MPendingIdentity pid : pids) {
    	    MIdentity mid = mIdentitiesManager.getIdentityForId(pid.identityId_);
    	    IBIdentity ident = IdentitiesManager.toIBIdentity(mid, 0L);
    	    idsToUpdate.add(ident);
    	    Log.i(TAG, "Identity to notify: " + mid.principal_);
    	}
    	
    	// Contact the IBE server for new keys and save if valid
    	for (IBIdentity ident : idsToUpdate) {
			MIdentity id = mIdentitiesManager.getIdentityForIBHashedIdentity(ident);
			//local identities can't be refreshed.
			if(id.type_ == Authority.Local)
				continue;
			assert(id != null);
			boolean hasBoth = true;
    		try {
    			mUserKeyManager.getEncryptionKey(id, ident);
    		} catch (NeedsKey.Encryption e) {
    			requestEncryptionKey(ident);
    			hasBoth = false;
    		}
    		try {
    			mUserKeyManager.getSignatureKey(id, ident);
    		} catch (NeedsKey.Signature e) {
    			requestSignatureKey(ident);
    			hasBoth = false;
    		}
    		
    		// Remove this pending identity if there is no work required
    		if (hasBoth) {
    		    mPendingIdentityManager.deleteIdentity(id.id_, ident.temporalFrame_);
    		}
    	}
    }
	
	public void initiateIdClaim(IBHashedIdentity hid) {
        if(hid.authority_ == Authority.Local) {
            Log.e(TAG, "requesting key for local identity");
            return;
        }
        synchronized (mRequestedClaimKeys) {
            mRequestedClaimKeys.add(hid);
        }
	    new Handler(mThread.getLooper()).post(new Runnable() {
	       @Override
	       public void run() {
	           for(;;) {
                   IBHashedIdentity hid;
                   synchronized (mRequestedClaimKeys) {
                       Iterator<IBHashedIdentity> it = mRequestedClaimKeys.iterator();
                       if(!it.hasNext())
                           break;
                       hid = it.next();
                   }
                   // Only attempt to send a request if the true principal is known
                   IBIdentity ident = mIdentitiesManager.getIBIdentityForIBHashedIdentity(hid);
                   if (ident != null) {
                       MIdentity mid = mIdentitiesManager.getIdentityForIBHashedIdentity(hid);
                       MPendingIdentity pid = mPendingIdentityManager.lookupIdentity(
                               mid.id_, hid.temporalFrame_);
                       if (pid == null) {
                           pid = mPendingIdentityManager.fillPendingIdentity(
                                   mid.id_, hid.temporalFrame_);
                           mPendingIdentityManager.insertIdentity(pid);
                       }
                       if (!pid.notified_) {
                           boolean success = mIdentityProvider.initiateTwoPhaseClaim(
                                   ident, pid.key_, pid.requestId_);
                           // If something went wrong, try again
                           if (!success) {
                               long backoff = updateBackoffForIdentity(hid, mClaimBackoff);
                               Log.i(TAG, "Claim request failed for " + mid.principal_ +
                                       ", retrying in " + backoff + " msec");
                               requestClaimAfterDelay(hid, backoff);
                           }
                           else {
                               synchronized (mClaimBackoff) {
                                   mClaimBackoff.remove(hid);
                               }
                           }
                       }
                       else {
                           Log.w(TAG, "Tried to notify an already-notified identity");
                       }
                   }
                   synchronized (mRequestedClaimKeys) {
                       mRequestedClaimKeys.remove(hid);
                   }
	           }
	       }
	    });
	}

	public void requestSignatureKey(IBHashedIdentity hid) {
		if(hid.authority_ == Authority.Local) {
			Log.e(TAG, "requesting key for local identity");
			return;
		}
		synchronized (mRequestedSignatureKeys) {
			mRequestedSignatureKeys.add(hid);
		}
		new Handler(mThread.getLooper()).post(new Runnable() {
			@Override
			public void run() {
				boolean addedNewKeys = false;
				for(;;) {
					IBHashedIdentity hid;
					synchronized (mRequestedSignatureKeys) {
						Iterator<IBHashedIdentity> it = mRequestedSignatureKeys.iterator();
						if(!it.hasNext())
							break;
						hid = it.next();
					}
					// Retrieve this specific signature key if possible
					MIdentity id = mIdentitiesManager.getIdentityForIBHashedIdentity(hid);
					IBIdentity ibid = new IBIdentity(hid.authority_, id.principal_, hid.temporalFrame_);
	    			try {
						IBSignatureScheme.UserKey sKey = mIdentityProvider.syncGetSignatureKey(ibid);
		    			assert(sKey != null);
		    			Log.d(TAG, "Adding signature user key for " + id.principal_);
		    			MSignatureUserKey key = new MSignatureUserKey();
	    				key.identityId_ = id.id_;
	    				key.when_ = hid.temporalFrame_;
	    				key.userKey_ = sKey.key_;
	    				mUserKeyManager.insertSignatureUserKey(key);
	    				synchronized (mSigBackoff) {
	    					mSigBackoff.remove(hid);
	    				}
	    				addedNewKeys = true;
	    			} catch (IdentityProviderException.NeedsRetry e) {
	    				// Should try to get the key again later
	    				long backoff = updateBackoffForIdentity(hid, mSigBackoff);
	    				Log.i(TAG, "Signature key fetch failed for " + id.principal_ +
	    						", retrying in " + backoff + " msec");
	    				requestSignatureKeyAfterDelay(hid, backoff);
					} catch (IdentityProviderException.TwoPhase e) {
					    Log.i(TAG, "Two-phase verification needed.");
					    initiateIdClaim(hid);
					} catch (IdentityProviderException e) {
	    				Log.i(TAG, "Server unable to obtain key for " + id.principal_);
	    			}
					synchronized (mRequestedSignatureKeys) {
						mRequestedSignatureKeys.remove(hid);
					}
				}
				if (addedNewKeys) {
    				mContext.getContentResolver().notifyChange(MusubiService.PLAIN_OBJ_READY, null);
				}
			}
		});
	}

	public void requestEncryptionKey(IBHashedIdentity hid) {
		if(hid.authority_ == Authority.Local) {
			Log.e(TAG, "requesting key for local identity");
			return;
		}
		synchronized (mRequestedEncryptionKeys) {
			mRequestedEncryptionKeys.add(hid);
		}
		new Handler(mThread.getLooper()).post(new Runnable() {
			@Override
			public void run() {
				boolean addedNewKeys = false;
				for(;;) {
					IBHashedIdentity hid;
					synchronized (mRequestedEncryptionKeys) {
						Iterator<IBHashedIdentity> it = mRequestedEncryptionKeys.iterator();
						if(!it.hasNext())
							break;
						hid = it.next();
					}
					
					// Retrieve this specific encryption key if possible
					MIdentity id = mIdentitiesManager.getIdentityForIBHashedIdentity(hid);
					IBIdentity ibid = new IBIdentity(hid.authority_, id.principal_, hid.temporalFrame_);
	    			try {
						IBEncryptionScheme.UserKey eKey = mIdentityProvider.syncGetEncryptionKey(ibid);
		    			assert(eKey != null);
		    			Log.d(TAG, "Adding encryption user key for " + id.principal_);
		    			MEncryptionUserKey key = new MEncryptionUserKey();
	    				key.identityId_ = id.id_;
	    				key.when_ = hid.temporalFrame_;
	    				key.userKey_ = eKey.key_;
	    				mUserKeyManager.insertEncryptionUserKey(key);
	    				synchronized (mEncBackoff) {
	    					mEncBackoff.remove(hid);
	    				}
	    				addedNewKeys = true;
	    			} catch (IdentityProviderException.NeedsRetry e) {
	    				// Should try to get the key again later
	    				long backoff = updateBackoffForIdentity(hid, mEncBackoff);
	    				Log.i(TAG, "Encryption key fetch failed for " + id.principal_ +
	    						", retrying in " + backoff + " msec");
	    				requestEncryptionKeyAfterDelay(hid, backoff);
					} catch (IdentityProviderException.TwoPhase e) {
                        Log.i(TAG, "Two-phase verification needed.");
                        initiateIdClaim(hid);
                    } catch (IdentityProviderException e) {
	    				Log.i(TAG, "Server unable to obtain key for " + id.principal_);
	    			}
					synchronized (mRequestedEncryptionKeys) {
						mRequestedEncryptionKeys.remove(hid);
					}
				}
				if (addedNewKeys) {
				    Log.d(TAG, "Notifying new key");
    				mContext.getContentResolver().notifyChange(MusubiService.ENCODED_RECEIVED, null);
				}
			}
		});
	}
	
	private long updateBackoffForIdentity(IBHashedIdentity hid,
			HashMap<IBHashedIdentity, Long> backoffMap) {
		long backoff = MINIMUM_BACKOFF;
		synchronized (backoffMap) {
			// Use a bounded exponential backoff
			if (backoffMap.containsKey(hid)) {
				backoff = backoffMap.get(hid).longValue() * 2;
				backoff = (backoff > MAXIMUM_BACKOFF) ? MAXIMUM_BACKOFF : backoff;
			}
			backoffMap.put(hid, backoff);
		}
		return backoff;
	}
	
	private void requestSignatureKeyAfterDelay(IBHashedIdentity hid, long delayMillis) {
		final IBHashedIdentity ident = hid;
		new Handler(mThread.getLooper()).postDelayed(new Runnable() {
			@Override
			public void run() {
				requestSignatureKey(ident);
			}
		}, delayMillis);
	}
    
    private void requestEncryptionKeyAfterDelay(IBHashedIdentity hid, long delayMillis) {
        final IBHashedIdentity ident = hid;
        new Handler(mThread.getLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                requestEncryptionKey(ident);
            }
        }, delayMillis);
    }
    
    private void requestClaimAfterDelay(IBHashedIdentity hid, long delayMillis) {
        final IBHashedIdentity ident = hid;
        new Handler(mThread.getLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                initiateIdClaim(ident);
            }
        }, delayMillis);
    }
}
