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

package mobisocial.musubi.nearby.item;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Date;

import mobisocial.crypto.IBHashedIdentity;
import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.musubi.App;
import mobisocial.musubi.Helpers;
import mobisocial.musubi.R;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MFeed.FeedType;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.helpers.FeedManager;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.objects.JoinRequestObj;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.provider.MusubiContentProvider.Provided;
import mobisocial.musubi.ui.NearbyActivity;
import mobisocial.musubi.util.Util;
import mobisocial.socialkit.Obj;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

public class NearbyFeed extends NearbyItem {
        final Context mContext;
		public final String mGroupName;
		public final String mSharerName;
		public final byte[] mSharerHash;
		public final byte[] mGroupCapability;
		public final byte[] mThumbnail;
		public final Authority mSharerType;
		public final int mMemberCount;
        public NearbyFeed(Context context, String group_name,
				byte[] group_capability, String sharer_name, Authority sharer_type, byte[] sharer_hash, byte[] thumbnail, int member_count) {
            super(Type.FEED, sharer_name, Uri.parse("nearbyfeed://" + new BigInteger(sharer_hash).toString(16) + "/" + new BigInteger(group_capability).toString(16)), null);
            mContext = context;
            mGroupName = group_name;
            mSharerName = sharer_name;
            mSharerHash = sharer_hash;
            mGroupCapability = group_capability;
            mThumbnail = thumbnail;
            mMemberCount = member_count;
            mSharerType = sharer_type;
		}

		@Override
        public Bitmap getIcon() {			
			if(mThumbnail != null)
				return BitmapFactory.decodeByteArray(mThumbnail, 0, mThumbnail.length);
            return BitmapFactory.decodeResource(mContext.getResources(), R.drawable.ic_contact_picture);
        }
		@Override
		public String getDetail() {
			return mGroupName;
		}

        @Override
        public void view(final NearbyActivity activity) {
        	new AlertDialog.Builder(mContext)
        		.setTitle("Join Group")
        		.setMessage("Would you like to join the group '" + mGroupName + "'?")
        		.setNegativeButton("No", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
					}
				})
        		.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						SQLiteOpenHelper db = App.getDatabaseSource(mContext);
						db.getWritableDatabase().beginTransaction();
						MFeed feed = null;
						MIdentity me = null;
						boolean need_join = false;
						try {
							IdentitiesManager im = new IdentitiesManager(db);
							IBHashedIdentity hid = new IBHashedIdentity(mSharerType, mSharerHash, 0);
							MIdentity sharer = im.ensureClaimedIdentity(hid);
							me  = im.getOwnedIdentities().get(1);
	
							FeedManager fm = new FeedManager(db);
							feed = fm.lookupFeed(FeedType.EXPANDING, mGroupCapability);
							if(feed == null) {
								need_join = true;
								feed = new MFeed();
								feed.capability_ = mGroupCapability;
								feed.thumbnail_ = mThumbnail;
								//TODO: this is causing a forced mapping to named feeds right now
								feed.name_ = mGroupName;
								feed.type_ = MFeed.FeedType.EXPANDING;
								feed.shortCapability_ = Util.shortHash(feed.capability_);
								feed.accepted_ = true;
								fm.insertFeed(feed);
								fm.ensureFeedMember(feed.id_, me.id_);
								fm.ensureFeedMember(feed.id_, sharer.id_);
							} else {
								feed.latestRenderableObjTime_ = System.currentTimeMillis();
								fm.updateFeed(feed);
							}
							
							db.getWritableDatabase().setTransactionSuccessful();
							activity.finish();
						} finally {
							db.getWritableDatabase().endTransaction();
						}
						if(need_join) {
							Obj joinObj = JoinRequestObj.from(Arrays.asList(me));
		                    Helpers.sendToFeed(mContext, joinObj, MusubiContentProvider.uriForItem(Provided.FEEDS_ID, feed.id_));
						}
					}
				}).show();
        }
    }