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

public class MAppTrust {
    public static final long PERM_NONE = 0;
    //
    public static final long PERM_READ = 1;
    public static final long PERM_DELETE = 2;
    //
    public static final long PERM_ALL =  3;
	
    public static final String TABLE = "app_trusts";
	
    public static final String COL_ID = "_id";
    /**
     * Domain being granted access to the feed
     */
    public static final String COL_DOMAIN_ID = "domain_id";
    /**
     * feed access is granted to
     */
    public static final String COL_FEED_ID = "feed_id";
    /**
     * permissions bitmask
     */
    public static final String COL_PERMISSIONS = "permissions";

    public long id_;
    public byte[] publicKey_;
    public long domainId_;
    public long feedId_;
    public long permissions_;
}
