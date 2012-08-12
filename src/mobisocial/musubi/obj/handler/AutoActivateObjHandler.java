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

package mobisocial.musubi.obj.handler;

import mobisocial.musubi.feed.iface.Activator;
import mobisocial.musubi.feed.iface.DbEntryHandler;
import mobisocial.musubi.objects.AppStateObj;
import mobisocial.musubi.ui.SettingsActivity;
import mobisocial.musubi.ui.fragments.SettingsFragment;
import mobisocial.socialkit.SignedObj;
import mobisocial.socialkit.musubi.DbObj;
import android.content.Context;

/**
 * Automatically launches some received objects.
 */
public class AutoActivateObjHandler extends ObjHandler {
    @Override
    public void afterDbInsertion(Context context, DbEntryHandler handler, DbObj obj) {
        if (!willActivate(context, obj)) {
            return;
        }
        if (handler instanceof Activator) {
            ((Activator)handler).activate(context, obj);
        }
    }

    public boolean willActivate(Context context, DbObj obj) {
        if (!context.getSharedPreferences(SettingsActivity.PREFS_NAME, 0)
                .getBoolean(SettingsFragment.PREF_AUTOPLAY, false)) {
            return false;
        }
        // Don't activate subfeed items
        if (AppStateObj.TYPE.equals(obj.getType())) {
            return false;
        }
        return true;
    }

    @Override
    public boolean handleObjFromNetwork(Context context, SignedObj obj) {
        return true;
    }
}
