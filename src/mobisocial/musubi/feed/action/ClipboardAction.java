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

package mobisocial.musubi.feed.action;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import mobisocial.musubi.App;
import mobisocial.musubi.R;
import mobisocial.musubi.feed.iface.FeedAction;
import mobisocial.socialkit.Obj;

public class ClipboardAction extends FeedAction {

    @Override
    public String getName() {
        return "Clipboard";
    }

    @Override
    public Drawable getIcon(Context c) {
        return c.getResources().getDrawable(R.drawable.ic_menu_paste_holo_light);
    }

    @Override
    public void onClick(Context context, Uri feedUri) {
        Obj clip = App.clipboardObject;
        if (clip != null) {
            App.getMusubi(context).getFeed(feedUri).postObj(clip);
            App.clipboardObject = null;
        }
    }

    @Override
    public boolean isActive(Context c) {
        return App.clipboardObject != null;
    }

}
