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

package mobisocial.musubi.util;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Contacts;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import mobisocial.musubi.R;

/**
 * Choose contacts from the device's address book.
 *
 */
public class AddressBookPicker extends FragmentActivity implements LoaderCallbacks<Cursor> {

    private ListView mListView;
    private ContactListAdapter mAdapter;
    private LayoutInflater mInflater;

    private static final String CLAUSE_ONLY_VISIBLE = Contacts.IN_VISIBLE_GROUP + "=1";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.addressbook_picker);
        mInflater = (LayoutInflater)getSystemService
                (Context.LAYOUT_INFLATER_SERVICE);
        mListView = (ListView)findViewById(R.id.contacts);
        getSupportLoaderManager().initLoader(0, null, this);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
        Context context = this;
        Uri uri = Contacts.CONTENT_URI;
        String[] projection = new String[] { Contacts._ID, Contacts.LOOKUP_KEY };
        String selection = CLAUSE_ONLY_VISIBLE;
        String[] selectionArgs = null;
        String sortOrder = null;
        return new CursorLoader(context, uri, projection, selection, selectionArgs, sortOrder);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        if (mAdapter == null) {
            mListView = (ListView)findViewById(R.id.contacts);
            mAdapter = new ContactListAdapter(loader.getContext(), cursor);
            mListView.setAdapter(mAdapter);
            mListView.setFastScrollEnabled(true);
        } else {
            mAdapter.changeCursor(cursor);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> arg0) {
    }

    
    class ContactListAdapter extends CursorAdapter {
        public ContactListAdapter(Context context, Cursor c) {
            super(context, c);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            RelativeLayout frame = (RelativeLayout) view;

            long contactId = cursor.getLong(0);
            String lookupKey = cursor.getString(1);

            Uri uri = CommonDataKinds.Email.CONTENT_URI;
            String[] projection = new String[] { CommonDataKinds.Email.DATA };
            String selection = CommonDataKinds.Email.CONTACT_ID + " = ?";
            String[] selectionArgs = new String[] { Long.toString(contactId) };
            String sortOrder = null;

            cursor = getContentResolver().query(
                    uri, projection, selection, selectionArgs, sortOrder);
            String email = null;
            if (cursor.moveToFirst()) {
                email = cursor.getString(0);
            } else {
                email = "";
                frame.setEnabled(false);
            }
            cursor.close();

            uri = Contacts.getLookupUri(contactId, lookupKey);
            projection = new String[] { Contacts.DISPLAY_NAME };
            selection = null;
            selectionArgs = null;
            sortOrder = null;
            cursor = getContentResolver().query(
                    uri, projection, selection, selectionArgs, sortOrder);
            String name = null;
            if (cursor.moveToFirst()) {
                name = cursor.getString(0);
            } else {
                // TODO
            }
            cursor.close();
            ((TextView)frame.findViewById(R.id.name)).setText(name);
            ((TextView)frame.findViewById(R.id.email)).setText(email);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            return mInflater.inflate(R.layout.addressbook_picker_entry, parent, false);
        }
    }
}
