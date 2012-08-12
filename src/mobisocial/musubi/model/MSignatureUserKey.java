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

public class MSignatureUserKey {
	public static final String TABLE = "signature_secrets";
	public static final String COL_ID = "_id";

	/**
	 * This field links a signing secret to a certain identity that is owned by
	 * the user of Musubi.  The identity fields are defined in the same global namespace
	 * with friends.  The identity row should have the property that COL_OWNED = 1.
	 */
	public static final String COL_IDENTITY_ID = "identity_id";

	/**
	 * This column contains the specific time frame that the signing secret is valid for.
	 * There will be one entry for each time a user authenticates to claim a new signing key.
	 * The newest should always be used.  The authentication to the message queues may use a
	 * signing secret that is never stored in this table for challenge response auth.  Adding
	 * an entry in this table when a standard time period hasn't expires should be accompanied
	 * with a broadcast message that triggers all friends to use the newer non-standard time
	 * for encryption ("distributed" key revocation).
	 */
	public static final String COL_WHEN = "key_time";

	/**
	 * This is the raw secret data to be fed into the identity based encryption library as 
	 * the user's private key.
	 */
	public static final String COL_USER_KEY = "user_key";
	
	public long id_;
	public long identityId_;
	public long when_;
	public byte[] userKey_;
}
