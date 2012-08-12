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

/**
 * A {@link PlatformReference} parsed from a byte array.
 */
public class ParsedPlatformReference extends PlatformReference {
	private int platformIdentifier;
	private int platformVersion;
	private int deviceModality;
	private byte[] appReference;
	
	public ParsedPlatformReference(int platformIdentifier,
			int platformVersion,
			int deviceModality,
			byte[] appReference) {
		
		this.platformIdentifier = platformIdentifier;
		this.platformVersion = platformVersion;
		this.deviceModality = deviceModality;
		this.appReference = appReference;
	}
	@Override
	public int getPlatformIdentifier() {
		return platformIdentifier;
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
