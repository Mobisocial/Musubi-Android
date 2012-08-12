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

package mobisocial.musubi.ui.util;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.crypto.IBIdentity;
import mobisocial.musubi.App;
import mobisocial.musubi.R;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MMyAccount;
import mobisocial.musubi.model.helpers.FeedManager;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.model.helpers.MyAccountManager;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.provider.MusubiContentProvider.Provided;
import mobisocial.musubi.service.MusubiService;
import mobisocial.musubi.ui.widget.MultiIdentitySelector;
import mobisocial.musubi.util.IdentityCache;
import mobisocial.musubi.util.IdentityCache.CachedIdentity;
import mobisocial.musubi.util.Util;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.util.Log;

public class UiUtil {
	/**
     * Do some pretty printing on feed names?
     * If it's a fixed feed and the name is currently blank, give it a default name
     */
    public static String getFeedNameFromMembersList(FeedManager feedManager, MFeed feed) {
    	if(feed.name_ != null && feed.name_.length() > 0) {
    		return feed.name_;
    	}
		MIdentity[] members = feedManager.getFeedMembersGroupedByVisibleName(feed);
    	
    	StringBuilder name = new StringBuilder(80);
    	for (int i = 0; i < members.length; i++) {
    		String one_name = internalSafeNameForIdentity(members[i]);
    		if(one_name == null)
    			continue;
    		if(name.length() > 0) {
    			name.append(", ");
    		}
        	name.append(one_name);
    	}
    	if(name.length() > 0)
    		return name.toString();
    	return "Top Secret";
    }

	public static String internalSafeNameForIdentity(MIdentity ident) {
		if (ident == null) {
			return null;
		}
	    if(ident.musubiName_ != null) {
	    	return ident.musubiName_;
	    } else if(ident.name_ != null) {
	    	return ident.name_;
	    } else if(ident.principal_ != null) {
			return safePrincipalForIdentity(ident);
	    } else {
	    	return null;
	    }
	}

	public static String safeNameForIdentity(MIdentity ident) {
	    String name = internalSafeNameForIdentity(ident);
	    if(name != null) {
	    	return name;
	    }
    	return "Unknown";
	}
	public static String safePrincipalForIdentity(MIdentity ident) {
		//face book identities should pretty much always have an associated name
		//for us to use.  We consider the users name to be their identity at facebook
		//for the purposes of display.
    	if(ident.type_ == Authority.Facebook && ident.name_ != null) {
    		return "Facebook: " + ident.name_;
		}
	    if(ident.principal_ != null) {
	    	if(ident.type_ == Authority.Email) 
	    		return ident.principal_;
	    	if(ident.type_ == Authority.Facebook) 
	    		return "Facebook #" + ident.principal_;
	    	return ident.principal_;
	    }
    	if(ident.type_ == Authority.Email) {
			return "Email User";
		}
    	if(ident.type_ == Authority.Facebook) {
			return "Facebook User";
		}
    	//we prefer not to say <unknown> anywhere, so principal will be blank
    	//in cases where it would be displayed on the screen and we don't
    	//have anything reasonable to display
    	return "";
	}

	public static Bitmap safeGetContactPicture(final Context context,
			IdentitiesManager identitiesManager, MIdentity sender) {
		Bitmap img = null;
    	identitiesManager.getMusubiThumbnail(sender);
    	byte[] thumbnail = sender.musubiThumbnail_;
    	if(thumbnail == null) {
        	identitiesManager.getThumbnail(sender);
        	thumbnail = sender.thumbnail_;
    	}
    	if(thumbnail != null) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPurgeable = true;
            options.inInputShareable = true;
    		img = BitmapFactory.decodeByteArray(thumbnail, 0, thumbnail.length, options);
    	} else {
    		//hopefully this implicitly reuses the same one?
           	img = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_contact_picture);
    	}
		return img;
	}

	public static Bitmap safeGetContactThumbnail(final Context context,
			IdentitiesManager identitiesManager, MIdentity sender) {
		IdentityCache cache = App.getContactCache(context);
	    CachedIdentity cached = cache.get(sender.id_);
    	return (cached == null) ? null : cached.thumbnail;
	}

	public static Bitmap safeGetContactThumbnailWithoutCache(IdentitiesManager identitiesManager, long senderId) {
		MIdentity sender = identitiesManager.getIdentityWithThumbnailsForId(senderId);
    	byte[] thumbnail = sender.musubiThumbnail_;
    	if(thumbnail == null) {
        	identitiesManager.getThumbnail(sender);
        	thumbnail = sender.thumbnail_;
    	}
    	if(thumbnail != null) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPurgeable = true;
            options.inInputShareable = true;
    		Bitmap b = BitmapFactory.decodeByteArray(thumbnail, 0, thumbnail.length, options);
    		return b;
    	} else {
    		return null;
    	}
	}

	public static Bitmap getDefaultContactThumbnail(Context context) {
		//hopefully this implicitly reuses the same one?
       	return BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_contact_picture);
	}

	public static boolean addToWhitelistsIfNecessary(FeedManager fm, MyAccountManager am, MIdentity[] members, boolean notify) {
		boolean needProfilePush = false;
        LinkedList<MIdentity> owned = FeedManager.getOwners(members);
        for(MIdentity persona : owned) {
    		MMyAccount provisional_account = am.getProvisionalWhitelistForIdentity(persona.id_);
            MMyAccount whitelist_account = am.getWhitelistForIdentity(persona.id_);
        
            for (MIdentity recipient : members) {
            	needProfilePush |= fm.addToWhitelistsIfNecessary(provisional_account, whitelist_account, persona, recipient);
			}
        }
        if(needProfilePush && notify) {
        	
        }
        return needProfilePush;
	}

	static final String[] sPositiveAdjectives = {
		"powerful",		"fascinating",		"authentic",		"loyal",
		"courageous",		"suave",		"jubilant",		"creative",
		"masterly",		"vivacious", 	"adorable",
		"gallant",		"earnest",		"serene",		"superb",
		"gentle",		"captivating",
	};
	static final String[] sAnimals = {
		"panther",		"kitten",		"puppy",		"lion",
		"elephant",		"mouse",		"peacock",		"equine",
		"iguana",		"dolphin",		"gazelle", 	"zebra",
		"ox",		"bear",		"antelope",		"giraffe",
		"shark",		"chipmunk",
	};
	static final String[] sEvents = {
		"gathering",		"party",		"date",		"performance",
		"dinner",		"movie",		"safari",		"march",
		"dessert",		"journey",		"prize", 	"victory",
		"escape",		"run",		"dance",		"flight",
		"arrival",		"departure",
	};
	public static String randomFunName() {
		Random r = new Random();
		String name = "";
		name += sPositiveAdjectives[r.nextInt(sPositiveAdjectives.length)] + " ";
		name += sAnimals[r.nextInt(sAnimals.length)] + " ";
		name += sEvents[r.nextInt(sEvents.length)];
		return name;
	}

	//i put this here because when we upgrade the multiidentityselector this is still useful
	public static Uri addedContact(Context context, Intent data,
			MultiIdentitySelector identitySelector) {
		if(data == null || data.getData() == null) {
			Log.w("uiutil", "unexpected add contact called with blank intent");
			return null;
		}
		//reread the contact list so that its possible for us to fill in what they typed
		context.getContentResolver().notifyChange(MusubiService.FORCE_RESCAN_CONTACTS, null);
		//fetch the actual value of the email and name for the picker
		Cursor c = context.getContentResolver().query(data.getData(), new String[] { Contacts.DISPLAY_NAME, Contacts._ID }, null, null, null);
		//TODO: this will require rework for phone number support
		try {
			while(c.moveToNext()) {
				String name = c.getString(0);
				long id = c.getLong(1);
				//select the highest id email under the contact
				Cursor cm = context.getContentResolver().query( 
				        ContactsContract.CommonDataKinds.Email.CONTENT_URI, 
				        new String[] { ContactsContract.CommonDataKinds.Email.DATA },
				        ContactsContract.CommonDataKinds.Email.CONTACT_ID + " = ?", 
				        new String[]{String.valueOf(id)}, ContactsContract.CommonDataKinds.Email._ID + " DESC"); 
				try {
				    while (cm.moveToNext()) { 
				        String email = cm.getString(0);
				        SQLiteOpenHelper databaseSource = App.getDatabaseSource(context);
				        IdentitiesManager identitiesManager = new IdentitiesManager(databaseSource);
				        IBIdentity ibid = new IBIdentity(Authority.Email, email, 0);
				        SQLiteDatabase db = databaseSource.getWritableDatabase();
				        db.beginTransaction();
				        long identId = identitiesManager.getIdForIBHashedIdentity(ibid);
				        if(identId == 0) {
				        	MIdentity ident = new MIdentity();
				        	ident.type_ = Authority.Email;
				        	ident.principal_ = email;
				        	ident.principalHash_ = Util.sha256(ident.principal_.getBytes());
				        	ident.principalShortHash_ = Util.shortHash(ident.principalHash_);
				        	ident.whitelisted_ = true;
				        	ident.name_ = name;
				        	identId = identitiesManager.insertIdentity(ident);
				        }
				        db.setTransactionSuccessful();
				        db.endTransaction();
				        if(identId > 0) { 	
				        	if(identitySelector != null)
				        		identitySelector.addIdentity(name, identId);
		    				return MusubiContentProvider.uriForItem(Provided.IDENTITIES, identId);
				        }
				    } 
				} finally {
					cm.close();
				}
			}
		} finally {
			c.close();
		}
		return null;
	}

	public static PeopleDetails populatePeopleDetails(Context context, IdentitiesManager im,
			long[] identityIds, IdentityCache identityCache, PeopleDetails details) {
		StringBuilder name = new StringBuilder(40);
		boolean noNames = true;
		List<Bitmap> thumbnails = details.images;
		thumbnails.clear();
		int unownedidentities = 0;

		for (long id : identityIds) {
			CachedIdentity cached = identityCache.get(id);
			if (cached == null || cached.midentity.owned_) {
				continue;
			}
			unownedidentities++;

			if (thumbnails.size() >= 4) {
				continue;
			}

			if (cached.hasThumbnail) {
				if (!noNames) {
					name.append(", ");
				} else {
					noNames = false;
				}
				name.append(cached.name);
				thumbnails.add(cached.thumbnail);
			}
		}

		if (thumbnails.size() == 0 && identityIds.length > 0) {
			// no thumbnail, just use first guy
			CachedIdentity cached = identityCache.get(identityIds[0]);
			name.append(cached.name);
			thumbnails.add(cached.thumbnail);
			unownedidentities++;
		}

		if (unownedidentities > thumbnails.size()) {
			name.append(" and ").append(unownedidentities - thumbnails.size()).append(" more");
		}
		details.name = name.toString();
		return details;
	}

	public static class PeopleDetails {
		public final List<Bitmap> images = new ArrayList<Bitmap>();
		public String name;
	}

	private static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
	    // Raw height and width of image
	    final int height = options.outHeight;
	    final int width = options.outWidth;
	    int inSampleSize = 1;
	
	    if (height > reqHeight || width > reqWidth) {
	        if (width > height) {
	            inSampleSize = Math.round((float)height / (float)reqHeight);
	        } else {
	            inSampleSize = Math.round((float)width / (float)reqWidth);
	        }
	    }
	    return inSampleSize;
	}

    public static Bitmap decodeSampledBitmapFromByteArray(byte[] bytes, int reqWidth, int reqHeight) {

        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }
}
