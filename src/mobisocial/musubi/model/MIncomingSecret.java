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
public class MIncomingSecret {
	public static final String TABLE = "channel_secrets";
	public static final String COL_ID = "_id";

	/**
	 * This references the particular ID used to communicate with the person to deal with the
	 * case where I receive some messages to tpurtell@stanford.edu and some to tpurtell@cs.stanford.edu
	 */
	public static final String COL_MY_IDENTITY_ID = "my_identity_id";

	/**
	 * This references the user with whom I am communicating.  Every identity has its own
	 * separate secret.  
	 */
	public static final String COL_OTHER_IDENTITY_ID = "other_identity_id";

	/**
	 * This field stores the specific time that a cached channel encryption secret was signed
	 */
	public static final String COL_INCOMING_SIGNATURE_WHEN = "incoming_signature_when";

	/**
	 * This field stores the specific time that is used to encrypt the message to me.  If I haven't
	 * fetched my secret from the server for this time, then I will have to.  It is different than
	 * the signature time period, because the standard expiration times are a function of identity.
	 */
	public static final String COL_INCOMING_ENCRYPTION_WHEN = "incoming_encryption_when";

	/**
	 * The binary blob of data that contains the encrypted key which has been
	 * decoded into this row.
	 */
	public static final String COL_INCOMING_ENCRYPTED_KEY = "incoming_encrypted_key";

	/**
	 * The link to the Device object that specifies the device name which is also included in the
	 * signature.
	 */
	public static final String COL_INCOMING_DEVICE_ID = "incoming_device_id";

	/**
	 * The binary blob of data that contains the signature of the encrypted key and this device name
	 */
	public static final String COL_INCOMING_SIGNATURE = "incoming_signature";

	/**
	 * The actual conversation key to use to decrypt message keys and private metadata from
	 * incoming messages if the signed encrypted key field matches the incoming message headers.
	 */
	public static final String COL_INCOMING_KEY = "incoming_key";
	
	public long id_;
	public long myIdentityId_;
	public long otherIdentityId_;
	public long signatureWhen_;
	public long encryptionWhen_;
	public byte[] encryptedKey_;
	public long deviceId_;
	public byte[] signature_;
	public byte[] key_;

}
