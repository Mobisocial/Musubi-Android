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

public class MFeedMember {
	public static final String TABLE = "feed_members";
	public static final String COL_ID = "_id";

	/**
	 * The feed owning this membership.
	 */
	public static final String COL_FEED_ID = "feed_id";

	/**
	 * The identity belonging to the given feed.
	 */
	public static final String COL_IDENTITY_ID = "identity_id";

	public long id_;
	public long feed_;
	public long identity_;
}
