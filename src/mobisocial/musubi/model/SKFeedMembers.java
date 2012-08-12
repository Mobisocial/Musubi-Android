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

import mobisocial.musubi.model.helpers.ViewColumn;


public class SKFeedMembers {
    public static final String TABLE = "sk_feed_members";

    public static final String COL_IDENTITY_ID = MFeedMember.COL_IDENTITY_ID;
    public static final String COL_IDENTITY_NAME = SKIdentities.COL_NAME;
    public static final String COL_IDENTITY_THUMBNAIL = SKIdentities.COL_THUMBNAIL;
    public static final String COL_IDENTITY_HASH = SKIdentities.COL_ID_HASH;
    public static final String COL_IDENTITY_SHORT_HASH = SKIdentities.COL_ID_SHORT_HASH;
    public static final String COL_IDENTITY_OWNED = SKIdentities.COL_OWNED;
    public static final String COL_IDENTITY_CLAIMED = SKIdentities.COL_CLAIMED;
    public static final String COL_IDENTITY_BLOCKED = SKIdentities.COL_BLOCKED;
    public static final String COL_IDENTITY_WHITELISTED = SKIdentities.COL_WHITELISTED;

    public static final String COL_FEED_ID = MFeedMember.COL_FEED_ID;

    public static ViewColumn[] VIEW_COLUMNS = new ViewColumn[] {
        new ViewColumn(COL_IDENTITY_ID, MFeedMember.TABLE, MFeedMember.COL_IDENTITY_ID),
        new ViewColumn(COL_IDENTITY_NAME, SKIdentities.TABLE, SKIdentities.COL_NAME),
        new ViewColumn(COL_IDENTITY_THUMBNAIL, SKIdentities.TABLE, SKIdentities.COL_THUMBNAIL),
        new ViewColumn(COL_IDENTITY_HASH, SKIdentities.TABLE, SKIdentities.COL_ID_HASH),
        new ViewColumn(COL_IDENTITY_SHORT_HASH, SKIdentities.TABLE, SKIdentities.COL_ID_SHORT_HASH),
        new ViewColumn(COL_IDENTITY_OWNED, SKIdentities.TABLE, SKIdentities.COL_OWNED),
        new ViewColumn(COL_IDENTITY_CLAIMED, SKIdentities.TABLE, SKIdentities.COL_CLAIMED),
        new ViewColumn(COL_IDENTITY_BLOCKED, SKIdentities.TABLE, SKIdentities.COL_BLOCKED),
        new ViewColumn(COL_IDENTITY_WHITELISTED, SKIdentities.TABLE, SKIdentities.COL_WHITELISTED),
        new ViewColumn(COL_FEED_ID, MFeedMember.TABLE, MFeedMember.COL_FEED_ID),
    };

    public static String[] getViewColumns() {
        String[] cols = new String[VIEW_COLUMNS.length];
        int i = 0;
        for (ViewColumn v : VIEW_COLUMNS) {
            cols[i] = v.getViewColumn();
        }
        return cols;
    }
}