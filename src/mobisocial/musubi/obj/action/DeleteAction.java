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
import mobisocial.musubi.obj.iface.ObjAction;
import mobisocial.musubi.objects.DeleteObj;
import mobisocial.socialkit.musubi.DbObj;
import mobisocial.socialkit.obj.MemObj;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

public class DeleteAction extends ObjAction {

    @Override
    public void onAct(Context context, DbEntryHandler objType, DbObj obj) {
        // ObjectManager om = new ObjectManager(App.getDatabaseSource(context));
        try {
        	//TODO: do with content provider... this method ignore the 
        	//feed uri for now
            String hash = obj.getUniversalHashString();
            Uri feedUri = obj.getContainingFeed().getUri();

            if (hash == null || hash.length() < 16) {
                Toast.makeText(context, "Error deleting.. Sorry!", Toast.LENGTH_SHORT).show();
                Log.e("DeleteObj", "Not deleting " + hash);
                return;
            }
        	MemObj deleteObj = DeleteObj.from(new String[] { hash }, false);
        	Helpers.sendToFeed(context, deleteObj, feedUri);
        	// delete locally in pipeline processor
        	//om.delete(obj.getLocalId());
        } finally {
        }
    }

    @Override
    public String getLabel(Context context) {
		return "Delete";
    }

}
