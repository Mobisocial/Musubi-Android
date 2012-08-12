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

package mobisocial.musubi.feed.iface;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MObject;
import mobisocial.socialkit.Obj;
import mobisocial.socialkit.SignedObj;
import mobisocial.socialkit.musubi.DbObj;
import android.content.Context;

/**
 * Base class for object handlers.
 */
public abstract class DbEntryHandler {
    public static String TAG = "CustomObjHelper";
    public abstract String getType();

	public boolean doNotification(Context context, DbObj obj) {
	    return true;
	}

	public boolean isRenderable(SignedObj obj) {
	    if (this instanceof FeedRenderer) {
	        return true;
	    }
	    return obj.getJson() != null && obj.getJson().has(Obj.FIELD_HTML);
	}

	/**
	 * Process the given object. Return true to keep the object in the database,
	 * false if it can be discarded.
	 * @param feed 
	 */
	public boolean processObject(Context context, MFeed feed, MIdentity sender, MObject object) {
	    return true;
	}
}