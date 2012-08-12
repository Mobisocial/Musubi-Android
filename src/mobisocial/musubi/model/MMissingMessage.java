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

public class MMissingMessage {
    public static final String TABLE = "missing_messages";

    /**
     * Primary ID for an missing message
     */
    public static final String COL_ID = "_id";
    
    /**
     * Integer referencing the stream of messages from a device
     */
    public static final String COL_DEVICE_ID = "device_id";

    /**
     * A sequence number that was not received from this device
     */
    public static final String COL_SEQUENCE_NUMBER = "seq_num";
    
    public long id_;
    public long deviceId_;
    public long sequenceNumber_;
}
