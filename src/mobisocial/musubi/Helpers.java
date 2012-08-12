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

package mobisocial.musubi;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.list.array.TLongArrayList;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.helpers.FeedManager;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.ui.EmailUnclaimedMembersActivity;
import mobisocial.socialkit.Obj;
import mobisocial.socialkit.musubi.DbObj;
import mobisocial.socialkit.obj.MemObj;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;

/**
 * A grab bag of utility methods. Avoid adding new code here.
 *
 */
public class Helpers {
    public static final String TAG = "Helpers";

    
    public static final void emailUnclaimedMembers(Context context, Obj obj, Uri feedUri) {
    	SQLiteOpenHelper helper = App.getDatabaseSource(context);
    	FeedManager feedManager = new FeedManager(helper);
    	IdentitiesManager identitiesManager = new IdentitiesManager(helper);
    	Cursor c = feedManager.getEmailReachableUnclaimedFeedMembersCursor(Long.parseLong(feedUri.getLastPathSegment()));

    	if (c.getCount() == 0) {
    		return;
    	}
		int max = c.getCount();
		ArrayList<String> recipients = new ArrayList<String>(max);
		TLongArrayList ids = new TLongArrayList(max);
		TIntArrayList authorities = new TIntArrayList(max);
		while(c.moveToNext()) {
			MIdentity member = identitiesManager.getIdentityForId(c.getLong(0));
			if(member.principal_ == null) {
				//we only know a hashed identity, so we can't contact them out of band
				continue;
			}
			recipients.add(member.principal_);
			ids.add(member.id_);
			authorities.add(member.type_.ordinal());
		}
		if(recipients.size() == 0) {
			//don't crash if we filtered all of the users out
			return;
		}
		Intent intent = new Intent(context, EmailUnclaimedMembersActivity.class);
		intent.putExtra(EmailUnclaimedMembersActivity.INTENT_EXTRA_FEED_URI, feedUri);
		intent.putExtra(android.content.Intent.EXTRA_BCC, recipients.toArray(new String[recipients.size()])); 
		intent.putExtra(EmailUnclaimedMembersActivity.INTENT_EXTRA_RECIPIENT_IDS, ids.toArray());
		intent.putExtra(EmailUnclaimedMembersActivity.INTENT_EXTRA_AUTHORITIES, authorities.toArray());
		context.startActivity(intent);
    }
    
    public static void sendToFeed(Context c, Obj obj, Uri feed) {
    	MusubiContentProvider.insertInBackground(obj, feed, null);
    }

    public static void sendToFeed(Context c, String callerAppId, Obj obj, Uri feedUri) {
    	MusubiContentProvider.insertInBackground(obj, feedUri, callerAppId);
    }

    /**
     * A convenience method for sending an object to multiple feeds.
     * TODO: This should be made much more efficient if it proves useful.
     */
    public static void sendToFeeds(Context c, MemObj obj, Collection<Uri> feeds) {
        for (Uri feed : feeds) {
            sendToFeed(c, obj, feed);
        }
    }

    public static void sendToEveryone(final Context c, Obj obj){
        Uri uri = Uri.parse(MusubiContentProvider.CONTENT_URI + "/feeds/me");

        BJDNotImplementedException.except("SendToEveryone is broken. Sorry!");
        ContentValues values = DbObj.toContentValues(uri, null, obj);
        c.getContentResolver().insert(uri, values);
    }

    private static HashMap<Long, SoftReference<MIdentity>> g_contacts = new HashMap<Long, SoftReference<MIdentity>>();
    public static void invalidateContacts() {
    	g_contacts.clear();
    }
    public static MIdentity getContact(Context context, long contactId) {
    	SoftReference<MIdentity> entry = g_contacts.get(contactId);
    	if(entry != null) {
    	    MIdentity c = entry.get();
	    	if(c != null)
	    		return c;
    	}
    	MIdentity c = new IdentitiesManager(App.getDatabaseSource(context)).getIdentityForId(contactId);
    	g_contacts.put(contactId, new SoftReference<MIdentity>(c));
    	return c;
    }
}
