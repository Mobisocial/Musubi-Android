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

package mobisocial.musubi.ui.fragments;

import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.crypto.IBIdentity;
import mobisocial.metrics.MusubiMetrics;
import mobisocial.musubi.App;
import mobisocial.musubi.R;
import mobisocial.musubi.facebook.SessionStore;
import mobisocial.musubi.model.MDevice;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MMyAccount;
import mobisocial.musubi.model.MPendingIdentity;
import mobisocial.musubi.model.helpers.DeviceManager;
import mobisocial.musubi.model.helpers.FeedManager;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.model.helpers.MyAccountManager;
import mobisocial.musubi.model.helpers.PendingIdentityManager;
import mobisocial.musubi.service.AddressBookUpdateHandler;
import mobisocial.musubi.service.MusubiService;
import mobisocial.musubi.service.WizardStepHandler;
import mobisocial.musubi.social.FacebookFriendFetcher;
import mobisocial.musubi.ui.MusubiBaseActivity;
import mobisocial.musubi.ui.SettingsActivity;
import mobisocial.musubi.ui.fragments.AccountLinkDialog.AccountLooperThread.Job;
import mobisocial.musubi.ui.util.UiUtil;
import mobisocial.musubi.util.CommonLayouts;
import mobisocial.musubi.util.InstrumentedActivity;

import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.SupportActivity;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.android.DialogError;
import com.facebook.android.Facebook;
import com.facebook.android.Facebook.ServiceListener;
import com.facebook.android.FacebookError;
import com.facebook.android.Util;

public class AccountLinkDialog  extends DialogFragment {
    final static String TAG = "AccountLinkDialog";
    final boolean DBG = MusubiBaseActivity.DBG;

    public static final String ACCOUNT_TYPE_FACEBOOK = "com.facebook.auth.login";
    public static final String ACCOUNT_TYPE_GOOGLE = "com.google";
    public static final String ACCOUNT_TYPE_PHONE = "mobisocial.musubi.phone";

    static final int MSG_CONNECT_GOOGLE = 1;
    static final int MSG_CONNECT_FB = 2;
    static final int MSG_ADD_TO_DATABASE = 3;
    static final int MSG_CONNECT_PHONE = 4;

    public static final String GOOGLE_OAUTH_SCOPE = "oauth2:https://www.google.com/m8/feeds/";
    public static final String FACEBOOK_APP_ID = "111111111111";
    public static final String[] FACEBOOK_PERMISSIONS =
            new String[] {"read_friendlists", "email", "offline_access","publish_stream"};

    private static final int REQUEST_GOOGLE_ACCOUNT = 97;
    private static final int REQUEST_FACEBOOK = 98;
    private static final int REQUEST_GOOGLE_AUTHENTICATE = 99;
    private static final int REQUEST_PHONE_NUMBER = 100;
    private static final String EXTRA_ACCOUNT = "account";

    private static final String TEXT_CONNECTED = "Connected!";
    private static final String TEXT_CHECKING = "Checking status...";
    private static final String TEXT_ERROR_CONNECTING = "Failed to connect.";
    private static final String TEXT_UNVERIFIED = "Verification Required.";
    
    private static final int DISPLAYED_SERVICES = 2;
    
    private static final int SHORTEST_PHONE_NUMBER = 6;
    private static final int LONGEST_PHONE_NUMBER = 14;

    // TODO: better support for facebook/google synchronisity issues.
    private Activity mActivity;
	private MyAccountManager mAccountManager;
    private ListView mAccountList;
    private AccountAdapter mAccountAdapter;
    private String mPendingAccountName = null;
	private String mPendingAccountType = null;

    private static AccountLooperThread sAccountLooperThread;
    enum AccountStatus { PENDING, CONNECTED, ERROR, UNVERIFIED };

    public static AccountLinkDialog newInstance() {
        AccountLinkDialog frag = new AccountLinkDialog();
        Bundle args = new Bundle();
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.Theme_D1dialog);
    }

    @Override
    public void onResume() {
    	super.onResume();
    	if(mPendingAccountType != null && mPendingAccountName != null && mPendingAccountType.equals(ACCOUNT_TYPE_GOOGLE)) {
	        String accountName = mPendingAccountName;
	        mPendingAccountName = null;
	        mPendingAccountType = null;
	        tryGoogleAccount(mActivity, accountName);
    	}
    }
    
    @Override
    public void onAttach(SupportActivity activity) {
    	super.onAttach(activity);
    	mActivity = activity.asActivity();
        SQLiteOpenHelper databaseSource = App.getDatabaseSource(mActivity);
        mAccountManager = new MyAccountManager(databaseSource);
        if(sAccountLooperThread == null) {
	        sAccountLooperThread = new AccountLooperThread();
	        sAccountLooperThread.start();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        LinearLayout window = new LinearLayout(mActivity);
        window.setLayoutParams(CommonLayouts.FULL_SCREEN);
        window.setOrientation(LinearLayout.VERTICAL);

        LinearLayout socialBox = new LinearLayout(mActivity);
        socialBox.setLayoutParams(CommonLayouts.FULL_WIDTH);
        socialBox.setOrientation(LinearLayout.HORIZONTAL);
        socialBox.setWeightSum(1.0f * DISPLAYED_SERVICES);

        /** Google **/
        ImageButton google = new ImageButton(mActivity);
        google.setImageResource(R.drawable.google);
        google.setOnClickListener(mGoogleClickListener);
        google.setLayoutParams(new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 1.0f));
        google.setAdjustViewBounds(true);
        socialBox.addView(google);
        
        /** Facebook **/
        ImageButton facebook = new ImageButton(mActivity);
        facebook.setImageResource(R.drawable.facebook);
        facebook.setOnClickListener(mFacebookClickListener);
        facebook.setLayoutParams(new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 1.0f));
        facebook.setAdjustViewBounds(true);
        socialBox.addView(facebook);
        
        /** Phone Number **/
        ImageButton phone = new ImageButton(mActivity);
        phone.setImageResource(R.drawable.phone);
        phone.setOnClickListener(mPhoneClickListener);
        phone.setLayoutParams(new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 1.0f));
        phone.setAdjustViewBounds(true);
        //socialBox.addView(phone);

        /** List of known accounts **/
        TextView chooseService = new TextView(mActivity);
        chooseService.setText("Choose a service to connect.");
        chooseService.setVisibility(View.GONE);
        chooseService.setLayoutParams(CommonLayouts.FULL_SCREEN);
        chooseService.setTextSize(20);
        mAccountAdapter = new AccountAdapter(getActivity());
        mAccountList = new ListView(getActivity());
        mAccountList.setAdapter(mAccountAdapter);
        mAccountList.setPadding(6, 10, 6, 0);
        mAccountList.setLayoutParams(CommonLayouts.FULL_SCREEN);
        mAccountList.setEmptyView(chooseService);

        /** Put it together **/
        window.addView(socialBox);
        window.addView(mAccountList);
        window.addView(chooseService);

        initialize();

        return window;
    }

    void initialize() {
        MMyAccount[] accounts = mAccountManager.getClaimedAccounts(ACCOUNT_TYPE_GOOGLE);
        for (MMyAccount account : accounts) {
            mAccountAdapter.add(account);

            Message m = sAccountLooperThread.obtainMessage();
            m.what = MSG_CONNECT_GOOGLE;
            Job job = new AccountLooperThread.Job();
            job.mAccount = account.accountName_;
            job.mDialog = this;
            m.obj = job;
            sAccountLooperThread.sendMessage(m);
        }

        accounts = mAccountManager.getClaimedAccounts(ACCOUNT_TYPE_FACEBOOK);
        for (MMyAccount account : accounts) {
            mAccountAdapter.add(account);

            Message m = sAccountLooperThread.obtainMessage();
            m.what = MSG_CONNECT_FB;
            Job job = new AccountLooperThread.Job();
            job.mId = account.id_;
            job.mDialog = this;
            m.obj = job;
            sAccountLooperThread.sendMessage(m);
        }
        
        accounts = mAccountManager.getMyAccounts(ACCOUNT_TYPE_PHONE);
        for (MMyAccount account : accounts) {
            mAccountAdapter.add(account);

            Message m = sAccountLooperThread.obtainMessage();
            m.what = MSG_CONNECT_PHONE;
            Job job = new AccountLooperThread.Job();
            job.mId = account.id_;
            job.mAccount = account.accountName_;
            job.mDialog = this;
            m.obj = job;
            sAccountLooperThread.sendMessage(m);
        }
    }

    public static class AccountDetails {
        public String principal;
        public String accountName;
        public String accountType;
        public boolean owned;

        public AccountDetails(String principal, String accountName,
                String accountType, boolean owned) {
            this.principal = principal;
            this.accountName = accountName;
            this.accountType = accountType;
            this.owned = owned;
        }
    }

    /**
     * Adds an account to the local database. Must not be called on the main thread.
     */
    public static MMyAccount addAccountToDatabase(Activity activity, AccountDetails accountDetails) {
        SQLiteOpenHelper databaseSource = App.getDatabaseSource(activity);
        IdentitiesManager im = new IdentitiesManager(databaseSource);
        MyAccountManager am = new MyAccountManager(databaseSource);
        DeviceManager dm = new DeviceManager(databaseSource);
        FeedManager fm = new FeedManager(databaseSource);

        String accountType = accountDetails.accountType;
        String accountName = accountDetails.accountName;
        String principal = accountDetails.principal; 
        boolean owned = accountDetails.owned;
        IBIdentity ibid;
        if (accountType.equals(ACCOUNT_TYPE_GOOGLE)) {
            ibid = new IBIdentity(Authority.Email, principal, 0);
        } else if (accountType.equals(ACCOUNT_TYPE_FACEBOOK)) {
            ibid = new IBIdentity(Authority.Facebook, principal, 0);
        } else if (accountType.equals(ACCOUNT_TYPE_PHONE)) {
            ibid = new IBIdentity(Authority.PhoneNumber, principal, 0);
        } else {
            throw new RuntimeException("Unsupported account type " + accountType);
        }

        SQLiteDatabase db = databaseSource.getWritableDatabase();
        db.beginTransaction();
        try {
            // Ensure identity in the database
            MIdentity id = im.getIdentityForIBHashedIdentity(ibid);
            //don't repeatedly add profile broadcast groups or do any
            //of this processing if the account is already owned.
            if (id != null && id.owned_) {
            	return null;
            }
            	
            	
            MIdentity original = im.getOwnedIdentities().get(0);
            //if this identity wasnt already in the contact book, we need to update it
            if (id == null) {
                id = new MIdentity();
                populateMyNewIdentity(activity, principal, im, ibid, id, original, owned);
                im.insertIdentity(id);
            } else {
                populateMyNewIdentity(activity, principal, im, ibid, id, original, owned);
                im.updateIdentity(id);
            } 
            
            im.updateMyProfileName(activity, id.musubiName_, false);
            im.updateMyProfileThumbnail(activity, id.musubiThumbnail_, false);

            // Ensure account entry exists
            MMyAccount account = am.lookupAccount(accountName, accountType);
            if (account == null) {
                //create the account
                account = new MMyAccount();
                account.accountName_ = accountName;
                account.accountType_ = accountType;
                account.identityId_ = id.id_;
                am.insertAccount(account);
            } else {
                account.identityId_ = id.id_;
                am.updateAccount(account);
            }
            
            // For accounts linked to identities that are not yet owned,
            // skip further initialization
            if (owned) {
                MDevice dev = dm.getDeviceForName(id.id_, dm.getLocalDeviceName());
                // Ensure device exists
                if(dev == null) {
    	            dev = new MDevice();
                	dev.deviceName_ = dm.getLocalDeviceName();
    	            dev.identityId_ = id.id_;
    	            dm.insertDevice(dev);
                }
                //this feed will contain all members who should receive
                //a profile for the account because of a friend introduction
                MFeed provisional = new MFeed();
                provisional.name_ = MFeed.PROVISONAL_WHITELIST_FEED_NAME;
                provisional.type_ = MFeed.FeedType.ASYMMETRIC;
                fm.insertFeed(provisional);
                //XXX
                //TODO: in other places in the code, we should be pruning the
                //provisional whitelist feed as people become whitelisted..
                
                //these get inserted for owned identities to allow profile
                //broadcasts to go out
                MMyAccount provAccount = new MMyAccount();
                provAccount.accountName_ = MMyAccount.PROVISIONAL_WHITELIST_ACCOUNT;
                provAccount.accountType_ = MMyAccount.INTERNAL_ACCOUNT_TYPE;
                provAccount.identityId_ = id.id_;
                provAccount.feedId_ = provisional.id_;
                am.insertAccount(provAccount);
    
                
                //this feed will contain all members who should receive
                //a profile for the account because they are whitelisted
                //and contacted you on one of your accounts.
                MFeed accountBroadcastFeed = new MFeed();
                accountBroadcastFeed.name_ = MFeed.LOCAL_WHITELIST_FEED_NAME;
                accountBroadcastFeed.type_ = MFeed.FeedType.ASYMMETRIC;
                fm.insertFeed(accountBroadcastFeed);
                
                MMyAccount localAccount = new MMyAccount();
                localAccount.accountName_ = MMyAccount.LOCAL_WHITELIST_ACCOUNT;
                localAccount.accountType_ = MMyAccount.INTERNAL_ACCOUNT_TYPE;
                localAccount.identityId_ = id.id_;
                localAccount.feedId_ = accountBroadcastFeed.id_;
                am.insertAccount(localAccount);
                
                db.setTransactionSuccessful();
    
                ContentResolver resolver = activity.getContentResolver();
                // Notify interested services (identity available makes AMQP wake up for example)
                resolver.notifyChange(MusubiService.OWNED_IDENTITY_AVAILABLE, null);
                resolver.notifyChange(MusubiService.MY_PROFILE_UPDATED, null);
    
                // Makes key update wake up
                resolver.notifyChange(MusubiService.AUTH_TOKEN_REFRESH, null);
                WizardStepHandler.accomplishTask(activity, WizardStepHandler.TASK_LINK_ACCOUNT);
    
                App.getUsageMetrics(activity).report(MusubiMetrics.ACCOUNT_CONNECTED,
                        account.accountType_);
            } else {
                db.setTransactionSuccessful();
            }
            return account;
        } finally {
            db.endTransaction();
        }
    }

	private static void populateMyNewIdentity(Activity activity, String accountName,
			IdentitiesManager im, IBIdentity ibid, MIdentity id,
			MIdentity original, boolean owned) {
		//its ours and we are on the network
		id.claimed_ = true;
		id.owned_ = owned;
		id.whitelisted_ = true;
		id.hasSentEmail_ = true;
		//set up the identity data
		id.principal_ = accountName;
		id.type_ = ibid.authority_;
		id.principalHash_ = ibid.hashed_;
		id.principalShortHash_ = mobisocial.musubi.util.Util.shortHash(ibid.hashed_);
		if (owned) {
    		//mark us for one way push
    		id.sentProfileVersion_ = 1;
    		//clone the profile fields
    		id.musubiName_ = original.musubiName_;
    		id.musubiThumbnail_ = im.getMusubiThumbnail(original);
    		id.receivedProfileVersion_ = original.receivedProfileVersion_;
    		//force some kind of name to be set
            if(id.type_ == Authority.Facebook) {
                Facebook facebook = getFacebookInstance(activity);
            	FacebookFriendFetcher fetcher = new FacebookFriendFetcher(facebook);
            	String name = fetcher.getLoggedinUserName();
            	byte[] thumbnail = fetcher.getLoggedinUserPhoto();
            	if(name != null) {
            		id.name_ = name;
            		if(id.musubiName_ == null) {
                		id.musubiName_ = name;
            		}
            	}
            	if(thumbnail != null) {
            		id.thumbnail_ = thumbnail;
            		if(id.musubiThumbnail_ == null) {
            			id.musubiThumbnail_ = thumbnail;
            		}
            	}
            } else {
    			if(id.musubiName_ == null) {
    				//use the real name extracted from facebook or the local contact
    				//book if possible
    				if(id.musubiName_ == null) {
    					id.musubiName_ = id.name_;
    				}
    				//otherwise just make up something cute
    				if(id.musubiName_ == null) {
    					id.musubiName_ = UiUtil.randomFunName();
    				}
    			}
    			if(id.musubiThumbnail_ == null) {
    				id.musubiThumbnail_ = id.thumbnail_;
    			}
            }
		}
	}

    View.OnClickListener mGoogleClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Account[] accounts = AccountManager.get(mActivity)
                    .getAccountsByType(ACCOUNT_TYPE_GOOGLE);
            if (accounts == null) {
            	accounts = new Account[0];
            }
            ((InstrumentedActivity)mActivity).showDialog(
                    GoogleAccountPickerDialog.newInstance(AccountLinkDialog.this, accounts));
        }
    };
    
	static Facebook facebook = new Facebook(FACEBOOK_APP_ID);
    public static Facebook getFacebookInstance(Context context) {
        // Load the current instance if available
		SessionStore.restore(facebook, context);
		return facebook;
    }
    
    /*
     * Refresh the current known Facebook token asynchronously.
     */
    public static void refreshFacebookToken(final Context context) {
        new Thread() {
            @Override
            public void run() {
                // Get a new instance if the current one is null
                final Facebook facebook = getFacebookInstance(context);
                // Extend the token as needed
                facebook.extendAccessTokenIfNeeded(context, new ServiceListener() {
                    @Override
                    public void onComplete(Bundle values) {
                        // If the token retrieval was successful, report it and save the state
                        long expiration = values.getLong(Facebook.EXPIRES);
                        Log.i(TAG, "New Facebook token expiration time: " + expiration);
                        SessionStore.save(facebook, context);
                    }
                    @Override
                    public void onFacebookError(FacebookError e) {
                        Log.i(TAG, "Facebook API error", e);
                    }
                    @Override
                    public void onError(Error e) {
                        Log.w(TAG, "Error on call to Facebook", e);
                    }
                });
            }
        }.start();
    }
    
    public void postActivityToFeed() {
	    SharedPreferences p = mActivity.getSharedPreferences(SettingsActivity.PREFS_NAME, 0);
	    if (p.getBoolean(SettingsActivity.PREF_ALREADY_SAW_FACEBOOK_POST, false)) {
	        return;
	    }
	    p.edit().putBoolean(SettingsActivity.PREF_ALREADY_SAW_FACEBOOK_POST, true).commit();
    	Bundle post = new Bundle();
    	post.putString("picture", "https://lh5.ggpht.com/hRTJJv7H9dpLXhHTTqiiNY2DD2wWO0hZFWEWPv1g-WArcUYLsWk-aQYUS0UgZfVIqtXm=w124");
    	post.putString("link", "https://market.android.com/details?id=mobisocial.musubi");
    	post.putString("caption", "Musubi");
    	post.putString("description", "I'm using Musubi, a social network without the Cloud for smartphones.");
    	Facebook facebook = getFacebookInstance(mActivity);
    	facebook.dialog(mActivity, "feed", post, new Facebook.DialogListener() {

			@Override
			public void onComplete(Bundle values) {
				Log.i(TAG, values.toString());
			}

			@Override
			public void onFacebookError(FacebookError e) {
				Log.e(TAG, e.toString());
			}

			@Override
			public void onError(DialogError e) {
				Log.e(TAG, e.toString());
			}

			@Override
			public void onCancel() {
				Log.i(TAG, "User canceled post to Facebook.");
			}


    	});
    }
    
    /**
     * Returns the currently-active Facebook token for the local user,
     * or null if none available.
     */
    public static String getActiveFacebookToken(Context context) {
        Facebook facebook = getFacebookInstance(context);
        if (facebook.isSessionValid()) {
            return facebook.getAccessToken();
        }
        return null;
    }

    /**
     * Returns a current token for the given Google account, or
     * null if a token isn't available without user interaction.
     */
    public static String silentBlockForGoogleToken(Context context, String accountName)
            throws IOException {
        Account account = new Account(accountName, ACCOUNT_TYPE_GOOGLE);
        AccountManager accountManager = AccountManager.get(context);
        // Need to get cached token, invalidate it, then get the token again
        String token = blockForCachedGoogleToken(context, account, accountManager);
        if (token != null) {
        	accountManager.invalidateAuthToken(ACCOUNT_TYPE_GOOGLE, token);
        }
        token = blockForCachedGoogleToken(context, account, accountManager);
        return token;
    }
    
    private static String blockForCachedGoogleToken(Context context,
    		Account account, AccountManager accountManager) throws IOException {
        AccountManagerFuture<Bundle> future = accountManager.getAuthToken(account,
                GOOGLE_OAUTH_SCOPE, true, null, null);
        if (future != null) {
            try {
                Bundle result = future.getResult();
                if (result.containsKey(AccountManager.KEY_AUTHTOKEN)) {
                    String cachedGoogleToken = result.getString(AccountManager.KEY_AUTHTOKEN);
                    return cachedGoogleToken;
                }
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
            }
        }
        return null;
    }

    private AccountManagerFuture<Bundle> tryGoogleAccount(Context context, String accountName) {
        if (accountName == null) {
            Log.e(TAG, "No selected Google account.");
            return null;
        }

        Account account = new Account(accountName, ACCOUNT_TYPE_GOOGLE);
        AccountManager accountManager = AccountManager.get(context);
        return accountManager.getAuthToken(account, GOOGLE_OAUTH_SCOPE,
                true, new GoogleAccountManagerCallback(account), null);
    }

    class GoogleAccountManagerCallback implements AccountManagerCallback<Bundle> {
    	private Account mAccount;
		public GoogleAccountManagerCallback(Account account) {
    		mAccount = account;
    	}
		@Override
        public void run(AccountManagerFuture<Bundle> future) {
            String authToken = null;
        	//callback can happen after the dialog is gone...
            try {
                Bundle bundle = future.getResult();
                authToken = bundle.getString(AccountManager.KEY_AUTHTOKEN);
                if (authToken != null) {
                    Message m = sAccountLooperThread.obtainMessage();
                    m.what = MSG_ADD_TO_DATABASE;
                    Job job = new AccountLooperThread.Job();
                    job.mDialog = AccountLinkDialog.this;
                    job.mDetails = new AccountDetails(mAccount.name, mAccount.name, mAccount.type, true);
                    m.obj = job;
                    sAccountLooperThread.sendMessage(m);
                } else if (bundle.containsKey(AccountManager.KEY_INTENT)) {
                    Intent intent = bundle.getParcelable(AccountManager.KEY_INTENT);
                    intent.setFlags(intent.getFlags() & ~Intent.FLAG_ACTIVITY_NEW_TASK);
                    mPendingAccountType = mAccount.type;
                    mPendingAccountName = mAccount.name;
                    startActivityForResult(intent, REQUEST_GOOGLE_AUTHENTICATE);
                } else {
                    // handle errors in one block
                    throw new Exception();
                }
            } catch (Exception e) {
                if (mAccount.type != null) {
                    toast("Failed to connect.");
                    mAccountAdapter.setAccountStatus(mAccount.name, mAccount.type, AccountStatus.ERROR);
                } else {
                    toast("Failed to connect " + mAccount.name + ".");
                }
                
                Log.i(TAG, "Invalidating auth token from error " + authToken);
                try {
                    AccountManager accountManager = AccountManager.get(mActivity);
                    accountManager.invalidateAuthToken(mAccount.type, authToken);
                } catch (Exception e2) {
                }
            }
        }
    };

    View.OnClickListener mFacebookClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Facebook facebook = getFacebookInstance(mActivity);
            if (!facebook.isSessionValid()) {
                facebook.authorize(mActivity, FACEBOOK_PERMISSIONS,
                        REQUEST_FACEBOOK, mFacebookCallback);
            }
        }
    };

    Facebook.DialogListener mFacebookCallback = new Facebook.DialogListener() {
        @Override
        public void onFacebookError(FacebookError e) {
            Log.e(TAG, "Facebook error", e);
            toast("Error connecting to Facebook.");
        }
        
        @Override
        public void onError(DialogError e) {
            Log.e(TAG, "error", e);
            toast("Error connecting to Facebook.");
        }
        class FacebookLoadMyProfileTask extends AsyncTask<Void, Void, Void> {
            final Facebook facebook = getFacebookInstance(mActivity);
        	Bundle mValues;
        	Throwable mError = null;
        	
        	FacebookLoadMyProfileTask(Bundle values) {
        		mValues = values;
        	}
        	
        	@Override
            protected Void doInBackground(Void... params) {
                facebook.setAccessToken(mValues.getString(Facebook.TOKEN));
                facebook.setAccessExpiresIn(mValues.getString(Facebook.EXPIRES));
        		try {
                    JSONObject json = Util.parseJson(facebook.request("me"));
                    String userId = json.getString("id");
                    String accountName = json.getString("email");
                    //String name = json.getString("name");
                    Log.d(TAG, "Facebook success");
                    SessionStore.save(facebook, mActivity);
                    Message m = sAccountLooperThread.obtainMessage();
                    m.what = MSG_ADD_TO_DATABASE;
                    Job job = new AccountLooperThread.Job();
                    job.mDialog = AccountLinkDialog.this;
                    job.mDetails = new AccountDetails(userId, accountName, ACCOUNT_TYPE_FACEBOOK, true);
                    m.obj = job;
                    sAccountLooperThread.sendMessage(m);
                    mActivity.getContentResolver().notifyChange(MusubiService.FACEBOOK_FRIEND_REFRESH, null);
        		} catch (JSONException e) {
        			Log.e(TAG, "JSONException", e);
        		} catch (Throwable e) {
                    Log.e(TAG, "Failed to log in with facebook", e);
                    mError = e;
                }
        		return null;
        	}
        	@Override
        	protected void onPostExecute(Void result) {
        		if(mError != null)
        			toast("Couldn't connect to Facebook.");
        		else
                    postActivityToFeed();
        	};
        }
        
        @Override
        public void onComplete(final Bundle values) {
        	new FacebookLoadMyProfileTask(values).execute();
        }
        
        @Override
        public void onCancel() {
            Log.i(TAG, "user cancelled facebook auth");
        }
    };

    View.OnClickListener mPhoneClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            // Show the phone number from the API if there is a valid one
            TelephonyManager tMgr = (TelephonyManager)mActivity.getSystemService(Context.TELEPHONY_SERVICE);
            Set<Account> accounts = new HashSet<Account>();
            String phoneNumber = tMgr.getLine1Number();
            if (phoneNumber != null && validatePhoneNumber(phoneNumber) != null) {
                Log.d(TAG, "Phone number: " + phoneNumber);
                accounts.add(new Account(validatePhoneNumber(phoneNumber), ACCOUNT_TYPE_PHONE));
            }
            // Also show any previously claimed phone numbers
            MMyAccount[] claimedAccounts = mAccountManager.getClaimedAccounts(ACCOUNT_TYPE_PHONE);
            for (MMyAccount acc : claimedAccounts) {
                accounts.add(new Account(acc.accountName_, acc.accountType_));
            }
            ((InstrumentedActivity)mActivity).showDialog(
                    PhoneNumberPickerDialog.newInstance(AccountLinkDialog.this, accounts));
        }
    };

    public static class PhoneNumberPickerDialog extends DialogFragment
            implements DialogInterface.OnClickListener {
        private static final String ENTER_ALTERNATE_NUMBER = "Enter a phone number";
        Activity mActivity;
        AccountLinkDialog mTarget;

        public static PhoneNumberPickerDialog newInstance(AccountLinkDialog target,
                Set<Account> accounts) {
            PhoneNumberPickerDialog frag = new PhoneNumberPickerDialog();
            Bundle args = new Bundle();
            String[] accountNames = new String[accounts.size() + 1];
            int i = 0;
            for (Account a : accounts) {
                accountNames[i++] = a.name;
            }
            accountNames[i++] = ENTER_ALTERNATE_NUMBER;
            args.putStringArray("accountNames", accountNames);
            frag.setArguments(args);
            frag.setTargetFragment(target, REQUEST_PHONE_NUMBER);
            return frag;
        }

        public PhoneNumberPickerDialog() {
            super();
        }

        @Override
        public void onAttach(SupportActivity activity) {
            super.onAttach(activity);
            mActivity = activity.asActivity();
        }
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(mActivity)
            .setTitle("Connect Phone Number")
            .setItems(getArguments().getStringArray("accountNames"), this)
            .create();
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            String account = getArguments().getStringArray("accountNames")[which];
            if(account.equals(ENTER_ALTERNATE_NUMBER)) {
                // TODO: this dialog comes back, but it shouldn't
                dismiss();
                ((InstrumentedActivity)mActivity).showDialog(
                        EnterPhoneNumberDialog.newInstance(
                                (AccountLinkDialog)getTargetFragment()));
            } else {
                Intent data = new Intent();
                data.putExtra(AccountManager.KEY_ACCOUNT_NAME, account);
                getTargetFragment().onActivityResult(REQUEST_PHONE_NUMBER, Activity.RESULT_OK, data);
                dismiss();
            }
        }
    }
    
    public static class EnterPhoneNumberDialog extends DialogFragment
            implements View.OnClickListener {
        Activity mActivity;
        Dialog mDialog;
        
        public static EnterPhoneNumberDialog newInstance(AccountLinkDialog target) {
            EnterPhoneNumberDialog frag = new EnterPhoneNumberDialog();
            frag.setTargetFragment(target, REQUEST_PHONE_NUMBER);
            return frag;
        }
        
        public EnterPhoneNumberDialog() {
            super();
        }

        @Override
        public void onAttach(SupportActivity activity) {
            super.onAttach(activity);
            mActivity = activity.asActivity();Dialog numberDlg = new Dialog(mActivity);
            numberDlg.setContentView(R.layout.phone_number_dialog);
            numberDlg.setTitle("Enter a Phone Number");
            Button go = (Button)numberDlg.findViewById(R.id.button1);
            go.setOnClickListener(this);
            mDialog = numberDlg;
        }
        
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return mDialog;
        }
        
        @Override
        public void onClick(View v) {
            EditText country = (EditText)mDialog.findViewById(R.id.editText1);
            EditText primary = (EditText)mDialog.findViewById(R.id.editText2);
            String number = country.getText().toString() + primary.getText().toString();
            Log.d(TAG, "Entered number: " + number);
            if (number != "") {
                Intent data = new Intent();
                data.putExtra(AccountManager.KEY_ACCOUNT_NAME, number);
                getTargetFragment().onActivityResult(REQUEST_PHONE_NUMBER, Activity.RESULT_OK, data);
            }
            dismiss();
        }
    }

    public static class GoogleAccountPickerDialog extends DialogFragment
            implements DialogInterface.OnClickListener {
        private static final String REGISTER_AN_ACCOUNT = "Use other email address via Google";
		private static final String ADD_AN_ACCOUNT = "Add a Google account";

		public static GoogleAccountPickerDialog newInstance(AccountLinkDialog target,
                Account[] accounts) {
            GoogleAccountPickerDialog frag = new GoogleAccountPickerDialog();
            Bundle args = new Bundle();
            String[] accountNames = new String[accounts.length + 2];
            int i = 0;
            for (Account a : accounts) {
                accountNames[i++] = a.name;
            }
            accountNames[i++] = ADD_AN_ACCOUNT;
            accountNames[i++] = REGISTER_AN_ACCOUNT;
            args.putStringArray("accountNames", accountNames);
            frag.setArguments(args);
            frag.setTargetFragment(target, REQUEST_GOOGLE_ACCOUNT);
            return frag;
        }

        public GoogleAccountPickerDialog() {
            
        }
        Activity mActivity;

        @Override
        public void onAttach(SupportActivity activity) {
        	super.onAttach(activity);
        	mActivity = activity.asActivity();
        }
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(mActivity)
            .setTitle("Connect Account")
            .setItems(getArguments().getStringArray("accountNames"), this)
            .create();
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            String account = getArguments().getStringArray("accountNames")[which];
            if(account.equals(ADD_AN_ACCOUNT)) {
            	try {
	            	Intent intent = new Intent();
	                intent.setClassName( "com.google.android.gsf", "com.google.android.gsf.login.AccountIntroActivity" );
	                mActivity.startActivity( intent );
	                dismiss();
	                return;
            	} catch(Throwable t) {}
            	try {
	            	Intent intent = new Intent(Settings.ACTION_ADD_ACCOUNT);
	                mActivity.startActivity( intent );
	                dismiss();
	                return;
            	} catch(Throwable t) {}
            	Toast.makeText(mActivity, "Failed to invoke Google account services", Toast.LENGTH_SHORT).show();
                dismiss();
                return;
            } else if(account.equals(REGISTER_AN_ACCOUNT)) {
            	Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://accounts.google.com/NewAccount"));
                mActivity.startActivity( intent );
                dismiss();
                return;
            } else {
	            Intent data = new Intent();
	            data.putExtra(EXTRA_ACCOUNT, account);
	            getTargetFragment().onActivityResult(REQUEST_GOOGLE_ACCOUNT, Activity.RESULT_OK, data);
            }
        }
    }
    
    /*
     * In some cases, the user may want Musubi to resend text verification
     */
    public static class RetryPhoneDialog extends DialogFragment {
        Set<MPendingIdentity> mPendingIdents;
        PendingIdentityManager mManager;
        Activity mActivity;
        
        public static RetryPhoneDialog newInstance(
                PendingIdentityManager manager, Set<MPendingIdentity> pendingIdents) {
            RetryPhoneDialog d = new RetryPhoneDialog(manager, pendingIdents);
            return d;
        }
        
        public RetryPhoneDialog(PendingIdentityManager manager, Set<MPendingIdentity> pendingIdents) {
            super();
            mPendingIdents = pendingIdents;
        }

        @Override
        public void onAttach(SupportActivity activity) {
            super.onAttach(activity);
            mActivity = activity.asActivity();
        }
        
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(mActivity)
                .setTitle("Resend Verification")
                .setMessage("Would you like Musubi to resend the verification text?")
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        for (MPendingIdentity pendingIdent : mPendingIdents) {
                            if (pendingIdent.notified_) {
                                pendingIdent.notified_ = false;
                                mManager.updateIdentity(pendingIdent);
                            }
                        }
                        mActivity.getContentResolver().notifyChange(MusubiService.AUTH_TOKEN_REFRESH, null);
                        dismiss();
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dismiss();
                    }
                })
                .create();
        }
    }

    private String validatePhoneNumber(String original) {
        // Strip non-numeric characters and leading zeros
        original.replaceAll("[^\\d]", "");
        long numerical = Long.parseLong(original);
        
        // Check to make sure the length is feasible
        String converted = Long.toString(numerical);
        if (converted.length() < SHORTEST_PHONE_NUMBER ||
                converted.length() > LONGEST_PHONE_NUMBER) {
            return null;
        }
        else {
            return "+" + converted;
        }
    }
    
    private void setupPhoneAccount(String phoneNumber) {
        // See if this phone number is known, and add it if it isn't
        Log.d(TAG, "Setting up " + phoneNumber);
        IBIdentity toAdd = new IBIdentity(Authority.PhoneNumber, phoneNumber, 0);
        SQLiteOpenHelper databaseSource = App.getDatabaseSource(mActivity);
        IdentitiesManager im = new IdentitiesManager(databaseSource);
        MIdentity id = im.getIdentityForIBHashedIdentity(toAdd);
        if (id == null) {
            id = new MIdentity();
            id.claimed_ = false;
            id.owned_ = false;
            id.whitelisted_ = true;
            id.hasSentEmail_ = true;
            //set up the identity data
            id.principal_ = toAdd.principal_;
            id.type_ = toAdd.authority_;
            id.principalHash_ = toAdd.hashed_;
            id.principalShortHash_ = mobisocial.musubi.util.Util.shortHash(toAdd.hashed_);
            im.insertIdentity(id);
        }
        
        // Get the corresponding pending identity
        PendingIdentityManager pManager = new PendingIdentityManager(databaseSource);
        Set<MPendingIdentity> pendingIdents = pManager.lookupIdentities(id.id_);
        
        if (!id.owned_) {
            // Add this account so that it can be tracked
            Message m = sAccountLooperThread.obtainMessage();
            m.what = MSG_ADD_TO_DATABASE;
            Job job = new AccountLooperThread.Job();
            job.mDialog = AccountLinkDialog.this;
            job.mDetails = new AccountDetails(toAdd.principal_, toAdd.principal_, ACCOUNT_TYPE_PHONE, id.owned_);
            m.obj = job;
            sAccountLooperThread.sendMessage(m);
            
            MPendingIdentity pendingIdent = pManager.lookupIdentity(id.id_, toAdd.temporalFrame_);
            if (pendingIdent == null) {
                pendingIdent = pManager.fillPendingIdentity(id.id_, toAdd.temporalFrame_);
                pManager.insertIdentity(pendingIdent);
            }
            pendingIdents.add(pendingIdent);
        }
            
        boolean anyUnnotified = false;
        for (MPendingIdentity pident : pendingIdents) {
            if (!pident.notified_) {
                anyUnnotified = true;
            }
        }
        
        // If any of these are unnotified, ask to resend
        if (!anyUnnotified) {
            ((InstrumentedActivity)mActivity).showDialog(
                    RetryPhoneDialog.newInstance(pManager, pendingIdents));
        }
        else {
            // Start the verification process
            mActivity.getContentResolver().notifyChange(MusubiService.AUTH_TOKEN_REFRESH, null);
        }
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    	if (data == null) {
    		return;
    	}
        switch (requestCode) {
            case REQUEST_GOOGLE_ACCOUNT:
                if (resultCode == Activity.RESULT_OK) {
                    String account = data.getStringExtra(EXTRA_ACCOUNT);
                    tryGoogleAccount(mActivity, account);
                }
                break;
            case REQUEST_GOOGLE_AUTHENTICATE:
            	//this doesnt really seem to be called because of the account manager having a
            	//weird api
                if (resultCode == Activity.RESULT_OK) {
                    String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    tryGoogleAccount(mActivity, accountName);
                }
                break;
            case REQUEST_FACEBOOK:
            	Log.d(TAG, "Authorizing Facebook callback");
            	Facebook facebook = getFacebookInstance(mActivity);
    			facebook.authorizeCallback(requestCode, resultCode, data);
                break;
            case REQUEST_PHONE_NUMBER:
                if (resultCode == Activity.RESULT_OK) {
                    String phoneNumber = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    phoneNumber = validatePhoneNumber(phoneNumber);
                    setupPhoneAccount(phoneNumber);
                }
                break;
        }
    }

    static class AccountLooperThread extends Thread {
        private Handler mHandler;
        static class Job {
        	public long mId;
			protected AccountDetails mDetails;
			AccountLinkDialog mDialog;
        	String mAccount;
        }

        public void run() {
            Looper.prepare();

            mHandler = new Handler() {
                public void handleMessage(Message msg) {
                    final Job job = (Job)msg.obj;
                	switch (msg.what) {
                        case MSG_CONNECT_GOOGLE:
                            String account = job.mAccount;
                            String token = null;
                            try {
                                token = silentBlockForGoogleToken(job.mDialog.mActivity, account);
                            } catch (IOException e) {
                                Log.i(TAG, "Could not get a Google token likely due to a network error");
                            }
                            AccountStatus status = (token == null) ?
                                    AccountStatus.ERROR : AccountStatus.CONNECTED;
                            job.mDialog.mAccountAdapter.setAccountStatus(account, ACCOUNT_TYPE_GOOGLE, status);
                            break;
                        case MSG_CONNECT_FB:
                            try {
                                // Make sure we can connect, but we don't care about the result.
                                Facebook facebook = getFacebookInstance(job.mDialog.mActivity);
                                Util.parseJson(facebook.request("me"));
                                job.mDialog.mAccountAdapter.setAccountStatus(job.mId, AccountStatus.CONNECTED);
                            } catch(Throwable e) {
                                Log.e(TAG, "silent facebook error", e);
                                job.mDialog.mAccountAdapter.setAccountStatus(job.mId, AccountStatus.ERROR);
                            }
                            break;
                        case MSG_CONNECT_PHONE:
                            SQLiteOpenHelper databaseSource = App.getDatabaseSource(job.mDialog.mActivity);
                            IdentitiesManager im = new IdentitiesManager(databaseSource);
                            MIdentity mid = im.getIdentityForIBHashedIdentity(
                                    new IBIdentity(Authority.PhoneNumber, job.mAccount, 0));
                            if (mid != null) {
                                PendingIdentityManager pManager = new PendingIdentityManager(databaseSource);
                                int unnotified = pManager.getUnnotifiedIdentities(mid.id_).size();
                                if (mid.owned_ && unnotified == 0) {
                                    job.mDialog.mAccountAdapter.setAccountStatus(job.mId, AccountStatus.CONNECTED);
                                } else if (unnotified == 0) {
                                    job.mDialog.mAccountAdapter.setAccountStatus(job.mId, AccountStatus.ERROR);
                                } else {
                                    job.mDialog.mAccountAdapter.setAccountStatus(job.mId, AccountStatus.UNVERIFIED);
                                }
                            }
                            break;
                        case MSG_ADD_TO_DATABASE:
                            final Activity activity = job.mDialog.mActivity;
                            final boolean previouslyOwned = new IdentitiesManager(
                                    App.getDatabaseSource(activity)).hasConnectedAccounts();
                            final AccountDetails details = job.mDetails;
                            final MMyAccount dbRow = AccountLinkDialog.addAccountToDatabase(
                                    activity, details);
                            // Update ui
                            if (dbRow != null) {
                            	activity.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                    	job.mDialog.mAccountAdapter.add(dbRow);
                                    	if (details.owned) {
                                    	    job.mDialog.mAccountAdapter.setAccountStatus(dbRow.id_,
                                    	            AccountStatus.CONNECTED);
                                    	} else {
                                    	    // In case an account is added without knowing if we
                                    	    // can get user keys for it
                                    	    job.mDialog.mAccountAdapter.setAccountStatus(dbRow.id_,
                                    	            AccountStatus.UNVERIFIED);
                                    	}

                                    	if (!previouslyOwned) {
                                            new AddressBookUpdateHandler.AddressBookImportTask(activity).execute();
                                        }
                                    }
                                });
                            }
                    }
                }
            };

            synchronized (this) {
                notify();
            }

            Looper.loop();
        }

        public Message obtainMessage() {
            while (mHandler == null) {
                try {
                    // watch for startup race condition
                    synchronized (this) {
                        wait(50);
                    }
                } catch (InterruptedException e) {}
            }
            return mHandler.obtainMessage();
        }

        public void sendMessage(Message msg) {
            mHandler.sendMessage(msg);
        }
    }

    class AccountAdapter extends ArrayAdapter<MMyAccount> {
        final Context mContext;
        final Map<Long, AccountStatus> mAccountStatus;
        final TLongSet mKnownAccounts;

        public AccountAdapter(Context context) {
            super(context, android.R.layout.simple_list_item_1);
            mContext = context;
            mAccountStatus = new HashMap<Long, AccountStatus>();
            mKnownAccounts = new TLongHashSet();
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            if (view == null) {
                view = getAccountView();
            }

            MMyAccount account = getItem(position);
            int iconResource = R.drawable.icon;
            if (ACCOUNT_TYPE_GOOGLE.equals(account.accountType_)) {
                iconResource = R.drawable.google;
            } else if (ACCOUNT_TYPE_FACEBOOK.equals(account.accountType_)) {
                iconResource = R.drawable.facebook;
            }
            ((ImageView)view.findViewById(R.id.icon)).setImageResource(iconResource);
            ((TextView)view.findViewById(R.id.text)).setText(account.accountName_);
            String status = statusForAccount(account.id_);
            ((TextView)view.findViewById(R.id.status)).setText(status);
            return view;
        }

        View getAccountView() {
            LinearLayout frame = new LinearLayout(mContext);
            frame.setOrientation(LinearLayout.HORIZONTAL);

            ImageView icon = new ImageView(mContext);
            int size = 60;
            icon.setLayoutParams(new LinearLayout.LayoutParams(size, size));
            icon.setId(R.id.icon);
            icon.setPadding(3, 6, 6, 0);

            LinearLayout accountView = new LinearLayout(mContext);
            accountView.setOrientation(LinearLayout.VERTICAL);

            TextView label = new TextView(mContext);
            label.setTextSize(20);
            label.setId(R.id.text);
            accountView.addView(label);

            TextView status = new TextView(mContext);
            status.setTextSize(14);
            status.setId(R.id.status);
            accountView.addView(status);

            frame.addView(icon);
            frame.addView(accountView);
            return frame;
        }

        public synchronized void setAccountStatus(long accountId, AccountStatus status) {
            mAccountStatus.put(accountId, status);
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    notifyDataSetChanged();     
                }
            });
        }

        @Override
        public synchronized void add(MMyAccount account) {
            if (!mKnownAccounts.contains(account.id_)) {
                mKnownAccounts.add(account.id_);
                super.add(account);   
            }
        };

        public synchronized void setAccountStatus(String accountName, String accountType, AccountStatus status) {
            MMyAccount account = mAccountManager.lookupAccount(accountName, accountType);
            if (account == null) {
                Log.e(TAG, "Could not find account " + accountName + "/" + accountType);
                return;
            }
            setAccountStatus(account.id_, status);
        }

        String statusForAccount(long accountId) {
            AccountStatus val = mAccountStatus.get(accountId);
            if (val == null) {
                return TEXT_CHECKING;
            }
            switch (val) {
                case CONNECTED:
                    return TEXT_CONNECTED;
                case ERROR:
                    return TEXT_ERROR_CONNECTING;
                case UNVERIFIED:
                    return TEXT_UNVERIFIED;
                case PENDING:
                default:
                    return TEXT_CHECKING;
            }
        }
    }

    void toast(final String text) {
        Toast.makeText(mActivity, text, Toast.LENGTH_SHORT).show();
    }
}
