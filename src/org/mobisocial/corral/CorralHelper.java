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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Date;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;

import mobisocial.musubi.App;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.socialkit.musubi.DbIdentity;
import mobisocial.socialkit.musubi.DbObj;

import org.json.JSONException;
import org.json.JSONObject;
import org.mobisocial.corral.CorralDownloadHandler.CorralDownloadFuture;
import org.mobisocial.corral.CorralHelper.DownloadProgressCallback.DownloadChannel;
import org.mobisocial.corral.CorralHelper.DownloadProgressCallback.DownloadState;

import android.content.Context;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

public class CorralHelper {

	private static String TAG = "CorralHelper";
	public static final long CANCEL_SAMPLE_WINDOW = 2500;

	private static MIdentity[] getBuddies(Context context, Uri feedUri) {
        SQLiteOpenHelper helper = App.getDatabaseSource(context);
        IdentitiesManager identitiesManager = new IdentitiesManager(helper);

        List<DbIdentity> dbis = App.getMusubi(context).getFeed(feedUri).getMembers();
        
        MIdentity[] buddies = new MIdentity[dbis.size()];
        int i = 0;
        for(DbIdentity dbi:dbis){
            buddies[i++] = identitiesManager.getIdentityForId(dbi.getLocalId());
        }
        return buddies;
    }

	static Uri downloadContent(Context context, File cachefile, DbObj obj,
	        CorralDownloadFuture future, DownloadProgressCallback callback) throws IOException {
		try {
		    DownloadChannel server = DownloadChannel.SERVER;
		    callback.onProgress(DownloadState.PREPARING_CONNECTION, server, 0);

			JSONObject json = obj.getJson();
			String mykey = json.getString(CorralDownloadClient.OBJ_PRESHARED_KEY);
			String objName = obj.getUniversalHashString();

			CorralTicketProvider ctp = new CorralTicketProvider(context);
			String ticket = ctp.getDownloadTicket(objName);
			String datestr = ctp.getDatestr();
			if(ticket==null){
	    		throw new IOException("failed to get ticket for download");
			}
			
			if (future.isCancelled()) {
			    throw new IOException("User cancelled download");
			}
			CorralS3Connector s3cn = new CorralS3Connector(context);
			try {
			    s3cn.downloadAndDecrypt(ticket, datestr, objName, cachefile, mykey, future, callback);
			} catch (IOException e) {
			    callback.onProgress(DownloadState.TRANSFER_COMPLETE, server, DownloadProgressCallback.FAILURE);
			    throw e;
			} catch (GeneralSecurityException e) {
			    callback.onProgress(DownloadState.TRANSFER_COMPLETE, server, DownloadProgressCallback.FAILURE);
			    throw new IOException("Failed to decrypt");
			}

			Uri result = Uri.fromFile(cachefile);
			future.setResult(result);
			callback.onProgress(DownloadState.TRANSFER_COMPLETE, server, DownloadProgressCallback.SUCCESS);
			Log.d(TAG, "-----END-----"+(String.valueOf(System.currentTimeMillis())));
			return Uri.fromFile(cachefile);
		} catch (JSONException e) {
			throw new IOException(e);
		}
	}

	public interface UploadProgressCallback {
	    enum UploadState { PREPARING_UPLOAD, TRANSFER_IN_PROGRESS, FINISHING_UP };
	    public void onProgress(UploadState state, int progress);
	    public boolean isCancelled();
	}

	public interface DownloadProgressCallback {
	    enum DownloadState { DOWNLOAD_PENDING, PREPARING_CONNECTION, TRANSFER_IN_PROGRESS,
	        TRANSFER_COMPLETE };
	    enum DownloadChannel { NONE, LAN, BLUETOOTH, SERVER };

	    public static final int SUCCESS = 1;
	    public static final int FAILURE = 2;

	    public void onProgress(DownloadState state, DownloadChannel channel, int progress);
	}

	public static boolean uploadContent(Context context, DbObj obj, UploadProgressCallback callback) {
	    MIdentity[] buddies = getBuddies(context, obj.getContainingFeed().getUri());
		try {
			Uri dataUri = null;
			String mykey = null;
			String mime_type = null;
			if (obj.getJson() == null) {
			    throw new JSONException("null json");
			}
			JSONObject jso = obj.getJson();
			if (jso == null || !(jso.has(CorralDownloadClient.OBJ_LOCAL_URI)
			        && jso.has(CorralDownloadClient.OBJ_MIME_TYPE))) {
			    return false;
			}

			dataUri = Uri.parse(jso.getString(CorralDownloadClient.OBJ_LOCAL_URI));
            mykey   = jso.getString(CorralDownloadClient.OBJ_PRESHARED_KEY);
            mime_type = jso.getString(CorralDownloadClient.OBJ_MIME_TYPE);
			
	    	EncRslt rslt = encryptData(dataUri, context, mykey, callback);
	    	if(rslt == null){
	    		Log.e(TAG, "failed to encrypt");
				return false;
	    	}
			String objName = obj.getUniversalHashString();
			// NOTE
			// We cannot use URLEncode and Base64 because they generate "/" and "%2f"
			// which are not allowed to use inside URLs for Tomcat Server...
			// So, hex encoded value of the strings will be sent.
			CorralTicketProvider ctp = new CorralTicketProvider(context);
			String ticket = ctp.getUploadTicket(
					objName,
					bin2hex(mime_type.getBytes()),
					String.valueOf(rslt.length),
					bin2hex(rslt.md5.getBytes()));
			String datestr = ctp.getDatestr();
			if(ticket==null){
	    		Log.e(TAG, "failed to get ticket for upload");
				return false;
			}
			
			CorralS3Connector s3cn = new CorralS3Connector(context);
			s3cn.uploadToServer(ticket, rslt, datestr, objName, callback);
			
			ctp.putACL(objName, buddies);
			return true;
			
		} catch (IOException e1) {
			e1.printStackTrace();
			return false;
		} catch (JSONException e) {
			e.printStackTrace();
			return false;
		}
		
	}
	
	private static EncRslt encryptData(Uri uri, Context context, String mykey,
	        UploadProgressCallback callback) {
		try {
			EncRslt rslt = new EncRslt();
			CryptUtil cu = new CryptUtil(mykey);
			cu.InitCiphers();
			rslt.key = cu.getKey();
			InputStream is = context.getContentResolver().openInputStream(uri);
			File dst = getFileForCrypt(String.valueOf(rslt.key.hashCode()), context);
			FileOutputStream fos = new FileOutputStream(dst);
			cu.encrypt(is, fos, callback);
			rslt.length = cu.getLength();
			rslt.md5 = cu.getMd5();
			rslt.uri = Uri.fromFile(dst);
			is.close();
			fos.close();
			return rslt;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return null;
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			return null;
		} catch (ShortBufferException e) {
			e.printStackTrace();
			return null;
		} catch (IllegalBlockSizeException e) {
			e.printStackTrace();
			return null;
		} catch (BadPaddingException e) {
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		} catch (InvalidKeyException e) {
			e.printStackTrace();
			return null;
		} catch (NoSuchProviderException e) {
			e.printStackTrace();
			return null;
		} catch (NoSuchPaddingException e) {
			e.printStackTrace();
			return null;
		} catch (InvalidAlgorithmParameterException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static File getFileForCrypt(String hash, Context context){
		File externalCacheDir = new File(new File(new File(new File(Environment.getExternalStorageDirectory(), "Android"), "data"), context.getPackageName()), "cypher");
		externalCacheDir.mkdirs();
		
		// clear old items
		String[] files = externalCacheDir.list();
		long th = new Date().getTime() - 24*60*60*1000; // expire one day
		for(int i=0; i<files.length;i++){
			File tmp = new File(externalCacheDir, files[i]);
			if(tmp.lastModified()<th){
				tmp.delete();
			}
		}
		
		// add new file
		File dst = new File(externalCacheDir, hash+".tmp");
		if(dst.exists()){
			dst.delete();
		}
//		dst.deleteOnExit();
		return dst;
	}
	
    public static class EncRslt {
    	public Uri uri;
    	public String key;
    	public long length;
    	public String md5;
    }

	public static String bin2hex(byte[] data) {
	    return String.format("%0" + (data.length*2) + "X", new BigInteger(1, data));
	}
	public byte[] hex2bin(String s) {
	    int len = s.length();
	    byte[] data = new byte[len / 2];
	    for (int i = 0; i < len; i += 2) {
	        data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
	                             + Character.digit(s.charAt(i+1), 16));
	    }
	    return data;
	}

}
