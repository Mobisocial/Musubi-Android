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

package mobisocial.musubi.model;

/**
 * Channel secrets limit the use of public key crypto operations to allow for more sophisticated schemes (IBE)
 * without impacting performance dramatically.  The overall stragegy is to lazily cache a unidirectional
 * communication secret key.  This allows for one client to speak to another while reusing a shared secret
 * that is implicit to the user.
 */
public class MOutgoingSecret {
	public static final String TABLE = "outgoing_secrets";
	public static final String COL_ID = "_id";

	/**
	 * This references the particular ID used to communicate with the person to deal with the
	 * case where I send some messages from tpurtell@stanford.edu and some from tpurtell@cs.stanford.edu
	 * 
	 * My device name is the same across all identities.
	 */
	public static final String COL_MY_IDENTITY_ID = "my_identity_id";

	/**
	 * This references the user with whom I am communicating.  Every identity has its own
	 * separate secret.  
	 */
	public static final String COL_OTHER_IDENTITY_ID = "other_identity_id";

	/**
	 * This is the period that the signature was computed for.  Instead of linking to the signature
	 * secrets table, we put the time here so this record is more self-describing.
	 */
	public static final String COL_OUTGOING_SIGNATURE_WHEN = "outgoing_signature_when";

	/**
	 * This identifies a particular encryption secret I used to communicate with a friend.  When the
	 * the time period expires (or I notice revocations) I may delete old cached secret keys.
	 */
	public static final String COL_OUTGOING_ENCRYPTION_WHEN = "outgoing_encryption_when";

	/**
	 * This is the binary blob of the encrypted key that I will embed in messages.
	 */
	public static final String COL_OUTGOING_ENCRYPTED_KEY = "outgoing_encrypted_key";

	/**
	 * This is the binary blob of the signature I will embed in messages, the signature covers both
	 * the encrypted key and the device name.
	 */
	public static final String COL_OUTGOING_SIGNATURE = "outgoing_signature";

	/**
	 * A few fields like the sequence number, device id, and message hash, and message will be 
	 * encrypted using this secret.  These fields are duplicated per recipient, so they need to be
	 * relatively small.  We are probably looking at ~100 bytes/recipient
	 */
	public static final String COL_OUTGOING_KEY = "outgoing_key";

	public long id_;
	public long myIdentityId_;
	public long otherIdentityId_;
	public long signatureWhen_;
	public long encryptionWhen_;
	public byte[] encryptedKey_;
	public byte[] signature_;
	public byte[] key_;
}
