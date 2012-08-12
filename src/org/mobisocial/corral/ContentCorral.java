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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mobisocial.comm.BluetoothDuplexSocket;
import mobisocial.comm.DuplexSocket;
import mobisocial.comm.StreamDuplexSocket;
import mobisocial.musubi.App;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MObject;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.model.helpers.ObjectManager;
import mobisocial.musubi.objects.StoryObj;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.provider.MusubiContentProvider.Provided;
import mobisocial.musubi.ui.util.UiUtil;
import mobisocial.musubi.util.Util;
import mobisocial.socialkit.Obj;
import mobisocial.socialkit.musubi.DbFeed;
import mobisocial.socialkit.musubi.DbObj;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.http.entity.ContentProducer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

/**
 * Stores and retrieves large content associated with objs.
 */
public class ContentCorral {
    private static final String PREF_CORRAL_BT_UUID = "corral_bt";
    public static final int SERVER_PORT = 8225;
    private static final String BT_CORRAL_NAME = "Content Corral";
    private static final String TAG = "ContentCorral";
    private static final boolean DBG = false;

    private BluetoothAcceptThread mBluetoothAcceptThread;
    private HttpAcceptThread mHttpAcceptThread;
    private Context mContext;

    /** 
     * A token generated for this corral instance.
     */
    static SecureRandom sSecureRandom;
    static String MOCK = "mock";
    static final int RAW = 1;
    static final int JSON = 2;
    static final int NEWS = 3;

    static UriMatcher sMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    static {
        sMatcher.addURI(MOCK, "raw/#", RAW);
        sMatcher.addURI(MOCK, "json/#", JSON);
        sMatcher.addURI(MOCK, "news", NEWS);
    }

    static final Map<String, AccessScope> sAppTokens = new HashMap<String, AccessScope>();
	public static final String PICTURE_SUBFOLDER = "Pictures/Musubi";
	public static final String HTML_SUBFOLDER = "Musubi/HTML";
	public static final String FILES_SUBFOLDER = "Musubi/Files";
	public static final String APPS_SUBFOLDER = "Musubi/Apps";

    public ContentCorral(Context context) {
        mContext = context;
    }

    public void start() {
        startHttpServer();
        startBluetoothService();
    }

    private void startBluetoothService() {
        if (mBluetoothAcceptThread != null)
            return;
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            return;
        }

        mBluetoothAcceptThread = new BluetoothAcceptThread(adapter,
                getLocalBluetoothServiceUuid(mContext));
        mBluetoothAcceptThread.start();
    }

    /**
     * Starts the simple image server
     */
    private synchronized void startHttpServer() {
        if (mHttpAcceptThread != null)
            return;

        /*String ip = getLocalIpAddress();
        if (ip == null) {
            Log.w(TAG, "No wifi ip address; corral not loaded.");
            return;
        }*/
        mHttpAcceptThread = new HttpAcceptThread(SERVER_PORT, true);
        mHttpAcceptThread.start();
    }

    public synchronized void stop() {
        if (mHttpAcceptThread != null) {
            mHttpAcceptThread.cancel();
            mHttpAcceptThread = null;
        }
    }

    static class AccessScope {
        public String appId;
        public long objId;

        public AccessScope(String appId, long objId) {
            this.appId = appId;
            this.objId = objId;
        }
    }

    public static synchronized String registerForAccessToken(String appId, long objId) {
        String appToken = new BigInteger(130, initializeSecureRandom()).toString(32);
        sAppTokens.put(appToken, new AccessScope(appId, objId));
        return appToken;
    }

    public static synchronized void unregisterAppToken(String appToken) {
        sAppTokens.remove(appToken);
    }

    static SecureRandom initializeSecureRandom() {
        if (sSecureRandom == null) {
            sSecureRandom = new SecureRandom();
        }
        return sSecureRandom;
    }

    public static Uri storeContent(Context context, Uri contentUri) {
        return storeContent(context, contentUri, context.getContentResolver().getType(contentUri));
    }

    public static Uri storeContent(Context context, byte[] raw, String type) {
        File contentDir;
        if (type != null && (type.startsWith("image/") || type.startsWith("video/"))) {
        	contentDir = new File(Environment.getExternalStorageDirectory(), PICTURE_SUBFOLDER);
        } else {
        	contentDir = new File(Environment.getExternalStorageDirectory(), FILES_SUBFOLDER);
        }

        if(!contentDir.exists() && !contentDir.mkdirs()) {
        	Log.e(TAG, "failed to create musubi corral directory");
        	return null;
        }
        int timestamp = (int) (System.currentTimeMillis() / 1000L);
        String ext = CorralDownloadClient.extensionForType(type);
        String fname = timestamp + "-" + "webapp-raw" + "." + ext;
        File copy = new File(contentDir, fname);
        FileOutputStream out = null;
        try {
            contentDir.mkdirs();
            out = new FileOutputStream(copy);
            out.write(raw);
            return Uri.fromFile(copy);
        } catch (IOException e) {
            Log.w(TAG, "Error copying file", e);
            if (copy.exists()) {
                copy.delete();
            }
            return null;
        } finally {
            try {
                if (out != null)
                    out.close();
            } catch (IOException e) {
                Log.e(TAG, "failed to close handle on store corral content", e);
            }
        }
    }

    public static Uri storeContent(Context context, Uri contentUri, String type) {
        File contentDir;
        if (type != null && (type.startsWith("image/") || type.startsWith("video/"))) {
        	contentDir = new File(Environment.getExternalStorageDirectory(), PICTURE_SUBFOLDER);
        } else {
        	contentDir = new File(Environment.getExternalStorageDirectory(), FILES_SUBFOLDER);
        }

        if(!contentDir.exists() && !contentDir.mkdirs()) {
        	Log.e(TAG, "failed to create musubi corral directory");
        	return null;
        }
        int timestamp = (int) (System.currentTimeMillis() / 1000L);
        String ext = CorralDownloadClient.extensionForType(type);
        String fname = timestamp + "-" + contentUri.getLastPathSegment() + "." + ext;
        File copy = new File(contentDir, fname);
        FileOutputStream out = null;
        InputStream in = null;
        try {
            contentDir.mkdirs();
            in = context.getContentResolver().openInputStream(contentUri);
            BufferedInputStream bin = new BufferedInputStream(in);
            byte[] buff = new byte[1024];
            out = new FileOutputStream(copy);
            int r;
            while ((r = bin.read(buff)) > 0) {
                out.write(buff, 0, r);
            }
            bin.close();
            return Uri.fromFile(copy);
        } catch (IOException e) {
            Log.w(TAG, "Error copying file", e);
            if (copy.exists()) {
                copy.delete();
            }
            return null;
        } finally {
            try {
                if (in != null)
                    in.close();
                if (out != null)
                    out.close();
            } catch (IOException e) {
                Log.e(TAG, "failed to close handle on store corral content", e);
            }
        }
    }

    private class HttpAcceptThread extends Thread {
        // The local server socket
        private final ServerSocket mmServerSocket;

        public HttpAcceptThread(int port, boolean allowRemote) {
            ServerSocket tmp = null;

            // Create a new listening server socket
            try {
                tmp = new ServerSocket(port);
                if (!allowRemote) {
                    tmp.bind(new InetSocketAddress("127.0.0.1", port));
                } else {
                    tmp.bind(new InetSocketAddress("0.0.0.0", port));
                }
            } catch (IOException e) {
                System.err.println("Could not open server socket");
                e.printStackTrace(System.err);
            }
            mmServerSocket = tmp;
        }

        public void run() {
            if (mmServerSocket == null) {
                return;
            }

            // Log.d(TAG, "BEGIN mAcceptThread" + this);
            setName("AcceptThread");
            Socket socket = null;

            // Listen to the server socket always
            while (true) {
                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    if (DBG)
                        Log.d(TAG, "corral waiting for client...");
                    socket = mmServerSocket.accept();
                    if (DBG)
                        Log.d(TAG, "corral client connected!");
                } catch (SocketException e) {
                    Log.e(TAG, "accept() failed", e);
                    break;
                } catch (IOException e) {
                    Log.e(TAG, "accept() failed", e);
                    break;
                }

                // If a connection was accepted
                if (socket == null) {
                    break;
                }

                DuplexSocket duplex;
                try {
                    duplex = new StreamDuplexSocket(socket.getInputStream(),
                            socket.getOutputStream());
                } catch (IOException e) {
                    Log.e(TAG, "Failed to connect to socket", e);
                    return;
                }
                HttpConnectedThread conThread = new HttpConnectedThread(socket, duplex);
                conThread.start();
            }
            Log.d(TAG, "END mAcceptThread");
        }

        public void cancel() {
            Log.d(TAG, "cancel " + this);
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of server failed", e);
            }
        }
    }

    private class BluetoothAcceptThread extends Thread {
        // The local server socket
        private final BluetoothServerSocket mmServerSocket;

        public BluetoothAcceptThread(BluetoothAdapter adapter, UUID coralUuid) {
            BluetoothServerSocket tmp = null;

            // Create a new listening server socket
            try {
                try {
                    if (DBG)
                        Log.d(TAG, "Bluetooth corral listening on " + adapter.getAddress() + ":"
                                + coralUuid);
                    tmp = adapter.listenUsingRfcommWithServiceRecord(BT_CORRAL_NAME, coralUuid);
                } catch (NoSuchMethodError e) {
                    // Let's not deal with pairing UI.
                }
            } catch (IOException e) {
                Log.e(TAG, "Could not open bt server socket");
                e.printStackTrace(System.err);
            } catch (NoSuchMethodError e) {
                Log.e(TAG, "Bluetooth Corral not available for this Android version.");
            }
            mmServerSocket = tmp;
        }

        public void run() {
            if (mmServerSocket == null) {
                return;
            }

            // Log.d(TAG, "BEGIN mAcceptThread" + this);
            setName("AcceptThread");
            BluetoothSocket socket = null;

            // Listen to the server socket always
            while (true) {
                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    if (DBG)
                        Log.d(TAG, "Corral bluetooth server waiting for client...");
                    socket = mmServerSocket.accept();
                    if (DBG)
                        Log.d(TAG, "Corral bluetooth server connected!");
                } catch (SocketException e) {
                    Log.e(TAG, "accept() failed", e);
                    break;
                } catch (IOException e) {
                    Log.e(TAG, "accept() failed", e);
                    break;
                }

                // If a connection was accepted
                if (socket == null) {
                    break;
                }

                DuplexSocket duplex = new BluetoothDuplexSocket(socket);
                CorralConnectedThread conThread = new CorralConnectedThread(duplex);
                conThread.start();
            }
            Log.d(TAG, "END mAcceptThread");
        }

        @SuppressWarnings("unused")
        public void cancel() {
            Log.d(TAG, "cancel " + this);
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of server failed", e);
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device. It supports
     * incoming and outgoing transmissions over HTTP.
     */
    private class HttpConnectedThread extends Thread {
        private final DuplexSocket mmDuplexSocket;
        private final Socket mmRealSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private final int BUFFER_LENGTH = 1024;

        public HttpConnectedThread(Socket socket, DuplexSocket streams) {
            // Log.d(TAG, "create ConnectedThread");
            mmRealSocket = socket;
            mmDuplexSocket = streams;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            Log.d(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[BUFFER_LENGTH];
            int bytes;

            if (mmInStream == null || mmOutStream == null)
                return;

            // Read header information, determine connection type
            try {
                bytes = mmInStream.read(buffer);
                if (DBG) Log.d(TAG, "read " + bytes + " header bytes");
                String header = new String(buffer, 0, bytes);
                if (DBG) Log.d(TAG, header);
                // determine request type
                if (header.startsWith("GET ")) {
                    doGetRequest(header);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reading connection header", e);
            }

            // No longer listening.
            cancel();
        }

        class CorralHttpRequest {
            final String mRequest;
            final Map<String, String> mHeaders = new HashMap<String, String>();
            String mMethod;
            String mPath;

            public CorralHttpRequest(String httpRequest) {
                mRequest = httpRequest;
                parseRequest();
            }

            void parseRequest() {
                String[] headers = mRequest.split("\r\n");
                if (headers.length == 0) {
                    throw new IllegalArgumentException("Bad http request");
                }
                for (int i = 1; i < headers.length; i++) {
                    String h = headers[i];
                    int col = h.indexOf(':');
                    if (col > -1) {
                        String v = h.substring(col+1).trim();
                        mHeaders.put(h.substring(0, col).trim().toLowerCase(), v);
                    }
                }

                String[] request = headers[0].split(" ");
                if (request.length == 0) {
                    throw new IllegalArgumentException("No method");
                }
                mMethod = request[0];
                mPath = request[1];
            }
        }

        void handleRaw(Uri targetUri) {
        	long objId = Long.parseLong(targetUri.getLastPathSegment());
            String ticket = targetUri.getQueryParameter("ticket"); 
            if (ticket == null) {
                notAuthorized();
                return;
            }
            AccessScope scope = sAppTokens.get(ticket);
            if (scope == null || scope.objId != objId) {
                // TODO: grant access based on appId/feed etc.
                notAuthorized();
                return;
            }

            Uri uri = MusubiContentProvider.uriForItem(Provided.OBJS_ID, objId);
            String[] projection = new String[] { DbObj.COL_RAW, DbObj.COL_JSON };
            String selection = DbObj.COL_ID + "=?";
            String[] selectionArgs = new String[] { Long.toString(objId) };
            String sortOrder = null;
            Cursor obj = mContext.getContentResolver().query(uri,
                    projection, selection, selectionArgs, sortOrder);
            try {
                if (obj.moveToFirst()) {
                    try {
                        String type = "application/octet";
                        byte[] bytes = obj.getBlob(0);
                        String jsonSrc = obj.getString(1);
                        try {
                            JSONObject json = new JSONObject(jsonSrc);
                            if (json.has(CorralDownloadClient.OBJ_MIME_TYPE)) {
                                type = json.getString(CorralDownloadClient.OBJ_MIME_TYPE);
                            }
                        } catch (JSONException e) {
                        }

                        sendRaw(type, bytes);
                        mmOutStream.close();
                    } catch(IOException e) {
                        if (DBG) Log.w(TAG, "corral http", e);
                    }
                } else {
                    try {
                        mmOutStream.write(header("HTTP/1.1 404 NOT FOUND"));
                        mmOutStream.close();
                    } catch (IOException e) {
                        if (DBG) Log.w(TAG, "corral http", e);
                    }
                }
            } finally {
                obj.close();
            }
            return;
        }

        void handleNews(Uri targetUri) {
        	if (DBG) Log.d(TAG, "reading the news");
            PrintWriter pw = new PrintWriter(mmOutStream);
            if (!"/127.0.0.1".equals(mmRealSocket.getLocalAddress().toString())) {
                pw.append("HTTP/1.1 403 FORBIDDEN\r\n\r\n");
                return;
            }

            Uri uri = MusubiContentProvider.uriForDir(Provided.OBJECTS);
            String[] projection = new String[] { MObject.COL_ID };
            String selection = "type=?";
            String[] selectionArgs = new String[] { StoryObj.TYPE };
            String sortOrder = DbObj.COL_ID + " DESC LIMIT 10";
            Cursor c = mContext.getContentResolver().query(
                    uri, projection, selection, selectionArgs, sortOrder);
            try {
                PulseFeed pulse = new PulseFeed(mContext, c);
                String news = pulse.toJson().toString();
                if (DBG) Log.d(TAG, "News feed: " + news);

                pw.append("HTTP/1.1 200 OK\r\n");
                pw.append("Content-Type: application/json\r\n");
                pw.append("Content-Length: " + news.getBytes().length + "\r\n");
                pw.append("\r\n");
                pw.append(news); // TODO: memory management; streaming interface
                pw.close();
                mmOutStream.close();
            } catch (IOException e) {
                if (DBG) Log.d(TAG, "io", e);
            } finally {
                c.close();
            }

            /*Log.d(TAG, "mocking pulse data");
            String PULSE_ASSET = "pulse.txt";
            try {
                String content = IOUtils.toString(mContext.getResources().getAssets().open(PULSE_ASSET));
                pw.append("HTTP/1.1 200 OK\r\n");
                pw.append("Content-Type: application/json\r\n");
                pw.append("Content-Length: " + content.getBytes().length);
                pw.append("\r\n\r\n");
                pw.append(content);
                pw.close();
                mmOutStream.close();
            } catch (IOException e) {
                Log.e(TAG, "failed to pulse", e);
            }*/
        }

        void handleApp(Uri targetUri) {
        	if (targetUri.getPath().contains("..")) {
        		return;
        	}
        	File appFolder = new File(Environment.getExternalStorageDirectory(), APPS_SUBFOLDER);
        	String filePath;
        	if (targetUri.getPathSegments().size() == 2) {
        		filePath = targetUri.getPathSegments().get(1) + "/index.html";
        	} else {
        		filePath = targetUri.getPath();
        		filePath = filePath.replaceFirst("/app", "");
        	}
        	try {
            	FileInputStream fileInputStream = new FileInputStream(new File(appFolder, filePath));
            	mmOutStream.write(header("HTTP/1.1 200 OK"));
                //mmOutStream.write(header("Content-Type: " + type));
                mmOutStream.write(header("Content-Length: " + fileInputStream.available()));
                mmOutStream.write(header(""));
            	IOUtils.copy(fileInputStream, mmOutStream);
        	} catch (IOException e) {
        		Log.e(TAG, "Error sending app file", e);
        	}
        }

        /**
         * Handles an HTTP GET request for objects, raw content,
         * and Corral images.
         */
        private void doGetRequest(String httpRequest) {
            CorralHttpRequest request = new CorralHttpRequest(httpRequest);
            if (!"GET".equals(request.mMethod)) {
                throw new IllegalArgumentException();
            }

            Uri targetUri = Uri.parse("content://" + MOCK + request.mPath);

            int match = sMatcher.match(targetUri);
            boolean handled = true;
            switch (match) {
                case RAW:
                    handleRaw(targetUri);
                    break;
                case NEWS:
                	handleNews(targetUri);
                    break;
                default:
            		handled = false;
            }

            if (handled) {
            	return;
            }

            if (request.mPath.startsWith("/app")) {
            	handleApp(targetUri);
            	return;
            }

            // Old-School:

            if (targetUri.getQueryParameter("content") == null) {
                try {
                    mmOutStream.write(header("HTTP/1.1 404 NOT FOUND\r\n\r\n"));
                    mmOutStream.close();
                } catch (IOException e) {
                }
                return;
            }

            if (targetUri.getQueryParameter("hash") == null) {
                notAuthorized();
                return;
            }

            // Verify the hash is for an obj with the given filepath.
            // TODO: This is not secure. Require challenge/response authentication.
            String universalHashStr = targetUri.getQueryParameter("hash");
            String contentPath = targetUri.getQueryParameter("content");
            byte[] universalHashBytes = Util.convertToByteArray(universalHashStr);
            ObjectManager om = new ObjectManager(App.getDatabaseSource(mContext));
            long objId = om.getObjectIdForHash(universalHashBytes);
            if (objId == -1) {
                try {
                    mmOutStream.write(header("HTTP/1.1 410 GONE\r\n\r\n"));
                    mmOutStream.close();
                } catch (IOException e) {
                }
                return;
            }

            JSONObject json;
            MObject obj = om.getObjectForId(objId);
            if (obj.json_ == null) {
                notAuthorized();
                return;
            }
            try {
                json = new JSONObject(obj.json_);
            } catch (JSONException e) {
                notAuthorized();
                return;
            }

            String localPath = json.optString(CorralDownloadClient.OBJ_LOCAL_URI);
            if (!contentPath.equals(localPath)) {
                try {
                    mmOutStream.write(header("HTTP/1.1 400 BAD REQUEST\r\n\r\n"));
                    mmOutStream.close();
                } catch (IOException e) {
                }
                return;
            }

            // OK to download:
            Uri requestPath = Uri.parse(contentPath);
            String scheme = requestPath.getScheme();
            if ("content".equals(scheme) || "file".equals(scheme)) {
                if (DBG) Log.d(TAG, "Retrieving for " + requestPath.getAuthority());
                if (MusubiContentProvider.AUTHORITY.equals(requestPath.getAuthority())) {
                    if (requestPath.getQueryParameter("obj") != null) {
                        int objIndex = Integer.parseInt(requestPath.getQueryParameter("obj"));
                        // sendObj(requestPath, objIndex);
                        Log.w(TAG, "Unknown corral request");
                    } else {
                        Log.w(TAG, "Unknown corral request");
                    }
                } else {
                    sendContent(requestPath);
                }
            }
        }

        class PulseFeed {
            final IdentitiesManager mIdentityManager;
            final JSONArray mEntries;

            public PulseFeed(Context context, Cursor c){
                mIdentityManager = new IdentitiesManager(App.getDatabaseSource(context));
                mEntries = new JSONArray();

                try {
                    while (c.moveToNext()) {
                        mEntries.put(getEntry(c.getLong(0)));
                    }
                } catch (JSONException e) {
                    throw new IllegalArgumentException(e);
                }
            }

            public JSONObject toJson() {
                try {
                    JSONObject pulse = new JSONObject();
                    pulse.put("responseData", getResponse());
                    return pulse;
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }

            JSONObject getEntry(long id) throws JSONException {
                JSONArray categories = new JSONArray();
                categories.put("friends");
                JSONObject entry = new JSONObject();
                // Pulse: title, link, author, publishedDate, contentSnippet, content, categories
                // Musubi: title, text, favicon_length, original_url, [raw]

                DbObj story = App.getMusubi(mContext).objForId(id);
                MIdentity senderId = mIdentityManager.getIdentityForId(story.getSenderId());
                String sender = UiUtil.safeNameForIdentity(senderId);
                JSONObject meta = story.getJson();
                entry.put("title", meta.optString(StoryObj.TITLE));
                entry.put("link", meta.optString(StoryObj.ORIGINAL_URL));
                entry.put("author", sender);
                entry.put("publishDate", new Date(story.getTimestamp()));
                entry.put("contentSnippet", meta.optString(StoryObj.TEXT));
                entry.put("content", meta.optString(StoryObj.TEXT));
                entry.put("categories", categories);
                
                return entry;
            }

            JSONObject getResponse() throws JSONException {
                JSONObject response = new JSONObject();
                response.put("feed", getFeed());
                return response;
            }

            JSONObject getFeed() throws JSONException {
                JSONObject feed = new JSONObject();
                feed.put("feedUrl", "content://org.musubi.db/news");
                feed.put("title", "Musubi");
                feed.put("link", "http://127.0.0.1:" + SERVER_PORT + "/news");
                feed.put("author", "Stanford");
                feed.put("description", "Stories from Friends");
                feed.put("type", "rss20");
                feed.put("entries", getEntries());
                return feed;
            }

            JSONArray getEntries() {
                return mEntries;
            }
        }

        void notAuthorized() {
            try {
                mmOutStream.write(header("HTTP/1.1 401 UNAUTHORIZED\r\n\r\n"));
                mmOutStream.close();
            } catch (IOException e) {
            }
        }

        void sendRaw(String type, byte[] bytes) throws IOException {
            mmOutStream.write(header("HTTP/1.1 200 OK"));
            mmOutStream.write(header("Content-Type: " + type));
            mmOutStream.write(header("Content-Length: " + bytes.length));
            mmOutStream.write(header(""));

            InputStream in = new ByteArrayInputStream(bytes);
            byte[] buf = new byte[1024];
            int r;
            while ((r = in.read(buf)) > 0) {
                mmOutStream.write(buf, 0, r);
            };
        }

        private void sendContent(Uri requestPath) {
            InputStream in;
            try {
                // img = Uri.withAppendedPath(Images.Media.EXTERNAL_CONTENT_URI,
                // imgId);
                in = mContext.getContentResolver().openInputStream(requestPath);
            } catch (Exception e) {
                Log.d(TAG, "Error opening file", e);
                return;
            }

            try {
                byte[] buffer = new byte[4096];
                int r = 0;

                // Gross way to get length. What's the right way??
                int size = 0;
                while ((r = in.read(buffer)) > 0) {
                    size += r;
                }
                String type = mContext.getContentResolver().getType(requestPath);
                if (type == null) {
                    int p = requestPath.toString().lastIndexOf(".");
                    if (p > 0) {
                        String ext = requestPath.toString().substring(p + 1);
                        type = CorralDownloadClient.typeForExtension(ext);
                    }
                }
                in = mContext.getContentResolver().openInputStream(requestPath);
                mmOutStream.write(header("HTTP/1.1 200 OK"));
                if (type != null) {
                    mmOutStream.write(header("Content-Type: " + type));
                }
                mmOutStream.write(header("Content-Length: " + size));
                // mmOutStream.write(header("Content-Disposition: attachment; filename=\""+filename+"\""));
                mmOutStream.write(header(""));

                while ((r = in.read(buffer)) > 0) {
                    mmOutStream.write(buffer, 0, r);
                }

            } catch (Exception e) {
                Log.e(TAG, "Error sending file", e);
            } finally {
                try {
                    mmOutStream.close();
                } catch (IOException e) {
                }
            }
        }

        private void sendObj(Uri requestPath, int objIndex) {
            InputStream in;
            byte[] bytes;
            try {
                Uri uri;
                try {
                    uri = DbFeed.uriForId(Long.parseLong(requestPath.getPath().substring(1)));
                } catch (NumberFormatException e) {
                    return;
                }
                String[] projection = new String[] {
                        DbObj.COL_ID, MObject.COL_JSON
                };
                String selection = MObject.COL_RENDERABLE + " = 1";
                String[] selectionArgs = null;
                String sortOrder = DbObj.COL_ID + " ASC";
                Cursor cursor = mContext.getContentResolver().query(uri, projection, selection,
                        selectionArgs, sortOrder);
                try {

                    if (!cursor.moveToPosition(objIndex)) {
                        Log.d(TAG, "No obj found for " + uri);
                        return;
                    }
                    String jsonStr = cursor.getString(1);
                    bytes = jsonStr.getBytes();
                    in = new ByteArrayInputStream(bytes);
                } finally {
                    cursor.close();
                }
            } catch (Exception e) {
                Log.d(TAG, "Error opening obj", e);
                return;
            }

            try {
                byte[] buffer = new byte[4096];
                int r = 0;

                mmOutStream.write(header("HTTP/1.1 200 OK"));
                mmOutStream.write(header("Content-Type: text/plain"));
                mmOutStream.write(header("Content-Length: " + bytes.length));
                // mmOutStream.write(header("Content-Disposition: attachment; filename=\""+filename+"\""));
                mmOutStream.write(header(""));

                while ((r = in.read(buffer)) > 0) {
                    Log.d(TAG, "sending: " + new String(buffer));
                    mmOutStream.write(buffer, 0, r);
                }

            } catch (Exception e) {
                Log.e(TAG, "Error sending file", e);
            } finally {
                try {
                    mmOutStream.close();
                } catch (IOException e) {
                }
            }
        }

        private void sendObjs(Uri requestPath) {
            // TODO: hard-coded limit of 30 in place.
            InputStream in;
            byte[] bytes;
            StringBuilder jsonArrayBuilder = new StringBuilder("[");
            try {
                Uri uri;
                try {
                    uri = DbFeed.uriForId(Long.parseLong(requestPath.getPath().substring(1)));
                } catch (NumberFormatException e) {
                    return;
                }
                String[] projection = new String[] {
                        DbObj.COL_ID, MObject.COL_JSON
                };
                String selection = MObject.COL_RENDERABLE + " = 1";
                String[] selectionArgs = null;
                String sortOrder = DbObj.COL_ID + " ASC LIMIT 30";
                Cursor cursor = mContext.getContentResolver().query(uri, projection, selection,
                        selectionArgs, sortOrder);

                try {
                    if (!cursor.moveToFirst()) {
                        Log.d(TAG, "No objs found for " + uri);
                        return;
                    }
                    jsonArrayBuilder.append(cursor.getString(1));
                    while (!cursor.isLast()) {
                        cursor.moveToNext();
                        String jsonStr = cursor.getString(1);
                        jsonArrayBuilder.append(",").append(jsonStr);
                    }
                } finally {
                    cursor.close();
                }
            } catch (Exception e) {
                Log.d(TAG, "Error opening obj", e);
                return;
            }

            bytes = jsonArrayBuilder.append("]").toString().getBytes();
            in = new ByteArrayInputStream(bytes);
            try {
                byte[] buffer = new byte[4096];
                int r = 0;

                mmOutStream.write(header("HTTP/1.1 200 OK"));
                mmOutStream.write(header("Content-Type: text/plain"));
                mmOutStream.write(header("Content-Length: " + bytes.length));
                // mmOutStream.write(header("Content-Disposition: attachment; filename=\""+filename+"\""));
                mmOutStream.write(header(""));

                while ((r = in.read(buffer)) > 0) {
                    Log.d(TAG, "sending: " + new String(buffer));
                    mmOutStream.write(buffer, 0, r);
                }

            } catch (Exception e) {
                Log.e(TAG, "Error sending file", e);
            } finally {
                try {
                    mmOutStream.close();
                } catch (IOException e) {
                }
            }
        }

        public void cancel() {
            try {
                mmDuplexSocket.close();
            } catch (IOException e) {
            }
        }
    }

    private byte[] header(String str) {
        return (str + "\r\n").getBytes();
    }

    public static String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en
                    .hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr
                        .hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        // not ready for IPv6, apparently.
                        if (!inetAddress.getHostAddress().contains(":")) {
                            return inetAddress.getHostAddress().toString();
                        }
                    }
                }
            }
        } catch (SocketException ex) {

        }
        return null;
    }

    /**
     * This thread runs during a connection with a remote device. It supports
     * incoming and outgoing transmissions over HTTP.
     */
    private class CorralConnectedThread extends Thread {
        private final DuplexSocket mmSocket;

        private final InputStream mmInStream;

        private final OutputStream mmOutStream;

        private final int BUFFER_LENGTH = 1024;

        public CorralConnectedThread(DuplexSocket socket) {
            if (DBG)
                Log.d(TAG, "create CorralConnectedThread");

            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            Log.d(TAG, "BEGIN CorralConnectedThread");
            byte[] buffer = new byte[BUFFER_LENGTH];
            int bytes;

            if (mmInStream == null || mmOutStream == null)
                return;

            // Read header information, determine connection type
            try {
                PosiServerProtocol protocol = new PosiServerProtocol(mmSocket);
                CorralRequestHandler handler = protocol.getRequestHandler();

                /**
                 * TODO: SNEP-like protocol here, for ObjEx. Remember, we have
                 * authenticated objs, ndef does not. server: NONCE CHALLENGE
                 * client: AUTHED REQUEST server: AUTHED RESPONSE
                 */
                bytes = mmInStream.read(buffer);
                Log.d(TAG, "read " + bytes + " header bytes");
                String header = new String(buffer, 0, bytes);

                /**
                 * Your task is to find out which friends are nearby. We're just
                 * going to try to connect to all of their CORRAL_BLUETOOTH
                 * ports and send a quick HELLO. First visual is to show this in
                 * a "nearby" list. We'll easily up-convert to groups. Dumb
                 * algorithm for now just iterates over MACs and tries to
                 * connect, following protocol.
                 */

                // TODO
                Log.d(TAG, "BJD BLUETOOTH CORRAL NOT READY: ObjEx needs defining.");
            } catch (Exception e) {
                Log.e(TAG, "Error reading connection header", e);
            }

            // No longer listening.
            cancel();
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
            }
        }
    }

    public static UUID getLocalBluetoothServiceUuid(Context c) {
        SharedPreferences prefs = c.getSharedPreferences("main", 0);
        if (!prefs.contains(PREF_CORRAL_BT_UUID)) {
            UUID btUuid = UUID.randomUUID();
            prefs.edit().putString(PREF_CORRAL_BT_UUID, btUuid.toString()).commit();
        }
        String uuidStr = prefs.getString(PREF_CORRAL_BT_UUID, null);
        return (uuidStr == null) ? null : UUID.fromString(uuidStr);
    }

    static class PosiServerProtocol {
        public static final int POSI_MARKER = 0x504f5349;

        public static final int POSI_VERSION = 0x01;

        static SecureRandom sSecureRandom;

        private final DuplexSocket mmDuplexSocket;

        public PosiServerProtocol(DuplexSocket socket) {
            if (sSecureRandom == null) {
                sSecureRandom = new SecureRandom();
            }
            mmDuplexSocket = socket;
        }

        private byte[] getHeader() {
            byte[] header = new byte[16];
            ByteBuffer buffer = ByteBuffer.wrap(header);
            buffer.putInt(POSI_MARKER);
            buffer.putInt(POSI_VERSION);
            buffer.putLong(sSecureRandom.nextLong());
            return header;
        }

        // TODO:
        public CorralRequestHandler getRequestHandler() throws IOException {
            if (DBG)
                Log.d(TAG, "Getting request handler for posi session");
            OutputStream out = mmDuplexSocket.getOutputStream();
            byte[] header = getHeader();
            if (DBG)
                Log.d(TAG, "Writing header " + new String(header));
            out.write(header);
            if (DBG)
                Log.d(TAG, "Flushing header bytes");
            out.flush();
            if (DBG)
                Log.d(TAG, "Done writing header.");
            /**
             * TODO: SignedObj obj = ObjDecoder.decode(readObj()) Authenticate
             * signer and select protocol. Authentication verifies nonce and
             * ensures timestamp is more recent than the users' last transmitted
             * obj's timestamp.
             */
            return new NonceRequestHandler();
        }
    }

    /**
     * The trivial request handler that sends a nonce and hangs up.
     */
    static class NonceRequestHandler implements CorralRequestHandler {
        // Does nothing.
    }

    interface CorralRequestHandler {
    }

    /**
     * Makes a webapp available offline via the Corral.
     */
    public static Uri cacheWebApp(Uri appUri) {
    	String appName = getWebappCacheName(appUri);
		return cacheWebApp(appUri, appName);
    }

    /**
     * Makes a webapp available offline via the Corral.
     */
    public static Uri cacheWebApp(Uri appUri, String name) {
    	File localAppDir = new File(
				new File(Environment.getExternalStorageDirectory(),
						ContentCorral.APPS_SUBFOLDER), name);
    	try {
			FileUtils.deleteDirectory(localAppDir);
		} catch (IOException e) {}
    	localAppDir.mkdirs();
		String indexPath = getIndexPath(appUri);
		try {
			downloadFile(localAppDir, appUri, indexPath);
			scrapePage(localAppDir, appUri.buildUpon().path("").build(), indexPath);
		} catch (IOException e) {
			Log.e(TAG, "Failed to cache webapp", e);
		}
		return getWebappCacheUrl(appUri, localAppDir.getName());
	}

    public static Uri getWebappCacheUrl(Uri webapp) {
    	return getWebappCacheUrl(webapp, getWebappCacheName(webapp));
    }

    public static Uri getWebappCacheUrl(Uri webapp, String localName) {
    	File appPath = new File(Environment.getExternalStorageDirectory(), APPS_SUBFOLDER);
    	appPath = new File(appPath, localName);
    	if (appPath.exists()) {
    		return Uri.parse("http://127.0.0.1:" + SERVER_PORT + "/app/" +
    				localName + getIndexPath(webapp));
    	}
    	return null;
    }

    public static String getWebappCacheName(Uri webapp) {
    	return Util.convertToHex(Util.sha256(webapp.toString().getBytes())).substring(0, 10);
    }

	private static Pattern sScriptRegex = Pattern.compile("<\\s*script\\s+[^>]+>", Pattern.CASE_INSENSITIVE);
	private static Pattern sSrcRegex = Pattern.compile("\\bsrc\\s*=\\s*(\"[^\"]+\"|'[^']+')", Pattern.CASE_INSENSITIVE);

	private static void scrapePage(File storageDir, Uri baseUri, String relativePath) throws IOException {
		File sourceFile = new File(storageDir, relativePath);
		String page = IOUtils.toString(new FileInputStream(sourceFile));
		Matcher matcher = sScriptRegex.matcher(page);
		int offset = 0;
		while (matcher.find(offset)) {
			try {
				String tag = matcher.group();
				Matcher srcMatcher = sSrcRegex.matcher(tag);
				if(!srcMatcher.find())
					continue;
				String srcPath = srcMatcher.group(1);
				srcPath = srcPath.substring(1, srcPath.length() - 1);
				srcPath = StringEscapeUtils.unescapeHtml4(srcPath);
				//srcPath = absoluteToRelative(baseUri, srcPath);
				if (!srcPath.contains("://")) {
					Uri absolutePath = getAbsoluteUri(baseUri, relativePath, srcPath);
					downloadFile(storageDir, absolutePath, absolutePath.getPath());
				}
			} finally {
				offset = matcher.end();
			}
		}
	}

	/**
	 * If the given path is provisioned by baseUri, returns a localized
	 * representation of that path.
	 */
	private String absoluteToRelative(Uri baseUri, String path) {
		if (path.startsWith(baseUri.toString())) {
			path = path.substring(baseUri.toString().length());
			Log.d(TAG, "TRUNCATED TO " + path);
		}
		return path;
	}

	private static String getIndexPath(Uri uri) {
		String seg = uri.getLastPathSegment();
		if (seg.endsWith(".html") || seg.endsWith(".htm") || seg.endsWith(".php")) {
			return uri.getPath();
		}
		return uri.buildUpon().appendPath("index.html").build().getPath();
	}

	private static Uri getAbsoluteUri(Uri baseUri, String parentFile, String src) {
		if (src.startsWith("/")) {
			return baseUri.buildUpon().path(src).build();
		}

		Uri.Builder builder = baseUri.buildUpon();
		String[] parentPath = parentFile.split("/");
		for (int i = 0; i < parentPath.length - 1; i++) {
			builder.appendPath(parentPath[i]);
		}

		String[] srcParts = src.split("/");
		for (String part : srcParts) {
			builder.appendPath(part);
		}
		return builder.build();
	}

	private static void downloadFile(File storageDir, Uri page, String localPath) throws IOException {
		URL url = new URL(page.toString());
		HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
		InputStream inputStream = new BufferedInputStream(urlConnection.getInputStream());

		File outFile = new File(storageDir, localPath);
		outFile.getParentFile().mkdirs();
		FileOutputStream outStream = new FileOutputStream(outFile);

		byte[] buffer = new byte[2048];
		int readLength;
		while ( (readLength = inputStream.read(buffer)) > 0) {
			outStream.write(buffer, 0, readLength);
		}

		outStream.close();
		inputStream.close();
	}
}
