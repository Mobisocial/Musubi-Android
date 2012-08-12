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

import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;

import mobisocial.metrics.MusubiMetrics;
import mobisocial.musubi.App;
import mobisocial.musubi.Helpers;
import mobisocial.musubi.MembersActivity.FeedMembersFragment.FeedMembersCursorLoader;
import mobisocial.musubi.R;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.helpers.DatabaseManager;
import mobisocial.musubi.model.helpers.FeedManager;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.model.helpers.MyAccountManager;
import mobisocial.musubi.nearby.GpsBroadcastTask;
import mobisocial.musubi.objects.FeedNameObj;
import mobisocial.musubi.objects.IntroductionObj;
import mobisocial.musubi.ui.util.EmojiSpannableFactory;
import mobisocial.musubi.ui.util.UiUtil;
import mobisocial.musubi.ui.widget.ActionBarLite;
import mobisocial.musubi.ui.widget.MultiIdentitySelector;
import mobisocial.musubi.ui.widget.MultiIdentitySelector.OnRequestAddIdentityListener;
import mobisocial.musubi.util.CommonLayouts;
import mobisocial.musubi.util.InstrumentedActivity;
import mobisocial.musubi.util.PhotoTaker;
import mobisocial.musubi.util.UriImage;
import mobisocial.socialkit.Obj;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Intents.Insert;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.SupportActivity;
import android.support.v4.content.Loader;
import android.text.Editable;
import android.text.Spannable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class FeedDetailsActivity extends MusubiBaseActivity implements OnItemClickListener,
LoaderManager.LoaderCallbacks<Cursor> {
	DatabaseManager mDb;

    private static final int sDeletedColor = Color.parseColor("#66FF3333");
	boolean mDetailsChanged = false;
	byte[] mThumbnailBytes;
	ImageView mThumbnailView;
	EditText mNameEditText;
	EditText mBroadcastPassword;
	ListView mFeedMembersView;
	Context mContext;

    IdentitiesManager mIdentitiesManager;
    ContactListCursorAdapter mContacts;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mContext = this;
		setContentView(R.layout.feed_details);
		setTitle("Feed Details");
		mDb = new DatabaseManager(this);

        SQLiteOpenHelper helper = App.getDatabaseSource(this);
        mIdentitiesManager = new IdentitiesManager(helper);
		mContacts = new ContactListCursorAdapter(this, null);
		
		mFeedMembersView = (ListView) findViewById(R.id.feed_details_members_list);
		LayoutInflater inflater = getLayoutInflater();
		ViewGroup header = (ViewGroup)inflater.inflate(R.layout.feed_details_header, mFeedMembersView, false);
		mFeedMembersView.addHeaderView(header, null, false);
		mFeedMembersView.setTextFilterEnabled(true);
        mFeedMembersView.setFastScrollEnabled(true);
        mFeedMembersView.setOnItemClickListener(this);
        mFeedMembersView.setCacheColorHint(Color.WHITE);
        mFeedMembersView.setAdapter(mContacts);
		
		mNameEditText = (EditText)header.findViewById(R.id.feed_title_edittext);
		mBroadcastPassword = (EditText)header.findViewById(R.id.broadcast_password);
		mThumbnailView = (ImageView)header.findViewById(R.id.icon);

		String name = null;
		if (savedInstanceState != null) {
			name = savedInstanceState.getString("name");
			mThumbnailBytes = savedInstanceState.getByteArray("thumbnailBytes");
			mDetailsChanged = savedInstanceState.getBoolean("detailsChanged");
		} else {
			Long feedId = Long.parseLong(getIntent().getData().getLastPathSegment());
			MFeed feed = mDb.getFeedManager().lookupFeed(feedId);
			name = UiUtil.getFeedNameFromMembersList(mDb.getFeedManager(), feed);
			mThumbnailBytes = mDb.getFeedManager().getFeedThumbnailForId(feedId);
		}
		Spannable span = EmojiSpannableFactory.getInstance(this).newSpannable(name);
		mNameEditText.setText(span);
		mNameEditText.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				mDetailsChanged = true;
				refreshUI();
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
				
			}

			@Override
			public void afterTextChanged(Editable s) {
		    	EmojiSpannableFactory.getInstance(mContext).updateSpannable(s);
			}
		});
		
		getSupportLoaderManager().initLoader(0, null, this);
		refreshUI();
	}
	
	public void onClickAddMembers(View v) {
		((InstrumentedActivity)this).showDialog(
                AddPeopleDialog.newInstance(getIntent().getData()));
	}

	public void onClickIcon(View v) {
		SelectImageDialogFragment sidf = SelectImageDialogFragment.newInstance();
		showDialog(sidf);
	}

	public void onClickBroadcast(View v) {
        long id = Long.parseLong(getIntent().getData().getLastPathSegment());
        FeedManager fm = new FeedManager(App.getDatabaseSource(this));
        final MFeed f = fm.lookupFeed(id);
        
        String provider = Settings.Secure.getString(this.getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
		if (provider == null || !provider.contains("gps") || !provider.contains("network")) { 
			new AlertDialog.Builder(this)
				.setTitle("Location Settings")
				.setMessage("You should enable both network-based and GPS-based location services to ensure your friends can find your groups.")
				.setNegativeButton("Share Anyway", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						new GpsBroadcastTask(mContext, f, mBroadcastPassword.getText().toString()).execute();
					}
				})
				.setPositiveButton("Fix Settings First", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						try {
							 Intent myIntent = new Intent( Settings.ACTION_LOCATION_SOURCE_SETTINGS);
							 startActivity(myIntent);
						} catch(Throwable t) { Log.e(TAG, "failed to launch location settings", t);}
					}
				}).show();
		} else {
			new GpsBroadcastTask(mContext, f, mBroadcastPassword.getText().toString()).execute();
		}
	}

	@Override
	public void onBackPressed() {
		onClickSave(null);
	}

	public void onClickSave(View v) {
		if (mDetailsChanged) {
			String name = mNameEditText.getText().toString();
			Obj obj = FeedNameObj.from(name, mThumbnailBytes);
			Helpers.sendToFeed(this, obj, getIntent().getData());
		}
		finish();
	}

	public void onClickCancel(View v) {
		finish();
	}

	public void refreshUI() {
		if (mDetailsChanged) {
			findViewById(R.id.submit).setVisibility(View.VISIBLE);
			findViewById(R.id.cancel).setVisibility(View.VISIBLE);
		} else {
			findViewById(R.id.submit).setVisibility(View.GONE);
			findViewById(R.id.cancel).setVisibility(View.GONE);
		}

		if (mThumbnailBytes != null) {
			Bitmap bitmap = BitmapFactory.decodeByteArray(mThumbnailBytes, 0, mThumbnailBytes.length);
			mThumbnailView.setImageBitmap(bitmap);
		}
		else {
			mThumbnailView.setImageResource(R.drawable.group_icon);
		}
	}


	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putString("name", mNameEditText.getText().toString());
		outState.putByteArray("thumbnailBytes", mThumbnailBytes);
		outState.putBoolean("detailsChanged", mDetailsChanged);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode != RESULT_OK) {
			return;
		}
		if (SelectImageDialogFragment.isImageRequest(requestCode)) {
			new SelectImageDialogFragment.ThumbnailAsyncTask(this, requestCode, data) {
				protected void onPreExecute() {
					
				};
	
				@Override
				protected void onThumbnailResult(byte[] imageBytes) {
					if (imageBytes != null) {
						mThumbnailBytes = imageBytes;
						mDetailsChanged = true;
						refreshUI();
					} else {
						toast("Failed to load image");
					}
				};
			}.execute();
		}
	}

	public static class SelectImageDialogFragment extends DialogFragment 
			implements DialogInterface.OnClickListener {
		public static final int REQUEST_CAMERA_IMAGE = 58;
		public static final int REQUEST_GALLERY_IMAGE = 59;

		static final int SOURCE_CAMERA = 0;
		static final int SOURCE_GALLERY = 1;

		public static SelectImageDialogFragment newInstance() {
			Bundle args = new Bundle();
			SelectImageDialogFragment sidf = new SelectImageDialogFragment();
			sidf.setArguments(args);
			return sidf;
		}

		public static boolean isImageRequest(int requestCode) {
			return (requestCode == REQUEST_CAMERA_IMAGE || requestCode == REQUEST_GALLERY_IMAGE);
		}

		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			String[] sources = new String[] { "From Camera", "From Gallery" };
			return new AlertDialog.Builder(getActivity())
				.setTitle("Choose an image...")
				.setItems(sources, this)
				.create();
		}

		@Override
		public void onClick(DialogInterface dialog, int which) {
			switch (which) {
			case SOURCE_CAMERA:
				final Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
				intent.putExtra(MediaStore.EXTRA_OUTPUT,
		                        Uri.fromFile(PhotoTaker.getTempFile(getActivity())));
				getActivity().startActivityForResult(intent, REQUEST_CAMERA_IMAGE);
				break;
			case SOURCE_GALLERY:
				Intent gallery = new Intent(Intent.ACTION_GET_CONTENT);
				gallery.setType("image/*");
				getActivity().startActivityForResult(gallery, REQUEST_GALLERY_IMAGE);
				break;
			}
		}

		public static abstract class ThumbnailAsyncTask extends AsyncTask<Void, Void, byte[]> {
			final Context mContext;
			final int mRequestCode;
			final Intent mData;

			public ThumbnailAsyncTask(Context context, int requestCode, Intent data) {
				mContext = context;
				mRequestCode = requestCode;
				mData = data;
			}

	        @Override
	        protected byte[] doInBackground(Void... params) {
	        	Uri imageUri;
	        	switch (mRequestCode) {
				case REQUEST_CAMERA_IMAGE:
					File imageFile = PhotoTaker.getTempFile(mContext);
					imageUri = Uri.fromFile(imageFile);
					break;
				case REQUEST_GALLERY_IMAGE:
					if (mData != null) {
						imageUri = mData.getData();
					} else {
						imageUri = null;
					}
					break;
				default:
					imageUri = null;
				}
	        	if (imageUri != null) {
	        		UriImage uriImage = new UriImage(mContext, imageUri);
					try {
						return uriImage.getResizedImageData(300, 300, 20*1024, true);
					} catch (IOException e) {
						Log.e(TAG, "Error decoding image", e);
						return null;
					}
	        	} else {
	        		Log.e(TAG, "bad request code");
	        		return null;
	        	}
	        }

	        @Override
	        protected final void onPostExecute(byte[] result) {
	        	onThumbnailResult(result);
	        }

	        protected abstract void onThumbnailResult(byte[] imageBytes);
	    }
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		refreshUI();
	}

	@Override
    public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
        long feedId = Long.parseLong(getIntent().getData().getLastPathSegment());
        return new FeedMembersCursorLoader(this, feedId);
    }
    
    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
    	mContacts.changeCursor(cursor);
    }
    
    @Override
    public void onLoaderReset(Loader<Cursor> arg0) {
    
    }

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id){
		Cursor cursor = (Cursor)mContacts.getItem(position-1);
        long identityId = cursor.getLong(cursor.getColumnIndexOrThrow(MIdentity.COL_ID));
        Intent intent = new Intent(this, ViewProfileActivity.class);
        intent.putExtra(ViewProfileActivity.PROFILE_ID, identityId);
        this.startActivity(intent);
	}
	
	private class ContactListCursorAdapter extends CursorAdapter {
        public ContactListCursorAdapter (Context context, Cursor c) {
            super(context, c);
        }
    
        @Override
        public View newView(Context context, Cursor c, ViewGroup parent) {
            final LayoutInflater inflater = LayoutInflater.from(context);
            View v = inflater.inflate(R.layout.contacts_item, parent, false);
            bindView(v, context, c);
            return v;
        }
    
        @Override
        public void bindView(View v, Context context, Cursor cursor) {
            TextView unreadCount = (TextView)v.findViewById(R.id.unread_count);
            unreadCount.setVisibility(View.GONE);
            TextView nameText = (TextView) v.findViewById(R.id.name_text);
            TextView statusText = (TextView) v.findViewById(R.id.status_text);
            TextView principalText = (TextView) v.findViewById(R.id.principal_text);
            final ImageView icon = (ImageView)v.findViewById(R.id.icon);
            final ImageView presenceIcon = (ImageView)v.findViewById(R.id.presence_icon);
            presenceIcon.setVisibility(View.GONE);
            final ImageView nearbyIcon = (ImageView)v.findViewById(R.id.nearby_icon);
            nearbyIcon.setVisibility(View.GONE);
            final ImageView more = (ImageView)v.findViewById(R.id.more);
    
            long identityId = cursor.getLong(0);
            final MIdentity member = mIdentitiesManager.getIdentityForId(identityId);
            if(member == null) {
                unreadCount.setVisibility(View.INVISIBLE);
                nameText.setText("Missing contact data...");
                statusText.setText("");
                icon.setImageResource(R.drawable.ic_contact_picture);
                return;
            }
            
    
            nameText.setText(UiUtil.safeNameForIdentity(member));
            principalText.setText(UiUtil.safePrincipalForIdentity(member));
            icon.setImageBitmap(UiUtil.safeGetContactThumbnail(context, mIdentitiesManager, member));
    
            //nearbyIcon.setVisibility(c.nearby ? View.VISIBLE : View.GONE);
            more.setVisibility(View.GONE);

            ImageView musubiEnabled = (ImageView)v.findViewById(R.id.musubi_enabled);
            ImageView ignoredOverlay = (ImageView) v.findViewById(R.id.ignored_overlay);
            if (member.claimed_) {
            	ignoredOverlay.setVisibility(View.GONE);
                nameText.setTextColor(Color.BLACK);
                principalText.setTextColor(Color.BLACK);
            	musubiEnabled.setBackgroundResource(R.drawable.musubi_enabled);
            }
            else {
            	ignoredOverlay.setVisibility(View.GONE);
                nameText.setTextColor(Color.BLACK);
                principalText.setTextColor(Color.BLACK);
            	musubiEnabled.setBackgroundResource(R.drawable.musubi_disabled);
            }
            if(member.blocked_) {
                nameText.setTextColor(sDeletedColor);
                principalText.setTextColor(sDeletedColor);
                ignoredOverlay.setVisibility(View.VISIBLE);
            }
        }
    }
	
	public static class AddPeopleDialog extends DialogFragment {
        private static final int REQUEST_ADD_CONTACT = 0;
        MultiIdentitySelector mmIdentitySelector;
        Activity mActivity;
        
        public static AddPeopleDialog newInstance(Uri feedUri) {
            AddPeopleDialog f = new AddPeopleDialog();
            Bundle args = new Bundle();
            args.putParcelable("feedUri", feedUri);
            f.setArguments(args);
            return f;
        }

        @Override
        public void onAttach(SupportActivity activity) {
            super.onAttach(activity);
            mActivity = activity.asActivity();
            mActivity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        }

        @Override
        public void onDetach() {
            super.onDetach();
            mActivity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setStyle(STYLE_NO_TITLE, R.style.Theme_D1tranlucent);
        }
        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if(requestCode == REQUEST_ADD_CONTACT) {
                if(resultCode == Activity.RESULT_OK) {
                    UiUtil.addedContact(mActivity, data, mmIdentitySelector);
                }
            }
        }
        private OnRequestAddIdentityListener mOnRequestAddIdentityListener = new OnRequestAddIdentityListener() {
            @Override
            public void onRequestAddIdentity(String enteredText) {
                Intent i = new Intent(Intent.ACTION_INSERT_OR_EDIT);
                i.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
                if(enteredText != null) {
                    Pattern emailPattern = Pattern.compile("\\b[A-Z0-9._%-]+@[A-Z0-9.-]+\\.[A-Z]{2,4}\\b", Pattern.CASE_INSENSITIVE);
                    if (emailPattern.matcher(enteredText).matches()) {
                        i.putExtra(Insert.EMAIL, enteredText);
                    } else {
                        i.putExtra(Insert.NAME, enteredText);
                    }
                }
                startActivityForResult(i, REQUEST_ADD_CONTACT);
            }
        };

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Framework builds dialog with view defined in onCreateView.
            Dialog dialog = super.onCreateDialog(savedInstanceState);
            dialog.getWindow().getAttributes().windowAnimations = R.style.Animation_SlideFromTopHalfHeight;
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            return dialog;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            Context context = getActivity();

            LinearLayout v = new LinearLayout(context);
            v.setMinimumWidth(500);
            v.setLayoutParams(CommonLayouts.FULL_WIDTH);
            v.setOrientation(LinearLayout.VERTICAL);
            v.setOnClickListener(new OnClickListener() {                
                @Override
                public void onClick(View v) {
                    dismiss();
                }
            });

            RelativeLayout.LayoutParams params;
            /////////////
            // Action bar
            /////////////
            ActionBarLite actionBar = new ActionBarLite(context);
            actionBar.setId(R.id.name);
            actionBar.setTitle("Add People");
            v.addView(actionBar);

            ////
            // Custom action bar area
            ////
            RelativeLayout contentFrame = new RelativeLayout(context);
            contentFrame.setLayoutParams(CommonLayouts.FULL_WIDTH);
            contentFrame.setBackgroundDrawable(actionBar.getBackground());

            // "Add" button
            Button b = new Button(context);
            b.setText(R.string.add);
            b.setOnClickListener(mmGoListener);
            params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT);
            params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            params.addRule(RelativeLayout.BELOW, R.id.name);
            b.setLayoutParams(params);
            b.setId(R.id.add_contact);
            contentFrame.addView(b);

            // Multi-select textbox
            mmIdentitySelector = new MultiIdentitySelector(context);
            params = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.FILL_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
            params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            params.addRule(RelativeLayout.LEFT_OF, R.id.add_contact);
            params.addRule(RelativeLayout.BELOW, R.id.name);
            mmIdentitySelector.setLayoutParams(params);
            mmIdentitySelector.setId(R.id.people);
            contentFrame.addView(mmIdentitySelector);
            mmIdentitySelector.setOnRequestAddIdentityListener(mOnRequestAddIdentityListener);

            int sz =  40;
            sz = (int)(sz * getResources().getDisplayMetrics().density / 160);

            actionBar.getCustomBar().addView(contentFrame);
            return v;
        }
        
        
        //TODO: this code makes pretty poor use for local vars/member vars to making it clear
        //and not wasteful
        final View.OnClickListener mmGoListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mmIdentitySelector.getSelectedIdentities().size() == 0) {
                    dismiss();
                    return;
                }

                Uri feedUri = getArguments().getParcelable("feedUri");
                long feedId = Long.parseLong(feedUri.getLastPathSegment());
                FeedManager fm = new FeedManager(App.getDatabaseSource(mActivity));
                for (MIdentity id : mmIdentitySelector.getSelectedIdentities()) {
                    fm.ensureFeedMember(feedId, id.id_);
                }
                UiUtil.addToWhitelistsIfNecessary(fm, new MyAccountManager(App.getDatabaseSource(mActivity)), fm.getFeedMembers(fm.lookupFeed(feedId)), true);

                // Send an invisble feed object to force the feed to detect new members
                // and recognize their names and email addresses.
                Obj invitedObj = IntroductionObj.from(mmIdentitySelector.getSelectedIdentities(), true);
                Helpers.sendToFeed(mActivity, invitedObj, feedUri);

                App.getUsageMetrics(mActivity).report(MusubiMetrics.ADDED_PERSON_TO_FEED,
                        "" + mmIdentitySelector.getSelectedIdentities().size());
                dismiss();
            }
        };
    }
}
