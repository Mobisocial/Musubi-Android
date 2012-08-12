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
 * stores the information required to keep track of the
 * continuous sync with the local address book
 */
public class MSyncState {
    public static final String TABLE = "sync_state";

    public static final String COL_ID = "_id";

    public static final String COL_MAX_CONTACT = "max_contact_id_seen";

    public static final String COL_MAX_DATA = "max_data_id_seen";
    
    public static final String COL_LAST_FACEBOOK_UPDATE_TIME = "last_facebook_update_time";

    //TODO: other options used for the contact sync adapter?

    public long id_;
    public long accountName_;
    public long accountType_;
}
