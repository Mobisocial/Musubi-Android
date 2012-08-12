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

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.security.GeneralSecurityException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.mobisocial.corral.CorralDownloadHandler.CorralDownloadFuture;
import org.mobisocial.corral.CorralHelper.DownloadProgressCallback;
import org.mobisocial.corral.CorralHelper.EncRslt;
import org.mobisocial.corral.CorralHelper.UploadProgressCallback;
import org.mobisocial.corral.CorralHelper.UploadProgressCallback.UploadState;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

public class CorralS3Connector {

	private static final String CONTENT_TYPE = "application/octet-stream; charset=UTF-8";
	private String TAG = "CorralS3Connector";
	private Context mContext;
	private static final String SERVER_URL = "http://corral-1.s3.amazonaws.com/";

	public CorralS3Connector(Context context){
        mContext = context;
	}
	
	public void uploadToServer(String ticket, final EncRslt rslt, String datestr, String objName,
	        final UploadProgressCallback callback) throws ClientProtocolException, IOException {
	    callback.onProgress(UploadState.PREPARING_UPLOAD, 0);
		Log.d(TAG, "-----UPLOAD START-----");
		Log.d(TAG, "URI: "+rslt.uri.toString());
		Log.d(TAG, "length: "+rslt.length);
		Log.d(TAG, "ticket: "+ticket);
		Log.d(TAG, SERVER_URL+objName);
		Log.d(TAG, "Authorization: AWS "+ticket);
		Log.d(TAG, "Content-Md5: "+rslt.md5);
		Log.d(TAG, "Content-Type: "+CONTENT_TYPE);
		Log.d(TAG, "Date: "+datestr);
		
		InputStream in = mContext.getContentResolver().openInputStream(rslt.uri);
		final int contentLength = in.available();

		HttpClient http = new DefaultHttpClient();
		HttpPut put = new HttpPut(SERVER_URL + objName);

		put.setHeader("Authorization", "AWS "+ticket);
		put.setHeader("Content-Md5", rslt.md5);
		put.setHeader("Content-Type", CONTENT_TYPE);
        put.setHeader("Date", datestr);

        HttpEntity progress = new ProgressEntity(callback, rslt, contentLength);
        
        put.setEntity(progress);
		HttpResponse response = http.execute(put);
		final int responseCode = response.getStatusLine().getStatusCode();
		
		// FOR DEBUG
		if(responseCode != HttpURLConnection.HTTP_OK){
    		throw new RuntimeException("invalid response code, " + responseCode);
		}
	}
	
	Uri downloadAndDecrypt(String ticket, String datestr, String objName,
			File cachefile, String mykey,
	        CorralDownloadFuture future, DownloadProgressCallback callback) throws IOException, GeneralSecurityException {
		Log.d(TAG, "-----DOWNLOAD+DECRYPT START-----"+(String.valueOf(System.currentTimeMillis())));
		Log.d(TAG, SERVER_URL+objName);
		Log.d(TAG, "Authorization: AWS "+ticket);
		Log.d(TAG, "Date: "+datestr);

		HttpClient http = new DefaultHttpClient();
		HttpGet get = new HttpGet(SERVER_URL+objName);
		get.addHeader("Authorization", "AWS " + ticket);
		get.addHeader("Date", datestr);
		HttpResponse response = http.execute(get);

        DataInputStream is = new DataInputStream(response.getEntity().getContent());  
        long contentLength = response.getEntity().getContentLength();

	    if (!cachefile.exists()) {
			File tmpFile = new File(cachefile.getAbsoluteFile() + ".tmp");
            tmpFile.getParentFile().mkdirs();
			try {
		        CryptUtil cu = new CryptUtil(mykey);
		        cu.InitCiphers();
		        FileOutputStream fos = new FileOutputStream(tmpFile);
		        cu.decrypt(is, fos, contentLength, callback);
		        try {
			        is.close();
		        } catch (IOException e) {}
		        try {
		            fos.close();
		        } catch (IOException e) {}
		        
				tmpFile.renameTo(cachefile);

            } catch (IOException e) {
                if (tmpFile.exists()) {
                    tmpFile.delete();
                }
                throw e;
			} catch (GeneralSecurityException e) {
                throw e;
            }
        }
        return cachefile.exists() ? Uri.fromFile(cachefile) : null;
	}

	class ProgressEntity extends AbstractHttpEntity {
        int uploaded = 0;
        int progress = 0;
        final UploadProgressCallback callback;
        final EncRslt rslt;
        final int contentLength;

        long mLastCheckedForCancellation = 0;
        
        public ProgressEntity(UploadProgressCallback callback, EncRslt encoding, int contentLength) {
            this.callback = callback;
            this.rslt = encoding;
            this.contentLength = contentLength;
        }

        @Override
        public void writeTo(OutputStream outstream) throws IOException {
            callback.onProgress(UploadState.TRANSFER_IN_PROGRESS, progress);
            if (outstream == null) {
                throw new IllegalArgumentException("Output stream may not be null");
            }

            checkUserCancellation();
            InputStream instream = getContent();
            try {
                checkUserCancellation();
                byte[] tmp = new byte[4096];
                int l;
                while ((l = instream.read(tmp)) != -1) {
                    checkUserCancellation();
                    outstream.write(tmp, 0, l);
                    uploaded += l;

                    int newProgress = (int) Math.round(100. * uploaded / contentLength);
                    if (newProgress > progress) {
                        progress = newProgress;
                        callback.onProgress(UploadState.TRANSFER_IN_PROGRESS, progress);
                    }
                }
                outstream.flush();
            } finally {
                instream.close();
            }
            callback.onProgress(UploadState.FINISHING_UP, 0);
        }

        void checkUserCancellation() throws IOException {
            long now = System.currentTimeMillis();
            if (now - mLastCheckedForCancellation > CorralHelper.CANCEL_SAMPLE_WINDOW) {
                if (callback.isCancelled()) {
                    throw new IOException("User cancelled transfer");
                }
                mLastCheckedForCancellation = now;
            }
        }

        @Override
        public boolean isStreaming() {
            return false;
        }
        
        @Override
        public boolean isRepeatable() {
            return true;
        }
        
        @Override
        public long getContentLength() {
            return contentLength;
        }
        
        @Override
        public InputStream getContent() throws IOException, IllegalStateException {
            return mContext.getContentResolver().openInputStream(rslt.uri);
        }
    };
}
