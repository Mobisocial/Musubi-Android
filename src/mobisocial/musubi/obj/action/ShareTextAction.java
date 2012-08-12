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


package mobisocial.musubi.obj.action;

import mobisocial.musubi.R;
import mobisocial.musubi.feed.iface.DbEntryHandler;
import mobisocial.musubi.obj.iface.ObjAction;
import mobisocial.musubi.objects.StatusObj;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.ui.SendContentActivity;
import mobisocial.socialkit.musubi.DbObj;

import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;

/**
 * Sends a picture object using the standard Android "SEND" intent.
 *
 */
public class ShareTextAction extends ObjAction {
	public static final String TAG = "ExportTextAction";
	
    @Override
    public void onAct(Context context, DbEntryHandler objType, DbObj obj) {
        Resources res = context.getResources();
        Intent intent = new Intent(android.content.Intent.ACTION_SEND);
        intent.putExtra(SendContentActivity.EXTRA_CALLING_APP, MusubiContentProvider.SUPER_APP_ID);
        intent.putExtra(Intent.EXTRA_SUBJECT, res.getString(R.string.shared_text_from_musubi));
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, textFromObj(obj));
        context.startActivity(Intent.createChooser(intent, "Export text to"));
    }

    String textFromObj(DbObj obj) {
        StringBuilder status = new StringBuilder(obj.getJson().optString(StatusObj.TEXT));
        status.append("\n\nShared from Musubi");
        return status.toString();
    }

    @Override
    public String getLabel(Context context) {
        return "Share";
    }

    @Override
    public boolean isActive(Context context, DbEntryHandler objType, DbObj obj) {
        return (objType instanceof StatusObj);
    }
}
