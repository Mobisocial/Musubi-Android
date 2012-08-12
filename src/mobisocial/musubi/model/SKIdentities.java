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

public class SKIdentities {
    public static final String TABLE = "sk_identities";

    public static final String COL_ID = "identity_id";
    public static final String COL_NAME = MIdentity.COL_NAME;
    public static final String COL_THUMBNAIL = MIdentity.COL_THUMBNAIL;
    public static final String COL_ID_HASH = MIdentity.COL_PRINCIPAL_HASH;
    public static final String COL_ID_SHORT_HASH = MIdentity.COL_PRINCIPAL_SHORT_HASH;
    public static final String COL_OWNED = MIdentity.COL_OWNED;
    public static final String COL_CLAIMED = MIdentity.COL_CLAIMED;
    public static final String COL_BLOCKED = MIdentity.COL_BLOCKED;
    public static final String COL_WHITELISTED = MIdentity.COL_WHITELISTED;
    
    /**
     * The columns of SocialKit's objects view. App_id is the string given in the
     * apps table for the given app.id_.
     */
    public static final ViewColumn[] VIEW_COLUMNS = new ViewColumn[] {
        new ViewColumn(COL_ID, MIdentity.TABLE, MIdentity.COL_ID),
        new ViewColumn(COL_NAME, MIdentity.TABLE),
        new ViewColumn(COL_THUMBNAIL, MIdentity.TABLE),
        new ViewColumn(COL_ID_HASH, MIdentity.TABLE),
        new ViewColumn(COL_ID_SHORT_HASH, MIdentity.TABLE),
        new ViewColumn(COL_OWNED, MIdentity.TABLE),
        new ViewColumn(COL_CLAIMED, MIdentity.TABLE),
        new ViewColumn(COL_BLOCKED, MIdentity.TABLE),
        new ViewColumn(COL_WHITELISTED, MIdentity.TABLE),
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
