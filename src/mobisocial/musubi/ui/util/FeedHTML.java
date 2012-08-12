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

package mobisocial.musubi.ui.util;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;

import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MObject;
import mobisocial.musubi.model.helpers.FeedManager;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.objects.PictureObj;
import mobisocial.musubi.objects.StatusObj;

import org.apache.commons.lang3.StringEscapeUtils;
import org.json.JSONObject;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.util.Base64;
import android.util.Log;

public class FeedHTML {

	public static void writeHeader(FileOutputStream fo, FeedManager feedManager, MFeed feed) {
		PrintWriter w = new PrintWriter(fo);
		w.print("<html>");
		w.print("<head>");
		w.print("<title>");
		w.print(StringEscapeUtils.escapeHtml4(UiUtil.getFeedNameFromMembersList(feedManager, feed)));
		w.print("</title>");
		w.print("</head>");
		w.print("<body>");
		w.print("<h1>");
		w.print(StringEscapeUtils.escapeHtml4(UiUtil.getFeedNameFromMembersList(feedManager, feed)));
		w.print("</h1>");
		w.flush();
	}

	public static void writeObj(FileOutputStream fo, Context context, IdentitiesManager identitiesManager, MObject object) {
		//TODO: it would be better to put the export code inside the obj handlers
		MIdentity ident = identitiesManager.getIdentityForId(object.identityId_);
		if(ident == null)
			return;
		PrintWriter w = new PrintWriter(fo);
		w.print("<div>");
		w.print("<div style=\"float:left\">");

		w.print("<img src=\"data:image/jpeg;base64,");
		Bitmap thumb = UiUtil.safeGetContactThumbnail(context, identitiesManager, ident);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		thumb.compress(CompressFormat.JPEG, 90, bos);
		w.print(Base64.encodeToString(bos.toByteArray(), Base64.DEFAULT));
		w.print("\">");

		w.print("</div>");
		w.print("<div>");
		w.print("<h6>");
		w.print(UiUtil.safeNameForIdentity(ident));
		w.print("</h6>");
		
		try {
			if(object.type_.equals(StatusObj.TYPE)) {
				w.print(new JSONObject(object.json_).getString(StatusObj.TEXT));
			} else if(object.type_.equals(PictureObj.TYPE)) {
				w.print("<img src=\"data:image/jpeg;base64,");
				w.print(Base64.encodeToString(object.raw_, Base64.DEFAULT));
				w.print("\">");				
			} else {
				throw new RuntimeException("unsupported type " + object.type_);
			}
		
		} catch(Throwable t) {
			Log.e("HTML EXPORT", "failed to process obj", t);
			w.print("<i>only visibile in musubi</i>");
		}
		w.print("</div>");
		w.print("</div>");

		
		w.print("</body>");
		w.print("</html>");
		w.flush();
	}

	public static void writeFooter(FileOutputStream fo) {
		PrintWriter w = new PrintWriter(fo);
		w.print("</body>");
		w.print("</html>");
		w.flush();
	}

}
