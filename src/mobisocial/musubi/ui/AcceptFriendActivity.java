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
import gnu.trove.list.array.TLongArrayList;

import java.util.Iterator;
import java.util.List;

import mobisocial.crypto.IBHashedIdentity;
import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.crypto.IBIdentity;
import mobisocial.musubi.App;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MMyAccount;
import mobisocial.musubi.model.helpers.FeedManager;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.model.helpers.MyAccountManager;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.provider.MusubiContentProvider.Provided;
import mobisocial.musubi.service.MusubiService;
import mobisocial.musubi.util.Util;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

/**
 * Accepts a friend represented by the given data uri.
 */
public class AcceptFriendActivity extends MusubiBaseActivity {
	Uri mUri;
	String mName;
    List<String> mTypes;
    List<String> mPrincipals;
	private SQLiteOpenHelper mDatabaseSource;
	private IdentitiesManager mIdentitiesManager;
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mDatabaseSource = App.getDatabaseSource(this);
		mIdentitiesManager = new IdentitiesManager(mDatabaseSource);

		View view = new View(this);
		view.setBackgroundColor(Color.TRANSPARENT);
		setContentView(view);
		
	}
	@Override
	protected void onResume() {
		super.onResume();
		if(getIntent() == null || getIntent().getData() == null) {
		    Toast.makeText(this, "No data.", Toast.LENGTH_SHORT).show();
			finish();
			return;
		}
		mUri = getIntent().getData();
        mName = mUri.getQueryParameter("n");
        if (mName == null) {
        	mName = "Unnamed Friend";
        } 

        mTypes = mUri.getQueryParameters("t");
        mPrincipals = mUri.getQueryParameters("p");

        
        if(mTypes.size() != mPrincipals.size()) {
        	Toast.makeText(this, "Mismatched identity information", Toast.LENGTH_SHORT).show();
        	finish();
        	return;
        }
        if(mTypes.size() == 0) {
        	Toast.makeText(this, "Missing identity information", Toast.LENGTH_SHORT).show();
        	finish();
        	return;
        }
    	
        Iterator<String> i_types = mTypes.iterator();
        Iterator<String> i_princiapls = mPrincipals.iterator();

        TLongArrayList ids = new TLongArrayList(4);
        SQLiteDatabase db = mDatabaseSource.getWritableDatabase();
        int num_facebook_ids = 0;
        String description = "";
        try {
	        db.beginTransaction();
	        while(i_types.hasNext()) {
	            int type;
	        	try {
	        	    type = Integer.parseInt(i_types.next());
	        	} catch (NumberFormatException e) {
	        	    continue;
	        	}
	        	String principal = i_princiapls.next();
	        	Authority authority = IBHashedIdentity.Authority.values()[type];
	        	if(authority == Authority.Local) {
	        		continue;
	        	}
	        	IBIdentity id = new IBIdentity(authority, principal, 0);

		        long identId = mIdentitiesManager.getIdForIBHashedIdentity(id);
		        MIdentity ident;
		        if(identId == 0) {
		        	ident = new MIdentity();
		        	ident.type_ = authority;
		        	ident.principal_ = principal;
		        	ident.principalHash_ = Util.sha256(ident.principal_.getBytes());
		        	ident.principalShortHash_ = Util.shortHash(ident.principalHash_);
		        	ident.claimed_ = true;
		        	ident.musubiName_ = mName;
		        	identId = mIdentitiesManager.insertIdentity(ident);
		        } else {
		        	ident = mIdentitiesManager.getIdentityForId(identId);
		        	ident.principal_ = principal; // implicitly checked by lookup
		        	ident.claimed_ = true;
		        	ident.musubiName_ = mName;
		        	mIdentitiesManager.updateIdentity(ident);
		        }
		        ids.add(identId);
		        if(ident.type_ == Authority.Facebook) {
		        	num_facebook_ids++;
	        	} else {
        			description += "\n" + ident.principal_;
	        	}		        
	        }
	        if(num_facebook_ids > 0) {
	        	description += "\n" + num_facebook_ids + " Facebook IDs";
	        }
	        db.setTransactionSuccessful();
        } catch(Exception e) {
		} finally {
			db.endTransaction();
		}
		
		showDialog(AcceptFriendDialog.newInstance(mName, description, ids.toArray()));
	}

	public static class AcceptFriendDialog extends DialogFragment {
	    public static AcceptFriendDialog newInstance(String name, String description, long[] ids) {
	        AcceptFriendDialog d = new AcceptFriendDialog();
	        Bundle b = new Bundle();
	        b.putString("name", name);
	        b.putString("description", description);
	        b.putLongArray("ids", ids);
	        d.setArguments(b);
	        return d;
	    }

	    public AcceptFriendDialog() {
	    }

	    @Override
	    public Dialog onCreateDialog(Bundle savedInstanceState) {
	    	Bundle args = getArguments();
	    	final String name = args.getString("name");
	    	final long[] ids = args.getLongArray("ids");
	    	final String description = args.getString("description");
	        return new AlertDialog.Builder(getActivity())
	            .setTitle("Add contact?")
	            .setMessage("Add " + name + " to contacts?" + description)
	            .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                        	SQLiteOpenHelper databaseSource = App.getDatabaseSource(getActivity());
                        	SQLiteDatabase db = databaseSource.getWritableDatabase();
                        	db.beginTransaction();
                            boolean run_profile_push = false;
                        	try {
	                            IdentitiesManager identitiesManager = new IdentitiesManager(databaseSource);
	                            for(long id : ids) {
	                            	MIdentity ident = identitiesManager.getIdentityForId(id);
	                            	ident.whitelisted_ = true;
	                            	ident.blocked_ = false;
	                            	identitiesManager.updateIdentity(ident);
	                            }

	                            FeedManager feedManager = new FeedManager(databaseSource);
	                            MyAccountManager accountManager = new MyAccountManager(databaseSource);
	            				for(MIdentity persona : identitiesManager.getOwnedIdentities()) {
	            					if(persona.type_ == Authority.Local)
	            						continue;
	    	                		MMyAccount provisional_account = accountManager.getProvisionalWhitelistForIdentity(persona.id_);
	    	                        MMyAccount whitelist_account = accountManager.getWhitelistForIdentity(persona.id_);
	                            
		                            for(long id : ids) {
		                            	MIdentity ident = identitiesManager.getIdentityForId(id);
	    	                        	run_profile_push |= feedManager.addToWhitelistsIfNecessary(provisional_account, whitelist_account, persona, ident);
	                				}
	    	                    }
	                            db.setTransactionSuccessful();
                        	} finally {
                        		db.endTransaction();
                        	}
            				if(run_profile_push) {
                                getActivity().getContentResolver().notifyChange(MusubiService.FORCE_PROFILE_PUSH, null);
            				}
            				//TODO: sadly this wakes up profile push as well... we probably need to rescope some of these events
                            getActivity().getContentResolver().notifyChange(MusubiService.WHITELIST_APPENDED, null);
                            getActivity().getContentResolver().notifyChange(MusubiService.COLORLIST_CHANGED, null);
                            Uri data = MusubiContentProvider.uriForItem(Provided.IDENTITIES_ID, ids[0]);
                            String type = MusubiContentProvider.getType(Provided.IDENTITIES_ID);
                            Intent view = new Intent(Intent.ACTION_VIEW);
                            view.setDataAndType(data, type);
                            startActivity(view);
                        } catch (Exception e) {
                            Toast.makeText(getActivity(), "Error adding contact.", Toast.LENGTH_LONG).show();
                            Log.e(TAG, "Error adding contact", e);
                        } finally {
                            dismiss();
                            getActivity().finish();   
                        }   
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dismiss();
                        getActivity().finish();
                    }
                }).create();
	    }
	}
}