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
import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.musubi.App;
import mobisocial.musubi.R;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MMyAccount;
import mobisocial.musubi.model.helpers.FeedManager;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.model.helpers.MyAccountManager;
import mobisocial.musubi.service.MusubiService;
import mobisocial.musubi.ui.EmailInviteActivity;
import mobisocial.musubi.ui.ViewProfileActivity;
import mobisocial.musubi.ui.util.UiUtil;
import mobisocial.musubi.util.LessSpammyContentObserver;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.SupportActivity;
import android.support.v4.view.MenuItem;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Displays a list of contacts. If the intent used to create
 * this activity as Long extra "group_id", contacts are chosen
 * from this group. Otherwise, lists all known contacts.
 *
 */
public class ContactsFragment extends Fragment
        implements OnItemClickListener {
	private ContactListCursorAdapter mContacts;
	public static final String TAG = "ContactsFragment";
	public static final int GROUP_ID = 111;
    private static final int sBlockedColor = Color.parseColor("#66FF3333");
	
	public static enum ContactListType {WHITE_LIST, GRAY_LIST};
	public static final String CONTACT_LIST_TYPE = "contact_list_type";
	private ContactListType listType;

	private SQLiteOpenHelper mDatabaseSource;
	private IdentitiesManager mIdentityManager;
	private FeedManager mFeedManager;
	private MyAccountManager mAccountManager;

	private LessSpammyContentObserver mObserver;
	private ContentObserver mEditableObserver;
	private Activity mActivity;
	
    public static ContactsFragment newInstance(ContactListType type) {
    	ContactsFragment frag = new ContactsFragment();
        Bundle args = new Bundle();
        args.putInt(CONTACT_LIST_TYPE, type.ordinal());
        frag.setArguments(args);
        return frag;
    }
    
    
    @Override
    public void onAttach(SupportActivity activity) {
        super.onAttach(activity);
        mActivity = activity.asActivity();
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.simple_list, container, false);
        Log.w(TAG, "returning view");
        return v;
    }
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        mObserver = new LessSpammyContentObserver(new Handler(mActivity.getMainLooper())) {
        	@Override
        	public void lessSpammyOnChange(boolean arg0) {
        		if(mContacts == null || mContacts.getCursor() == null || !isAdded())
        			return;
        		mContacts.getCursor().requery();
        	}
		};
        mEditableObserver = new ContentObserver(new Handler(mActivity.getMainLooper())) {
        	@Override
        	public void onChange(boolean arg0) {
        		if(mContacts == null || mContacts.getCursor() == null || !isAdded())
        			return;
        		mContacts.getCursor().requery();
        	}
		};
	}
	
	@Override
	public void onResume() {
    	super.onResume();
    	mActivity.getContentResolver().registerContentObserver(MusubiService.WHITELIST_APPENDED, false, mEditableObserver);
    	mActivity.getContentResolver().registerContentObserver(MusubiService.COLORLIST_CHANGED, false, mEditableObserver);
    	mActivity.getContentResolver().registerContentObserver(MusubiService.PRIMARY_CONTENT_CHANGED, false, mObserver);
        mObserver.resetTimeout();
    	mObserver.dispatchChange(false);
	}
	
	@Override
	public void onPause() {
    	super.onPause();
    	mActivity.getContentResolver().unregisterContentObserver(mObserver);
    	mActivity.getContentResolver().unregisterContentObserver(mEditableObserver);
	}
	
	@Override
	public void onDestroy() {
    	super.onDestroy();
	}
    public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		listType = ContactListType.values()[getArguments().getInt(CONTACT_LIST_TYPE)];
		
        mDatabaseSource = App.getDatabaseSource(this.mActivity);
        mIdentityManager = new IdentitiesManager(mDatabaseSource);
        mFeedManager = new FeedManager(mDatabaseSource);
        mAccountManager = new MyAccountManager(mDatabaseSource);
        Cursor identities;

        
        if (listType == ContactListType.GRAY_LIST) {
        	identities = mIdentityManager.getGrayListIdentitiesCursor();
        }
        else {
        	identities = mIdentityManager.getWhiteListIdentitiesCursor();
        }
        
        mContacts = new ContactListCursorAdapter(this.mActivity, identities);
           
        ListView lv = (ListView)getView().findViewById(android.R.id.list);
        lv.setAdapter(mContacts);
        lv.setTextFilterEnabled(true);
        lv.setFastScrollEnabled(true);
        registerForContextMenu(lv);
		lv.setOnItemClickListener(this);
    }


    @Override
    public void onItemClick(AdapterView<?> parent, final View view, int position, long id){
        final Cursor cursor = (Cursor)mContacts.getItem(position);
        final long identityId = cursor.getLong(cursor.getColumnIndexOrThrow(MIdentity.COL_ID));
        final MIdentity ident = mIdentityManager.getIdentityForId(identityId);
        assert(ident != null);
        switch(listType) {
	        case WHITE_LIST:
	        	Intent intent = new Intent(mActivity, ViewProfileActivity.class);
	            intent.putExtra(ViewProfileActivity.PROFILE_ID, identityId);
	            mActivity.startActivity(intent);
	        	break;
	        case GRAY_LIST:
	        	break;
        }
    }
    
    private void addContact(final MIdentity ident) {
    	/*
    	AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
    	//name = cursor.getString(cursor.getColumnIndexOrThrow(Contact.NAME));
    	String name = UiUtil.safeNameForIdentity(ident);
    	builder = new AlertDialog.Builder(mActivity);
    	//name = cursor.getString(cursor.getColumnIndexOrThrow(Contact.NAME));
    	builder.setMessage("Do you want to add " + name + " to your friends list?")
    	       .setCancelable(true)
    	       .setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
    	           public void onClick(DialogInterface dialog, int id) {
    	        	   addToWhitelist(ident);
    	               dialog.cancel();
	        	   }
    	       })
    	       .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
    	           public void onClick(DialogInterface dialog, int id) {
    	               dialog.cancel();
    	           }
    	       });
    	AlertDialog alert = builder.create();
    	alert.show();*/
    	addToWhitelist(ident);
    }

    void addToWhitelist(MIdentity ident) {
    	ident.whitelisted_ = true;
    	ident.blocked_ = false;
    	//force a profile exchange to ensure we get an icon after a user action
    	ident.sentProfileVersion_ = 0;
    	mIdentityManager.updateIdentity(ident);
    	mFeedManager.acceptFeedsFromMember(mActivity, ident.id_);
    	//stop sending them broadcasts, e.g. profiles
    	MMyAccount[] accounts =  mAccountManager.getMyAccounts();
    	boolean found_specific_account = false;
    	for(MMyAccount account : accounts) {
    		//TODO: someday you can remove these checks
    		assert(account.identityId_ != null);
    		if(account.identityId_ == null) continue;
    		assert(account.feedId_ != null);
    		if(account.feedId_ == null) continue;

    		//check if this member is on a provisional whitelist, that's the identity we want to put them in
    		if(!account.accountType_.equals(MMyAccount.INTERNAL_ACCOUNT_TYPE))
    			continue;
    		if(!account.accountName_.equals(MMyAccount.PROVISIONAL_WHITELIST_ACCOUNT))
    			continue;
    		MMyAccount whitelist_account = mAccountManager.getWhitelistForIdentity(account.identityId_);
    		assert(whitelist_account != null); //should be guaranteed
    		assert(whitelist_account.feedId_ != null); //should be guaranteed
    		mFeedManager.ensureFeedMember(whitelist_account.feedId_, ident.id_);
    		found_specific_account = true;
    	}
    	//put them in an unassociated group which will always use the default identity whatever that is
    	if(!found_specific_account) {
    		mFeedManager.ensureFeedMember(MFeed.NONIDENTITY_SPECIFIC_WHITELIST_ID, ident.id_);
    	}
    	mObserver.resetTimeout();
    	mActivity.getContentResolver().notifyChange(MusubiService.WHITELIST_APPENDED, null);
    	mActivity.getContentResolver().notifyChange(MusubiService.COLORLIST_CHANGED, null);
    }

    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)menuInfo;
        Cursor c = (Cursor)mContacts.getItem(info.position);
        long identityId = c.getLong(c.getColumnIndexOrThrow(MIdentity.COL_ID));
        MIdentity ident = mIdentityManager.getIdentityForId(identityId);
        String[] menuItems;
        if (ident.type_ == Authority.Email && !ident.claimed_) {
            menuItems = new String[] { "Ignore", "Invite" };
        } else {
            menuItems = new String[] { "Ignore" };
        }

        for (int i = 0; i<menuItems.length; i++) {
            menu.add(GROUP_ID, i, i, menuItems[i]);
        }
    }
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
        int menuItemIndex = item.getItemId();

        Cursor cursor = (Cursor)mContacts.getItem(info.position);
        final MIdentity ident = mIdentityManager.getIdentityForId(cursor.getLong(cursor.getColumnIndexOrThrow(MIdentity.COL_ID)));

 
        switch(menuItemIndex) {
	        case 0:
	        	ignoreContact(ident);
	        	break;
	        case 1:
	            String email = ident.principal_;
	            if (email == null) {
	                Toast.makeText(getActivity(), "Oops, couldn't set up email.", Toast.LENGTH_SHORT).show();
	                return true;
	            }

	            Intent intent = EmailInviteActivity.getInviteIntentForEmail(getActivity(), email);
	            startActivity(intent);
	        	break;
        }
        return true;
    }
    
    private void ignoreContact(final MIdentity ident) {
    	AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
    	//name = cursor.getString(cursor.getColumnIndexOrThrow(Contact.NAME));
    	String name = UiUtil.safeNameForIdentity(ident);
    	builder.setMessage("Are you sure you want to ignore " + name + "?")
    	       .setCancelable(true)
    	       .setPositiveButton("Ignore", new DialogInterface.OnClickListener() {
    	           public void onClick(DialogInterface dialog, int id) {
    	        	   ident.whitelisted_ = false;
    	         	   ident.blocked_ = true;
    	         	   mIdentityManager.updateIdentity(ident);
    	         	   //stop sending them broadcasts, e.g. profiles
    	         	   MMyAccount[] accounts =  mAccountManager.getMyAccounts();
    	         	   for(MMyAccount account : accounts) {
    	         		   //TODO: someday you can remove these checks
    	         		   assert(account.feedId_ != null);
    	         		   if(account.feedId_ == null) continue;

    	         		   mFeedManager.deleteFeedMember(account.feedId_, ident.id_);
    	         	   }
    	         	   //TODO: hide all the feeds this person is the only other person on?
    	         	   //TODO: hide this persons objects or delete them? ew?
    	         	   mObserver.resetTimeout();
    	         	   mActivity.getContentResolver().notifyChange(MusubiService.COLORLIST_CHANGED, null);
	        	   }
    	       })
    	       .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
    	           public void onClick(DialogInterface dialog, int id) {
    	               dialog.cancel();
    	           }
    	       });
    	AlertDialog alert = builder.create();
    	alert.show();
    }

    private class ContactListCursorAdapter extends CursorAdapter {
        public ContactListCursorAdapter (Context context, Cursor c) {
            super(context, c);
        }

        @Override
        public View newView(Context context, Cursor c, ViewGroup parent) {
            final LayoutInflater inflater = LayoutInflater.from(context);
            View v = inflater.inflate(R.layout.contacts_item, parent, false);
            return v;
        }

        @Override
        public void bindView(View v, Context context, Cursor cursor) {
        	final MIdentity ident = mIdentityManager.getIdentityForId(cursor.getLong(0));   
        	v.setTag(ident.id_);

            TextView nameText = (TextView) v.findViewById(R.id.name_text);
            nameText.setText(UiUtil.safeNameForIdentity(ident));

            TextView principalText = (TextView) v.findViewById(R.id.principal_text);
            principalText.setText(UiUtil.safePrincipalForIdentity(ident));
            
            TextView statusText = (TextView) v.findViewById(R.id.status_text);
            statusText.setVisibility(View.GONE);

            TextView unreadCount = (TextView)v.findViewById(R.id.unread_count);
            unreadCount.setVisibility(View.GONE);

            ImageView icon = (ImageView)v.findViewById(R.id.icon);            
        	icon.setImageBitmap(UiUtil.safeGetContactThumbnail(context, mIdentityManager, ident));

        	
            final ImageView presenceIcon = (ImageView)v.findViewById(R.id.presence_icon);
            presenceIcon.setVisibility(View.GONE);
            //presenceIcon.setImageResource(c.currentPresenceResource());

            final ImageView nearbyIcon = (ImageView)v.findViewById(R.id.nearby_icon);
            nearbyIcon.setVisibility(View.GONE);
        	//nearbyIcon.setVisibility(c.nearby ? View.VISIBLE : View.GONE);

            final ImageView more = (ImageView)v.findViewById(R.id.more);
            more.setVisibility(View.GONE);
            
            if (listType == ContactListType.GRAY_LIST) {
            	final View buttonBar = v.findViewById(R.id.button_bar);
                buttonBar.setVisibility(View.VISIBLE);
                
                Button addButton = (Button) v.findViewById(R.id.add_contact);
                addButton.setOnClickListener(new OnClickListener() {
					
					@Override
					public void onClick(View v) {
						// TODO Auto-generated method stub
						addContact(ident);
					}
				});
                
                Button ignoreButton = (Button) v.findViewById(R.id.ignore_contact);
                if(ident.blocked_) {
                	ignoreButton.setVisibility(View.GONE);
                	addButton.setText("Un-ignore");
                }
                else {
	                ignoreButton.setOnClickListener(new OnClickListener() {
						
						@Override
						public void onClick(View v) {
							// TODO Auto-generated method stub
							ignoreContact(ident);
						}
					});
                }
            }
            
            ImageView musubiEnabled = (ImageView)v.findViewById(R.id.musubi_enabled);
            ImageView ignoredOverlay = (ImageView) v.findViewById(R.id.ignored_overlay);
            if (ident.claimed_) {
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
            
            if(ident.blocked_) {
                nameText.setTextColor(sBlockedColor);
                principalText.setTextColor(sBlockedColor);
                ignoredOverlay.setVisibility(View.VISIBLE);
            }

        	musubiEnabled.setVisibility(View.VISIBLE);
        }
    }
}