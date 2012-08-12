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

public class MFact {
    public static final String TABLE = "facts";

    public static final String COL_ID = "_id";

    /**
     * The application defining this fact.
     */
    public static final String COL_APP_ID = "app_id";

    /**
     * The id from the fact_types table for this fact.
     */
    public static final String COL_FACT_TYPE_ID = "fact_type_id";

    /**
     * An un-indexed value for this fact.
     */
    public static final String COL_V = "V";

    /**
     * Indexed, type-free signifiers for this fact.
     */
    public static final String COL_A = "A";
    public static final String COL_B = "B";
    public static final String COL_C = "C";
    public static final String COL_D = "D";

    public long id_;
    public long appId_;
    public long fact_type_id;
    public Object A_;
    public Object B_;
    public Object C_;
    public Object D_;
    public Object V_;
}
