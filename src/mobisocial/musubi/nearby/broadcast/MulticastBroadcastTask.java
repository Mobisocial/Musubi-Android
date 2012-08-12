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

package mobisocial.musubi.nearby.broadcast;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.ByteBuffer;

import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.nearby.scanner.MulticastScannerTask;


import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

public class MulticastBroadcastTask extends AsyncTask<Void, Void, Void> {
    private static final String TAG = "MulticastBroadcast";
    private static MulticastBroadcastTask sInstance;
    public static final int SEVEN_SECONDS = 7000;
    public static final int THIRTY_SECONDS = 30000;
    public static final int NO_RETRY = -1;

    private InetAddress mNearbyGroup;
    private MulticastSocket mSocket;
    private final byte[] mBroadcastMsg;
    private boolean mRunning;
    private boolean mDone;
    private final int mDuration;
    private final int mWaitRetry;

    /**
     * 
     * @param context
     * @param duration The number of ms to wait between broadcasts
     * @param waitRetry After a failure, the number of ms to wait before retrying
     */
    public MulticastBroadcastTask(Context context, int duration, int waitRetry) {
        String requestStr = IdentitiesManager.uriForMyIBHashedIdentity().toString();
        mBroadcastMsg = new byte[4 + requestStr.length()];
        ByteBuffer buf = ByteBuffer.wrap(mBroadcastMsg);
        buf.putInt(MulticastScannerTask.PROTOCOL_BROADCAST_URI);
        buf.put(requestStr.getBytes());
        mWaitRetry = waitRetry;
        mDuration = duration;
    }

    public static MulticastBroadcastTask getInstance(Context context) {
        if (sInstance == null || sInstance.mDone) {
            sInstance = new MulticastBroadcastTask(context, SEVEN_SECONDS, NO_RETRY);
        }
        return sInstance;
    }

    @Override
    protected void onPreExecute() {
        try {
            mRunning = true;
            mDone = false;
            mNearbyGroup = InetAddress.getByName(MulticastScannerTask.NEARBY_GROUP);
            mSocket = new MulticastSocket(MulticastScannerTask.NEARBY_PORT);
        } catch (IOException e) {
            Log.w(TAG, "error multicasting", e);
            mSocket = null;
        }
    }

    @Override
    protected Void doInBackground(Void... params) {
        try {
            mSocket.joinGroup(mNearbyGroup);
        } catch (IOException e) {
            Log.w(TAG, "Failed to connect to multicast", e);
        }
        while (mSocket != null) {
            if (isCancelled()) {
                mSocket.disconnect();
                break;
            }
            try {
                DatagramPacket profile = new DatagramPacket(mBroadcastMsg,
                        mBroadcastMsg.length, mNearbyGroup, MulticastScannerTask.NEARBY_PORT);
                // if (DBG) Log.d(TAG, "sending multicast packet");
                mSocket.send(profile);
                try {
                    Thread.sleep(mDuration);
                } catch (InterruptedException e) {}
            } catch (IOException e) {
                if (mWaitRetry > 0) {
                    try {
                        Thread.sleep(mWaitRetry);
                    } catch (InterruptedException e2) {}
                } else {
                    mSocket = null;
                }
            }
        }
        mRunning = false;
        mDone = true;
        return null;
    }

    public boolean isRunning() {
        return mRunning;
    }
}