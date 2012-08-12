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
import java.util.List;

import mobisocial.musubi.obj.action.ClipboardObjAction;
import mobisocial.musubi.obj.action.DeleteAction;
import mobisocial.musubi.obj.action.EditPhotoAction;
import mobisocial.musubi.obj.action.RetryUploadAction;
import mobisocial.musubi.obj.action.SetFeedPhotoAction;
import mobisocial.musubi.obj.action.SetProfileObjAction;
import mobisocial.musubi.obj.action.SharePhotoAction;
import mobisocial.musubi.obj.action.ShareTextAction;
import mobisocial.musubi.obj.iface.ObjAction;


public class ObjActions {
    private static final List<ObjAction> sActions = new ArrayList<ObjAction>();
    static {
        //sActions.add(new OpenObjAction());
        sActions.add(new RetryUploadAction());
        sActions.add(new SharePhotoAction());
        sActions.add(new ShareTextAction());
        sActions.add(new EditPhotoAction());
        sActions.add(new ClipboardObjAction());
        sActions.add(new SetProfileObjAction());
        sActions.add(new SetFeedPhotoAction());
        sActions.add(new DeleteAction());
        // sActions.add(new PlayAllAudioAction());
    }

    public static List<ObjAction> getObjActions() {
        return sActions;
    }
}
