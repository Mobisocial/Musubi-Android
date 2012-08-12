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

package mobisocial.musubi.protocol;

public class Recipient {
	/* the serialized hashed identity, including the type, hashed principal, and time period */
	public byte[] i;
	/* the IBE encrypted key block*/
	public byte[] k;
	/* the IBE signature block, signature for the key block||device, the identity is in the sender block of the message*/
	public byte[] s;
	/* the encrypted block of secrets for the message for this person */
	public byte[] d;
}
