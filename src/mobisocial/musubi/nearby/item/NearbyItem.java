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

package mobisocial.musubi.nearby.item;

import mobisocial.musubi.ui.NearbyActivity;
import android.graphics.Bitmap;
import android.net.Uri;

public abstract class NearbyItem {
    public static enum Type { PERSON, FEED, OBJ };

    public final Type type;
    public final String name;
    public final Uri uri;
    public final String mimeType;

    public NearbyItem(Type type, String name, Uri uri, String mimeType) {
        this.type = type;
        this.name = name;
        this.uri = uri;
        this.mimeType = mimeType;
    }

    public abstract Bitmap getIcon();

    public abstract void view(NearbyActivity nearbyActivity); // TODO: getIntent()

    public String toString() {
        return "[nearby item: " + uri + "]";
    }
    public abstract String getDetail();
}
