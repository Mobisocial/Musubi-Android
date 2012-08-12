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

package mobisocial.musubi.encoding;

import mobisocial.musubi.model.MIdentity;

public class OutgoingMessage {
	/* the reference to the identity that i send the message as */
	public MIdentity fromIdentity_;
	/* a list of all of the recipients, some of which I may or may not really know, it probably includes me */
	public MIdentity[] recipients_;
	/* the actual private message bytes that are decrypted */
	public byte[] data_;
	/* the hash of data_ */
	public byte[] hash_;
	/* a flag that control whether client should see the full recipient list */
	public boolean blind_;
	/* the id of the application namespace */
	public byte[] app_;
}
