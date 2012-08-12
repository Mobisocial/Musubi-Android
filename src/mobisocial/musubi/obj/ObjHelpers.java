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

package mobisocial.musubi.obj;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import mobisocial.crypto.IBHashedIdentity;
import mobisocial.musubi.App;
import mobisocial.musubi.Helpers;
import mobisocial.musubi.R;
import mobisocial.musubi.feed.iface.Activator;
import mobisocial.musubi.feed.iface.DbEntryHandler;
import mobisocial.musubi.feed.iface.FeedRenderer;
import mobisocial.musubi.model.MApp;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MObject;
import mobisocial.musubi.model.helpers.AppManager;
import mobisocial.musubi.model.helpers.DatabaseManager;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.model.helpers.ObjectManager;
import mobisocial.musubi.obj.iface.ObjAction;
import mobisocial.musubi.objects.AppObj;
import mobisocial.musubi.objects.AppStateObj;
import mobisocial.musubi.objects.DeleteObj;
import mobisocial.musubi.objects.FeedNameObj;
import mobisocial.musubi.objects.FileObj;
import mobisocial.musubi.objects.IntroductionObj;
import mobisocial.musubi.objects.JoinRequestObj;
import mobisocial.musubi.objects.LikeObj;
import mobisocial.musubi.objects.LocationObj;
import mobisocial.musubi.objects.MusicObj;
import mobisocial.musubi.objects.MusubiWizardObj;
import mobisocial.musubi.objects.OutOfBandInvitedObj;
import mobisocial.musubi.objects.PhoneStateObj;
import mobisocial.musubi.objects.PictureObj;
import mobisocial.musubi.objects.ProfileObj;
import mobisocial.musubi.objects.SharedSecretObj;
import mobisocial.musubi.objects.StatusObj;
import mobisocial.musubi.objects.StoryObj;
import mobisocial.musubi.objects.UnknownObj;
import mobisocial.musubi.objects.VideoObj;
import mobisocial.musubi.objects.VoiceObj;
import mobisocial.musubi.objects.WebAppObj;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.provider.MusubiContentProvider.Provided;
import mobisocial.musubi.ui.MusubiBaseActivity;
import mobisocial.musubi.ui.ViewProfileActivity;
import mobisocial.musubi.ui.fragments.FeedListFragment.FeedSummary;
import mobisocial.musubi.ui.util.AddToWhitelistListener;
import mobisocial.musubi.ui.util.EmojiSpannableFactory;
import mobisocial.musubi.ui.util.UiUtil;
import mobisocial.musubi.ui.widget.DbObjCursorAdapter;
import mobisocial.musubi.ui.widget.DbObjCursorAdapter.DbObjCursor;
import mobisocial.musubi.util.IdentityCache.CachedIdentity;
import mobisocial.musubi.util.RelativeDate;
import mobisocial.musubi.util.Util;
import mobisocial.musubi.webapp.WebAppActivity;
import mobisocial.socialkit.Obj;
import mobisocial.socialkit.musubi.DbFeed;
import mobisocial.socialkit.musubi.DbObj;
import mobisocial.socialkit.musubi.Musubi;
import mobisocial.socialkit.obj.MemObj;

import org.json.JSONObject;

import android.app.ActionBar.LayoutParams;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.Spannable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TextView.BufferType;
import android.widget.Toast;

public final class ObjHelpers {
    public static final String TAG = "ObjHelpers";
    private static OnClickViewProfile sViewProfileAction;

    private static final int sDeletedColor = Color.parseColor("#66FF3333");
    private static final boolean showLikeCount = true;

    // Basic property names for all objects
    public static final String TYPE = "type";
    public static final String FEED_NAME = "feedName";
    public static final String SEQUENCE_ID = "sequenceId";
    public static final String TIMESTAMP = "timestamp";
    public static final String APP_ID = "appId";

    // A ContentValue field used when a Musubi sends data on a 3rd party app's behalf
    // See AppObj.CLAIMED_APP_ID for json value
    public static final String CALLER_APP_ID = "callerAppId";

    /**
     * Common types that don't need to be stored in the database,
     * but also don't require a {@link DbEntryHandler} class.
     */
    public static final Set<String> sDiscardTypes = new HashSet<String>();
    static {
        sDiscardTypes.add("locUpdate");
        sDiscardTypes.add("userAttributes");
    }

    /**
     * {@see DbRelation}
     */
    public static final String TARGET_HASH = "target_hash";
    public static final String TARGET_RELATION = "target_relation";

    private static final List<DbEntryHandler> objs = new ArrayList<DbEntryHandler>();
    private static UnknownObj mUnknownObjHandler = new UnknownObj();
    static {
        objs.add(new AppObj());
        objs.add(new AppStateObj());
        objs.add(new StoryObj());
        objs.add(new ProfileObj());
        objs.add(new StatusObj());
        objs.add(new IntroductionObj());
        objs.add(new JoinRequestObj());
        objs.add(new OutOfBandInvitedObj());
        objs.add(new LocationObj());
        objs.add(new PictureObj());
        objs.add(new VideoObj());
        objs.add(new VoiceObj());
        objs.add(new PhoneStateObj());
        objs.add(new MusicObj());
        objs.add(new SharedSecretObj()) ;
        objs.add(new DeleteObj());
        objs.add(new LikeObj());
        objs.add(new MusubiWizardObj());
        objs.add(new FeedNameObj());
        objs.add(new FileObj());
        objs.add(new WebAppObj());
    }

	public static FeedRenderer getFeedRenderer(String type) {
	    for (DbEntryHandler obj : objs) {
            if (obj instanceof FeedRenderer && obj.getType().equals(type)) {
                return (FeedRenderer)obj;
            }
        }
        return getGenericRenderer();
	}

	private static FeedRenderer genericRenderer;
	static FeedRenderer getGenericRenderer() {
		if (genericRenderer == null) {
			genericRenderer = new FeedRenderer() {
				@Override
				public void render(Context context, View view, DbObjCursor obj,
						boolean allowInteractions) throws Exception {
					LinearLayout frame = (LinearLayout)view;
					frame.removeAllViews();

			        JSONObject json = obj.getJson();
			        if (json == null) {
			            return;
			        }
			        if (json.has(Obj.FIELD_RENDER_TYPE)) {
			            String type = json.getString(Obj.FIELD_RENDER_TYPE);
			            if (Obj.RENDER_LATEST.equals(type)) {
			                new AppObj().render(context, frame, obj, allowInteractions);
			            }
			        } else if (obj.getJson().has(Obj.FIELD_HTML)) {
			            String html = obj.getJson().optString(Obj.FIELD_HTML);
			            AppStateObj.renderHtml(context, frame, html);   
			        }
				}
				
				@Override
				public View createView(Context context, ViewGroup parent) {
					LinearLayout frame = new LinearLayout(context);
					return frame;
				}

				@Override
				public void getSummaryText(Context context, TextView view, FeedSummary summary) {
					view.setTypeface(null, Typeface.ITALIC);
					view.setText(summary.getSender() + " just did something.");
				}
			};
		}
		return genericRenderer;
	}

	public static Activator getActivator(String type) {
        for (DbEntryHandler obj : objs) {
            if (obj instanceof Activator && obj.getType().equals(type)) {
                return (Activator)obj;
            }
        }
        return null;
	}

	public static String[] getRenderableTypes() {
	    List<String> renderables = new ArrayList<String>();
	    for (DbEntryHandler o : objs) {
	        if (o instanceof FeedRenderer){
	            renderables.add(o.getType());
	        }
	    }
	    return renderables.toArray(new String[renderables.size()]);
	}

	public static DbEntryHandler forType(String requestedType) {
	    if (requestedType == null) {
	        return null;
	    }
        for (DbEntryHandler type : objs) {
            if (type.getType().equals(requestedType)) {
                return type;
            }
        }
        return mUnknownObjHandler;
    };

    /**
     * {@see DbObject#RENDERABLE}
     */
    @Deprecated
    public static String getFeedObjectClause(String[] types) {
    	if(types == null) {
    		types = ObjHelpers.getRenderableTypes();
    	}
        StringBuffer allowed = new StringBuffer();
        for (String type : types) {
            allowed.append(",'").append(type).append("'");
        }
        return MObject.COL_TYPE + " in (" + allowed.substring(1) + ")";
    }

    public static class ItemClickListener implements View.OnClickListener {
        private static ItemClickListener sInstance;
    
        public static ItemClickListener getInstance() {
            if (sInstance == null) {
                sInstance = new ItemClickListener();
            }
            return sInstance;
        }
    
        @Override
        public void onClick(View v) {
        	Context context = v.getContext();
            Musubi musubi = App.getMusubi(context);
            Object tag = v.getTag();
            if (tag == null || !(tag instanceof Long)) {
                Log.d(TAG, "no id for dbobj " + v + "; parent: " + v.getParent());
                return;
            }
            long objId = (Long)tag;
    
            DbObj obj = musubi.objForId(objId);
            if (MusubiBaseActivity.DBG) Log.i(TAG, "Clicked object " + obj.getType());
            Activator activator = getActivator(obj.getType());
            if (activator != null) {
                activator.activate(context, obj);
            } else {
                activateGeneric(context, obj);
            }
        }
    }

    public static class ItemLongClickListener implements View.OnLongClickListener {
        private final Context mContext;
		private Musubi mMusubi;
    
        private ItemLongClickListener(Context context) {
            mContext = context;
            mMusubi = App.getMusubi(context);
        }
    
        public static ItemLongClickListener getInstance(Context context) {
        	return new ItemLongClickListener(context);
        }
    
        @Override
        public boolean onLongClick(View v) {
            if (v == null || v.getTag() == null) {
                //Log.d(TAG, "missing objId for " + v);
                return false;
            }
            long objId = (Long)v.getTag();
            DbObj obj = mMusubi.objForId(objId);
            //maybe it was deleted and disappeared
            if(obj == null)
            	return false;
            ObjHelpers.createActionDialog(mContext, obj).show();
            return false;
        }
    }

    public static Dialog createActionDialog(final Context context, final DbObj obj) {
        final DbEntryHandler dbType = forType(obj.getType());
        final List<ObjAction> actions = new ArrayList<ObjAction>();
        for (ObjAction action : ObjActions.getObjActions()) {
            if (action.isActive(context, dbType, obj)) {
                actions.add(action);
            }
        }
        final String[] actionLabels = new String[actions.size()];
        int i = 0;
        for (ObjAction action : actions) {
            actionLabels[i++] = action.getLabel(context);
        }
        return new AlertDialog.Builder(context)
                .setItems(actionLabels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        actions.get(which).actOn(context, dbType, obj);
                    }
                }).create();
    }

    /**
     * A re-usable StringBuilder.
     */
    static final StringBuilder sStringBuilder = new StringBuilder(50);

    /**
     * Binds the generic frame of a feed object. This method is not threadsafe.
     *
     * @param v the view to bind
     * @param context standard activity context
     * @param c the cursor source for the object in the db object table.
     * Must include _id in the projection.
     * 
     * @param allowInteractions controls whether the bound view is
     * allowed to intercept touch events and do its own processing.
     */
    public static void bindObjViewFrame(Context context, DatabaseManager db, ViewGroup frame,
    		DbObjCursorAdapter.ViewHolder viewHolder, DbObjCursor objRow) {
        if (objRow == null) {
            Log.d(TAG, "Missing object!");
            viewHolder.senderName.setText("Missing object!");
            return;
        }

        frame.setTag(objRow.objId);

        final CachedIdentity sender = App.getContactCache(context).get(objRow.senderId);
        if (sender == null) {
            viewHolder.senderName.setText("Message from unknown contact.");
            Log.w(TAG, "unknown contact " + objRow.senderId);
            return;
        }

        Spannable span = EmojiSpannableFactory.getInstance(context).newSpannable(sender.name);
        viewHolder.senderName.setText(span, BufferType.SPANNABLE);
        //viewHolder.senderName.setText(sender.name);

        final ImageView icon = viewHolder.senderIcon;
        if (sViewProfileAction == null) {
            sViewProfileAction = new OnClickViewProfile((Activity)context);
        }
        if (!sender.midentity.whitelisted_ && !sender.midentity.owned_ &&
                sender.midentity.type_ != IBHashedIdentity.Authority.Local) {
	        viewHolder.addContact.setVisibility(View.VISIBLE);
	        viewHolder.addContact.setOnClickListener(new AddToWhitelistListener(context, sender.midentity));
        } else {
        	viewHolder.addContact.setVisibility(View.GONE);
        }
        
        icon.setTag(sender.midentity.id_);
        icon.setOnClickListener(sViewProfileAction);

    	icon.setImageBitmap(sender.thumbnail);
        
        if (objRow.deleted) {
            frame.setBackgroundColor(sDeletedColor);
        } else {
            frame.setBackgroundColor(Color.TRANSPARENT);
        }

        sStringBuilder.setLength(0);        
        //check to see if we should print date or not
        sStringBuilder.append(RelativeDate.getRelativeDate(objRow.timestamp));

        String appId = objRow.appId;
        if (appId != null && !MusubiContentProvider.SUPER_APP_ID.equals(appId)) {
            if(appId != null) {
	            if (objRow.appName != null) {
	            	sStringBuilder.append(" via ").append(objRow.appName);
	            }
            }
        }
        if (sStringBuilder.length() == 0) {
            viewHolder.timeText.setVisibility(View.GONE);
        } else {
        	viewHolder.timeText.setVisibility(View.VISIBLE);
        	viewHolder.timeText.setText(sStringBuilder);
        }
        frame.setTag(objRow.objId);
        
		try {
	        if (!objRow.sent) {
	        	viewHolder.sendingIcon.setVisibility(View.VISIBLE);
	            viewHolder.attachmentsIcon.setVisibility(View.GONE);
	            viewHolder.attachmentsText.setVisibility(View.GONE);
	        } else {
	        	viewHolder.sendingIcon.setVisibility(View.GONE);
	            if (!showLikeCount) {
	                viewHolder.attachmentsIcon.setVisibility(View.GONE);
	                viewHolder.attachmentsText.setVisibility(View.GONE);
	            } else {
	                viewHolder.attachmentsIcon.setVisibility(View.VISIBLE);

                    int likeCount = objRow.likeCount;
                    sStringBuilder.setLength(0);
                    sStringBuilder.append("+").append(likeCount);
                    viewHolder.attachmentsText.setText(sStringBuilder);
                    if (likeCount > 0) {
                    	viewHolder.attachmentsIcon.setImageResource(R.drawable.ic_menu_love_red); 
                    	viewHolder.attachmentsText.setVisibility(View.VISIBLE);
                    } else {
                    	viewHolder.attachmentsIcon.setImageResource(R.drawable.ic_menu_love);
                    	viewHolder.attachmentsText.setVisibility(View.INVISIBLE);
                    }

                    viewHolder.attachmentsIcon.setTag(objRow.objId);
                    LikeListener ll = LikeListener.getInstance();
                    viewHolder.attachmentsIcon.setOnClickListener(ll);
                    viewHolder.attachmentsText.setOnClickListener(ll);
	            }
	        }
		} catch (Throwable t) {
			Log.e(TAG, "failed to handle rendering of an obj", t);
			TextView tv = new TextView(context);
			tv.setText("Unable to render object: " + t.getLocalizedMessage());
			//TODO: this should fill in something
			frame.removeAllViews();
			frame.addView(tv, LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
		}
    }

    public static boolean isRenderable(Obj obj) {
        if (forType(obj.getType()) instanceof FeedRenderer) {
            return true;
        }
        JSONObject json = obj.getJson();
        if (json == null) {
            return false;
        }
        return json.has(Obj.FIELD_HTML) || json.has(Obj.FIELD_RENDER_TYPE);
    }

    private static void activateGeneric(Context context, DbObj obj) {
        String pkg = obj.getAppId();
        if (MusubiContentProvider.isSuperApp(pkg)) {
        	return;
        }

        Log.d(TAG, "Activating for app " + pkg);
    	MApp app = new AppManager(App.getDatabaseSource(context)).lookupAppByAppId(obj.getAppId());

        String mimeType = mimeTypeOf(obj.getType());
        Intent view = new Intent(Intent.ACTION_VIEW);
        view.setDataAndType(obj.getUri(), mimeType);
        view.putExtra(Musubi.EXTRA_FEED_URI, obj.getContainingFeed().getUri());

        if (app != null && app.webAppUrl_ != null) {
            view.setClass(context, WebAppActivity.class);
            view.putExtra(WebAppActivity.EXTRA_APP_URI, Uri.parse(app.webAppUrl_));
            view.putExtra(WebAppActivity.EXTRA_APP_ID, pkg);
        } else if (!MusubiContentProvider.UNKNOWN_APP_ID.equals(pkg)) {
            // When Musubi shares data using the SEND intent,
            // the obj gets an unknown app id.
            view.setPackage(pkg);
        }
        try {
            context.startActivity(view);
        } catch (ActivityNotFoundException e) {
            Intent m = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg));
            try {
                context.startActivity(m);
            } catch (ActivityNotFoundException e2) {
                Toast.makeText(context, "Cannot view this object.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public static class OnClickViewProfile implements View.OnClickListener {
        private final Activity mmContext;
        
        public OnClickViewProfile(Activity c) {
            mmContext = c;
        }

        @Override
        public void onClick(View v) {
            Log.e(TAG, "profile for " + v.getTag());
            SQLiteOpenHelper SQLiteOpenHelper = App.getDatabaseSource(mmContext);
            IdentitiesManager identitiesManager = new IdentitiesManager(SQLiteOpenHelper);
            MIdentity person = identitiesManager.getIdentityForId(((Long)v.getTag()).longValue());
            Log.w(TAG, UiUtil.safeNameForIdentity(person));
            Intent intent = new Intent(mmContext, ViewProfileActivity.class);
            intent.putExtra(ViewProfileActivity.PROFILE_ID, ((Long)v.getTag()).longValue());
            mmContext.startActivity(intent);
        }
    }

    private static class LikeListener implements View.OnClickListener {
        private static LikeListener sInstance;

        private LikeListener() {
        }

        public static LikeListener getInstance() {
            if (sInstance == null) {
                sInstance = new LikeListener();
            }
            return sInstance;
        }

        @Override
        public void onClick(View v) {
            View p = (View)(v.getParent());
            ((ImageView)p.findViewById(R.id.obj_attachments_icon)).setImageResource(
                    R.drawable.ic_menu_love_red);

            Object objIdObj = v.getTag();
            if (objIdObj == null) {
            	Log.e(TAG, "Error liking object; no tag found");
            	return;
            }
            long objId = (Long)objIdObj;
            MObject obj = new ObjectManager(App.getDatabaseSource(v.getContext())).getObjectForId(objId);
            
            //unsent objects may be displayed, don't let liking them crash
            if(obj.universalHash_ == null)
            	return;
            String hashString = Util.convertToHex(obj.universalHash_);
            long feedId = obj.feedId_;
            MemObj like = LikeObj.forObj(hashString);
            Helpers.sendToFeed(v.getContext(), like, DbFeed.uriForId(feedId));
        }
    }

    /**
     * Checks a type against a list of internally known types that
     * should not be stored in the database.
     */
    public static boolean isDiscardableObjType(String type) {
        return sDiscardTypes.contains(type);
    };

    public static Uri uriForId(long id) {
        return MusubiContentProvider.uriForItem(Provided.OBJECTS, id);
    }

    /**
     * Returns the hex encoding of a byte array, which is the standard
     * string representation for an Obj's universal hash.
     */
    public static String hashToString(byte[] data) {
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < data.length; i++) {
            int halfbyte = (data[i] >>> 4) & 0x0F;
            int two_halfs = 0;
            do {
                if ((0 <= halfbyte) && (halfbyte <= 9)) {
                    buf.append((char) ('0' + halfbyte));
                } else {
                    buf.append((char) ('a' + (halfbyte - 10)));
                }

                halfbyte = data[i] & 0x0F;
            } while (two_halfs++ < 1);
        }
        return buf.toString();
    }

    /**
     * Converts the string representation of a hash to a byte array.
     */
    public static byte[] stringToHash(String hashString) {
        if (hashString.length() % 2 != 0) {
            throw new IllegalArgumentException("Hash must have even length");
        }
        int j = 0;
        byte[] hash = new byte[hashString.length() / 2];
        for (int i = 0; i < hashString.length(); i += 2) {
            hash[j++] = Byte.parseByte(hashString.substring(i, i + 2));
        }
        return hash;
    }

    public static final String mimeTypeOf(String objType) {
        return "vnd.musubi.obj/" + objType;
    }
}
