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

public class MContactDataVersion {
    public static final String TABLE = "contact_data_version";

    /**
     * The id of an android contact raw data item that has been
     * used to fill in the Musubi address book, since this
     * is the primary key for the local contacts, we just direct map;
     */
    public static final String COL_RAW_DATA_ID = "raw_data_id";

    /**
     * The version when this item was synced
     */
    public static final String COL_VERSION = "synced_version";

    public long rawDataId_;
    public long syncedVersion_;
}
