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
import java.text.DecimalFormat;
import java.text.NumberFormat;

import mobisocial.musubi.feed.iface.Activator;
import mobisocial.musubi.feed.iface.DbEntryHandler;
import mobisocial.musubi.feed.iface.FeedRenderer;
import mobisocial.musubi.ui.fragments.FeedListFragment.FeedSummary;
import mobisocial.musubi.ui.widget.DbObjCursorAdapter.DbObjCursor;
import mobisocial.socialkit.musubi.DbObj;
import mobisocial.socialkit.obj.MemObj;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.location.Location;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

public class LocationObj extends DbEntryHandler implements FeedRenderer, Activator {
    public static final String TYPE = "loc";
    public static final String COORD_LAT = "lat";
    public static final String COORD_LONG = "lon";

    @Override
    public String getType() {
        return TYPE;
    }

    public static MemObj from(Location location) {
        return new MemObj(TYPE, json(location));
    }

    public static JSONObject json(Location location){
        JSONObject obj = new JSONObject();
        try{
            obj.put(COORD_LAT, location.getLatitude());
            obj.put(COORD_LONG, location.getLongitude());
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
    	return valueTV;
    }

    @Override
    public void render(Context context, View view, DbObjCursor obj, boolean allowInteractions) {
    	TextView valueTV = (TextView)view;
        JSONObject content = obj.getJson();
        
        NumberFormat df =  DecimalFormat.getNumberInstance();
        df.setMaximumFractionDigits(5);
        df.setMinimumFractionDigits(5);
        
        String msg = "I'm at " + 
        	df.format(content.optDouble(COORD_LAT)) +
        	", " +
        	df.format(content.optDouble(COORD_LONG));
        valueTV.setText(msg);
    }

    @Override
    public void activate(Context context, DbObj obj) {
        JSONObject content = obj.getJson();
        String loc = "geo:" + content.optDouble(COORD_LAT) + "," +
                content.optDouble(COORD_LONG) + "?z=17";
        Intent map = new Intent(Intent.ACTION_VIEW, Uri.parse(loc));
        context.startActivity(map);
    }

    @Override
    public boolean doNotification(Context context, DbObj obj) {
        return false;
    }

	@Override
	public void getSummaryText(Context context, TextView view, FeedSummary summary) {
		view.setTypeface(null, Typeface.ITALIC);
		view.setText(summary.getSender() + " shared their location.");
	}
}
