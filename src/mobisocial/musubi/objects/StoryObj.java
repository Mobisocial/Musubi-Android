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
import java.util.Arrays;
import java.util.regex.Pattern;

import mobisocial.musubi.feed.iface.Activator;
import mobisocial.musubi.feed.iface.DbEntryHandler;
import mobisocial.musubi.feed.iface.FeedRenderer;
import mobisocial.musubi.ui.fragments.FeedListFragment.FeedSummary;
import mobisocial.musubi.ui.widget.DbObjCursorAdapter.DbObjCursor;
import mobisocial.musubi.util.FlowTextHelper;
import mobisocial.socialkit.musubi.DbObj;
import mobisocial.socialkit.obj.MemObj;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class StoryObj extends DbEntryHandler implements FeedRenderer, Activator {

    public static final String TYPE = "story";
    //this is the display url
    public static final String URL = "url";
    //keep the original url in case there are link shortners/etc in effect
    //that are used for tracking purposes.  we want to be courteous to other
    //app developers
    public static final String ORIGINAL_URL = "original_url";
    public static final String TITLE = "title";
    public static final String TEXT = "text";
    public static final String FAV_ICON_LENGTH = "favicon_length";
    public static final String MIME_TYPE = "mime_type";

    @Override
    public String getType() {
        return TYPE;
    }

    public static MemObj from(String original_uri, String uri, String mime_type, String text, String title, byte[] favicon, byte[] thumbnail) {
    	byte[] data = null;
    	if(thumbnail == null) {
    		data = favicon;
    	} else if(favicon == null) {
    		data = thumbnail;
    	} else {
    		data = Arrays.copyOf(favicon, favicon.length + thumbnail.length);
    		for(int i = 0; i < thumbnail.length; ++i) {
    			data[i + favicon.length] = thumbnail[i];
    		}
    	}
        return new MemObj(TYPE, json(original_uri, uri, mime_type, text, title, favicon == null ? 0 : favicon.length), data);
    }

    public static JSONObject json(String original_uri, String uri, String mime_type, String text, String title, int favicon_length) {
    	if(uri == null)
    		throw new RuntimeException("can not pass a null uri to story obj");
        JSONObject obj = new JSONObject();
        try{
            obj.put(ORIGINAL_URL, original_uri);
            obj.put(URL, uri);
            obj.put(TEXT, text);
            obj.put(MIME_TYPE, mime_type);
            obj.put(TITLE, title);
            obj.put(FAV_ICON_LENGTH, favicon_length);
        } catch(JSONException e){}
        return obj;
    }

    @Override
    public View createView(Context context, ViewGroup frame) {
    	LinearLayout vertical = new LinearLayout(context);
    	vertical.setOrientation(LinearLayout.VERTICAL);
	    vertical.setGravity(Gravity.TOP | Gravity.LEFT);
    	return vertical;
    }

    //TODO: measure the text, split it into two chunks so we can just embed all of this in one text view using the spannable and margin settings
    //then we can have the wrap look good... or just make html and go really slow...? or set html on the text view and see if it supports margin
    //features with out webview?
    @Override
	public void render(Context context, View view, DbObjCursor obj, boolean allowInteractions) throws Exception {
	    JSONObject content = obj.getJson();
	    LinearLayout vertical = (LinearLayout)view;
	    vertical.removeAllViews();

        TextView valueTV = null;
        SpannableStringBuilder span = new SpannableStringBuilder();
        
        if (content.has(TITLE) && content.has(TEXT)) {
        	valueTV  = new TextView(context);
        	String heading;
			try {
				heading = content.getString(TITLE);
	            span.append(heading);
	            span.setSpan(new StyleSpan(Typeface.BOLD), span.length() - heading.length(), span.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
	            valueTV.setText(span);
	            valueTV.setLayoutParams(new LinearLayout.LayoutParams(
	                                        LinearLayout.LayoutParams.WRAP_CONTENT,
	                                        LinearLayout.LayoutParams.WRAP_CONTENT));
	            valueTV.setGravity(Gravity.TOP | Gravity.LEFT);
	            vertical.addView(valueTV);
			} catch (JSONException e) {}
        }

	    RelativeLayout horizontal = new RelativeLayout(context);
	    horizontal.setGravity(Gravity.TOP | Gravity.LEFT);
	    
	    int favicon_length = content.optInt(FAV_ICON_LENGTH);
        byte[] thumbnail = obj.getRaw();
        ImageView thumbnail_view = null;
        if(thumbnail != null && favicon_length < thumbnail.length) {
        	thumbnail_view = new ImageView(context);
        	thumbnail_view.setPadding(0, 4, 8, 0);
        	thumbnail_view.setBackgroundResource(android.R.drawable.picture_frame);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPurgeable = true;
            options.inInputShareable = true;
        	Bitmap b = BitmapFactory.decodeByteArray(thumbnail, favicon_length, thumbnail.length - favicon_length, options);
        	//TODO: scaling, depend on the presence of text
        	thumbnail_view.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
        	thumbnail_view.setImageBitmap(b);
        	horizontal.addView(thumbnail_view);        	
        }
	    
        if (content.has(TEXT)) {
        	String text;
	        valueTV = new TextView(context);
	        span = new SpannableStringBuilder();        
			text = content.getString(TEXT);
			text.trim();
            span.append(text);
            valueTV.setText(span);
            valueTV.setLayoutParams(new LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.WRAP_CONTENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT));
            valueTV.setGravity(Gravity.TOP | Gravity.LEFT);
            horizontal.addView(valueTV);
        } else if(content.has(TITLE)) {
        	String heading;
			valueTV = new TextView(context);
	        span = new SpannableStringBuilder();        
			heading = content.getString(TITLE);
            span.append(heading);
            span.setSpan(new StyleSpan(Typeface.BOLD), span.length() - heading.length(), span.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
            valueTV.setText(span);
            valueTV.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT));
            valueTV.setGravity(Gravity.TOP | Gravity.LEFT);
            horizontal.addView(valueTV);
        }
        WindowManager wm = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
        Display display =  wm.getDefaultDisplay();
        if (thumbnail_view != null && valueTV != null) {
            FlowTextHelper.tryFlowText(span, thumbnail_view, valueTV, display);
        }
        vertical.addView(horizontal);

        valueTV = new TextView(context);
        span = new SpannableStringBuilder();        
        
        if(thumbnail != null && favicon_length > 0) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPurgeable = true;
            options.inInputShareable = true;
        	Bitmap b = BitmapFactory.decodeByteArray(thumbnail, 0, favicon_length, options);
        	float density = context.getResources().getDisplayMetrics().density;
        	int dstWidth = (int)(b.getWidth() * density);
        	int dstHeight = (int)(b.getHeight() * density);
        	Drawable d = new BitmapDrawable(b);
        	d.setBounds(0, 0, dstWidth, dstHeight);
        	span.append(" ");
        	span.setSpan(new ImageSpan(d, ImageSpan.ALIGN_BASELINE), span.length() - 1, span.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
        	span.append(" ");
        }

        String url;
		url = content.getString(URL);
        span.append(url);
    	span.setSpan(new URLSpan(url), span.length() - url.length(), span.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
        
		valueTV.setPadding(0, 3, 0, 0);
        valueTV.setText(span);
        valueTV.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        valueTV.setGravity(Gravity.TOP | Gravity.LEFT);
        vertical.addView(valueTV);
    }
	static final Pattern p = Pattern.compile("\\b[-0-9a-zA-Z+\\.]+:\\S+");
    @Override
    public void activate(Context context, DbObj obj) {
        JSONObject content = obj.getJson();
        String url;
		try {
			//try the original url to be nice about tracking information
			url = content.optString(ORIGINAL_URL);
			if(url == null)
				url = content.getString(URL);
			Log.i(TAG, "URL: " + url);
	        Intent intent = new Intent(Intent.ACTION_VIEW).setData(Uri.parse(url));
            if (!(context instanceof Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
	        if (content.has(MIME_TYPE)) {
	            // Browser doesn't think it handles text/html. 
	        	// intent.setType(content.getString(MIME_TYPE));
	        }
        	context.startActivity(intent);
		} catch (JSONException e) {}
    }

	@Override
	public void getSummaryText(Context context, TextView view, FeedSummary summary) {
		JSONObject obj = summary.getJson();
		view.setTypeface(null, Typeface.ITALIC);
		String title = obj.optString(URL);
		if (obj.has(TITLE)) {
			title = obj.optString(TITLE);
		}
		view.setText(summary.getSender() + " posted a web story: " + title);
	}
}