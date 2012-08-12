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

import mobisocial.musubi.model.helpers.FeedManager;
import mobisocial.musubi.util.Util;

/**
 * @see FeedManager
 */
public class MFeed {
	public static final String TABLE = "feeds";
	public static final String COL_ID = "_id";

	// Leave room for a few future well-known feeds with positive ids.
	// ^^^ THANK YOU ^^^
	public static final int NONIDENTITY_SPECIFIC_WHITELIST_ID = 9;
	public static final int GLOBAL_BROADCAST_FEED_ID = 10;
	public static final int WIZ_FEED_ID = 11;

	/**
	 * The type of a feed dictates certain properties of the feed:
	 * <ul>
	 *   <li>Auto membership: are newly discovered identities added to the feed?
	 *   <li>Greylist policy: Are messages from greylisted identities shown? discarded? queued?
	 * </ul>
	 */
	public static final String COL_TYPE = "type";

	/**
	 * This is the "secret" key for the feed.  It is transmitted inside the private payload
	 * of the Musubi message because knowing this capability is sufficient to add other
	 * members to the group (and at least whitelist them in the scope of the feed).  This
	 * blob is well known for certain feeds, e.g. the friend direct message feed.  In non-special
	 * cases, it is a random array of bytes.
	 * 
	 * There are some special capabilities, we have to standardize on them.  Maybe the first byte
	 * can dispatch between them...  other alternative is to make this a string to match the old
	 * semi user readable feed name.
	 */
	public static final String COL_CAPABILITY = "capability";

	/**
	 * An efficient lookup field for selecting capabilities.
	 */
	public static final String COL_SHORT_CAPABILITY = "short_capability";

	/**
	 * The view of the most recent message for each feed is a home screen primitive.  This
	 * column caches the specific object to render to accelerate the perfomance of the
	 * home screen.  If we switch to a model where several recent message may be displayed,
	 * this will have to be broken out into a different table.
	 */
	public static final String COL_LATEST_RENDERABLE_OBJ_ID = "latest_renderable_obj_id";

	/**
	 * This is the time stamp associated with the aforementioned latest renderable object.  For
	 * app feeds (subfeed? like primitive), the renderable obj id may no change even though this
	 * timestamp might change to represent activity within the application. 
	 */
	public static final String COL_LATEST_RENDERABLE_OBJ_TIME = "latest_renderable_obj_time";

	/**
	 * The number of renderable messages received since the user last viewed the feed.
	 */
	public static final String COL_NUM_UNREAD = "num_unread";
	
	/**
	 * The local, user-set name of the feed.
	 */
	public static final String COL_NAME = "name";

    /**
     * Only display feeds that have been accepted.
     */
    public static final String COL_ACCEPTED = "accepted";

    public static final String COL_THUMBNAIL = "thumbnail";

	public enum FeedType { // order matters 
	    UNKNOWN, FIXED, EXPANDING, ASYMMETRIC, ONE_TIME_USE
	}
	public static final byte[] ALL_CONTACTS_CAPABILITY = Util.sha256("broadcast".getBytes());
	public static final String LOCAL_WHITELIST_FEED_NAME = MMyAccount.LOCAL_WHITELIST_ACCOUNT;
	public static final String PROVISONAL_WHITELIST_FEED_NAME = MMyAccount.PROVISIONAL_WHITELIST_ACCOUNT;
    
    public long id_;
	public FeedType type_;
	public byte[] capability_;
	public Long shortCapability_;
	public Long latestRenderableObjId_;
	public Long latestRenderableObjTime_;
	public long numUnread_;
	public String name_;
	public boolean accepted_;
	public byte[] thumbnail_;
}
