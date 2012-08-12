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

package mobisocial.musubi.obj.action;

import mobisocial.musubi.Helpers;
import mobisocial.musubi.feed.iface.DbEntryHandler;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.helpers.DatabaseManager;
import mobisocial.musubi.obj.iface.ObjAction;
import mobisocial.musubi.objects.FeedNameObj;
import mobisocial.musubi.objects.PictureObj;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.provider.MusubiContentProvider.Provided;
import mobisocial.musubi.ui.FeedDetailsActivity;
import mobisocial.musubi.ui.util.UiUtil;
import mobisocial.socialkit.Obj;
import mobisocial.socialkit.musubi.DbObj;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;

public class SetFeedPhotoAction extends ObjAction {
    public void onAct(Context context, DbEntryHandler objType, DbObj obj) {
        byte[] raw = obj.getRaw();
        

        Uri feedUri = obj.getContainingFeed().getUri();
        
        DatabaseManager db = new DatabaseManager(context);
        
        Long feedId = Long.parseLong(feedUri.getLastPathSegment());
		MFeed feed = db.getFeedManager().lookupFeed(feedId);
		String name = UiUtil.getFeedNameFromMembersList(db.getFeedManager(), feed);
		Obj feedNameObj = FeedNameObj.from(name, raw);
		Helpers.sendToFeed(context, feedNameObj, feedUri);
    }

    @Override
    public String getLabel(Context context) {
        return "Set as Feed Photo";
    }

    @Override
    public boolean isActive(Context context, DbEntryHandler objType, DbObj obj) {
        return (objType instanceof PictureObj);
    }
}
