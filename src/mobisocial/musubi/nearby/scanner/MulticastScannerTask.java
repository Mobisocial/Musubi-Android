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

package mobisocial.musubi.nearby.scanner;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import mobisocial.crypto.IBHashedIdentity;
import mobisocial.musubi.App;
import mobisocial.musubi.model.DbContactAttributes;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.nearby.item.NearbyItem;
import mobisocial.musubi.nearby.item.NearbyStranger;
import mobisocial.musubi.ui.MusubiBaseActivity;
import android.app.Activity;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.util.Log;

/**
 * Scans the LAN for nearby content.
 *
 */
public class MulticastScannerTask extends NearbyScannerTask {
    boolean DBG = MusubiBaseActivity.DBG;
    public static final String NEARBY_GROUP = "239.5.5.0";
    public static final int NEARBY_PORT = 9178;
    public static final int PROTOCOL_BROADCAST_URI = 0x853000;

    private final Activity mContext;
    private final WifiManager mWifiManager;
    private final Set<Uri> mSeenUris = new HashSet<Uri>();
    private MulticastSocket mSocket;
    private MulticastLock mLock;
    private String mWifiBSSID;
    private String mWifiSSID;
    private IdentitiesManager identitiesManager_;
    public MulticastScannerTask(Activity context, WifiManager manager) {
        mContext = context;
        mWifiManager = manager;
        identitiesManager_ = new IdentitiesManager(App.getDatabaseSource(context));
    }

    @Override
    protected void onPreExecute() {
        if (mWifiManager == null) {
            Log.d(TAG, "No wifi available.");
            return;
        }

        mLock = mWifiManager.createMulticastLock("msb-scanner");
        mLock.acquire();

        try {
            mSocket = new MulticastSocket(NEARBY_PORT);
        } catch (IOException e) {
            Log.w(TAG, "error multicasting", e);
            mSocket = null;
        }

        mWifiBSSID = mWifiManager.getConnectionInfo().getBSSID();
        mWifiSSID = mWifiManager.getConnectionInfo().getSSID();

        // Ignore this device's profile:
        mSeenUris.add(IdentitiesManager.uriForMyIBHashedIdentity());
    }

    @Override
    protected List<NearbyItem> doInBackground(Void... params) {
        if (DBG) Log.d(TAG, "Scanning for nearby multicast...");
        try {
            mSocket.joinGroup(InetAddress.getByName(NEARBY_GROUP));
        } catch (IOException e) {
            Log.w(TAG, "Failed to listen on multicast", e);
            mSocket = null;
        }
        while (mSocket != null) {
            if (isCancelled()) {
                break;
            }
            try {
                byte[] buf = new byte[2048];
                DatagramPacket recv = new DatagramPacket(buf, buf.length);
                mSocket.receive(recv);

                Uri friendUri = null;
                boolean acceptFriend = false;
                ByteBuffer packet = ByteBuffer.wrap(recv.getData());
                String theirIp = recv.getAddress().getHostAddress();
                int protocol = packet.getInt();
                try {
                    switch (protocol) {
                        case PROTOCOL_BROADCAST_URI: {
                            byte[] rest = new byte[recv.getLength() - 4];
                            packet.get(rest);
                            friendUri = Uri.parse(new String(rest));
                            break;
                        }
                        /*case MulticastBroadcastTask.PROTOCOL_FRIEND_REQUEST: {
                                acceptFriend = true;
                                byte[] rest = new byte[recv.getLength() - 4];
                                packet.get(rest);
                                friendUri = Uri.parse(new String(rest));
                                break;
                            }*/
                        default: {
                            String uriStr = new String(recv.getData(), 0, recv.getLength());
                            friendUri = Uri.parse(uriStr);
                        }
                    }
                } catch (Exception e) {
                    if (DBG) Log.e(TAG, "Error processing packet", e);
                }

                if (friendUri == null || mSeenUris.contains(friendUri)) {
                    continue;
                }

                IBHashedIdentity hid = IdentitiesManager.ibHashedIdentityForUri(friendUri);
                MIdentity ident = identitiesManager_.getIdentityForIBHashedIdentity(hid);
                if (ident.contactId_ == null && acceptFriend) {
                	//TODO: add to android contact book
                }
                if (ident.contactId_ != null) {
                	//TODO: ident.contactId_ may not make any sense to this call.
                	//Also are attributes on musubi contacts or identities?
                    DbContactAttributes.update(mContext, ident.contactId_, DbContactAttributes.ATTR_NEARBY_TIMESTAMP,
                            Long.toString(new Date().getTime()));

                    DbContactAttributes.update(mContext, ident.contactId_, DbContactAttributes.ATTR_LAN_IP,
                            theirIp);

                    DbContactAttributes.update(mContext, ident.contactId_, DbContactAttributes.ATTR_WIFI_BSSID,
                            mWifiBSSID);
                    DbContactAttributes.update(mContext, ident.contactId_, DbContactAttributes.ATTR_WIFI_SSID,
                            mWifiSSID);
                }

                // TODO: User user = FriendRequest.parseUri(friendUri);
                String name = friendUri.getQueryParameter("name");
                if (name == null) {
                    name = "Unknown";
                }
                addNearbyItem(new NearbyStranger(mContext, name, friendUri, null));
                mSeenUris.add(friendUri);
            } catch (IOException e) {
                Log.e(TAG, "Error receiving multicast", e);
                mSocket = null;
            }
        }
        Log.d(TAG, "Done scanning lan");
        mLock.release();
        return null;
    }
}