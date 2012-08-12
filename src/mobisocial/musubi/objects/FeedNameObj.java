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
import mobisocial.musubi.App;
import mobisocial.musubi.R;
import mobisocial.musubi.feed.iface.DbEntryHandler;
import mobisocial.musubi.feed.iface.FeedRenderer;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MObject;
import mobisocial.musubi.model.helpers.FeedManager;
import mobisocial.musubi.service.MusubiService;
import mobisocial.musubi.ui.fragments.FeedListFragment.FeedSummary;
import mobisocial.musubi.ui.util.EmojiSpannableFactory;
import mobisocial.musubi.ui.widget.DbObjCursorAdapter.DbObjCursor;
import mobisocial.socialkit.obj.MemObj;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.text.Spannable;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TextView.BufferType;

/**
 * An object that provides minimal information about introduced participants to a group.
 * This ensures the minimum latency in a person being able to tell who is participating
 * in a feed.  Other approaches, such as sending profile to people we discover (which we still do),
 * have one round-trip of latency.
 *
 */
public class FeedNameObj extends DbEntryHandler implements FeedRenderer {

    public static final String TYPE = "feed_name";
    public static final String FEED_NAME = "name";

    @Override
    public String getType() {
        return TYPE;
    }

    public static MemObj from(String name, byte[] thumbnail) {
        return new MemObj(TYPE, json(name), thumbnail);
    }

    public static JSONObject json(String name){
        JSONObject obj = new JSONObject();
        try{
            obj.put(FEED_NAME, name);
        }catch(JSONException e){}
        return obj;
    }

    @Override
    public View createView(Context context, ViewGroup frame) {
    	LinearLayout vertical = new LinearLayout(context);
    	vertical.setOrientation(LinearLayout.VERTICAL);

    	TextView valueTV = new TextView(context);
    	valueTV.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
		valueTV.setGravity(Gravity.TOP | Gravity.LEFT);
		valueTV.setId(R.id.text);
		vertical.addView(valueTV);

		ImageView im = new ImageView(context);
		im.setLayoutParams(new LinearLayout.LayoutParams(200, 200));
		im.setId(R.id.icon);
		im.setBackgroundResource(R.drawable.frame_gallery_thumb);
		vertical.addView(im);

		return vertical;
    }

    @Override
    public void render(Context context, View frame, DbObjCursor obj, boolean allowInteractions) {
        JSONObject content = obj.getJson();
        TextView valueTV = (TextView)(frame.findViewById(R.id.text));
        StringBuilder text = new StringBuilder(50).append("I updated the details of \"").append(content.opt(FEED_NAME)).append("\".");
        Spannable span = EmojiSpannableFactory.getInstance(context).newSpannable(text);
        valueTV.setText(span, BufferType.SPANNABLE);

        ImageView iv = (ImageView)(frame.findViewById(R.id.icon));
        byte[] raw = obj.getRaw();
        if (raw == null) {
        	iv.setVisibility(View.GONE);
        } else {
        	iv.setVisibility(View.VISIBLE);
        	Bitmap bm = BitmapFactory.decodeByteArray(raw, 0, raw.length);
        	iv.setImageBitmap(bm);
        }
    }

    @Override
    public boolean processObject(Context context, MFeed feed, MIdentity sender,
    		MObject object) {
        SQLiteOpenHelper databaseSource = App.getDatabaseSource(context);
    	FeedManager feedManager = new FeedManager(databaseSource);
    	if (object.json_ == null) {
            Log.w(TAG, "bad feed rename format");
            return false;
        }
        JSONObject json;
        try {
            json = new JSONObject(object.json_);
        } catch (JSONException e) {
            Log.e(TAG, "Bad json in database", e);
            return false;
        }

        byte[] feedThumbnail = object.raw_;
        String feedName = null;
        try {
			feedName = json.getString(FEED_NAME);
		} catch (JSONException e) {
		}

        if (feedName == null && feedThumbnail == null) {
        	Log.e(TAG, "no feed details to set!");
        	return false;
        }

		if (feedManager.isLatestFeedNameSuggestion(object)) {
			if (feedName != null) {
				feed.name_ = feedName;
			}
			if (feedThumbnail != null) {
				feed.thumbnail_ = feedThumbnail;
			}
			feedManager.updateFeedDetails(feed.id_, feed.name_, feed.thumbnail_);
			context.getContentResolver().notifyChange(MusubiService.FEED_UPDATED, null);
		}
    	return true;
    }

	@Override
	public void getSummaryText(Context context, TextView view, FeedSummary summary) {
		//JSONObject obj = summary.getJson();
		StringBuilder text = new StringBuilder(50).append(summary.getSender()).append(" changed the feed details.");
		//Spannable span = EmojiSpannableFactory.getInstance(context).newSpannable(text);
		view.setTypeface(null, Typeface.ITALIC);
		view.setText(text, BufferType.SPANNABLE);
	}
}
