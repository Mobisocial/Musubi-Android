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

import mobisocial.musubi.feed.iface.Activator;
import mobisocial.musubi.feed.iface.DbEntryHandler;
import mobisocial.musubi.obj.iface.ObjAction;
import mobisocial.socialkit.musubi.DbObj;
import android.content.Context;

/**
 * Opens the given Obj using its {@link Activator}.
 *
 */
public class OpenObjAction extends ObjAction {

    @Override
    public void onAct(Context context, DbEntryHandler objType, DbObj obj) {
        if (objType instanceof Activator) {
            ((Activator) objType).activate(context, obj);
        }
    }

    @Override
    public boolean isActive(Context context, DbEntryHandler objType, DbObj obj) {
        return (objType instanceof Activator);
    }

    @Override
    public String getLabel(Context context) {
        return "Open";
    }
}
