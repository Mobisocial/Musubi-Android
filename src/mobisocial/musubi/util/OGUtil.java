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

package mobisocial.musubi.util;

import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.util.Log;

public class OGUtil {
	public static class OGData {
		String mTitle;
		String mUrl;
		byte[] mImage;
		String mDescription;
		String mMimeType;
	}
	private static final String TAG = "OGUtil";
	//TODO: these could be better, unit tests as well
	private static Pattern sTitleRegex = Pattern.compile("<\\s*title\\s*>([^<]+)<\\s*/title\\s*>", Pattern.CASE_INSENSITIVE);
	private static Pattern sImageRegex = Pattern.compile("<\\s*img\\s+[^>]+>", Pattern.CASE_INSENSITIVE);
	private static Pattern sMetaRegex = Pattern.compile("<\\s*meta\\s+[^>]+>", Pattern.CASE_INSENSITIVE);
	private static Pattern sPropertyOfMeta = Pattern.compile("\\b(?:name|property)\\s*=\\s*(\"[^\"]+\"|'[^']+')", Pattern.CASE_INSENSITIVE);
	private static Pattern sContentOfMeta = Pattern.compile("\\bcontent\\s*=\\s*(\"[^\"]+\"|'[^']+')", Pattern.CASE_INSENSITIVE);
	private static Pattern sSrcOfImage = Pattern.compile("\\bsrc\\s*=\\s*(\"[^\"]+\"|'[^']+')", Pattern.CASE_INSENSITIVE);

	public static OGData getOrGuess(String url) {
		DefaultHttpClient hc = new DefaultHttpClient();
		HttpResponse res;
		try {
			HttpGet hg =  new HttpGet(url);
			res = hc.execute(hg);
		} catch (Exception e) {
			Log.e(TAG, "unable to fetch page to get og tags", e);
			return null;
		}
		String location = url;
		//TODO: if some kind of redirect magic happened, then
		//make the location match that

		OGData og = new OGData();
		HttpEntity he = res.getEntity();
		Header content_type = he.getContentType();
		//TODO: check the content directly if they forget the type header
		if(content_type == null || content_type.getValue() == null) {
			Log.e(TAG, "page missing content type ..abandoning: " + url);
			return null;
		}
		og.mMimeType = content_type.getValue();
		//just make a thumbnail if the shared item is an image
		if(og.mMimeType.startsWith("image/")) {
			Bitmap b;
			try {
				b = BitmapFactory.decodeStream(he.getContent());
			} catch (Exception e) {
				return null;
			}
			//TODO: scaling
			int w = b.getWidth();
			int h = b.getHeight();
			if(w > h) {
				h = h * 200 / w;
				w = 200;
			} else {
				w = w * 200 / h;
				h = 200;
			}
			
			Bitmap b2 = Bitmap.createScaledBitmap(b, w, h, true);
			b.recycle();
			b = b2;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			b.compress(CompressFormat.PNG, 100, baos);
			og.mImage = baos.toByteArray();
			b.recycle();
			return og;
		}
		//if its not html, we can't extract more details, the caller
		//should rely on what they already know.
		if(!og.mMimeType.startsWith("text/html") && !og.mMimeType.startsWith("application/xhtml")) {
			Log.e(TAG, "shared content is not a known type for meta data processing " + og.mMimeType);
			return og;
		}
		
		String html;
		try {
			html = IOUtils.toString(he.getContent());
		} catch (Exception e) {
			Log.e(TAG, "failed to read html content", e);
			return og;
		}
		
		Matcher m = sTitleRegex.matcher(html);
		if(m.find()) {
			og.mTitle = StringEscapeUtils.unescapeHtml4(m.group(1));
			
		}
		m = sMetaRegex.matcher(html);
		int offset = 0;
		String raw_description = null;
		while(m.find(offset)) {
			try {
				String meta_tag = m.group();
				Matcher mp = sPropertyOfMeta.matcher(meta_tag);
				if(!mp.find())
					continue;
				String type = mp.group(1);
				type = type.substring(1, type.length() - 1);
				Matcher md = sContentOfMeta.matcher(meta_tag);
				if(!md.find())
					continue;
				String data = md.group(1);
				//remove quotes
				data = data.substring(1, data.length() - 1);
				data = StringEscapeUtils.unescapeHtml4(data);
				if(type.equalsIgnoreCase("og:title")) {
					og.mTitle = data;
				} else if(type.equalsIgnoreCase("og:image")) {
					HttpResponse resi;
					try {
						HttpGet hgi = new HttpGet(data);
						resi = hc.execute(hgi);
					} catch (Exception e) {
						Log.e(TAG, "unable to fetch og image url", e);
						continue;
					}
					HttpEntity hei = resi.getEntity();
					if(!hei.getContentType().getValue().startsWith("image/")) {
						Log.e(TAG, "image og tag points to non image data" + hei.getContentType().getValue());
					}
					try {
						Bitmap b;
						try {
							b = BitmapFactory.decodeStream(hei.getContent());
						} catch (Exception e) {
							return null;
						}
						//TODO: scaling
						int w = b.getWidth();
						int h = b.getHeight();
						if(w > h) {
							h = h * Math.min(200, w) / w;
							w = Math.min(200, w);
						} else {
							w = w * Math.min(200, h) / h;
							h = Math.min(200, h);
						}
						Bitmap b2 = Bitmap.createScaledBitmap(b, w, h, true);
						b.recycle();
						b = b2;
						ByteArrayOutputStream baos = new ByteArrayOutputStream();
						b.compress(CompressFormat.PNG, 100, baos);
						b.recycle();
						og.mImage = baos.toByteArray();
					} catch(Exception e) {
						Log.e(TAG, "failed to fetch image for og", e);
						continue;
					}
				} else if(type.equalsIgnoreCase("description")) {
					raw_description  = data;
				} else if(type.equalsIgnoreCase("og:description")) {
					og.mDescription = data;
				} else if(type.equalsIgnoreCase("og:url")) {
					og.mUrl = data;
				}
			} finally {
				offset = m.end();
			}
		}
		HashSet<String> already_fetched = new HashSet<String>();
		if(og.mImage == null) {
			int max_area = 0;
			m = sImageRegex.matcher(html);
			int img_offset = 0;
			while(m.find(img_offset)) {
				try {
					String img_tag = m.group();
					Matcher ms = sSrcOfImage.matcher(img_tag);
					if(!ms.find())
						continue;
					String img_src = ms.group(1);
					img_src = img_src.substring(1, img_src.length() - 1);
					img_src = StringEscapeUtils.unescapeHtml4(img_src);
					//don't fetch an image twice (like little 1x1 images)
					if(already_fetched.contains(img_src))
						continue;
					already_fetched.add(img_src);
					HttpResponse resi;
					try {
						HttpGet hgi = new HttpGet(new URL(new URL(location), img_src).toString());
						resi = hc.execute(hgi);
					} catch (Exception e) {	
						Log.e(TAG, "unable to fetch image url for biggest image search" + img_src, e);
						continue;
					}
					HttpEntity hei = resi.getEntity();
					if(hei == null) {
						Log.w(TAG, "image missing en ..trying entity response: " + url);
						continue;
					}
					Header content_type_image = hei.getContentType();
					if(content_type_image == null || content_type_image.getValue() == null) {
						Log.w(TAG, "image missing content type ..trying anyway: " + url);
					}
					if(!content_type_image.getValue().startsWith("image/")) {
						Log.w(TAG, "image tag points to non image data " + hei.getContentType().getValue() + " " + img_src);
					}
					try {
						Bitmap b;
						try {
							b = BitmapFactory.decodeStream(hei.getContent());
						} catch (Exception e) {
							return null;
						}
						//TODO: scaling
						int w = b.getWidth();
						int h = b.getHeight();
						if(w * h <= max_area) {
							continue;
						}
						if(w < 32 || h < 32) {
							//skip dinky crap
							continue;
						}
						if(w > h) {
							h = h * Math.min(200, w) / w;
							w = Math.min(200, w);
						} else {
							w = w * Math.min(200, h) / h;
							h = Math.min(200, h);
						}
						Bitmap b2 = Bitmap.createScaledBitmap(b, w, h, true);
						b.recycle();
						b = b2;
						ByteArrayOutputStream baos = new ByteArrayOutputStream();
						b.compress(CompressFormat.PNG, 100, baos);
						og.mImage = baos.toByteArray();
						b.recycle();
						max_area = w * h;
					} catch(Exception e) {
						Log.e(TAG, "failed to fetch image for og", e);
						continue;
					}
				} finally {
					img_offset = m.end();
				}
			}
			
		}
		if(og.mDescription == null)
			og.mDescription = raw_description;
		return og;
	}
}
