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

import mobisocial.musubi.feed.iface.DbEntryHandler;
import mobisocial.musubi.feed.iface.FeedRenderer;
import mobisocial.musubi.ui.fragments.FeedListFragment.FeedSummary;
import mobisocial.musubi.ui.widget.DbObjCursorAdapter.DbObjCursor;
import mobisocial.socialkit.musubi.DbObj;
import mobisocial.socialkit.obj.MemObj;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.graphics.Typeface;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

public class PhoneStateObj extends DbEntryHandler implements FeedRenderer {

    public static final String EXTRA_STATE_UNKNOWN = "UNKNOWN";
    public static final String TYPE = "phone";
    public static final String ACTION = "action";
    public static final String NUMBER = "num";

    @Override
    public String getType() {
        return TYPE;
    }

    public static MemObj from(String action, String number) {
        return new MemObj(TYPE, json(action, number));
    }

    public static JSONObject json(String action, String number){
        JSONObject obj = new JSONObject();
        try{
            obj.put(ACTION, action);
            obj.put(NUMBER, number);
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
    public void render(Context context, View frame, DbObjCursor obj, boolean allowInteractions) {
        JSONObject content = obj.getJson();
        TextView valueTV = (TextView)frame;
        valueTV.setText(asText(content));
    }

    private String asText(JSONObject obj) {
        StringBuilder status = new StringBuilder();
        String a = obj.optString(ACTION);
        String b = obj.optString(NUMBER);
        if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(a)) {
            status.append("Calling ");
        } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(a)) {
            status.append("Ending phone call with ");
        } else if (TelephonyManager.EXTRA_STATE_RINGING.equals(a)) {
            status.append("Inbound call from ");
        }
        status.append(b).append(".");
        return status.toString();
    }

    @Override
    public boolean doNotification(Context context, DbObj obj) {
        return false;
    }

	@Override
	public void getSummaryText(Context context, TextView view, FeedSummary summary) {
		//TODO: you're sketchy
		view.setTypeface(null, Typeface.ITALIC);
		view.setText(summary.getSender() + " had some phone activity.");
	}
}
