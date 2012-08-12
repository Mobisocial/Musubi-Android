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

public class MEncryptionUserKey {
	public static final String TABLE = "encryption_secrets";
	public static final String COL_ID = "_id";

	/**
	 * This field links an encrpytion secret to a certain identity that is owned by
	 * the user of Musubi.  The identity fields are defined in the same global namespace
	 * with friends.  The identity row should have the property that COL_OWNED = 1.
	 */
	public static final String COL_IDENTITY_ID = "identity_id";

	/**
	 * This column contains the specific time frame that the decryption secret is valid for.
	 * There will be one entry for each time period a friend uses to send a message to the user.
	 * If no revocations are involved, then this will update one the order of weeks/months.  There
	 * will be multiple records because messages can come in that are encrypted for an old secret
	 * during a short window when the key changes.
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
