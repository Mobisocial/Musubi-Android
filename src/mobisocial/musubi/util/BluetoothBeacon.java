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

package mobisocial.musubi.util;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

public class BluetoothBeacon {
    public static final UUID NEAR_GROUPS = UUID.fromString("1aba3c40-c2a5-11e0-962b-0800200c9a66");
    public static final int NEAR_PORT = -1; // bluetooth uuid service discovery not working.
    private static final String TAG = "btbeacon";
    private static final boolean DBG = true;

    public static void share(final Context context, final byte[] data, int duration) {
        new AcceptThread(context, data, duration).start();
    }

    @SuppressWarnings("unused")
    public static void discover(Activity activity, BluetoothDevice device, OnDiscovered discovered) {
        try {
            BluetoothSocket socket = null;
            try {
                if (NEAR_PORT > 0) {
                    Method listener = device.getClass().getMethod("createInsecureRfcommSocket", int.class);
                    socket = (BluetoothSocket) listener.invoke(device, NEAR_PORT);
                } else {
                    socket = device.createInsecureRfcommSocketToServiceRecord(NEAR_GROUPS);
                }
            } catch (NoSuchMethodError e) {
                Log.w(TAG, "Falling back to secure connection.", e);
                socket = device.createRfcommSocketToServiceRecord(NEAR_GROUPS);
            } catch (Exception e) {
                if (DBG) Log.w(TAG, "Could not connect to channel.", e);
            }
        
            if (socket == null) {
                throw new IOException("null socket");
            }
            socket.connect();
            byte[] receivedBytes = new byte[2048];
            int r = socket.getInputStream().read(receivedBytes);
            byte[] returnBytes = new byte[r];
            System.arraycopy(receivedBytes, 0, returnBytes, 0, r);
            discovered.onDiscovered(returnBytes);
        } catch (IOException e) {
            //toast(activity, "Couldn't connect to " + device.getName());
            Log.d(TAG, "failed bluetooth connection", e);
        }
    }

    private static void toast(final Activity context, final String text) {
        context.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
            }
        });
        
    }

    public interface OnDiscovered {
        public void onDiscovered(byte[] data);
    }

    private static class AcceptThread extends Thread {
        // The local server socket
        private final BluetoothServerSocket mmServerSocket;
        private final Context mmContext;
        private final byte[] mmData;
        private final int mmDuration;

        private AcceptThread(Context context, byte[] data, int duration) {
            mmContext = context;
            mmData = data;
            mmDuration = duration;

            BluetoothServerSocket tmp = null;

            // Create a new listening server socket
            try {
                tmp = getServerSocket(NEAR_PORT, NEAR_GROUPS);
            } catch (IOException e) {
                Log.e(TAG, "listen() failed", e);
            }
            mmServerSocket = tmp;
        }

        public void run() {
            setName("AcceptThread");
            BluetoothSocket socket = null;

            while (true) {
                try {
                    socket = mmServerSocket.accept(1000 * mmDuration);
                } catch (IOException e) {
                    if (DBG) Log.e(TAG, "accept() failed", e);
                    return;
                }
    
                if (socket == null) {
                    if (DBG) Log.e(TAG, "no socket.");
                    return;
                }
    
                doConnection(socket);
            }
        }

        public void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) {
            }
        }

        private void doConnection(BluetoothSocket socket) {
            try {
                socket.getOutputStream().write(mmData);
                socket.getOutputStream().flush();
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error writing content", e);
            }
        }

        private int getBluetoothListeningPort(BluetoothServerSocket serverSocket) {
            try {
                Field socketField = BluetoothServerSocket.class.getDeclaredField("mSocket");
                socketField.setAccessible(true);
                BluetoothSocket socket = (BluetoothSocket)socketField.get(serverSocket);

                Field portField = BluetoothSocket.class.getDeclaredField("mPort");
                portField.setAccessible(true);
                int port = (Integer)portField.get(socket);
                return port;
            } catch (Exception e) {
                Log.d(TAG, "Error getting port from socket", e);
                return -1;
            }
        }

        private BluetoothServerSocket getServerSocket(int port, UUID service) 
                throws IOException {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (port > 0) {
                try {
                    Method listener = adapter.getClass().getMethod("listenUsingInsecureRfcommOn", int.class);
                    return (BluetoothServerSocket) listener.invoke(adapter, port);
                } catch (Exception e) {
                    throw new IOException(e);
                }
            }
            return adapter.listenUsingInsecureRfcommWithServiceRecord("mobinear", NEAR_GROUPS);
        }
    }
}
