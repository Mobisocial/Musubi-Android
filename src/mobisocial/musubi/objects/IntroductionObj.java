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

package mobisocial.musubi.objects;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import mobisocial.crypto.IBHashedIdentity;
import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.musubi.App;
import mobisocial.musubi.MembersActivity;
import mobisocial.musubi.R;
import mobisocial.musubi.feed.iface.Activator;
import mobisocial.musubi.feed.iface.DbEntryHandler;
import mobisocial.musubi.feed.iface.FeedRenderer;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MObject;
import mobisocial.musubi.model.helpers.FeedManager;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.model.helpers.ObjectManager;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.provider.MusubiContentProvider.Provided;
import mobisocial.musubi.service.MusubiService;
import mobisocial.musubi.ui.fragments.FeedListFragment.FeedSummary;
import mobisocial.musubi.ui.util.UiUtil;
import mobisocial.musubi.ui.widget.DbObjCursorAdapter.DbObjCursor;
import mobisocial.musubi.util.Util;
import mobisocial.socialkit.Obj;
import mobisocial.socialkit.musubi.DbObj;
import mobisocial.socialkit.obj.MemObj;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.Uri;
import android.support.v4.widget.CursorAdapter;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.AbsListView.LayoutParams;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Gallery;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * An object that provides minimal information about introduced participants to a group.
 * This ensures the minimum latency in a person being able to tell who is participating
 * in a feed.  Other approaches, such as sending profile to people we discover (which we still do),
 * have one round-trip of latency.
 *
 */
public class IntroductionObj extends DbEntryHandler implements FeedRenderer, Activator {

    public static final String TYPE = "introduction";
    public static final String IDENTITIES = "identities";
    public static final String ID_AUTHORITY = "authority";
    public static final String ID_PRINCIPAL = "principal";
    public static final String ID_PRINCIPAL_HASH = "hash";
    public static final String ID_NAME = "name";

    @Override
    public String getType() {
        return TYPE;
    }

    public static MemObj from(Collection<MIdentity> identities, boolean tellPrincipals) {
        return new MemObj(TYPE, json(identities, tellPrincipals));
    }
    static JSONObject json(Collection<MIdentity> identities, boolean tellPrincipals){
    	JSONArray array = new JSONArray();
        JSONObject obj = new JSONObject();
        try{
        	for (MIdentity id : identities) {
	        	JSONObject identity = new JSONObject();
	        	identity.put(ID_AUTHORITY, id.type_.ordinal());
	        	identity.put(ID_PRINCIPAL_HASH, Base64.encodeToString(id.principalHash_, Base64.DEFAULT));
	        	identity.put(ID_PRINCIPAL, id.principal_);
	        	if(tellPrincipals)
	        		identity.put(ID_PRINCIPAL, id.principal_);
	        	identity.put(ID_NAME, UiUtil.safeNameForIdentity(id));
	        	array.put(identity);
        	}
        	obj.put(IDENTITIES, array);
        }catch(JSONException e){}
        return obj;
    }

    @Override
    public View createView(Context context, ViewGroup frame) {
    	LinearLayout wrap = new LinearLayout(context);
        wrap.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        wrap.setEnabled(false);
        wrap.setFocusableInTouchMode(false);
        wrap.setFocusable(false);
        wrap.setClickable(false);

        TextView title = new TextView(context);
        title.setText(R.string.introduced);
        title.setTypeface(null, Typeface.BOLD);
        title.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        wrap.addView(title);

        Gallery intro = new Gallery(context);
        intro.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        hackGalleryInit(context, intro);
        wrap.addView(intro);
        return wrap;
    }

    @Override
    public void render(Context context, final View frame, DbObjCursor obj, final boolean allowInteractions) {
    	Gallery intro = (Gallery)((ViewGroup)frame).getChildAt(1);
    	
        // TODO: LoaderManager requires access to a SupportActivity.
        intro.setAdapter(FacesAdapter.forObj(context, obj));
        intro.setSpacing(1);
        intro.setOnItemClickListener(mIdentityClickListener);
    }

    void hackGalleryInit(Context context, Gallery gallery) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        MarginLayoutParams mlp = (MarginLayoutParams) gallery.getLayoutParams();
        mlp.setMargins(-(metrics.widthPixels/2), 
                       mlp.topMargin, 
                       mlp.rightMargin, 
                       mlp.bottomMargin
        );
    }
    
    @Override
    public boolean processObject(Context context, MFeed feed, MIdentity sender,
    		MObject object) {
    	boolean anyChanged = false;
        SQLiteOpenHelper databaseSource = App.getDatabaseSource(context);
    	IdentitiesManager identitiesManager = new IdentitiesManager(databaseSource);

        if (object.json_ == null) {
            Log.w(TAG, "bad introduction format");
            return false;
        }
        JSONObject json;
        try {
            json = new JSONObject(object.json_);
        } catch (JSONException e) {
            Log.e(TAG, "Bad json in database", e);
            return false;
        }
        JSONArray array;
        try {
			array = json.getJSONArray(IDENTITIES);
		} catch (JSONException e) {
            Log.e(TAG, "json identity array missing", e);
            return false;
		}
        // TODO: use getIdentitiesForObj
        for(int i = 0; i < array.length(); ++i) {
        	JSONObject identity;
			try {
				identity = array.getJSONObject(i);
			} catch (JSONException e) {
				Log.e(TAG, "identity entry in introduction access error", e);
				continue;
			}
			int authority = -1;
			String principalHashString = null;
        	try {
				authority = identity.getInt(ID_AUTHORITY);
				principalHashString = identity.getString(ID_PRINCIPAL_HASH);
			} catch (JSONException e) {
				Log.e(TAG, "identity entry in introduction missing key fields", e);
				continue;
			}
        	String principal = null;
        	try {
				principal = identity.getString(ID_PRINCIPAL);
			} catch (JSONException e) {
			}
        	String name = null;
        	try {
				name = identity.getString(ID_NAME);
			} catch (JSONException e) {
			}
        	if(name == null && principal == null) {
        		//not much of an introduction
        		continue;
        	}
        	
        	byte[] principalHash = Base64.decode(principalHashString, Base64.DEFAULT);
        	IBHashedIdentity hid = new IBHashedIdentity(Authority.values()[authority], principalHash, 0);
        	MIdentity ident = identitiesManager.getIdentityForIBHashedIdentity(hid);
        	if(ident == null) {
        		//this introduction has to be sent to both participants, so the low leve
        		//will already have added the identity
        		Log.e(TAG, "identity introduction for totally unseen identities");
        		continue;
        	}
        	if(ident.owned_) {
        		//we won't have a received profile version, so owned keeps us from self updating
        		continue;
        	}
        	//TODO: rely on  deferred handling for gray list participants
        	//TODO: check that the person is actually in the feed
        	boolean changed = false;
        	if(principal != null && ident.principal_ == null) {
            	if(!Arrays.equals(Util.sha256(principal.getBytes()), principalHash)) {
            		Log.e(TAG, "received mismatched principal and principal hash");
            		continue;
            	}
            	changed = true;
        		ident.principal_ = principal;
        	}
        	if(name != null && ident.receivedProfileVersion_ == 0) {
            	changed = true;
            	//each time someone introduces us, we'll just accept the new name
            	//as long as we never got a real profile.
        		ident.musubiName_ = name;
        	}
        	if(changed) {
        		identitiesManager.updateIdentity(ident);
        		anyChanged = true;
        	}
        }
        if(anyChanged) {
        	context.getContentResolver().notifyChange(MusubiService.PRIMARY_CONTENT_CHANGED, null);
        }
        return true;
    }

	@Override
	public void activate(Context context, DbObj obj) {
        SQLiteOpenHelper databaseSource = App.getDatabaseSource(context);
		ObjectManager objectManger = new ObjectManager(databaseSource);
		FeedManager feedManager = new FeedManager(databaseSource);
		MObject object = objectManger.getObjectForId(obj.getLocalId());
		if (!(context instanceof Activity)) {
			return;
		}
		if(object == null) {
			return;
		}
		MFeed feed = feedManager.lookupFeed(object.feedId_);
		if(feed == null) {
			return;
		}
        Intent members = new Intent(context, MembersActivity.class);
        members.putExtra(MembersActivity.INTENT_EXTRA_FEED_URI, MusubiContentProvider.uriForItem(Provided.FEEDS, feed.id_));
		context.startActivity(members);
	}

	
	static class FacesAdapter extends CursorAdapter {
	    static IdentitiesManager sIdentitiesManager;

	    // TODO: No access to LoaderManager.
        private FacesAdapter(Context context,  Cursor c) {
            super(context, c);
        }

        public static CursorAdapter forObj(Context context, Obj obj) {
            MatrixCursor recruits = new MatrixCursor(new String[] {MIdentity.COL_ID});

            sIdentitiesManager = new IdentitiesManager(App.getDatabaseSource(context));
            List<IBHashedIdentity> ids = getIdentitiesForObj(obj);
            for (IBHashedIdentity id : ids) {
                MIdentity ident = sIdentitiesManager.getIdentityForIBHashedIdentity(id);
                if (ident != null) {
                    recruits.addRow(new Object[] { ident.id_ });
                }
            }

            return new FacesAdapter(context, recruits);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            ImageView face = new ImageView(context);
            int size = (int)context.getResources().getDisplayMetrics().density * 80;
            Gallery.LayoutParams spec = new Gallery.LayoutParams(size, size);
            face.setLayoutParams(spec);
            face.setScaleType(ScaleType.FIT_XY);
            face.setPadding(6, 6, 6, 6);
            face.setBackgroundResource(android.R.drawable.picture_frame);
            return face;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            long id = cursor.getLong(0);
            view.setTag(id);
            MIdentity stub = new MIdentity();
            stub.id_ = id;
            Bitmap bm = UiUtil.safeGetContactThumbnail(context, sIdentitiesManager, stub);
            if (bm != null) {
                ((ImageView)view).setImageBitmap(bm);
            } else {
                Log.w(TAG, "safe thumbnail lookup not safe");
            }
        }
	}

	/**
	 * Extracts the list of identities from an introductionObj.
	 */
	static List<IBHashedIdentity> getIdentitiesForObj(Obj obj) {
	    ArrayList<IBHashedIdentity> ids = new ArrayList<IBHashedIdentity>();
	    
	    JSONObject json = obj.getJson();
	    if (json == null) {
	        return ids;
	    }
	    JSONArray array;
        try {
            array = json.getJSONArray(IDENTITIES);
        } catch (JSONException e) {
            return ids;
        }

	    for(int i = 0; i < array.length(); ++i) {
            JSONObject identity;
            try {
                identity = array.getJSONObject(i);
            } catch (JSONException e) {
                Log.e(TAG, "identity entry in introduction access error", e);
                continue;
            }
            int authority = -1;
            String principalHashString = null;
            try {
                authority = identity.getInt(ID_AUTHORITY);
                principalHashString = identity.getString(ID_PRINCIPAL_HASH);
            } catch (JSONException e) {
                Log.e(TAG, "identity entry in introduction missing key fields", e);
                continue;
            }
            String principal = null;
            try {
                principal = identity.getString(ID_PRINCIPAL);
            } catch (JSONException e) {
            }
            String name = null;
            try {
                name = identity.getString(ID_NAME);
            } catch (JSONException e) {
            }
            if(name == null && principal == null) {
                //not much of an introduction
                continue;
            }
            
            byte[] principalHash = Base64.decode(principalHashString, Base64.DEFAULT);
            ids.add(new IBHashedIdentity(Authority.values()[authority], principalHash, 0));
	    }
	    return ids;
	}

	OnItemClickListener mIdentityClickListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> av, View v, int pos, long id) {
            long identityId = (Long)v.getTag();
            Uri identityUri = MusubiContentProvider.uriForItem(Provided.IDENTITIES_ID, identityId);
            String identityType = MusubiContentProvider.getType(Provided.IDENTITIES_ID);
            Intent view = new Intent(Intent.ACTION_VIEW);
            view.setDataAndType(identityUri, identityType);
            v.getContext().startActivity(view);
        }
    };

	@Override
	public void getSummaryText(Context context, TextView view, FeedSummary summary) {
		view.setTypeface(null, Typeface.ITALIC);
		view.setText(summary.getSender() + " introduced new people to the feed.");	
	}
}
