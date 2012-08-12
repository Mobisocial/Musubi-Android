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

import mobisocial.socialkit.musubi.DbObj;

/**
 * <p>DO NOT USE AS A REPRESENTATION OF A MUSUBI OBJ.
 * <ul>
 * <li>Obj is an interface for basic Musubi content.
 * <li>MemObj is a concrete implementation stored in memory.
 * <li>SignedObj represents an obj that has been signed for sending by some user.
 * <li>DbObj represents an obj that has been sent or received and is held
 * in Musubi's database.
 * </ul></p>
 * 
 * <p>Note that this class used as both a representation of Objs, and a set of
 * utility methods and constants. Only the use as an Obj is deprecated,
 * the rest will be moved to a new class.</p>
 */
public class MObject {
    public static final String TABLE = DbObj.TABLE;

    public static final String COL_ID = DbObj.COL_ID;

    /* link to the Feed table that specifies where this obj goes */
    public static final String COL_FEED_ID = DbObj.COL_FEED_ID;

    /* sender */
    public static final String COL_IDENTITY_ID = DbObj.COL_IDENTITY_ID;
    /* sender device */
    public static final String COL_DEVICE_ID = DbObj.COL_DEVICE_ID;

    public static final String COL_PARENT_ID = DbObj.COL_PARENT_ID;

    public static final String COL_APP_ID = DbObj.COL_APP_ID;

    public static final String COL_TIMESTAMP = DbObj.COL_TIMESTAMP;

    public static final String COL_UNIVERSAL_HASH = DbObj.COL_UNIVERSAL_HASH;

    public static final String COL_SHORT_UNIVERSAL_HASH = DbObj.COL_SHORT_UNIVERSAL_HASH;

    public static final String COL_TYPE = DbObj.COL_TYPE;

    public static final String COL_JSON = DbObj.COL_JSON;

    public static final String COL_RAW = DbObj.COL_RAW;

    public static final String COL_INT_KEY = DbObj.COL_INT_KEY;

    public static final String COL_STRING_KEY = DbObj.COL_STRING_KEY;

    public static final String COL_LAST_MODIFIED_TIMESTAMP = DbObj.COL_LAST_MODIFIED_TIMESTAMP;

	public static final String COL_ENCODED_ID = DbObj.COL_ENCODED_ID;

	public static final String COL_DELETED = DbObj.COL_DELETED;

	public static final String COL_RENDERABLE = DbObj.COL_RENDERABLE;

	public static final String COL_PROCESSED = "processed";

	public long id_;
	public long feedId_;
    public long identityId_;
    public long deviceId_;
    public Long parentId_;
    public long appId_;
    public long timestamp_;
    public byte[] universalHash_;
    public Long shortUniversalHash_;
    public String type_;
	public String json_;
	public byte[] raw_;
    public Integer intKey_;
    public String stringKey_;
	public long lastModifiedTimestamp_;
	public Long encodedId_;
	public boolean deleted_;
	public boolean renderable_;
	public boolean processed_;
}