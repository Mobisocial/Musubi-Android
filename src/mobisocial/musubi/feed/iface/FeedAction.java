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

import java.util.ArrayList;
import java.util.List;

import mobisocial.musubi.feed.action.CamcorderAction;
import mobisocial.musubi.feed.action.CameraAction;
import mobisocial.musubi.feed.action.ClipboardAction;
import mobisocial.musubi.feed.action.FileGalleryAction;
import mobisocial.musubi.feed.action.VoiceAction;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.v4.app.Fragment;

/**
 * Interface for actions that act over a feed.
 *
 */
public abstract class FeedAction extends Fragment {
    public abstract String getName();
    public abstract Drawable getIcon(Context c);
    public abstract void onClick(Context context, Uri feedUri);
    public abstract boolean isActive(Context c);

    private static final List<FeedAction> sFeedActions = new ArrayList<FeedAction>();
    static {
        //sFeedActions.add(new PresenceAction());
        sFeedActions.add(new CameraAction());
        sFeedActions.add(new CamcorderAction());
        //sFeedActions.add(new VideoGalleryAction());
        //sFeedActions.add(new GalleryAction());
        sFeedActions.add(new FileGalleryAction());
        sFeedActions.add(new VoiceAction());
        sFeedActions.add(new ClipboardAction());
        //sFeedActions.add(new LaunchApplicationAction());
    }

    public static List<FeedAction> getFeedActions() {
        return sFeedActions;
    }
}
