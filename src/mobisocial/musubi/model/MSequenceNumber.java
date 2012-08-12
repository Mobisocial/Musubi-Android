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

public class MSequenceNumber {
    public static final String TABLE = "sequence_numbers";
    /**
     * Primary ID for an encoded message
     */
    public static final String COL_ID = "_id";
    
    /**
     * The id of the encoded in encoded_messages
     */
    public static final String COL_ENCODED_ID = "encoded_id";

    /**
     * The identity id of the sender
     */
    public static final String COL_RECIPIENT = "recipient_id";

    /**
     * The sequence number this message was encoded as to the specific recipient
     */
    public static final String COL_SEQUENCE_NUMBER = "seq_number";

    public long id_;
    public long encodedId_;
    public long recipientId_;
    public long sequenceNumber_;
}
