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

public class MAppDomainExtension {
    public static final String TABLE = "extended_domains";
	
    public static final String COL_ID = "_id";
    /**
     * the app domain that is being extended
     */
    public static final String COL_DOMAIN_ID = "domain_id";
    /**
     * the app that is trusted with data in this domain
     */
    public static final String COL_EXTENDED_TRUST_TO_DOMAIN_ID = "extended_to";

    public long id_;
    public long domainId_;
    public long trustedDomainId_; //there are n of these
	
}
