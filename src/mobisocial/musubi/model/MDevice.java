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

public class MDevice {
    public static final String TABLE = "device_identities";

    /**
     * Primary ID for an devices
     */
    public static final String COL_ID = "_id";
    
    /**
     * Integer referencing the identity that owns the device
     */
    public static final String COL_IDENTITY_ID = "identity_id";

    /**
     * The 8-byte identity of the device the owner has arbitrarily picked
     */
    public static final String COL_DEVICE_NAME = "device_id";

    /**
     * The next sequence number for the outbound communication channel with
     * this device.
     */
    public static final String COL_MAX_SEQUENCE_NUMBER = "max_seq_number";
    
    public long id_;
    public long identityId_;
    public long deviceName_;
    public long maxSequenceNumber_;
    

}
