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

import java.util.LinkedList;

import org.mobisocial.appmanifest.platforms.PlatformReference;

public class Builder {
		ApplicationManifest mApplicationManifest;
		
		public Builder() {
			mApplicationManifest = new ApplicationManifest();
			mApplicationManifest.mPlatformReferences = new LinkedList<PlatformReference>();
		}
		
		public ApplicationManifest create() {
			return mApplicationManifest;
		}
		
		public Builder setName(String name) {
			mApplicationManifest.mName = name;
			return this;
		}
		
		public Builder addPlatformReference(PlatformReference reference) {
			mApplicationManifest.mPlatformReferences.add(reference);
			return this;
		}
	}