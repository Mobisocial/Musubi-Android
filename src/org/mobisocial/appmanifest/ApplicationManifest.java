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

package org.mobisocial.appmanifest;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.mobisocial.appmanifest.platforms.ParsedPlatformReference;
import org.mobisocial.appmanifest.platforms.PlatformReference;

import android.util.Log;


public class ApplicationManifest {
	public static final int MIME_MAGIC_NUMBER = 0x41504d46; // 'APMF'
	public static final String MIME_TYPE = "application/vnd.mobisocial-appmanifest";
	public static final int PLATFORM_WEB_GET = 0x57454247; // 'WEBG'
	public static final int PLATFORM_WEB_INLINE = 0x57454249; // 'WEBI'
	public static final int PLATFORM_ANDROID_PACKAGE = 0x414e4450; // 'ANDP'
	//public static final int PLATFORM_ANDROID_INTENT = 0x414E4401; // {'A', 'N', 'D', 0x01}
	public static final int PLATFORM_SYMBIAN_PACKAGE = 0x53594D00; // {'S', 'Y', 'M', 0x00}
	public static final int PLATFORM_IOS_PACKAGE = 0x694F5300; // {'i', 'O', 'S', 0x00i
	public static final int PLATFORM_WINDOWS_PHONE_PACKAGE = 0x57503700; // {'W', 'P', '7', 0x00}
	public static final int PLATFORM_WEBOS_PACKAGE = 0x774f5300; // {'w', 'O', 'S', 0x00}
	public static final int PLATFORM_JAVA_JAR = 0x4a415600; // {'J', 'A', 'V', 0x00}
	
	public static int MODALITY_UNSPECIFIED = 0x00;
	public static int MODALITY_HEADLESS = 0x01;
	public static int MODALITY_PHONE = 0x02;
	public static int MODALITY_COMPUTER = 0x03;
	public static int MODALITY_TELEVISION = 0x04;
	public static int MODALITY_TABLET = 0x05;
	public static int MODALITY_PROJECTOR = 0x06;
	
	String mName;
	List<PlatformReference> mPlatformReferences;
	
	public String getName() {
		return mName;
	}
	
	public List<PlatformReference> getPlatformReferences() {
		return mPlatformReferences;
	}
	
	public byte[] toByteArray() {
		// TODO: allocate correct size
		ByteBuffer buffer = ByteBuffer.allocate(1024);
		buffer.putInt(MIME_MAGIC_NUMBER);
		buffer.putInt(mName.length());
		buffer.put(mName.getBytes());
		buffer.putInt(mPlatformReferences.size());
		for (PlatformReference platform : mPlatformReferences) {
			byte[] appReference = platform.getAppReference();
			buffer.putInt(platform.getPlatformIdentifier());
			buffer.putInt(platform.getPlatformVersion());
			buffer.putInt(platform.getDeviceModality());
			buffer.putInt(appReference.length);
			buffer.put(appReference);
		}
		int length = buffer.position();
		buffer.position(0);
		byte[] manifest = new byte[length];
		buffer.get(manifest, 0, length);
		return manifest;
	}
	
	public ApplicationManifest(byte[] source) {
		Log.d("junction", "Parsing app manifest size " + source.length);
		String b = "";
		for (int i=0;i<source.length;i++) {
			b += (char)source[i];
		}
		Log.d("junction", "its " + b);
		ByteBuffer buffer = ByteBuffer.wrap(source);
		int magicNumber = buffer.getInt();
		if (magicNumber != MIME_MAGIC_NUMBER) {
			throw new IllegalArgumentException("Magic number not found. (" + magicNumber + " vs " + MIME_MAGIC_NUMBER + ")");
		}
		int nameLength = buffer.getInt();
		byte[] nameBytes = new byte[nameLength];
		buffer.get(nameBytes);
		mName = new String(nameBytes);

		int platformCount = buffer.getInt();
		Log.d("NfcService", "I HAVE " + platformCount);
		mPlatformReferences = new ArrayList<PlatformReference>(platformCount);
		for (int i = 0; i < platformCount; i++) {
			int platformIdentifier = buffer.getInt();
			int platformVersion = buffer.getInt();
			int deviceModality = buffer.getInt();
			int appReferenceLength = buffer.getInt();
			byte[] appReference = new byte[appReferenceLength];
			buffer.get(appReference);

			PlatformReference platformReference = new ParsedPlatformReference(
					platformIdentifier, platformVersion, deviceModality, appReference);
			mPlatformReferences.add(platformReference);
		}
	}
	
	protected ApplicationManifest() {}
}
