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

import mobisocial.musubi.model.helpers.PendingIdentityManager;

/**
 * Identities that need to go through two-step identity verification
 * @see PendingIdentityManager
 */
public class MPendingIdentity {
    public static final String TABLE = "pending_accounts";
    /**
     * Primary identifier
     */
    public static final String COL_ID = "_id";
    
    /**
     * Linked identity
     */
    public static final String COL_IDENTITY_ID = "identity_id";
    
    /**
     * Requested timestamp for identity
     */
    public static final String COL_TIMESTAMP = "timestamp";
    
    /**
     * Key used to decrypt IBE secrets when they are received
     */
    public static final String COL_KEY = "key";
    
    /**
     * Random identifier that the server can echo (prevent bad insertions)
     */
    public static final String COL_REQUEST_ID = "request_id";
    
    /**
     * Whether the first stage (notification) is complete
     */
    public static final String COL_NOTIFIED = "notified";
    
    public long id_;
    public Long identityId_;
    public Long timestamp_;
    public String key_;
    public Integer requestId_;
    public boolean notified_;
}
