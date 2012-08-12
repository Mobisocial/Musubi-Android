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

public class ShortNameGenerator {

	public static void main(String[] args) {
		ByteBuffer buffer = ByteBuffer.allocate(4);
		buffer.put((byte)'M');
		buffer.put((byte)'U');
		buffer.put((byte)'S');
		buffer.put((byte)'U');
		
		buffer.position(0);
		System.out.println("0x" + Integer.toHexString(buffer.getInt()));
	}
}
