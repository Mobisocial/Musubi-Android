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

package mobisocial.metrics;


public class MusubiMetrics {
    public static final String WIZARD_ACCOMPLISH_TASK = "WIZARD_TASK";
    public static final String VISITED_NEARBY = "VISITED_NEARBY";

    public static final String EULA_ACCEPTED = "EULA_ACCEPTED";
    public static final String EULA_DECLINED = "EULA_DECLINED";
    public static final String EULA_EMAIL_REQUESTED = "EULA_EMAIL_REQUESTED";

    /**
     * Created a feed from the FeedListFragment
     */
    public static final String FEED_CREATED_EXPANDING = "FEED_CREATED_EXPANDING";
   
    /**
     * Created a feed from the Conversations list
     */
    public static final String FEED_CREATED_FROM_PROFILE = "FEED_CREATED_FROM_PROFILE";

    /**
     * Created a feed from a 3rd party app
     */
    public static final String FEED_CREATED_APP = "FEED_CREATED_APP";

    public static final String ADDED_PERSON_TO_FEED = "ADDED_PERSON_TO_FEED";

    public static final String ACCOUNT_CONNECTED = "ACOCUNT_CONNECTED";

    public static final String PROFILE_PICTURE_UPDATED = "PROFILE_PICTURE_UPDATED";
    public static final String PROFILE_NAME_UPDATED = "PROFILE_NAME_UPDATED";
    public static final String CLICKED_TO_INVITE = "CLICKED_TO_INVITE";

    public static final String CLICKED_QR_SCAN = "CLICKED_QR_SCAN";
    public static final String CLICKED_QR_INVITE = "CLICKED_QR_INVITE";
    public static final String CLICKED_SEND_INVITE = "CLICKED_SEND_INVITE";
    public static final String CLICKED_ADD_CONTACT = "CLICKED_ADD_CONTACT";
    public static final String FREE_UP_SPACE = "FREE_UP_SPACE";
}
