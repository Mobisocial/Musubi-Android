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

import mobisocial.crypto.IBHashedIdentity;

/**
 * Maintains a list of all identities known to this Musubi client.
 *
 */
public class MIdentity {
    public static final String TABLE = "identities";

    /**
     * Primary ID for an identity
     */
    public static final String COL_ID = "_id";

    /**
     * Type of the id's principal, eg email, facebook, phone_number.
     */
    public static final String COL_TYPE = "id_type";

    /**
     * The stable portion of this id, eg you@yourhost.com
     */
    public static final String COL_PRINCIPAL = "principal";

    /**
     * Sha-256 hash of the principal
     */
    public static final String COL_PRINCIPAL_HASH = "principal_hash";
    
    /**
     * The 8 least-significant bytes of the principal hash
     */
    public static final String COL_PRINCIPAL_SHORT_HASH = "principal_short_hash";

    /**
     * The friendly name for this identity from the address book
     */
    public static final String COL_NAME = "name";

    /**
     * A small image representing this identity from the address book
     */
    public static final String COL_THUMBNAIL = "thumbnail";

    /**
     * The friendly name for this identity as set/propagated through the musubi app
     */
    public static final String COL_MUSUBI_NAME = "musubi_name";

    /**
     * A small image representing this identity as set/propagated through the musubi app
     */
    public static final String COL_MUSUBI_THUMBNAIL = "musubi_thumbnail";
    
    /**
     * Integer indicating whether the local user has the private key for
     * this identity.
     */
    public static final String COL_OWNED = "owned";

    /**
     * Integer indicating whether this identity is known to be claimed
     * by someone in the Musubi network.
     * 
     * "Owned" identities are claimed by the local user.
     */
    public static final String COL_CLAIMED = "claimed";

    /**
     * Integer indicating whether this identity has been blacklisted.
     */
    public static final String COL_BLOCKED = "blocked";

    /**
     * Integer referencing an entry in the {@link Contact} database.
     */
    public static final String COL_CONTACT_ID = "contact_id";

    /**
     * A reference to an Android contact data entry
     * {@see http://developer.android.com/resources/articles/contacts.html
     * {@see http://developer.android.com/reference/android/provider/ContactsContract.Data.html}
     */
    public static final String COL_ANDROID_DATA_ID = "android_data_id";

    /**
     * The version (formatted as a timestamp) of the most received profile
     * received from this identity. May be null.
     */
    public static final String COL_RECEIVED_PROFILE_VERSION = "received_profile_version";

    /**
     * The version (formatted as a timestamp) of the most recent profile sent by the
     * account that knows this identity. May be null.
     */
    public static final String COL_SENT_PROFILE_VERSION = "sent_profile_version";

    /**
     * the next sequence number to use when sending to this identity.
     */
    public static final String COL_NEXT_SEQUENCE_NUMBER = "next_seq";
    
    /**
     *  the timestamp in second this identity is inserted into the
     *  database. automatically done by the identity manager
     */
    public static final String COL_CREATED_AT = "created_at";
    
    /**
     *  the timestamp in second this recorded is updated in 
     *  database. automatically done by the identity manager 
     *  
     */
    public static final String COL_UPDATED_AT = "updated_at";
    
    /**
     * Integer indicated whether or not you have sent an email to this 
     * unclaimed identity yet
     */
    public static final String COL_HAS_SENT_EMAIL = "has_sent_email";

    /**
     * Integer indicated whether or not a person is whitelisted
     */
    public static final String COL_WHITELISTED = "whitelisted";

    public long id_;
    public IBHashedIdentity.Authority type_;
    public String principal_;
    public byte[] principalHash_;
    public Long principalShortHash_;
    public boolean owned_;
    public boolean claimed_;
    public boolean blocked_;
	public long receivedProfileVersion_;
	public long sentProfileVersion_;
    public Long contactId_;
    public Long androidAggregatedContactId_;
    public long nextSequenceNumber_;
    public byte[] thumbnail_;
    public String name_;
    public byte[] musubiThumbnail_;
    public String musubiName_;
    public long createdAt_;
    public long updatedAt_;
    public boolean hasSentEmail_;
    public boolean whitelisted_;
}
