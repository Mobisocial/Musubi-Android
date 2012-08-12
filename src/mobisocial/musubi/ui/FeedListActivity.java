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

import java.util.BitSet;
import java.util.List;

import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.musubi.App;
import mobisocial.musubi.R;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.objects.AppObj;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.provider.MusubiContentProvider.Provided;
import mobisocial.musubi.service.WizardStepHandler;
import mobisocial.musubi.ui.fragments.FeedListFragment;
import mobisocial.musubi.ui.fragments.FeedViewFragment;
import mobisocial.musubi.util.LessSpammyContentObserver;
import mobisocial.nfc.NdefFactory;
import mobisocial.nfc.NdefHandler;
import mobisocial.nfc.Nfc;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.Menu;
import android.support.v4.view.MenuItem;
import android.util.Log;
import android.view.MenuInflater;
import android.widget.Toast;

/**
 * Displays a list of all user-accessible threads (feeds).
 */
public class FeedListActivity extends MusubiBaseActivity implements
        FeedListFragment.OnFeedSelectedListener {

    public static final boolean DBG = true;
    public static final String TAG = "DungBeetleActivity";
    public static final String SHARE_SCHEME = "db-share-contact";
    public static final String GROUP_SESSION_SCHEME = "dungbeetle-group-session";
    public static final String GROUP_SCHEME = "dungbeetle-group";
    public static final String AUTO_UPDATE_URL_BASE = "http://mobisocial.stanford.edu/files";
    public static final String AUTO_UPDATE_METADATA_FILE = "dungbeetle_version.json";
    public static final String AUTO_UPDATE_APK_FILE = "dungbeetle-debug.apk";

    public static final int DIALOG_PLZ_LINK_ACCCOUNT = 1;

    private Nfc mNfc;
    private FeedListFragment mFeedListFragment;
    private static final String FRAGMENT_FEED_ACTIONS = "feedActions";
    private boolean mLoaded = false;
    private boolean mDualPane;
    private IdentitiesManager mIdentitiesManager;
	private LessSpammyContentObserver mObserver;
	private Menu mMenu;
	private boolean mSavedInstance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SQLiteOpenHelper databaseSource = App.getDatabaseSource(this);
        mIdentitiesManager = new IdentitiesManager(databaseSource);
        // TODO: NfcFragment
        mNfc = new Nfc(this);

        SharedPreferences p = getSharedPreferences(WizardStepHandler.WIZARD_PREFS_NAME, 0);
		boolean doRestore = p.getBoolean(WizardStepHandler.DO_RESTORE, WizardStepHandler.DO_RESTORE_DEFAULT);
		if (doRestore) {
			WizardStepHandler.restoreStepsFromDatabase(this);
		}

        // TODO: Hack.
        try {
            if (getIntent().hasExtra(AppObj.EXTRA_APPLICATION_ARGUMENT)) {
                getIntent().setData(Uri.parse(getIntent().getStringExtra(AppObj.EXTRA_APPLICATION_ARGUMENT)));
            }
        } catch (ClassCastException e) {}
        
        setContentView(R.layout.activity_feed_list);
        mFeedListFragment = new FeedListFragment();
        mDualPane = (null != findViewById(R.id.feed_view));
        if(mDualPane) {
            Bundle b = new Bundle();
            b.putBoolean(FeedListFragment.DUAL_PANE, true);
            mFeedListFragment.setArguments(b);
        }
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
            .replace(R.id.feed_list, mFeedListFragment).commit();
        }

        // TODO: Combine doHandleInput calls in onNewIntent.
        doHandleInput(getIntent().getData());
        mNfc.addNdefHandler(new NdefHandler() {
                public int handleNdef(final NdefMessage[] messages){
                	FeedListActivity.this.runOnUiThread(new Runnable(){
                            public void run() {
                                Log.d(TAG, "Handling ndef uri: " + uriFromNdef(messages));
                                doHandleInput(uriFromNdef(messages));
                            }
                        });
                    return NDEF_CONSUME;
                }
            });

        mNfc.setOnTagWriteListener(new Nfc.OnTagWriteListener(){
            public void onTagWrite(final int status){
                FeedListActivity.this.runOnUiThread(new Runnable(){
                    public void run(){
                        if (status == WRITE_OK) {
                            Toast.makeText(FeedListActivity.this, "Wrote successfully!",
                                    Toast.LENGTH_SHORT).show();
                        } else if(status == WRITE_ERROR_READ_ONLY) {
                            Toast.makeText(FeedListActivity.this, "Can't write read-only tag!",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(FeedListActivity.this, "Failed to write!",
                                    Toast.LENGTH_SHORT).show();
                        }
                        pushContactInfoViaNfc();
                    }
                }); 
            }
        });

        mObserver = new LessSpammyContentObserver(new Handler(getMainLooper())) {
        	@Override
        	public void lessSpammyOnChange(boolean arg0) {
        		fillInCounter();
        	}
		};
		//rerun it whenever something about a feed changes, because feed creation/modification implies graylist alteration
    	getContentResolver().registerContentObserver(MusubiContentProvider.uriForDir(Provided.FEEDS), false, mObserver);
        
        if(mDualPane && getIntent() != null && getIntent().getData() != null) {
        	Uri data = getIntent().getData();
        	if(data.toString().startsWith(MusubiContentProvider.uriForDir(Provided.FEEDS_ID).toString())) {
        		onFeedSelected(data);
        	}
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
    	switch (id) {
    		case DIALOG_PLZ_LINK_ACCCOUNT:
    			final Activity activity = FeedListActivity.this;
    			AlertDialog.Builder builder = new AlertDialog.Builder(activity);
    	    	//name = cursor.getString(cursor.getColumnIndexOrThrow(Contact.NAME));
    	    	builder.setMessage("Before you can send any messages to your friends, you'll need to link to your Google or Facebook account. Would you like to do that now?")
    	    	       .setCancelable(true)
    	    	       .setPositiveButton("Link Account", new DialogInterface.OnClickListener() {
    	    	           public void onClick(DialogInterface dialog, int id) {
    		    				Intent intent = new Intent(activity, SettingsActivity.class);
    				    	    intent.putExtra(SettingsActivity.ACTION, SettingsActivity.SettingsAction.ACCOUNT.toString());
    				    	    startActivity(intent);
    		        	   }
    	    	       })
    	    	       .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
    	    	           public void onClick(DialogInterface dialog, int id) {
    	    	               dialog.cancel();
    	    	           }
    	    	       });
    	    	return builder.create();
    	}
    	return null;
    }

    /* Creates the menu items */
    public boolean onCreateOptionsMenu(android.support.v4.view.Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.feed_list_activity, menu);
    	mMenu = menu;
        fillInCounter();
        return true;
    }
    
    class FillInCounter extends AsyncTask<Void, Void, Void> {
    	int mCount;
    	MenuItem mRelationshipItem;
    	public FillInCounter() {
    		//grab it now to avoid threading woes
    		mRelationshipItem = (MenuItem)mMenu.findItem(R.id.menu_relationships);
		}
    	
    	@Override
    	protected Void doInBackground(Void... params) {
    		mCount = mIdentitiesManager.getPendingGraylistCount();
    		return null;
    	}
    	@Override
    	protected void onPostExecute(Void result) {
            Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_menu_allfriends);
            icon.setDensity(Bitmap.DENSITY_NONE);
            Bitmap dn = icon.copy(icon.getConfig(), true);
            if(mCount > 0) {
    	        Canvas c = new Canvas(dn);
    	
    	        Paint p = new Paint();
    	        p.setAntiAlias(true);
    	        p.setStrokeWidth(1);
    	        p.setStyle(Style.FILL_AND_STROKE);
    	        p.setColor(Color.rgb(180, 0, 1));
    	
    	        RectF bubble = new RectF(dn.getWidth() / 2, dn.getHeight() / 2, dn.getWidth(), dn.getHeight());
    	        c.drawRoundRect(bubble, 5, 5, p);
    	        p.setColor(Color.rgb(255, 255, 255));
    	        p.setTextAlign(Align.CENTER);
    	        p.setTextSize(bubble.height() * 2 / 3);
    	        c.drawText("" + mCount, bubble.centerX(), bubble.centerY() + bubble.height() * 1 / 4, p);
            }
            mRelationshipItem.setIcon(new BitmapDrawable(dn));
    	}
    }

	private void fillInCounter() {
		if(mMenu == null)
			return;
		new FillInCounter().execute();
	}
 
    /* Handles item selections */
    public boolean onOptionsItemSelected(android.support.v4.view.MenuItem item) {
        switch (item.getItemId()) {
    	case R.id.menu_settings:
    		Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
    	case R.id.menu_relationships:
    		List<MIdentity> owned = mIdentitiesManager.getOwnedIdentities();
    		for(MIdentity ident : owned) {
    			if(ident.type_ != Authority.Local) {
    	    	    intent = new Intent(this, RelationshipsActivity.class);
	    	    	if (mIdentitiesManager.getPendingGraylistCount() > 0) {
    					intent.putExtra(RelationshipsActivity.EXTRA_SHOW_REQUESTS, true);
    				}
    	            startActivity(intent);
    	            return true;
    			}
    		}
    		showDialog(DIALOG_PLZ_LINK_ACCCOUNT);
            return true;
        }
        return false;
    }

    public Uri uriFromNdef(NdefMessage... messages) {
        if (messages.length == 0 ||  messages[0].getRecords().length == 0) {
            return null;
        }

        try {
            return NdefFactory.parseUri(messages[0].getRecords()[0]);
        } catch (FormatException e) {
            return null;
        }
    }

    protected void doHandleInput(Uri uri) {
        if (DBG) Log.d(TAG, "Handling input uri " + uri);
        if (uri == null) {
            return;
        }

        if (uri.getScheme() == null) {
            Log.w(TAG, "Null uri scheme for " + uri);
            return;
        }
        
        //TODO: if there are URI's that are opened with the FeedListActivity that we want to 
        //handle, e.g. an NFC url, then we put that code here.  We used to have friend invites
        //but now we don't have to worry about that. 
        
        //If we want to share a URL that opens a specific Musubi feed, then we need to create a 
        //URL that links to it without giving away the secret feed capability.
       	if (uri.getScheme().equals("content")) {
            if (uri.getAuthority().equals("vnd.mobisocial.db")) {
                if (uri.getPath().startsWith("/feed")) {
//                    Intent view = new Intent(Intent.ACTION_VIEW, uri);
//                    view.addCategory(Intent.CATEGORY_DEFAULT);
//                    // TODO: fix in AndroidManifest.
//                    //view.setClass(this, FeedActivity.class);
//                    view.setClass(this, FeedHomeActivity.class);
//                    startActivity(view);
//                    finish();
//                    return;
                }
    		}
        }

       	
        // Re-push the contact info ndef
        pushContactInfoViaNfc();
    }

    public void writeGroupToTag(Uri uri) {
        NdefRecord urlRecord = new NdefRecord(
                NdefRecord.TNF_ABSOLUTE_URI, 
                NdefRecord.RTD_URI, new byte[] {},
                uri.toString().getBytes());
        NdefMessage ndef = new NdefMessage(new NdefRecord[] { urlRecord });
        mNfc.enableTagWriteMode(ndef);
        Toast.makeText(this, "Touch a tag to write the group...", 
                Toast.LENGTH_SHORT).show();
    }
    

    public static byte[] toByteArray(BitSet bits) {
        byte[] bytes = new byte[bits.length()/8+1];
        for(int i = 0; i < bits.length(); i++) {
            if(bits.get(i)) {
                bytes[bytes.length-i/8-1] |= 1<<(i%8);
            }
        }
        return bytes;
    }

    public void pushGroupInfoViaNfc(Uri uri) {
        mNfc.share(NdefFactory.fromUri(uri));
    }

    public void pushContactInfoViaNfc() {
    	//for now just share musubi over NFC
        mNfc.share(NdefFactory.fromUri("https://market.android.com/details?id=mobisocial.musubi"));
    }

    @Override
    public void onPause() {
        super.onPause();
        mNfc.onPause(this);
    	getContentResolver().unregisterContentObserver(mObserver);
    }

    @Override
    public void onResume() {
        super.onResume();
        mSavedInstance = false;
        mNfc.onResume(this);
        pushContactInfoViaNfc();
        fillInCounter();
    	getContentResolver().registerContentObserver(MusubiContentProvider.uriForDir(Provided.FEEDS), false, mObserver);
        mObserver.resetTimeout();
    	mObserver.dispatchChange(false);
    }

    @Override
    public void onNewIntent(Intent intent) {
        if (mNfc.onNewIntent(this, intent)) {
            return;
        }
        setIntent(intent);
		getContentResolver().registerContentObserver(MusubiContentProvider.uriForDir(Provided.FEEDS), false, mObserver);
        if(mDualPane && getIntent() != null && getIntent().getData() != null) {
        	Uri data = getIntent().getData();
        	if(data.toString().startsWith(MusubiContentProvider.uriForDir(Provided.FEEDS_ID).toString())) {
        		onFeedSelected(data);
        	}
        }
    }

    @Override
    public void onDestroy() {
    	getContentResolver().unregisterContentObserver(mObserver);
        super.onDestroy();
    }
    @Override
    public void onBackPressed() {
    	super.onBackPressed();
    	finish();
    }
    @Override
    protected void onSaveInstanceState(Bundle outState) {
    	super.onSaveInstanceState(outState);
    	mSavedInstance = true;
    }
    
    @Override
    public void onFeedSelected(Uri feedUri) {
        if (mDualPane) {
        	if(mSavedInstance) {
        		return;
        	}
            Bundle args = new Bundle();
            args.putParcelable(FeedViewFragment.ARG_FEED_URI, feedUri);
            args.putBoolean(FeedViewFragment.ARG_DUAL_PANE, mDualPane);
            Fragment feedView = new FeedViewFragment();
            feedView.setArguments(args);
            Fragment oldSelector =
                    getSupportFragmentManager().findFragmentByTag(FRAGMENT_FEED_ACTIONS);
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            if (oldSelector != null) {
                ft.remove(oldSelector);
            }
            ft.replace(R.id.feed_view, feedView);
            ft.commit();
        } else {
            Intent panner = new Intent(this, FeedPannerActivity.class);
            panner.setData(feedUri);
        	startActivity(panner);
        }
    }
}
