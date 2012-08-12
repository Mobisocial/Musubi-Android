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
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.mobisocial.corral.CorralHelper.DownloadProgressCallback;
import org.mobisocial.corral.CorralHelper.DownloadProgressCallback.DownloadChannel;
import org.mobisocial.corral.CorralHelper.DownloadProgressCallback.DownloadState;
import org.mobisocial.corral.CorralHelper.UploadProgressCallback;

import mobisocial.musubi.util.Base64;

import android.util.Log;

public class CryptUtil {

	int blocksize = 16;
	int bufsize   = 65536;
    Cipher encCipher = null;
    Cipher decCipher = null;
    byte[] buf = new byte[blocksize];
    byte[] obuf = new byte[512];
    byte[] key = null;
    byte[] IV = null;
    String keystr = null;
    long length = 0;
    String md5 = null;

    long mLastCheckedForCancellation;

    public CryptUtil() throws NoSuchAlgorithmException{
		KeyGenerator kgen = KeyGenerator.getInstance("AES");
		SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
	    kgen.init(128, sr);
	    SecretKey skey = kgen.generateKey();
	    key = skey.getEncoded();
	    keystr = Base64.encodeToString(key, false);
        IV = new byte[blocksize];
    }
    
    public CryptUtil(String mykey){
        key = Base64.decode(mykey);
        keystr= mykey;
        IV = new byte[blocksize];
    }
    
    public String getKey(){
//    	Log.e("KEY", keystr);
//    	Log.e("HASH", ""+keystr.hashCode());
    	return keystr;
    }
    
    public long getLength(){
    	return length;
    }

    public String getMd5(){
    	return md5;
    }
    
    public void InitCiphers()
            throws NoSuchAlgorithmException,
            NoSuchProviderException,
            NoSuchProviderException,
            NoSuchPaddingException,
            InvalidKeyException,
            InvalidAlgorithmParameterException{
       encCipher = Cipher.getInstance("AES/CBC/PKCS5Padding", "BC");
       SecretKey keyValue = new SecretKeySpec(key,"AES");
       AlgorithmParameterSpec IVspec = new IvParameterSpec(IV);
       encCipher.init(Cipher.ENCRYPT_MODE, keyValue, IVspec);

       decCipher = Cipher.getInstance("AES/CBC/PKCS5Padding", "BC");
       decCipher.init(Cipher.DECRYPT_MODE, keyValue, IVspec);
    }

    public void ResetCiphers()
    {
        encCipher=null;
        decCipher=null;
    }

    public void encrypt(InputStream fis, OutputStream fos, UploadProgressCallback callback)
            throws IOException, ShortBufferException, IllegalBlockSizeException,
            BadPaddingException, NoSuchAlgorithmException {
        fis = new BufferedInputStream(fis);
        fis = new CipherInputStream(fis, encCipher);
        fos = new BufferedOutputStream(fos);
        byte[] buf = new byte[bufsize];
        int len = 0;
        length = 0;
        md5 = null;
        MessageDigest md = MessageDigest.getInstance("MD5");

        checkUserCancellation(callback);
        while ((len = fis.read(buf)) != -1) {
            checkUserCancellation(callback);
        	fos.write(buf,0,len);
        	md.update(buf,0,len);
            length += len;
        }

        byte[] b = md.digest();
        md5 = Base64.encodeToString(b, false);

        fos.close();
        fis.close();
        Log.e("MD5", md5);
    }

    void checkUserCancellation(UploadProgressCallback callback) throws IOException {
        long now = System.currentTimeMillis();
        if (now - mLastCheckedForCancellation > CorralHelper.CANCEL_SAMPLE_WINDOW) {
            if (callback.isCancelled()) {
                throw new IOException("User cancelled transfer");
            }
            mLastCheckedForCancellation = now;
        }
    }

    public void decrypt(InputStream fis, OutputStream fos, long total, DownloadProgressCallback callback)
            throws IOException, ShortBufferException, IllegalBlockSizeException, BadPaddingException {
        DownloadChannel channel = DownloadChannel.SERVER;
        callback.onProgress(DownloadState.TRANSFER_IN_PROGRESS, channel, 0);
        byte[] buf = new byte[bufsize];
        int len = 0;
        fis = new BufferedInputStream(fis);
        fos = new BufferedOutputStream(fos);
        fos = new CipherOutputStream(fos, decCipher);
        int read = 0;
        int progress = 0;
        while ((len = fis.read(buf)) != -1) {
            read += len;
            int newProgress = Math.round(100f * read / total);
            if (progress != newProgress) {
                progress = newProgress;
                callback.onProgress(DownloadState.TRANSFER_IN_PROGRESS, channel, progress);
            }
            fos.write(buf,0,len);
        }
        fos.close();
        fis.close();
    }
}