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

package org.mobisocial.corral;

import java.io.IOException;
import java.util.UUID;

import mobisocial.comm.BluetoothDuplexSocket;
import mobisocial.comm.DuplexSocket;
import mobisocial.comm.TcpDuplexSocket;
import mobisocial.musubi.model.DbContactAttributes;
import mobisocial.socialkit.musubi.DbIdentity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Build;
import android.util.Log;

public class ConnectionCorral {
    private static final String TAG = ConnectionCorral.class.getName();
    public static final UUID CONNECTION_CORRAL_UUID = UUID.fromString(
            "482327b0-31e9-11e1-b86c-0800200c9a66");
    public static final int CONNECTION_CORRAL_PORT = 8325;

    /**
     * Blocks until a connection can be established with the given user.
     */
    public DuplexSocket connectionWithUser(DbIdentity remote) {
        try {
            String btMac = remote.getAttribute(DbContactAttributes.ATTR_BT_MAC);
            if (btMac == null) {
                throw new IOException("No bluetooth address for user");
            }

            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) {
                throw new IOException("No bluetooth adapter");
            }
            BluetoothDevice device = adapter.getRemoteDevice(btMac);
            final BluetoothSocket socket;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD_MR1) {
                socket = device.createInsecureRfcommSocketToServiceRecord(CONNECTION_CORRAL_UUID);
            } else {
                socket = device.createRfcommSocketToServiceRecord(CONNECTION_CORRAL_UUID);
            }
            return new BluetoothDuplexSocket(socket);
        } catch (IOException e) {
            Log.d(TAG, "couldn't connect over bluetooth", e);
        }

        try {
            // TODO: dyndns
            String lanIp = remote.getAttribute(DbContactAttributes.ATTR_LAN_IP);
            if (lanIp == null) {
                throw new IOException("no known ip address for user");
            }
            return new TcpDuplexSocket(lanIp, CONNECTION_CORRAL_PORT);
        } catch (IOException e) {
            Log.d(TAG, "couldn't connect over lan", e);
        }

        return null;
    }

    /**
     * Attaches a Junction runtime to a session generated for the give
     * Obj.
     */
    /*
    public Junction joinJunctionForObj(JunctionActor actor, DbObj obj)
            throws JunctionException {
        String uid = obj.getUri().getLastPathSegment();
        uid = uid.replace("^", "_").replace(":", "_");
        Uri uri = new Uri.Builder().scheme("junction")
                .authority("sb.openjunction.org")
                .appendPath("dbf-" + uid).build();
        return AndroidJunctionMaker.bind(uri, actor);
    }*/
}
