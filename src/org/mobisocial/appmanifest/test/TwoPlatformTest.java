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

package org.mobisocial.appmanifest.test;

import java.util.Arrays;

import org.mobisocial.appmanifest.ApplicationManifest;
import org.mobisocial.appmanifest.Builder;
import org.mobisocial.appmanifest.platforms.AndroidPlatformReference;
import org.mobisocial.appmanifest.platforms.PlatformReference;
import org.mobisocial.appmanifest.platforms.WebPlatformReference;


public class TwoPlatformTest {

	public static void main(String... args) {
		PlatformReference androidReference = new AndroidPlatformReference(10, "org.openjunction.wescribble", "junction://sb.openjunction.org/myt3sts3s5i0n");
		PlatformReference webReference = new WebPlatformReference("http://openjunction.org/demo/whiteboard?jx=junction://sb.openjunction.org/myt3sts3s5i0n");
		ApplicationManifest manifest =
			new Builder()
				.setName("weScribble")
				.addPlatformReference(androidReference)
				.addPlatformReference(webReference)
				.create();
		byte[] bytes = manifest.toByteArray();
		ApplicationManifest parsedManifest = new ApplicationManifest(bytes);
		
		System.out.println("Created application manifest of size " + bytes.length + " bytes.");
		
		if (!parsedManifest.getName().equals(manifest.getName())) {
			System.err.println("Mismatching names");
		}
		
		int platformCount = manifest.getPlatformReferences().size();
		if (platformCount != parsedManifest.getPlatformReferences().size()) {
			System.err.println("Mismatch platform reference length.");
			return;
		}
		
		System.out.println("Comparing " + platformCount + " references.");
		for (int i = 0; i < platformCount; i++) {
			PlatformReference original = manifest.getPlatformReferences().get(i);
			PlatformReference parsed = manifest.getPlatformReferences().get(i);
			
			if (original.getPlatformIdentifier() != parsed.getPlatformIdentifier()) {
				System.err.println("Mismatched platform identifier");
			}
			
			if (original.getPlatformVersion() != parsed.getPlatformVersion()) {
				System.err.println("Mismatched platform version");
			}
			
			if (original.getDeviceModality() != parsed.getDeviceModality()) {
				System.err.println("Mismatched device modality");
			}
			
			if (!Arrays.equals(original.getAppReference(), parsed.getAppReference())) {
				System.err.println("Mismatched platform arguments");
			}
			
			String arg = new String(parsed.getAppReference());
			System.out.println("Result: " + arg);
		}
		
		
	}
}
