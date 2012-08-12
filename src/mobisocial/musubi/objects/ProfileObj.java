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

package mobisocial.musubi.objects;

import java.util.UUID;

import mobisocial.musubi.App;
import mobisocial.musubi.feed.iface.DbEntryHandler;
import mobisocial.musubi.model.DbContactAttributes;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MObject;
import mobisocial.musubi.service.MessageDecodeProcessor;
import mobisocial.socialkit.Obj;
import mobisocial.socialkit.obj.MemObj;

import org.json.JSONException;
import org.json.JSONObject;
import org.mobisocial.corral.ContentCorral;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;

/**
 * Obj to update user profiles. Globally defined user attributes
 * are also scanned across all Objs.
 * {@see Contact#ATTR_LAN_IP}
 * {@see DbContactAttributes}
 */
public class ProfileObj extends DbEntryHandler {
	public static final String TAG = "ProfileObj";

    public static final String TYPE = "profile";
    public static final String NAME = "name";
    public static final String VERSION = "version";
    public static final String REPLY = "reply";

	public static final String PRINCIPAL = "principal";

    @Override
    public String getType() {
        return TYPE;
    }

    public static Obj getUserAttributesObj(Context c) {
        JSONObject obj = new JSONObject();
        try {
            addLocalProperties(c, obj);
        } catch (JSONException e) {
        }
        return new MemObj("userAttributes", obj);
    }

    private static void addLocalProperties(Context c, JSONObject json) throws JSONException {
     // TODO: Framework.
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter != null) {
            UUID btUuid = ContentCorral.getLocalBluetoothServiceUuid(c);
            String btMac = btAdapter.getAddress();
            json.put(DbContactAttributes.ATTR_BT_MAC, btMac);
            json.put(DbContactAttributes.ATTR_BT_CORRAL_UUID, btUuid.toString());
        }
        json.put(DbContactAttributes.ATTR_PROTOCOL_VERSION, App.POSI_VERSION);
    }

    /**
     * Profile objects are processed by the {@link MessageDecodeProcessor}.
     * Only profileObj sent from the local device should hit here. No need to
     * process them since we apply our own changes directly to the database.
     */
    @Override
    public boolean processObject(Context context, MFeed feed, MIdentity sender, MObject object) {
        return false;
    }
}