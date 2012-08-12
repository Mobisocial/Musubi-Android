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

import mobisocial.socialkit.User;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import mobisocial.musubi.R;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.ui.NearbyActivity;

public class NearbyUser extends NearbyItem {
        final Context mContext;
        final User mUser;
        public NearbyUser(Context context, User user) {
            super(Type.PERSON, user.getName(), uriForUser(user), IdentitiesManager.MIME_TYPE);
            mContext = context;
            mUser = user;
        }

        private static Uri uriForUser(User user) {
            return null;
        }

        @Override
        public Bitmap getIcon() {
            return BitmapFactory.decodeResource(mContext.getResources(), R.drawable.ic_contact_picture);
        }

        @Override
        public void view(NearbyActivity activity) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeType);
            intent.setPackage(mContext.getPackageName());
            mContext.startActivity(intent);
        }
        public String getDetail() {
        	return "";
        }
    }