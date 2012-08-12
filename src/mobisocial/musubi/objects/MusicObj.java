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

package mobisocial.musubi.objects;

import mobisocial.musubi.R;
import mobisocial.musubi.feed.iface.Activator;
import mobisocial.musubi.feed.iface.DbEntryHandler;
import mobisocial.musubi.feed.iface.FeedRenderer;
import mobisocial.musubi.ui.fragments.FeedListFragment.FeedSummary;
import mobisocial.musubi.ui.widget.DbObjCursorAdapter.DbObjCursor;
import mobisocial.musubi.util.CommonLayouts;
import mobisocial.socialkit.musubi.DbObj;
import mobisocial.socialkit.obj.MemObj;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MusicObj extends DbEntryHandler implements FeedRenderer, Activator {

    public static final String TYPE = "music";
    public static final String ARTIST = "a";
    public static final String ALBUM = "l";
    public static final String TRACK = "t";
    public static final String URL = "url";
    public static final String MIME_TYPE = "mimeType";

    @Override
    public String getType() {
        return TYPE;
    }

    public static MemObj from(String action, String number) {
        return new MemObj(TYPE, json(action, number));
    }

    public static MemObj from(String artist, String album, String track) {
        return new MemObj(TYPE, json(artist, album, track));
    }

    public static JSONObject json(String artist, String number) {
        JSONObject obj = new JSONObject();
        try{
            obj.put(ARTIST, artist);
            obj.put(TRACK, number);
        }catch(JSONException e){}
        return obj;
    }

    public static JSONObject json(String artist, String album, String track) {
        JSONObject obj = new JSONObject();
        try{
            obj.put(ARTIST, artist);
            obj.put(ALBUM, album);
            obj.put(TRACK, track);
        }catch(JSONException e){}
        return obj;
    }

    @Override
    public View createView(Context context, ViewGroup frame) {
    	LinearLayout container = new LinearLayout(context);
        container.setLayoutParams(CommonLayouts.FULL_WIDTH);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER);
        ImageView imageView = new ImageView(context);
        imageView.setImageResource(R.drawable.play);
        imageView.setLayoutParams(new LinearLayout.LayoutParams(
                                      LinearLayout.LayoutParams.WRAP_CONTENT,
                                      LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView valueTV = new TextView(context);
        valueTV.setLayoutParams(new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.FILL_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT));
        valueTV.setGravity(Gravity.BOTTOM | Gravity.LEFT);
        valueTV.setPadding(4, 0, 0, 0);

        container.addView(imageView);
        container.addView(valueTV);
        return container;
    }

    @Override
    public void render(Context context, View view, DbObjCursor obj, boolean allowInteractions) {
    	TextView valueTV = (TextView)((LinearLayout)view).getChildAt(1);
        JSONObject content = obj.getJson();
        valueTV.setText(asText(content));
    }

    private String asText(JSONObject obj) {
        StringBuilder status = new StringBuilder();
        String a = obj.optString(ARTIST);
        String b = obj.optString(TRACK);
        if (b == null || b.length() == 0) {
            b = obj.optString(ALBUM);
        }
        status.append(a).append(" - ").append(b);
        return status.toString();
    }

    @Override
    public void activate(Context context, DbObj obj) {
        JSONObject content = obj.getJson();
        if (content.has(URL)) {
            Intent view = new Intent(Intent.ACTION_VIEW);
            Uri uri = Uri.parse(content.optString(URL));
            String type = "audio/x-mpegurl";
            if (content.has(MIME_TYPE)) {
                type = content.optString(MIME_TYPE);
            }
            view.setDataAndType(uri, type);
            if (!(context instanceof Activity)) {
                view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(view);
        }
    }

    @Override
    public boolean doNotification(Context context, DbObj obj) {
        return false;
    }

	@Override
	public void getSummaryText(Context context, TextView view, FeedSummary summary) {
		JSONObject obj = summary.getJson();
		view.setTypeface(null, Typeface.ITALIC);
		view.setText(summary.getSender() + " posted a new song: " + asText(obj));
	}
}
