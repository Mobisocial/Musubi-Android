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

package mobisocial.musubi.ui.widget;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import mobisocial.crypto.IBHashedIdentity;
import mobisocial.musubi.App;
import mobisocial.musubi.R;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.helpers.DatabaseManager;
import mobisocial.musubi.ui.util.UiUtil;
import android.app.ActionBar.LayoutParams;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CursorAdapter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.MultiAutoCompleteTextView;
import android.widget.TextView;

/**
 * Allows the user to type in multiple identities. Get the identities with
 * getSelectedIdentities();
 */
public class MultiIdentitySelector extends MultiAutoCompleteTextView {
    private final ContactAdapter mContactAdapter;
    private DatabaseManager mDatabaseManger;
    private OnIdentitiesUpdatedListener mIdentitiesUpdatedListener;
    private OnRequestAddIdentityListener mRequestAddIdentityListener;
    boolean ranOnce = false;
    private String mPreviousText;
    private int mPreviousCaret;
	private CommaTokenizer mTokenizer;
	private boolean mIdentityMatchesDirty = false;
    private LinkedHashSet<MIdentity> mIdentityMatches;

    public static final String TAG = "MultiIdentitySelector";

    public MultiIdentitySelector(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        mContactAdapter = new ContactAdapter(context);
        init(context);
    }
    public MultiIdentitySelector(Context context) {
        super(context);
        mContactAdapter = new ContactAdapter(context);
        init(context);
    }

    void init(Context context) {
    	mPreviousText = "";
    	mPreviousCaret = 0;
    	mTokenizer = new MultiAutoCompleteTextView.CommaTokenizer();
    	setTokenizer(mTokenizer);
        setAdapter(mContactAdapter);
        setOnItemClickListener(mContactAdapter);
        setDropDownWidth(LayoutParams.FILL_PARENT);
        setHint("Type some names...");
        // Set threshold to 0 after first content load to avoid insta-dropdown.
        setThreshold(0);
        setInputType(InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        
        SQLiteOpenHelper databaseSource = App.getDatabaseSource(context);
        mDatabaseManger = new DatabaseManager(databaseSource);
        mIdentityMatches = new LinkedHashSet<MIdentity>();

        addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
				mPreviousCaret = getSelectionStart();
				mPreviousText = s.toString();
			}
			@Override
			public void afterTextChanged(Editable s) {
				handlePotentialIdentityUpdate();
			}
		});
    }
    @Override
    public boolean onTouchEvent(MotionEvent event) {
    	int action = event.getAction();
    	boolean result = super.onTouchEvent(event);
    	if(getText().length() == 0 && action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
			showDropDown();
    	}
    	return result;
    }

    public interface OnIdentitiesUpdatedListener {
        void onIdentitiesUpdated();
    }
    public interface OnRequestAddIdentityListener {
        void onRequestAddIdentity(String enteredText);
    }

    class ContactAdapter extends CursorAdapter implements Filterable, OnItemClickListener {
        private final String[] sMusubiProjection = new String[] { MIdentity.COL_ID };
        private static final String sSortOrder = MIdentity.COL_CLAIMED + " desc, " +
                MIdentity.COL_NAME + " desc";
        private final SQLiteOpenHelper mmDatabase;
        private final Map<String, Long> mmEntryMap = new HashMap<String, Long>();

        private static final int c_id = 0;

        private ContactAdapter(Context context) {
            super(context, null);
            mmDatabase = App.getDatabaseSource(context);
        }
        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            TextView name = (TextView) view.findViewById(R.id.name_text);
            TextView principal = (TextView) view.findViewById(R.id.principal_text);
            ImageView icon = (ImageView) view.findViewById(R.id.icon);
            long identityId = cursor.getLong(c_id);
            view.setTag(identityId);
            MIdentity ident = mDatabaseManger.getIdentitiesManager().getIdentityForId(identityId);

            if (cursor.getPosition() == cursor.getCount() - 1) {
                name.setText("Add a new contact");
                icon.setImageResource(R.drawable.ic_add_contact_holo_light);
                principal.setVisibility(View.GONE);
            } else {
                if (!ident.claimed_) { // TODO: find white/gray split
                    view.setBackgroundColor(Color.LTGRAY);
                } else {
                    view.setBackgroundColor(Color.TRANSPARENT);
                }
                name.setText(UiUtil.safeNameForIdentity(ident));
                principal.setText(UiUtil.safePrincipalForIdentity(ident));
                principal.setVisibility(View.VISIBLE);
                icon.setImageBitmap(UiUtil.safeGetContactThumbnail(context, mDatabaseManger.getIdentitiesManager(), ident));
            }
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            final LayoutInflater inflater = LayoutInflater.from(context);
            View v = inflater.inflate(R.layout.contact_autocomplete_item, parent, false);
            bindView(v, context, cursor);
            return v;
        }

        @Override
        public CharSequence convertToString(Cursor cursor) {
            if (cursor == null) {
                return "";
            }
            long identityId = cursor.getLong(c_id);
            MIdentity ident = mDatabaseManger.getIdentitiesManager().getIdentityForId(identityId);
            if(ident == null)
            	return "";
            return UiUtil.safeNameForIdentity(ident);
        }

        @Override
        public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
        	assert(getFilterQueryProvider() == null);
            String musubiSelect = null;
            String[] args = null;
            String localType = Integer.toString(IBHashedIdentity.Authority.Local.ordinal());
            if (constraint == null || constraint.length() == 0) {
                return new AddNewContactCursor();
            } else {
                StringBuilder select = new StringBuilder(100)
                    .append("((UPPER(").append(MIdentity.COL_NAME).append(") LIKE ? OR UPPER(")
                    .append(MIdentity.COL_MUSUBI_NAME).append(") LIKE ? OR UPPER(")
                    .append(MIdentity.COL_PRINCIPAL).append(") LIKE ?))")
                    .append(" AND ").append(MIdentity.COL_TYPE).append("!=").append(localType);
                String param = constraint.toString().toUpperCase();
                String arg1 = "%" + param + "%";
                args = new String[] { arg1, arg1, arg1 };

                if (constraint.length() < 2) {
                    select.append(" AND ").append(MIdentity.COL_CLAIMED).append("=1");
                }
                musubiSelect = select.toString();
            }
            Cursor identitiesCursor = mmDatabase.getWritableDatabase().query(MIdentity.TABLE, sMusubiProjection,
                    musubiSelect, args, null, null, sSortOrder);
            AddNewContactCursor addNewContactCursor = new AddNewContactCursor();
            return new MergeCursor(new Cursor[]{ identitiesCursor, addNewContactCursor });
        }

        @Override
        public void onItemClick(AdapterView<?> adapter, View v, int pos, long id) {
            String key = ((TextView)v.findViewById(R.id.name_text)).getText().toString();
            Long val = (Long)v.getTag();
            if (pos == adapter.getCount() - 1) {
            	int start = mTokenizer.findTokenStart(mPreviousText, mPreviousCaret);
                int end = mTokenizer.findTokenEnd(mPreviousText, mPreviousCaret);
                String entered = null;
                if(end - start > 0) {
                	entered = mPreviousText.substring(start, end);
                }

                String dirtyString = getText().toString();
                String cleanString = dirtyString.replaceAll(", ,", ", ");
                if (cleanString.equalsIgnoreCase(", ")) {
                	setText("");
                }
                else {
                	setText(cleanString);
                	setSelection(getText().length()-1);
                }
                if (mRequestAddIdentityListener != null) {
                	mRequestAddIdentityListener.onRequestAddIdentity(entered);
                }
                //clearSelectedIdentities();
            } else {
            	//trim or the deletion code will always assume the person is removed.
                mmEntryMap.put(key.trim(), val);
            }
            handlePotentialIdentityUpdate();
        }
    }

    /**
     * Removes entered identities.
     */
    public void clearSelectedIdentities() {
        mContactAdapter.mmEntryMap.clear();
        setText("");
    }


    synchronized void handlePotentialIdentityUpdate() {
    	mIdentityMatchesDirty = true;
    	LinkedHashSet<MIdentity> matches = getSelectedIdentities();
		mIdentityMatchesDirty = false;

		if (matches.size() == mIdentityMatches.size()
				&& matches.containsAll(mIdentityMatches)) {
			return;
		}
		mIdentityMatches = matches;

    	if (mIdentitiesUpdatedListener != null) {
            mIdentitiesUpdatedListener.onIdentitiesUpdated();
        }
    }

    public void setOnIdentitiesUpdatedListener(OnIdentitiesUpdatedListener listener) {
        mIdentitiesUpdatedListener = listener;
    }

    public LinkedHashSet<MIdentity> getSelectedIdentities() {
    	if (!mIdentityMatchesDirty) {
    		return mIdentityMatches;
    	}

        LinkedHashSet<MIdentity> hits = new LinkedHashSet<MIdentity>();
        String[] names = getText().toString().split(",");
        for (String name : names) {
            name = name.trim();
            if (name.length() == 0) {
            	continue;
            }
            if (mContactAdapter.mmEntryMap.containsKey(name)) {
                long cid = mContactAdapter.mmEntryMap.get(name);
                hits.add(mDatabaseManger.getIdentitiesManager().getIdentityForId(cid));
            }
        }
        return hits;
    }

    /**
     * A stub cursor to be used in the autocomplete adapter allowing the user
     * to add a new contact to the address book.
     */
    class AddNewContactCursor extends MatrixCursor {
        public AddNewContactCursor() {
            super(new String[] { "_id" });
            addRow(new Object[] { 0 });
        }
    }

	public void setOnRequestAddIdentityListener(
			OnRequestAddIdentityListener listener) {
		mRequestAddIdentityListener = listener;
		
	}
	public void addIdentity(String name, long identId) {
		mContactAdapter.mmEntryMap.put(name.trim(), identId);
		if(getText().toString().trim().length() == 0) {
			setText(name + ", ");
		} else {
			setText(getText().toString().trim() + ", " + name + ", ");
		}
		setSelection(getText().length());
	}
}
