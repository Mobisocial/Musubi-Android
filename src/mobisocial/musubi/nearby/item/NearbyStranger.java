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

import mobisocial.crypto.IBHashedIdentity;
import mobisocial.musubi.App;
import mobisocial.musubi.R;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.ui.NearbyActivity;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.widget.Toast;

public class NearbyStranger extends NearbyItem {
        final Activity mContext;
		private IdentitiesManager identitiesManager_;
        public NearbyStranger(Activity context, String name, Uri uri, String mimeType) {
            super(Type.PERSON, name, uri, null);
            mContext = context;
            identitiesManager_ = new IdentitiesManager(App.getDatabaseSource(context));
        }

        @Override
        public Bitmap getIcon() {
            return BitmapFactory.decodeResource(mContext.getResources(), R.drawable.ic_contact_picture);
        }

        public String getDetail() {
        	return "";
        }
        @Override
        public void view(NearbyActivity activity) {
        	IBHashedIdentity hid = IdentitiesManager.ibHashedIdentityForUri(uri);
        	MIdentity ident = identitiesManager_.getIdentityForIBHashedIdentity(hid);
        	if(ident.contactId_ != null) {
        	    Toast.makeText(mContext, "implement me", Toast.LENGTH_SHORT).show();
            } else {
            	Toast.makeText(mContext, "Didn't do anything...", Toast.LENGTH_SHORT).show();
            }
            return;
        }
    }