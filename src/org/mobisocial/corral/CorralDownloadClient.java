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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import mobisocial.musubi.App;
import mobisocial.musubi.model.DbContactAttributes;
import mobisocial.musubi.model.helpers.ObjectManager;
import mobisocial.musubi.objects.PictureObj;
import mobisocial.musubi.objects.VideoObj;
import mobisocial.socialkit.SignedObj;
import mobisocial.socialkit.musubi.DbIdentity;
import mobisocial.socialkit.musubi.DbObj;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;
import org.mobisocial.corral.CorralDownloadHandler.CorralDownloadFuture;
import org.mobisocial.corral.CorralHelper.DownloadProgressCallback;
import org.mobisocial.corral.CorralHelper.DownloadProgressCallback.DownloadChannel;
import org.mobisocial.corral.CorralHelper.DownloadProgressCallback.DownloadState;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;

public class CorralDownloadClient {
    private static final String TAG = "corral";
    private static final boolean DBG = true;

    public static final String OBJ_MIME_TYPE = "mimeType";
    public static final String OBJ_LOCAL_URI = "localUri";
    public static final String OBJ_PRESHARED_KEY = "sharedkey";

    private final Context mContext;
	private ObjectManager mObjectManager;

    public static CorralDownloadClient getInstance(Context context) {
        return new CorralDownloadClient(context);
    }

    private CorralDownloadClient(Context context) {
        mContext = context;
        mObjectManager = new ObjectManager(App.getDatabaseSource(context));
    }

    public boolean fileAvailableLocally(DbObj obj) {
        try {
            if (mObjectManager.isObjectFromLocalDevice(obj.getLocalId())) {
                return true;
            }
            // if (obj.getSender() is owned AND obj.getDevice() is this one) ...
            // return true if we can fetch content locally
            return localFileForContent(obj, false).exists();
        } catch (Exception e) {
            Log.w(TAG, "Error checking file availability", e);
            return false;
        }
    }

    /**
     * Returns a uri for the locally available content or null
     * if the content is not available locally.
     */
    public Uri getAvailableContentUri(DbObj obj) {
        if (!fileAvailableLocally(obj)) {
            return null;
        }
        if (mObjectManager.isObjectFromLocalDevice(obj.getLocalId())) {
            try {
                String uriString = obj.getJson().getString(OBJ_LOCAL_URI);
                return Uri.parse(uriString);
            } catch (Exception e) {
                return null;
            }
        }
        return Uri.fromFile(localFileForContent(obj, false));
    }

    /**
     * Synchronized method that retrieves content by any possible transport, and
     * returns a uri representing it locally. This method blocks until the file
     * is available locally, or it has been determined that the file cannot
     * currently be fetched.
     */
    Uri fetchContent(DbObj obj, CorralDownloadFuture future,
            DownloadProgressCallback callback) throws IOException {
        if (obj.getJson() == null || !obj.getJson().has(OBJ_LOCAL_URI)) {
            if (DBG) {
                Log.d(TAG, "no local uri for obj.");
            }
            return null;
        }
        if (mObjectManager.isObjectFromLocalDevice(obj.getLocalId())) {
            try {
                // TODO: Objects shared out from the content corral should
                // be accessible through the content corral. We don't have
                // to copy all files but we should have the option to create
                // a locate cache.
                return Uri.parse(obj.getJson().getString(OBJ_LOCAL_URI));
            } catch (JSONException e) {
                Log.e(TAG, "json exception getting local uri", e);
                return null;
            }            
        }

        DbIdentity user = obj.getSender();
        if (user == null) {
            throw new IOException("Null user in corral");
        }
        File localFile = localFileForContent(obj, false);
        if (localFile.exists()) {
            return Uri.fromFile(localFile);
        }

        try {
            if (userAvailableOnLan(user)) {
                return doMediaScan(getFileOverLan(user, obj, future, callback));
            }
        } catch (IOException e) {
            if (DBG) Log.d(TAG, "Failed to pull LAN file", e);
        }

        try {
            return doMediaScan(CorralHelper.downloadContent(mContext,
                    localFile, obj, future, callback));
        } catch (IOException e) {
            if (DBG) Log.d(TAG, "Failed to pull Corral file", e);
        }

        try {
            return doMediaScan(getFileOverBluetooth(user, obj, future, callback));
        } catch (IOException e) {
        }

        if (!localFile.exists()) {
            callback.onProgress(DownloadState.TRANSFER_COMPLETE, DownloadChannel.NONE, DownloadProgressCallback.FAILURE);
            throw new IOException("Failed to fetch file");
        }

        callback.onProgress(DownloadState.TRANSFER_COMPLETE, DownloadChannel.NONE, DownloadProgressCallback.SUCCESS);
        return doMediaScan(Uri.fromFile(localFile));
    }

    Uri doMediaScan(Uri content) {
        String[] paths = new String[] { content.getPath() };
        MediaScannerConnection.scanFile(mContext, paths, null, null);
        return content;
    }

    public String getMimeType(DbObj obj) {
        if (obj.getJson() != null && obj.getJson().has(OBJ_MIME_TYPE)) {
            try {
                return obj.getJson().getString(OBJ_MIME_TYPE);
            } catch (JSONException e) {
            }
        }
        return null;
    }

    private Uri getFileOverBluetooth(DbIdentity user, SignedObj obj, CorralDownloadFuture future,
            DownloadProgressCallback callback) throws IOException {
        callback.onProgress(DownloadState.PREPARING_CONNECTION, DownloadChannel.BLUETOOTH, 0);
        String macStr = DbContactAttributes.getAttribute(mContext, user.getLocalId(),
                DbContactAttributes.ATTR_BT_MAC);
        if (macStr == null) {
            throw new IOException("No bluetooth mac address for user");
        }
        String uuidStr = DbContactAttributes.getAttribute(mContext, user.getLocalId(),
                DbContactAttributes.ATTR_BT_CORRAL_UUID);
        if (uuidStr == null) {
            throw new IOException("No corral uuid for user");
        }
        UUID uuid = UUID.fromString(uuidStr);
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice device = adapter.getRemoteDevice(macStr);
        BluetoothSocket socket;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD_MR1) {
            socket = device.createInsecureRfcommSocketToServiceRecord(uuid);
        } else {
            socket = device.createRfcommSocketToServiceRecord(uuid);
        }

        // TODO:
        // Custom wire protocol, look for header bits to map to protocol handler.
        Log.d(TAG, "BJD BLUETOOTH CORRAL NOT READY: can't pull file over bluetooth.");
        return null;
    }

    private Uri getFileOverLan(DbIdentity user, DbObj obj, CorralDownloadFuture future,
            DownloadProgressCallback callback) throws IOException {
        DownloadChannel channel = DownloadChannel.LAN;
        callback.onProgress(DownloadState.PREPARING_CONNECTION, channel, 0);
        InputStream in = null;
        OutputStream out = null;
        try {
            // Remote
            String ip = getUserLanIp(mContext, user);
            Uri remoteUri = uriForLanContent(ip, obj);

            if (DBG) {
                Log.d(TAG, "Attempting to pull lan file " + remoteUri);
            }

            HttpClient http = new DefaultHttpClient();
            HttpGet get = new HttpGet(remoteUri.toString());
            HttpResponse response = http.execute(get);
            long contentLength = response.getEntity().getContentLength();

            File localFile = localFileForContent(obj, false);
            if (!localFile.exists()) {
                if (future.isCancelled()) {
                    throw new IOException("User error");
                }
                localFile.getParentFile().mkdirs();
                try {
                    in = response.getEntity().getContent();
                    out = new FileOutputStream(localFile);
                    byte[] buf = new byte[1024];
                    int len;

                    callback.onProgress(DownloadState.TRANSFER_IN_PROGRESS, channel, 0);
                    int read = 0;
                    int progress = 0;
                    while (!future.isCancelled() && (len = in.read(buf)) > 0) {
                        read += len;
                        if (contentLength > 0) {
                            int newProgress = Math.round(100f * read / contentLength);
                            if (progress != newProgress) {
                                progress = newProgress;
                                callback.onProgress(DownloadState.TRANSFER_IN_PROGRESS, channel, progress);
                            }
                        }
                        out.write(buf, 0, len);
                    }
                    if (future.isCancelled()) {
                        throw new IOException("user cancelled");
                    }
                    if (DBG) Log.d(TAG, "successfully fetched content over lan");
                    callback.onProgress(DownloadState.TRANSFER_COMPLETE, channel, DownloadProgressCallback.SUCCESS);
                } catch (IOException e) {
                	if (DBG) Log.d(TAG, "failed to get content from lan");
                    callback.onProgress(DownloadState.TRANSFER_COMPLETE, channel, DownloadProgressCallback.FAILURE);
                    if (localFile.exists()) {
                        localFile.delete();
                    }
                    throw e;
                }
            }

            return Uri.fromFile(localFile);
        } catch (Exception e) {
            throw new IOException(e);
        } finally {
        	try {
				if(in != null) in.close();
				if(out != null) out.close();
			} catch (IOException e) {
				Log.e(TAG, "failed to close handle on get corral content", e);
			}
        }
    }

    private boolean userAvailableOnLan(DbIdentity user) {
        // TODO: ipv6 compliance.
        // TODO: Try multiple ip endpoints; multi-sourced download;
        // torrent-style sharing
        // (mobile, distributed CDN)
        return null != DbContactAttributes.getAttribute(mContext, user.getLocalId(),
                DbContactAttributes.ATTR_LAN_IP);
    }


    private static Uri uriForLanContent(String host, DbObj obj) {
        try {
            String localContent = obj.getJson().getString(OBJ_LOCAL_URI);
            Uri baseUri = Uri.parse("http://" + host + ":" + ContentCorral.SERVER_PORT);
            return baseUri.buildUpon()
                    .appendQueryParameter("content", localContent)
                    .appendQueryParameter("hash", "" + obj.getUniversalHashString()).build();
        } catch (Exception e) {
            Log.d(TAG, "No uri for content " + obj.getHash() + "; " + obj.getJson());
            return null;
        }
    }

    private static String getUserLanIp(Context context, DbIdentity user) {
        return DbContactAttributes.getAttribute(context, user.getLocalId(),
                DbContactAttributes.ATTR_LAN_IP);
    }

    /**
     * The filename where this obj's content would be stored.
     */
    public static File localFileForContent(DbObj obj, boolean thumb) {
        try {
            File contentDir;
            String type = obj.getType();
            if (PictureObj.TYPE.equals(type) || VideoObj.TYPE.equals(type)) {
                contentDir = new File(Environment.getExternalStorageDirectory(), ContentCorral.PICTURE_SUBFOLDER);
            } else {
                contentDir = new File(Environment.getExternalStorageDirectory(), ContentCorral.FILES_SUBFOLDER);
            }

            JSONObject json = obj.getJson();
            String suffix = extensionForType(json.optString(OBJ_MIME_TYPE));
            if (thumb) {
                suffix = thumb + "." + suffix;
            }
            String fname = obj.getUniversalHashString() + "." + suffix;
            return new File(contentDir, fname);
        } catch (Exception e) {
            Log.e(TAG, "Error looking up file name", e);
            return null;
        }
    }

    static String extensionForType(String type) {
        final String DEFAULT = "dat";
        if (type == null) {
            return DEFAULT;
        }
        if (type.equals("image/jpeg")) {
            return "jpg";
        }
        if (type.equals("video/3gpp")) {
            return "3gp";
        }
        if (type.equals("image/png")) {
            return "png";
        }
        return DEFAULT;
    }
    static boolean containsBytes(byte[] header, byte[] test, int limit) {
    	assert(limit > test.length);
    	for(int i = 0; i < limit - test.length; ++i) {
    		int j = 0;
    		for(; j < test.length; ++j) {
    			if(header[i] != test[j])
    				break;
    		}
    		if(j == test.length)
    			return true;
    	}
    	return false;
    }
    public static String typeForBytes(byte[] header, String obj_type) {
        String DEFAULT = null;
        if(obj_type == PictureObj.TYPE) {
        	//TODO: lame our jpeg encoder doesn't put in proper
        	//jfif headers
        	DEFAULT = "image/jpeg";
        }
        	

        if(containsBytes(header, "JFIF".getBytes(), 16)) {
        	return "image/jpeg";
        }
        if(containsBytes(header, "�PNG".getBytes(), 16)) {
        	return "image/png";
        }
        return DEFAULT;
    }

    static String typeForExtension(String ext) {
        if (ext == null) {
            return null;
        }
        if (ext.equals("jpg")) {
            return "image/jpeg";
        }
        if (ext.equals("3gp")) {
            return "video/3gp";
        }
        if (ext.equals("png")) {
            return "image/png";
        }
        return null;
    }

    private static class HashUtils {
        static String convertToHex(byte[] data) {
            StringBuffer buf = new StringBuffer();
            for (int i = 0; i < data.length; i++) {
                int halfbyte = (data[i] >>> 4) & 0x0F;
                int two_halfs = 0;
                do {
                    if ((0 <= halfbyte) && (halfbyte <= 9))
                        buf.append((char) ('0' + halfbyte));
                    else
                        buf.append((char) ('a' + (halfbyte - 10)));
                    halfbyte = data[i] & 0x0F;
                } while (two_halfs++ < 1);
            }
            return buf.toString();
        }

        public static String SHA1(String text) throws NoSuchAlgorithmException,
                UnsupportedEncodingException {
            MessageDigest md;
            md = MessageDigest.getInstance("SHA-1");
            byte[] sha1hash = new byte[40];
            md.update(text.getBytes("iso-8859-1"), 0, text.length());
            sha1hash = md.digest();
            return convertToHex(sha1hash);
        }
    }

    private static String hashToString(long hash) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();  
            DataOutputStream dos = new DataOutputStream(bos);  
            dos.writeLong(hash);  
            dos.writeInt(-4);  
            byte[] data = bos.toByteArray();
            return Base64.encodeToString(data, Base64.DEFAULT).substring(0, 11);
        } catch (IOException e) {
            return null;
        }
    }
}
