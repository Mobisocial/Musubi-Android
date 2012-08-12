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

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import android.net.wifi.ScanResult;
import android.util.Base64;

public class Util {

    public static final String TAG = "Util";

    public static String computeWifiFingerprint(List<ScanResult> results) {
		Set<String> ssidList = new HashSet<String>();
		for (ScanResult result : results) {
			ssidList.add(Util.MD5(result.BSSID));
		}
		
		String fingerprint = null;
		for (String string : ssidList) {
			if (fingerprint == null) {
				fingerprint = string;
			}
			else {
				fingerprint += ":" + string;
			}
		}
		
		if (fingerprint == null) {
			return "";
		}
		else {
			return fingerprint;
		}
    }
    
    /**
	 * Copies a stream.
	 */
	public static void copy(InputStream is, OutputStream os) throws IOException {
		int i;
		byte[] b = new byte[1024];
		while((i=is.read(b))!=-1) {
			os.write(b, 0, i);
		}
	}

	public static String convertToHex(byte[] data) {
		StringBuffer buf = new StringBuffer();

		for (int i = 0; i < data.length; i++) {
			int halfbyte = (data[i] >>> 4) & 0x0F;
			int two_halfs = 0;

			do {
				if ((0 <= halfbyte) && (halfbyte <= 9)) {
					buf.append((char) ('0' + halfbyte));
				} else {
					buf.append((char) ('a' + (halfbyte - 10)));
				}

				halfbyte = data[i] & 0x0F;
			} while (two_halfs++ < 1);
		}
		return buf.toString();
	}

	/**
	 * Converts a hex string to a byte array.
	 */
	public static byte[] convertToByteArray(String s) {
	    int len = s.length();
	    byte[] data = new byte[len/2];
	    for (int i = 0; i < len; i += 2) {
	        data[i/2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
	                + Character.digit(s.charAt(i+1), 16));
	    }
	    return data;
	}

	public static String SHA1(byte[] input) throws NoSuchAlgorithmException,
                                                   UnsupportedEncodingException {
		MessageDigest md;
		md = MessageDigest.getInstance("SHA-1");

		byte[] sha1hash = new byte[40];
		md.update(input, 0, input.length);
		sha1hash = md.digest();

		return convertToHex(sha1hash);
	}

	public static byte[] sha256(byte[] data) {
	    MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
            return md.digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("your platform does not support sha256", e);
        }
	}

	public static long shortHash(byte[] data) {
	    if (data.length < 8) {
	        throw new IllegalArgumentException("Data too short");
	    }
	    return ByteBuffer.wrap(data).getLong();
	}

    public static String MD5(String plaintext){
        try{
            MessageDigest m = MessageDigest.getInstance("MD5");
            m.reset();
            m.update(plaintext.getBytes());
            byte[] digest = m.digest();
            BigInteger bigInt = new BigInteger(1,digest);
            String hashtext = bigInt.toString(16);
            while(hashtext.length() < 32 ){
                hashtext = "0"+hashtext;
            }
            return hashtext;
        }catch(Exception e){
            return null;
        }
    }

    public static byte[] newAESKey(){
        try{
            KeyGenerator kgen = KeyGenerator.getInstance("AES");
            kgen.init(128); // 192 and 256 bits may not be available
            SecretKey skey = kgen.generateKey();
            return skey.getEncoded();
        }
        catch(NoSuchAlgorithmException e){
            throw new RuntimeException(e);
        }
    }

    public static byte[] decryptAES(byte[] cipherText, byte[] key){
        // Use AES key to encrypt the body
        try{
            SecretKeySpec skeySpec = new SecretKeySpec(key, "AES");
            Cipher aesCipher = Cipher.getInstance("AES");
            aesCipher.init(Cipher.DECRYPT_MODE, skeySpec);
            ByteArrayOutputStream cipherOut = new ByteArrayOutputStream();
            CipherOutputStream aesOut = new CipherOutputStream(cipherOut, aesCipher);
            aesOut.write(cipherText);
            aesOut.close();
            return cipherOut.toByteArray();
        }
        catch(InvalidKeyException e){
            throw new RuntimeException(e);
        }
        catch(NoSuchAlgorithmException e){
            throw new RuntimeException(e);
        }
        catch(NoSuchPaddingException e){
            throw new RuntimeException(e);
        }
        catch(IOException e){
            throw new RuntimeException(e);
        }
    }

    public static byte[] encryptAES(byte[] plainText, byte[] key){
        try{
            SecretKeySpec skeySpec = new SecretKeySpec(key, "AES");
            Cipher aesCipher = Cipher.getInstance("AES");
            aesCipher.init(Cipher.ENCRYPT_MODE, skeySpec);
            ByteArrayOutputStream cipherOut = new ByteArrayOutputStream();
            CipherOutputStream aesOut = new CipherOutputStream(cipherOut, aesCipher);
            aesOut.write(plainText);
            aesOut.close();
            return cipherOut.toByteArray();
        }
        catch(InvalidKeyException e){
            throw new RuntimeException(e);
        }
        catch(NoSuchAlgorithmException e){
            throw new RuntimeException(e);
        }
        catch(NoSuchPaddingException e){
            throw new RuntimeException(e);
        }
        catch(IOException e){
            throw new RuntimeException(e);
        }
    }

    public static String encryptAES(String plainText, byte[] key){
        return Base64.encodeToString(encryptAES(plainText.getBytes(), key), Base64.DEFAULT);
    }

    public static String decryptAES(String b64CipherText, byte[] key){
        try{
            return new String(decryptAES(Base64.decode(b64CipherText, Base64.DEFAULT), key), "UTF8");
        }
        catch(UnsupportedEncodingException e){
            throw new RuntimeException(e);
        }
    }

    public static String join(Collection<String> s, String delimiter) {
        if (s.isEmpty()) return "";
        Iterator<String> iter = s.iterator();
        StringBuffer buffer = new StringBuffer(iter.next());
        while (iter.hasNext()) {
            buffer.append(delimiter);
            buffer.append(iter.next());
        }
        return buffer.toString();
    }

    public static String joinLongs(Collection<Long> s, String delimiter) {
        if (s.isEmpty()) return "";
        Iterator<Long> iter = s.iterator();
        StringBuffer buffer = new StringBuffer(iter.next().toString());
        while (iter.hasNext()) {
            buffer.append(delimiter);
            buffer.append(iter.next());
        }
        return buffer.toString();
    }

    public static String join(long[] s, String delimiter) {
        if (s.length == 0) return "";
        StringBuffer buffer = new StringBuffer(String.valueOf(s[0]));
        for(int i = 1; i < s.length; i++){
            buffer.append(delimiter);
            buffer.append(s[i]);
        }
        return buffer.toString();
    }

    public static String join(String[] s, String delimiter) {
        if (s.length == 0) return "";
        StringBuffer buffer = new StringBuffer(s[0]);
        for(int i = 1; i < s.length; i++){
            buffer.append(delimiter);
            buffer.append(s[i]);
        }
        return buffer.toString();
    }

    public static long[] splitLongs(String s, String delimiter) {
        String[] ss = s.split(delimiter);
        long[] result = new long[ss.length];
        for(int i = 0; i < ss.length; i++){
            result[i] = Long.valueOf(ss[i]);
        }
        return result;
    }

    public static List<Long> splitLongsToList(String s, String delimiter) {
        String[] ss = s.split(delimiter);
        List<Long> result = new ArrayList<Long>();
        for(int i = 0; i < ss.length; i++){
            result.add(Long.valueOf(ss[i]));
        }
        return result;
    }
    
    public static void copyFile(FileInputStream fromFile, FileOutputStream toFile) throws IOException {
        FileChannel fromChannel = null;
        FileChannel toChannel = null;
        try {
            fromChannel = fromFile.getChannel();
            toChannel = toFile.getChannel();
            fromChannel.transferTo(0, fromChannel.size(), toChannel);
        } finally {
            try {
                if (fromChannel != null) {
                    fromChannel.close();
                }
            } finally {
                if (toChannel != null) {
                    toChannel.close();
                }
            }
        }
    }

}
