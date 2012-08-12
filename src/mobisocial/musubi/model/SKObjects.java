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
import mobisocial.socialkit.musubi.DbObj;

public class SKObjects {
    public static final String TABLE = "sk_objects";

    /**
     * The columns of SocialKit's objects view. App_id is the string given in the
     * apps table for the given app.id_.
     */
    public static final ViewColumn[] VIEW_COLUMNS = new ViewColumn[] {
        new ViewColumn(DbObj.COL_ID, DbObj.TABLE),
        new ViewColumn(DbObj.COL_TYPE, DbObj.TABLE),
        new ViewColumn(DbObj.COL_FEED_ID, DbObj.TABLE),
        new ViewColumn(DbObj.COL_IDENTITY_ID, DbObj.TABLE),
        new ViewColumn(DbObj.COL_PARENT_ID, DbObj.TABLE),
        new ViewColumn(DbObj.COL_JSON, DbObj.TABLE),
        new ViewColumn(DbObj.COL_TIMESTAMP, DbObj.TABLE),
        new ViewColumn(MApp.COL_APP_ID, MApp.TABLE),
        new ViewColumn(DbObj.COL_UNIVERSAL_HASH, DbObj.TABLE),
        new ViewColumn(DbObj.COL_SHORT_UNIVERSAL_HASH, DbObj.TABLE),
        new ViewColumn(DbObj.COL_RAW, DbObj.TABLE),
        new ViewColumn(DbObj.COL_INT_KEY, DbObj.TABLE),
        new ViewColumn(DbObj.COL_STRING_KEY, DbObj.TABLE),
        new ViewColumn(DbObj.COL_LAST_MODIFIED_TIMESTAMP, DbObj.TABLE),
        new ViewColumn(DbObj.COL_RENDERABLE, DbObj.TABLE),
    };
}
