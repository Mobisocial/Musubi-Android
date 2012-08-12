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

import mobisocial.musubi.model.MDevice;
import mobisocial.musubi.model.MIdentity;

public class IncomingMessage {
	/* the identity used by me to decode or encode messages */
	public MIdentity[] personas_;
	/* the reference to the identity that sent the message, could be me */
	public MIdentity fromIdentity_;
	/* the device that sent the message */
	public MDevice fromDevice_;
	/* a list of all of the recipients, some of which I may or may not really know */
	public MIdentity[] recipients_;
	/* the hash of the data which was validated */
	public byte[] hash_;
	/* the actual private message bytes that are decrypted */
	public byte[] data_;
	/* the sequence number of the message from this device */
	public long sequenceNumber_;
	/* whether or not this was state update, e.g. blind cc */
	public boolean blind_;
	/* application namespace */
	public byte[] app_;
}
