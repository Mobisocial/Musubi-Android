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

public class DbRelation {
    public static final String TABLE = "relations";
    public static final String _ID = "_id";
    public static final String OBJECT_ID_A = "object_id_A";
    public static final String OBJECT_ID_B = "object_id_B";
    public static final String RELATION_TYPE = "relation";

    /**
     * A is the parent of B
     */
    public static final String RELATION_PARENT = "parent";

    /**
     * B updates and replaces A.
     */
    public static final String RELATION_UPDATE = "update";

    /**
     * B is an edit of the data in A.
     */
    public static final String RELATION_EDIT = "edit";
}
