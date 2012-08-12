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

import java.util.List;

import mobisocial.musubi.App;
import mobisocial.musubi.feed.iface.DbEntryHandler;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.obj.iface.ObjAction;
import mobisocial.musubi.objects.PictureObj;
import mobisocial.musubi.ui.ViewProfileActivity;
import mobisocial.socialkit.musubi.DbObj;
import android.content.Context;
import android.content.Intent;

public class SetProfileObjAction extends ObjAction {
    public void onAct(Context context, DbEntryHandler objType, DbObj obj) {
        byte[] raw = obj.getRaw();
        IdentitiesManager idMan = new IdentitiesManager(App.getDatabaseSource(context));
        idMan.updateMyProfileThumbnail(context, raw, true);
        List<MIdentity> mine = idMan.getOwnedIdentities();
        assert(mine.size() > 0);
        Intent intent = new Intent(context, ViewProfileActivity.class);
        intent.putExtra(ViewProfileActivity.PROFILE_ID, mine.get(mine.size() - 1).id_);
        context.startActivity(intent);
    }

    @Override
    public String getLabel(Context context) {
        return "Set as Profile";
    }

    @Override
    public boolean isActive(Context context, DbEntryHandler objType, DbObj obj) {
        return (objType instanceof PictureObj);
    }
}
