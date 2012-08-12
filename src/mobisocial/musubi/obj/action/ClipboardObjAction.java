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

import mobisocial.musubi.feed.iface.DbEntryHandler;
import mobisocial.musubi.obj.iface.ObjAction;
import mobisocial.musubi.objects.PictureObj;
import mobisocial.musubi.objects.StatusObj;
import mobisocial.musubi.objects.StoryObj;
import mobisocial.musubi.objects.VoiceObj;
import mobisocial.musubi.ui.fragments.ClipboardKeeper;
import mobisocial.socialkit.musubi.DbObj;

import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;

public class ClipboardObjAction extends ObjAction {
    @Override
    public void onAct(Context context, DbEntryHandler objType, DbObj obj) {
        new ClipboardKeeper((Activity)context).store(obj);
    }

    @Override
    public String getLabel(Context context) {
        return "Copy to clipboard";
    }

    public boolean isActive(Context context, DbEntryHandler objType, JSONObject objData) {
        String type = objType.getType();
        return (type.equals(PictureObj.TYPE)
        		|| type.equals(StatusObj.TYPE)
        		|| type.equals(StoryObj.TYPE)
        		|| type.equals(VoiceObj.TYPE));
    }
}
