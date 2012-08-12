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

package org.mobisocial.appmanifest.platforms;

import org.mobisocial.appmanifest.ApplicationManifest;

public class AndroidPlatformReference extends PlatformReference {
	private int platformVersion;
	private int deviceModality = ApplicationManifest.MODALITY_UNSPECIFIED;
	private byte[] appReference;
	
	public AndroidPlatformReference(int version, String pkg, String argument) {
		platformVersion = version;
		appReference = (pkg + ":" + argument).getBytes();
	}

	@Override
	public int getPlatformIdentifier() {
		return ApplicationManifest.PLATFORM_ANDROID_PACKAGE;
	}

	@Override
	public int getPlatformVersion() {
		return platformVersion;
	}

	@Override
	public int getDeviceModality() {
		return deviceModality;
	}

	@Override
	public byte[] getAppReference() {
		return appReference;
	}
}