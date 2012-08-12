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

import java.util.Iterator;

import mobisocial.musubi.feed.iface.DbEntryHandler;
import mobisocial.musubi.model.DbContactAttributes;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.socialkit.SignedObj;
import mobisocial.socialkit.musubi.DbObj;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.util.Log;

/**
 * Scans inbound objs for user information that can can be added as attributes.
 *
 */
public class ProfileScanningObjHandler implements IObjHandler {
    public static final String TAG = "musubi-profilescanner";
    public static final boolean DBG = true;

    @Override
    public void afterDbInsertion(Context context, DbEntryHandler handler, DbObj obj) {
    }

    @Override
    public boolean handleObjFromNetwork(Context context, SignedObj obj) {
        if (!MusubiContentProvider.isSuperApp(obj.getAppId())) {
            return true;
        }
        JSONObject json = obj.getJson();
        Iterator<String> iter = json.keys();
        while (iter.hasNext()) {
            String attr = iter.next();
            try {
                if (DbContactAttributes.isWellKnownAttribute(attr)) {
                    if (DBG) Log.d(TAG, "Inserting attribute " + attr + " for " + obj.getSender());
                    String val = json.getString(attr);
                    if (!(obj instanceof SignedObj)) {
                        Log.e(TAG, "profile scanning non-db obj");
                        return true;
                    }
                    long contactId = ((DbObj)obj).getSender().getLocalId();
                    DbContactAttributes.update(context, contactId, attr, val);
                }
            } catch (JSONException e) {
                if (DBG) Log.w(TAG, "Could not pull attribute " + attr);
            }
        }
        return true;
    }

}
