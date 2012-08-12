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

import java.util.ArrayList;
import java.util.List;

import mobisocial.metrics.MusubiMetrics;
import mobisocial.metrics.UsageMetrics;
import mobisocial.musubi.App;
import mobisocial.musubi.R;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.provider.MusubiContentProvider.Provided;
import mobisocial.musubi.service.MusubiService;
import mobisocial.musubi.social.QRInviteDialog;
import mobisocial.musubi.ui.fragments.ContactsFragment;
import mobisocial.musubi.ui.util.UiUtil;
import mobisocial.musubi.util.LessSpammyContentObserver;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.MenuInflater;

import com.viewpagerindicator.TabPageIndicator;
import com.viewpagerindicator.TitleProvider;

public class RelationshipsActivity extends MusubiBaseActivity {
    @SuppressWarnings("unused")
    private static final String TAG = "RelationshipsActivity";
    public static final String EXTRA_SHOW_REQUESTS = "extra_show_requests";

    private IdentitiesManager mIdentitiesManager;

    private ViewPager mViewPager;
    private final List<Fragment> mFragments = new ArrayList<Fragment>();
    private final List<String> mLabels = new ArrayList<String>();
	private LessSpammyContentObserver mObserver;
	private TabPageIndicator mTitleIndicator;
	private static final int REQUEST_ADD_ANDROID_CONTACT = 2;
    private static final int LABELS_FRIENDS = 0;
    private static final int LABELS_PENDING = 1;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_tabbed);
    	
        SQLiteOpenHelper databaseSource = App.getDatabaseSource(this);
        mIdentitiesManager = new IdentitiesManager(databaseSource);
        
        setTitle("Relationships");
        mLabels.add(LABELS_FRIENDS, "Friends");
        mLabels.add(LABELS_PENDING, "New");
        mFragments.add(ContactsFragment.newInstance(ContactsFragment.ContactListType.WHITE_LIST));
        mFragments.add(ContactsFragment.newInstance(ContactsFragment.ContactListType.GRAY_LIST));

        PagerAdapter adapter = new ViewFragmentAdapter(getSupportFragmentManager(), mFragments, mLabels);
        mViewPager = (ViewPager)findViewById(R.id.feed_pager);
        mViewPager.setAdapter(adapter);

        //Bind the title indicator to the adapter
        mTitleIndicator = (TabPageIndicator)findViewById(R.id.feed_titles);
        mTitleIndicator.setViewPager(mViewPager);
        //mTitleIndicator.setTextColor(Color.DKGRAY);
        //mTitleIndicator.setSelectedColor(Color.BLACK);
        //let the title pager do the propagation of messages
        //mTitleIndicator.setOnPageChangeListener(this);
        
        Intent intent = getIntent();
        boolean showRequests = intent.getBooleanExtra(EXTRA_SHOW_REQUESTS, false);
        if (showRequests) {
        	mViewPager.setCurrentItem(1);
        } else {
        	mViewPager.setCurrentItem(0);
        }
    	fillInCounter();
        
        mObserver = new LessSpammyContentObserver(new Handler(getMainLooper())) {
        	@Override
        	public void lessSpammyOnChange(boolean selfChange) {
        		fillInCounter();
        	}
		};
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onPause() {
    	super.onPause();
    	getContentResolver().unregisterContentObserver(mObserver);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        fillInCounter();
    	getContentResolver().registerContentObserver(MusubiService.WHITELIST_APPENDED, false, mObserver);
        mObserver.resetTimeout();
    	mObserver.dispatchChange(false);
    }
    
    private void fillInCounter() {
		int count = mIdentitiesManager.getPendingGraylistCount();
        if (count > 0) {
	        mLabels.set(LABELS_PENDING, "New (" + count + ")");
        }
        else {
	        mLabels.set(LABELS_PENDING, "New");
        }
        mTitleIndicator.invalidate();
    }
    

    public class ViewFragmentAdapter extends FragmentPagerAdapter implements TitleProvider {
        final int NUM_ITEMS;
        final List<Fragment> mFragments;
        final List<String> mTitles;

        public ViewFragmentAdapter(FragmentManager fm, List<Fragment> fragments, List<String> titles) {
            super(fm);
            mFragments = fragments;
            mTitles = titles;
            assert(mTitles.size() == mFragments.size());
            NUM_ITEMS = mFragments.size();
        }

        @Override
        public int getCount() {
            return NUM_ITEMS;
        }

        @Override
        public Fragment getItem(int position) {
            return mFragments.get(position);
        }

		@Override
		public String getTitle(int position) {
			return mTitles.get(position);
		}
    }

    /* Creates the menu items */
    public boolean onCreateOptionsMenu(android.support.v4.view.Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.relationships_activity, menu);
        return true;
    }
 
    /* Handles item selections */
    public boolean onOptionsItemSelected(android.support.v4.view.MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_add_contact:
                showDialog(AddContactDialog.newInstance(), false);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
    	super.onActivityResult(requestCode, resultCode, data);
    	if(requestCode == REQUEST_ADD_ANDROID_CONTACT) {
			if(resultCode == Activity.RESULT_OK) {
				Uri added = UiUtil.addedContact(this, data, null);
				if(added != null) {
					Intent i = new Intent(this, ViewProfileActivity.class);
					i.setDataAndType(added, MusubiContentProvider.getType(Provided.IDENTITIES_ID));
					startActivity(i);
				}
			}
    	}
    }

    public static class AddContactDialog extends DialogFragment implements DialogInterface.OnClickListener {
        String[] labels = new String[] { "Send Invitation", "Add to address book", "QR Code" };
        enum codes { REQUEST_INVITE, REQUEST_ADD_CONTACT, REQUEST_QR };
        String title = "Add Contact...";

        public static AddContactDialog newInstance() {
            AddContactDialog d = new AddContactDialog();
            Bundle args = new Bundle();
            d.setArguments(args);
            return d;
        }

        public AddContactDialog() {
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity()).setTitle(title)
                    .setItems(labels, this).create();
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            UsageMetrics m = new UsageMetrics(getActivity());
            switch (codes.values()[which]) {
                case REQUEST_QR:
                    m.report(MusubiMetrics.CLICKED_QR_INVITE);
                    ((MusubiBaseActivity)getActivity()).showDialog(QRInviteDialog.newInstance(), false);
                    break;
                case REQUEST_INVITE:
                    m.report(MusubiMetrics.CLICKED_SEND_INVITE);
                    startActivity(EmailInviteActivity.getInviteIntent(getActivity()));
                    break;
                case REQUEST_ADD_CONTACT:
                    m.report(MusubiMetrics.CLICKED_ADD_CONTACT);
                    Intent i = new Intent(Intent.ACTION_INSERT_OR_EDIT);
                    i.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
                    getActivity().startActivityForResult(i, REQUEST_ADD_ANDROID_CONTACT);
                    break;
            }
        }
    }
}