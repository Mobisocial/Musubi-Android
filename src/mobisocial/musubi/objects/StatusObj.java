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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mobisocial.musubi.feed.iface.Activator;
import mobisocial.musubi.feed.iface.DbEntryHandler;
import mobisocial.musubi.feed.iface.FeedRenderer;
import mobisocial.musubi.ui.fragments.FeedListFragment.FeedSummary;
import mobisocial.musubi.ui.util.EmojiSpannableFactory;
import mobisocial.musubi.ui.widget.DbObjCursorAdapter.DbObjCursor;
import mobisocial.socialkit.musubi.DbObj;
import mobisocial.socialkit.obj.MemObj;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.Spannable;
import android.text.util.Linkify;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.TextView.BufferType;

/**
 * A text-based status update.
 *
 */
public class StatusObj extends DbEntryHandler implements FeedRenderer, Activator {

    public static final String TYPE = "status";
    public static final String TEXT = "text";

    @Override
    public String getType() {
        return TYPE;
    }

    public static MemObj from(String status) {
        return new MemObj(TYPE, json(status));
    }

    public static JSONObject json(String status){
        JSONObject obj = new JSONObject();
        try{
            obj.put(TEXT, status);
        }catch(JSONException e){}
        return obj;
    }

    @Override
	public View createView(Context context, ViewGroup frame) {
    	TextView valueTV = new TextView(context);
    	valueTV.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
		valueTV.setGravity(Gravity.TOP | Gravity.LEFT);
		// XXX: WTF, not working on NexusS, but is working on GalaxyNexus???
		//valueTV.setSpannableFactory(new EmojiSpannableFactory(context));
		return valueTV;
	}

    @Override
    public void render(Context context, View view, DbObjCursor obj, boolean allowInteractions) {
    	TextView valueTV = (TextView)view;
        String text = obj.getJson().optString(TEXT);
        Spannable span = EmojiSpannableFactory.getInstance(context).newSpannable(text);
        valueTV.setText(span, BufferType.SPANNABLE);
        if(Linkify.addLinks(valueTV, Linkify.ALL)) {
            if(!allowInteractions)
            	valueTV.setMovementMethod(null);
        }
    }

	static final Pattern p = Pattern.compile("\\b[-0-9a-zA-Z+\\.]+:\\S+");
	@Override
    public void activate(Context context, DbObj obj){
    	//linkify should have picked it up already but if we are in TV mode we
    	//still need to activate
        Intent intent = new Intent(Intent.ACTION_VIEW);
        String text = obj.getJson().optString(TEXT);
        
        //launch the first thing that looks like a link
        Matcher m = p.matcher(text);
        while(m.find()) {
	        Uri uri = Uri.parse(m.group());
	        String scheme = uri.getScheme();
	
	        if (scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                intent.setData(uri);
	            if (!(context instanceof Activity)) {
	                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	            }
	            context.startActivity(intent);
	            return;
	        }
        }    
	}

	@Override
	public void getSummaryText(Context context, TextView view, FeedSummary summary) {
		JSONObject json = summary.getJson();
		String text;
		if (json == null) {
			text = summary.getSender().name + ": <Empty messsage>";
		} else {
			text = summary.getSender().name + ": " + json.optString(StatusObj.TEXT);
		}
		Spannable span = EmojiSpannableFactory.getInstance(context).newSpannable(text);
		view.setText(span, BufferType.SPANNABLE);
	}
}
