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

package mobisocial.musubi.ui.widget;

import mobisocial.musubi.App;
import mobisocial.musubi.feed.iface.FeedRenderer;
import mobisocial.musubi.model.MObject;
import mobisocial.musubi.model.helpers.DatabaseManager;
import mobisocial.musubi.obj.ObjHelpers;
import mobisocial.musubi.objects.AppStateObj;
import mobisocial.musubi.ui.widget.DbObjCursorAdapter.DbObjCursor;
import mobisocial.socialkit.Obj;

import org.json.JSONObject;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Renders an Obj as a view.
 */
// XXX must match ObjHelpers ViewGroup type.
public class ObjView extends LinearLayout {
    final Obj mObj;
    public ObjView(Context context, Obj obj) {
        super(context);
        setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT));
        mObj = obj;
        FeedRenderer renderer = ObjHelpers.getFeedRenderer(obj.getType());
        try {
	        if (renderer != null) {
	        	View frame = renderer.createView(context, this);
	        	addView(frame);
	            renderer.render(context, frame, new SketchyDbObjCursor(shim(obj)), false);
	        } else {
	            renderGeneric(context, obj);
	        }
        } catch(Throwable t) {
			Log.e("OBjView", "failed to handle rendering of an obj", t);
			TextView tv = new TextView(context);
			tv.setText("Unable to render object: " + t.getLocalizedMessage());
			//TODO: this should fill in something
			addView(tv, LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
        }
    }

    void renderGeneric(Context context, Obj obj) {
        if (obj.getJson() != null && obj.getJson().has(Obj.FIELD_HTML)) {
            String html = obj.getJson().optString(Obj.FIELD_HTML);
            AppStateObj.renderHtml(context, this, html);   
        }
    }

    private final class SketchyDbObjCursor extends DbObjCursor {
		public SketchyDbObjCursor(MObject obj) {
			super(new DatabaseManager(App.getDatabaseSource(getContext())), obj);
		}
    }

    private MObject shim(Obj obj) {
    	MObject o = new MObject();
		o.type_ = obj.getType();
		o.json_ = obj.getJson().toString();
		o.raw_ = obj.getRaw();
		o.intKey_ = obj.getIntKey();
		o.stringKey_ = obj.getStringKey();
		return o;
    }
}