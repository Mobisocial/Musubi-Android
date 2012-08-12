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

import java.io.IOException;

import mobisocial.crypto.IBHashedIdentity;
import mobisocial.metrics.MusubiMetrics;
import mobisocial.musubi.App;
import mobisocial.musubi.R;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.objects.PictureObj;
import mobisocial.musubi.service.MusubiService;
import mobisocial.musubi.service.WizardStepHandler;
import mobisocial.musubi.ui.EmailInviteActivity;
import mobisocial.musubi.ui.MusubiBaseActivity;
import mobisocial.musubi.ui.ViewProfileActivity;
import mobisocial.musubi.ui.util.AddToWhitelistListener;
import mobisocial.musubi.ui.util.EmojiSpannableFactory;
import mobisocial.musubi.ui.util.UiUtil;
import mobisocial.musubi.util.UriImage;
import mobisocial.socialkit.Obj;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents.Insert;
import android.provider.ContactsContract.QuickContact;
import android.support.v4.app.Fragment;
import android.support.v4.app.SupportActivity;
import android.text.Editable;
import android.text.Spannable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class ViewProfileFragment extends Fragment {
    private ImageView mThumbnail;
    private TextView mProfileName, mProfilePrincipal;
    private TextView mEditProfileNameButton, mAddToAddressBook;
    //private TextView mProfileEmail;
    //private TextView mProfileAbout;
	private Activity mActivity;
	
	MIdentity mIdent;
	private ContentObserver mObserver;
	private IdentitiesManager mIdentitiesManager;
	private long mId;
	private TextView mAddToFriends;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SQLiteOpenHelper helper = App.getDatabaseSource(mActivity);
        mIdentitiesManager = new IdentitiesManager(helper);
        mId = this.getArguments().getLong(ViewProfileActivity.PROFILE_ID);
        
        
        mIdent = mIdentitiesManager.getIdentityWithThumbnailsForId(mId);
        
        //TODO: some of these can be "deduped" but some can't e.g. we depend on the my profile updated
        //ones to refresh the ui after a change happens.  for now, just don't leave musubi open to the profile screen
        //while your gettign a huge blast of changes
        mObserver = new ContentObserver(new Handler(mActivity.getMainLooper())) {
        	@Override
        	public void onChange(boolean arg0) {
        		updateDynamicElements();
        	}
		};
    }
    

    @Override
    public void onResume() {
        super.onResume();
        updateDynamicElements();

        if (mIdent.owned_) {
			mActivity.getContentResolver().registerContentObserver(MusubiService.MY_PROFILE_UPDATED, false, mObserver);
        } else {
			mActivity.getContentResolver().registerContentObserver(MusubiService.PRIMARY_CONTENT_CHANGED, false, mObserver);
        }
		mActivity.getContentResolver().registerContentObserver(MusubiService.WHITELIST_APPENDED, false, mObserver);
		mActivity.getContentResolver().registerContentObserver(MusubiService.COLORLIST_CHANGED, false, mObserver);
    	mObserver.dispatchChange(false);
    }

    @Override
    public void onPause() {
        super.onPause();
    	mActivity.getContentResolver().unregisterContentObserver(mObserver);
    }

    
    @Override
    public void onDestroy() {
    	super.onDestroy();
    }
    
    public static ViewProfileFragment newInstance(Long id) {
    	ViewProfileFragment frag = new ViewProfileFragment();
        Bundle args = new Bundle();
        args.putLong(ViewProfileActivity.PROFILE_ID, id);
        frag.setArguments(args);
        return frag;
    }
    
	@Override
    public void onAttach(SupportActivity activity) {
        super.onAttach(activity);
    	mActivity = activity.asActivity();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
    	View v = inflater.inflate(R.layout.view_profile, container, false);

    	Spinner presence = (Spinner) v.findViewById(R.id.view_profile_presence);
        presence.setVisibility(View.GONE);
        
        mThumbnail = (ImageView) v.findViewById(R.id.view_profile_thumbnail);
        if (mIdent.owned_) {
        	//show the person that they need to set their own musubi thumbnail if necessary
        	if(!mIdentitiesManager.hasMusubiThumbnail(mIdent)) {
        		mThumbnail.setImageResource(R.drawable.ic_set_profile_picture);
        	} else {
        		mThumbnail.setImageBitmap(UiUtil.safeGetContactPicture(mActivity, mIdentitiesManager, mIdent));
        	}
        } else {
        	//always just use the best available thumbnail for a friend
    		mThumbnail.setImageBitmap(UiUtil.safeGetContactPicture(mActivity, mIdentitiesManager, mIdent));
        }
        mThumbnail.setOnLongClickListener(mCopyThumbnailToClipboard);
        mThumbnail.setOnClickListener(mThumbnailClickListener);

    	mProfileName = (TextView) v.findViewById(R.id.view_profile_name);
    	Spannable span = EmojiSpannableFactory.getInstance(mActivity).newSpannable(UiUtil.safeNameForIdentity(mIdent));
    	mProfileName.setText(span);

		mProfilePrincipal = (TextView) v.findViewById(R.id.view_profile_principal);
    	
        mEditProfileNameButton = (TextView) v.findViewById(R.id.view_edit_profile_name);
    	if (mIdent.owned_) {
    		mThumbnail.setOnClickListener(new ThumbnailOnClickListener());
    		mEditProfileNameButton.setOnClickListener(new EditNameOnClickListener());
    		mProfilePrincipal.setVisibility(View.GONE);
    	} else {
    		mEditProfileNameButton.setVisibility(View.GONE);
    		String email = UiUtil.safePrincipalForIdentity(mIdent);
	        if (email != null) {
	            // TODO: Fearful of the pushiness. On hold.
	            v.findViewById(R.id.invite_over_email).setVisibility(View.GONE);
	            v.findViewById(R.id.invite_over_email).setOnClickListener(mEmailListener);
	            mProfilePrincipal.setText(email);
	        }
    	}
    	
    	
    	mAddToFriends = (TextView) v.findViewById(R.id.add_to_friends);

    	//TODO: android contact sync can change this state
    	mAddToAddressBook = (TextView) v.findViewById(R.id.add_to_addressbook);
    	updateDynamicElements();
        
        return v;
    }

    View.OnClickListener mEmailListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            App.getUsageMetrics(getActivity()).report(MusubiMetrics.CLICKED_TO_INVITE);
            String email = UiUtil.safePrincipalForIdentity(mIdent);
            Intent intent = EmailInviteActivity.getInviteIntentForEmail(getActivity(), email);
            startActivity(intent);
        }
    };
	void updateDynamicElements() {
		mIdent = mIdentitiesManager.getIdentityForId(mId);
		if(mIdent.owned_ && !mIdentitiesManager.hasMusubiThumbnail(mIdent)) {
    		mThumbnail.setImageResource(R.drawable.ic_set_profile_picture);
    	} else {
    		mThumbnail.setImageBitmap(UiUtil.safeGetContactPicture(mActivity, mIdentitiesManager, mIdent));
    	}
		Spannable span = EmojiSpannableFactory.getInstance(mActivity).newSpannable(UiUtil.safeNameForIdentity(mIdent));
		mProfileName.setText(span);
		if (shouldShowAddToFriends()) {
    		mAddToFriends.setVisibility(View.VISIBLE);
    		mAddToFriends.setOnClickListener(new AddToWhitelistListener(mActivity, mIdent));
    	}
    	else {
    		mAddToFriends.setVisibility(View.GONE);
    	}
    	if (shouldShowAddToAddressBook()) {
    		mAddToAddressBook.setVisibility(View.VISIBLE);
    		mAddToAddressBook.setOnClickListener(new AddToAddressBookListener());
    	}
    	else {
    		mAddToAddressBook.setVisibility(View.GONE);
    	}
	}

	boolean shouldShowAddToFriends() {
		//not owned is only necessary because our bootstrap identity is not whitelisted
		//and the view profile will show that profile
		return !mIdent.whitelisted_ && !mIdent.owned_;
	}

	boolean shouldShowAddToAddressBook() {
		return mIdent.whitelisted_ 
			&& mIdent.androidAggregatedContactId_ == null 
			&& mIdent.type_ == IBHashedIdentity.Authority.Email 
			&& mIdent.principal_ != null;
	}

    
    private class AddToAddressBookListener implements OnClickListener {
		@Override
		public void onClick(View v) {
			Intent i = new Intent(Intent.ACTION_INSERT_OR_EDIT);
            i.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
            
            if(mIdent.musubiName_ != null) {
         	   i.putExtra(Insert.NAME, mIdent.musubiName_);
            }
            if (mIdent.type_ == IBHashedIdentity.Authority.Email) {
         	   i.putExtra(Insert.EMAIL, UiUtil.safePrincipalForIdentity(mIdent));
            }
            mActivity.startActivity(i);
		}
    }
        
    private class ThumbnailOnClickListener implements OnClickListener {
		@Override
		public void onClick(View v) {
		    ChooseImageDialog spd = ChooseImageDialog.newInstance();
		    spd.setTargetFragment(ViewProfileFragment.this, ChooseImageDialog.REQUEST_PROFILE_PICTURE);
			((MusubiBaseActivity)getActivity()).showDialog(spd);
		}
    }

    private class EditNameOnClickListener implements OnClickListener {
		@Override
		public void onClick(View v) {
			AlertDialog.Builder alert = new AlertDialog.Builder(mActivity);

    		alert.setTitle("Change your name");

    		// Set an EditText view to get user input 
    		final EditText input = new EditText(mActivity);
    		
        	Spannable span = EmojiSpannableFactory.getInstance(mActivity).newSpannable(UiUtil.safeNameForIdentity(mIdent));
    		input.setText(span);
    		input.addTextChangedListener(new TextWatcher() {
				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {
				}
				
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count,
						int after) {
				}
				
				@Override
				public void afterTextChanged(Editable s) {
			    	EmojiSpannableFactory.getInstance(mActivity).updateSpannable(s);
				}
			});
    		alert.setView(input);

    		alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
        		public void onClick(DialogInterface dialog, int whichButton) {
        			String newName = input.getText().toString();
        			SQLiteOpenHelper helper = App.getDatabaseSource(mActivity);
        			IdentitiesManager manager = new IdentitiesManager(helper);
        			mIdent.musubiName_ = newName;
        			manager.updateMyProfileName(mActivity, newName, true);
                	WizardStepHandler.accomplishTask(mActivity, WizardStepHandler.TASK_SET_PROFILE_NAME);
                	App.getUsageMetrics(mActivity).report(MusubiMetrics.PROFILE_NAME_UPDATED);
    		  	}
    		});

    		alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
    			public void onClick(DialogInterface dialog, int whichButton) {
    		    }
    		});
    		
    		final AlertDialog dialog = alert.create();
    		
    		input.setOnFocusChangeListener(new View.OnFocusChangeListener() {
    		    @Override
    		    public void onFocusChange(View v, boolean hasFocus) {
    		        if (hasFocus) {
    		        	((EditText)v).selectAll();
    		            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    		        }
    		    }
    		});

    		dialog.show();
			
		}
    }

    View.OnLongClickListener mCopyThumbnailToClipboard = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            IdentitiesManager im = new IdentitiesManager(App.getDatabaseSource(getActivity()));
            MIdentity person = im.getIdentityWithThumbnailsForId(mId);
            byte[] data = person.musubiThumbnail_;
            if (data == null) {
                data = person.thumbnail_;
            }
            if (data != null) {
                Obj obj = PictureObj.from(data);
                new ClipboardKeeper(getActivity()).store(obj, false);
                Toast.makeText(getActivity(), "Copied thumbnail to clipboard.",
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(getActivity(), "No thumbnail to copy.",
                        Toast.LENGTH_LONG).show();
            }
            return true;
        }
    };

    View.OnClickListener mThumbnailClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Context context = getActivity();
            String lookupKey = IdentitiesManager.androidLookupKeyForIdentitiy(context, mIdent);
            if (lookupKey == null) {
                return;
            }
            Uri lookupUri = Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey);
            QuickContact.showQuickContact(context, v, lookupUri, QuickContact.MODE_LARGE, null);
        }
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ChooseImageDialog.REQUEST_PROFILE_PICTURE) {
            if (resultCode == Activity.RESULT_OK) {
                byte[] thumbnail = data.getByteArrayExtra(ChooseImageDialog.EXTRA_THUMBNAIL);
                setThumbnail(thumbnail);
            }
        }
        if (requestCode == ChooseImageDialog.REQUEST_GALLERY_THUMBNAIL) {
            if (resultCode == Activity.RESULT_OK) {
                new ThumbnailTask().execute(data.getData());
            }
        }
    };

    /**
     * Prepares a byte[] from an image uri in the background and sets
     * it on the main thread.
     *
     */
    class ThumbnailTask extends AsyncTask<Uri, Void, byte[]> {
        @Override
        protected byte[] doInBackground(Uri... params) {
            try {
                UriImage image = new UriImage(getActivity(), params[0]);
                return image.getResizedImageData(300, 300, 20*1024, true);
            } catch (IOException e) {
                Toast.makeText(getActivity(), "Error getting picture.", Toast.LENGTH_LONG).show();
            }
            return null;
        }

        @Override
        protected void onPostExecute(byte[] result) {
            // triggers a toast in WizardStepHandler, requires looper.
            setThumbnail(result);
        }
    }

    void setThumbnail(byte[] thumbnail) {
        SQLiteOpenHelper helper = App.getDatabaseSource(getActivity());
        IdentitiesManager manager = new IdentitiesManager(helper);
        manager.updateMyProfileThumbnail(getActivity(), thumbnail, true);
        mIdent.thumbnail_ = thumbnail;
        WizardStepHandler.accomplishTask(mActivity, WizardStepHandler.TASK_SET_PROFILE_PICTURE);
        App.getUsageMetrics(mActivity).report(MusubiMetrics.PROFILE_PICTURE_UPDATED);
    }
}