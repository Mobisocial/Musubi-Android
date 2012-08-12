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
import mobisocial.musubi.ui.SettingsActivity;
import mobisocial.musubi.ui.fragments.FeedListFragment.FeedSummary;
import mobisocial.musubi.ui.widget.DbObjCursorAdapter.DbObjCursor;
import mobisocial.socialkit.musubi.DbObj;
import mobisocial.socialkit.obj.MemObj;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MusubiWizardObj extends DbEntryHandler implements FeedRenderer, Activator {
	public static final String TAG = "MusubiWizardObj";

	public static enum WizardAction {PROFILE, ACCOUNT, PICSAY};
    public static final String TYPE = "musubi_wizard";
    public static final String ACTION = "action";

    @Override
    public String getType() {
        return TYPE;
    }

    public static MemObj from(WizardAction action) {
        return new MemObj(TYPE, json(action));
    }

    public static JSONObject json(WizardAction action) {
        JSONObject obj = new JSONObject();
        try{
            obj.put(ACTION, action);
        }catch(JSONException e){}
        return obj;
    }


    @Override
    public View createView(Context context, ViewGroup frame) {
    	return new LinearLayout(context);
    }

    @Override
	public void render(final Context context, View view, DbObjCursor obj, boolean allowInteractions) throws Exception {
	    JSONObject content = obj.getJson();
	    LinearLayout frame = (LinearLayout)view;
        frame.removeAllViews();
        View parentView = (View)frame.getParent().getParent().getParent();
        
		TextView valueTV;
		ImageView avatar;
		TextView label;
		switch(WizardAction.valueOf(content.getString(ACTION))) {
			case PROFILE:
				valueTV = new TextView(context);
		        valueTV.setText("Click here to set your profile.");
		        valueTV.setLayoutParams(new LinearLayout.LayoutParams(
		                                    LinearLayout.LayoutParams.WRAP_CONTENT,
		                                    LinearLayout.LayoutParams.WRAP_CONTENT));
		        valueTV.setGravity(Gravity.TOP | Gravity.LEFT);
		 
		        frame.addView(valueTV);

	            avatar = (ImageView)parentView.findViewById(R.id.icon);
	    		avatar.setImageResource(R.drawable.ic_menu_preferences);
	    		avatar.setOnClickListener(new OnClickListener() {
	    			@Override
	                public void onClick(View v) {
	    				Intent intent = new Intent().setClass(context, SettingsActivity.class);
			    	    intent.putExtra(SettingsActivity.ACTION, SettingsActivity.SettingsAction.PROFILE.toString());
			    	    context.startActivity(intent);
	                }
	    		});

	            label = (TextView)parentView.findViewById(R.id.name_text);
	            label.setText("Profile");
				break;
			case ACCOUNT:
				valueTV = new TextView(context);
		        valueTV.setText("Click here to manage your accounts.");
		        valueTV.setLayoutParams(new LinearLayout.LayoutParams(
		                                    LinearLayout.LayoutParams.WRAP_CONTENT,
		                                    LinearLayout.LayoutParams.WRAP_CONTENT));
		        valueTV.setGravity(Gravity.TOP | Gravity.LEFT);
		 
		        frame.addView(valueTV);

	            avatar = (ImageView)parentView.findViewById(R.id.icon);
	    		avatar.setImageResource(R.drawable.ic_menu_preferences);
	    		avatar.setOnClickListener(new OnClickListener() {
	    			@Override
	                public void onClick(View v) {
	    				Intent intent = new Intent(context, SettingsActivity.class);
			    	    intent.putExtra(SettingsActivity.ACTION, SettingsActivity.SettingsAction.ACCOUNT.toString());
			    	    context.startActivity(intent);
	                }
	    		});

	            label = (TextView)parentView.findViewById(R.id.name_text);
	            label.setText("Accounts");
				break;
			case PICSAY:
				valueTV = new TextView(context);
		        valueTV.setText("Click here to download PicSay from the Android Market");
		        valueTV.setLayoutParams(new LinearLayout.LayoutParams(
		                                    LinearLayout.LayoutParams.WRAP_CONTENT,
		                                    LinearLayout.LayoutParams.WRAP_CONTENT));
		        valueTV.setGravity(Gravity.TOP | Gravity.LEFT);
		 
		        frame.addView(valueTV);

	            avatar = (ImageView)parentView.findViewById(R.id.icon);
	    		avatar.setImageResource(R.drawable.picsay_icon);
	    		avatar.setOnClickListener(new OnClickListener() {
	    			@Override
	                public void onClick(View v) {
						try {
		    				Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.shinycore.picsayfree"));
							context.startActivity(intent);
						}  catch (ActivityNotFoundException e) {
						    Toast.makeText(context, "Android Market not found on your device.", Toast.LENGTH_LONG).show();
						}
	                }
	    		});

	            label = (TextView)parentView.findViewById(R.id.name_text);
	            label.setText("PicSay");
				break;
		}
    }
	
    @Override
    public void activate(Context context, DbObj obj) {
        JSONObject content = obj.getJson();
        try {
        	switch(WizardAction.valueOf(content.getString(ACTION))) {
				case PROFILE:
			        Intent intent = new Intent().setClass(context, SettingsActivity.class);
		    	    intent.putExtra(SettingsActivity.ACTION, SettingsActivity.SettingsAction.PROFILE.toString());
			        context.startActivity(intent); 
					break;
				case ACCOUNT:
		    	    intent = new Intent(context, SettingsActivity.class);
		    	    intent.putExtra(SettingsActivity.ACTION, SettingsActivity.SettingsAction.ACCOUNT.toString());
		            context.startActivity(intent);
		            break;
				case PICSAY:
					intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.shinycore.picsayfree"));
					context.startActivity(intent);
					break;
			}
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}   
    }

	@Override
	public void getSummaryText(Context context, TextView view, FeedSummary summary) {
		view.setTypeface(null, Typeface.ITALIC);
		view.setText("Click me to learn about Musubi!");
	}
}