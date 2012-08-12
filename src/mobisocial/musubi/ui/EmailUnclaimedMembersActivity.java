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

package mobisocial.musubi.ui;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Arrays;

import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.musubi.App;
import mobisocial.musubi.Helpers;
import mobisocial.musubi.R;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.objects.OutOfBandInvitedObj;
import mobisocial.musubi.ui.fragments.AccountLinkDialog;
import mobisocial.musubi.ui.fragments.EmailUnclaimedMembersFragment;
import mobisocial.socialkit.Obj;
import android.content.Intent;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.facebook.android.AsyncFacebookRunner.RequestListener;
import com.facebook.android.DialogError;
import com.facebook.android.Facebook;
import com.facebook.android.Facebook.DialogListener;
import com.facebook.android.FacebookError;

/**
 * Pick contacts and/or groups for various purposes.
 * TODO: Remove TabActivity in favor of fragments;
 * Make activity a floating window.
 * 
 * TODO: Picker should return personId, not id.
 */
public class EmailUnclaimedMembersActivity extends FragmentActivity {

	public final static String INTENT_EXTRA_FEED_URI = "feed_uri";
	final String message = "I just sent you a message on Musubi, a social sharing and application platform! " +
            "To get your message, install the Musubi app on your Android phone from the Android market.";
    public static final String MUSUBI_MARKET_URL = "https://market.android.com/details?id=mobisocial.musubi";
	public final static String INTENT_EXTRA_RECIPIENT_IDS = "recipient_ids";
	public final static String INTENT_EXTRA_AUTHORITIES = "authority";
	
	public final static String LOGO_PICTURE_URL = "http://lh5.ggpht.com/hRTJJv7H9dpLXhHTTqiiNY2DD2wWO0hZFWEWPv1g-WArcUYLsWk-aQYUS0UgZfVIqtXm=w124";
	
	public final static String TAG = "EmailUnclaimedMembersActivity";
	
	private Uri mFeedUri;
	private Handler mHandler;
	private Intent mIntent;
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mHandler = new Handler();
		mIntent = getIntent();
		mFeedUri = mIntent.getParcelableExtra(INTENT_EXTRA_FEED_URI);

		final int[] authorities = mIntent.getIntArrayExtra(INTENT_EXTRA_AUTHORITIES);
		final String recipients[] = mIntent.getStringArrayExtra(android.content.Intent.EXTRA_BCC);

		// 	for debug 
//		final String recipients[]={"574632066","640321536"};
//		authorities[0] = Authority.Facebook.ordinal();
		
		Fragment memberView = new EmailUnclaimedMembersFragment();

		Bundle args = new Bundle();
		args.putParcelable("feed_uri", mFeedUri);

		memberView.setArguments(args);

		setContentView(R.layout.activity_email_unclaimed_member_list);

		Button sendButton = (Button) findViewById(R.id.send_unclaimed_email);
		Button cancelButton = (Button) findViewById(R.id.cancel_unclaimed_email);
		
		sendButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				//TODO: need to support different invitations for different providers
				if(authorities[0] == Authority.Email.ordinal()) {
					final String subject = "You have a new Musubi message!";
					final String body = message + " " + MUSUBI_MARKET_URL;
					Intent send = new Intent(Intent.ACTION_SENDTO);
					StringBuilder recipientsString = new StringBuilder();
					// only add emails
					for(int i = 0; i < recipients.length; i++) {
						if(authorities[i] == Authority.Email.ordinal()) {
							recipientsString.append(recipients[i]).append(",");
						}
					}
					recipientsString.deleteCharAt(recipientsString.length()-1);
					String uriText;
					uriText = "mailto:" + recipientsString.toString() +
					"?subject=" + subject + 
					"&body=" + body;
					//TODO: real url encoding?
					uriText = uriText.replace(" ", "%20");
					Uri uri = Uri.parse(uriText);
					send.setData(uri);
					startActivity(Intent.createChooser(send, "Send invitation..."));

					MIdentity[] identities = markAsHasSent(mIntent);
					//let other people in the feed know that spamming is unnecessary
					if(mFeedUri != null) {
						Obj invitedObj = OutOfBandInvitedObj.from(Arrays.asList(identities).iterator());
						Helpers.sendToFeed(EmailUnclaimedMembersActivity.this, invitedObj, mFeedUri);
					}

					EmailUnclaimedMembersActivity.this.finish();
				} else if(authorities[0] == Authority.Facebook.ordinal()) {
					Facebook fb = AccountLinkDialog.getFacebookInstance(EmailUnclaimedMembersActivity.this);
//					AsyncFacebookRunner asyncRunner = new AsyncFacebookRunner(fb);
//
		    		if(fb.isSessionValid()) {
//		    			// TODO: batch request in json array. currently facebook limits 50 requests per batch
//		    			// need to split up it if it's more than 50
//		    			final String fbmsg = new StringBuilder()
//		    				.append("message=").append(message)
//		    				.append("&link=").append(link)
//		    				.append("&picture=").append(LOGO_PICTURE_URL).toString();
//		    			
//		    			JSONArray batchObj = new JSONArray();
//		    			try {
//		    				for(String id : recipients) {
//		        				JSONObject post = new JSONObject();
//		        				post.put("method", "POST");
//		        				post.put("relative_url", id+"/feed");
//		        				post.put("body", fbmsg);
//		        				batchObj.put(post);
//		        			}
//		    			} catch (JSONException e) {
//		    				Log.e(TAG, e.toString());
//		    			}
//		    			Bundle batch = new Bundle();
//		    			batch.putString("batch", batchObj.toString());
//		    			asyncRunner.request("/", batch, "POST", new FriendRequestListener(), null);
		    			
		    			StringBuilder recipientsString = new StringBuilder();
						// only add fb ids
						for(int i = 0; i < recipients.length; i++) {
							if(authorities[i] == Authority.Facebook.ordinal()) {
								recipientsString.append(recipients[i]).append(",");
							}
						}
						recipientsString.deleteCharAt(recipientsString.length()-1);
		    			Bundle params = new Bundle();
		    			params.putString("message", message);
		    			params.putString("to", recipientsString.toString());
		    			fb.dialog(EmailUnclaimedMembersActivity.this, "apprequests", params, new AppRequestDialogListener());

		    		}
				}
			}
		});

		cancelButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				//if i say no, i mean NO!
				markAsHasSent(mIntent);
				EmailUnclaimedMembersActivity.this.finish();
			}
		});

		getSupportFragmentManager().beginTransaction()
		.replace(R.id.member_list, memberView).commit();

	}

	private MIdentity[] markAsHasSent(final Intent mIntent) {
		SQLiteOpenHelper dbhelper = App.getDatabaseSource(EmailUnclaimedMembersActivity.this);
		IdentitiesManager manager = new IdentitiesManager(dbhelper);
		long ids[] = mIntent.getLongArrayExtra(INTENT_EXTRA_RECIPIENT_IDS);
		MIdentity identities[] = manager.getIdentitiesForIds(ids);
		for (int i = 0; i < identities.length; i++) {
			identities[i].hasSentEmail_ = true;
			manager.updateIdentity(identities[i]);
		}
		return identities;
	}
	
	public class AppRequestDialogListener implements DialogListener {

		@Override
		public void onComplete(Bundle values) {
			Log.i(TAG, values.toString());
			MIdentity[] identities = markAsHasSent(mIntent);
			//let other people in the feed know that spamming is unnecessary
			if(mFeedUri != null) {
				Obj invitedObj = OutOfBandInvitedObj.from(Arrays.asList(identities).iterator());
				Helpers.sendToFeed(EmailUnclaimedMembersActivity.this, invitedObj, mFeedUri);
			}

			EmailUnclaimedMembersActivity.this.finish();
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
			EmailUnclaimedMembersActivity.this.finish();
		}
	}
		
	
	public class FriendRequestListener implements RequestListener {

		@Override
		public void onComplete(String response, Object state) {
			showToast("Invitations posted to friends' wall");
			Log.i(TAG, response);
			EmailUnclaimedMembersActivity.this.finish();
		}

		@Override
		public void onIOException(IOException e, Object state) {
			Log.e(TAG, e.toString());
		}

		@Override
		public void onFileNotFoundException(FileNotFoundException e,
				Object state) {
			Log.e(TAG, e.toString());
		}

		@Override
		public void onMalformedURLException(MalformedURLException e,
				Object state) {
			Log.e(TAG, e.toString());
		}

		@Override
		public void onFacebookError(FacebookError e, Object state) {
			Log.e(TAG, e.toString());
		}
		
	}
	
    public void showToast(final String msg) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast toast = Toast.makeText(EmailUnclaimedMembersActivity.this, msg, Toast.LENGTH_LONG);
                toast.show();
            }
        });
    }


}