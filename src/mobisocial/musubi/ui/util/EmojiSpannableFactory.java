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

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.support.v4.util.LruCache;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.DynamicDrawableSpan;
import android.util.Log;

public class EmojiSpannableFactory extends Spannable.Factory {
	private static final String TAG = "StickerFactory";
	//final int MAX_RECYCLED_SPANS = 50;
	final int MAX_CACHED_BITMAPS = 75;

	final Context mContext;
	static SoftReference<EmojiSpannableFactory> sSpannableFactory;

	Bitmap mEmojiBitmap;
	Map<Long, Integer> mEmojiMap;
	final StickerCache mStickerCache = new StickerCache(75);
	private boolean mEmojiPrepared = false;

	public static EmojiSpannableFactory getInstance(Context context) {
		if (sSpannableFactory != null) {
			EmojiSpannableFactory f = sSpannableFactory.get();
			if (f != null) {
				return f;
			}
		}
		EmojiSpannableFactory f = new EmojiSpannableFactory(context);
		sSpannableFactory = new SoftReference<EmojiSpannableFactory>(f);
		return f;
	}
	
	private EmojiSpannableFactory(Context context) {
		mContext = context.getApplicationContext();
		new PrepareEmojiAsyncTask().execute();
	}

	@Override
	public Spannable newSpannable(CharSequence source) {
		if (source == null) {
			return null;
		}
		SpannableString span = new SpannableString(source);
		updateSpannable(span);
		return span;
	}

	public void updateSpannable(Spannable span) {
		Spannable source = span;
		for (int i = 0; i < source.length(); i++) {
			char high = source.charAt(i);
			if (high <= 127) {
				// fast exit ascii
				continue;
			}

			// Block until we're initialized
			waitForEmoji();

			long codePoint = high;
			if (Character.isHighSurrogate(high)) {
				char low = source.charAt(++i);
				codePoint = Character.toCodePoint(high, low);
				if (Character.isSurrogatePair(high, low)) {
					// from BMP
					if (!mEmojiMap.containsKey(codePoint)) {
						if (i >= source.length() - 2) {
							continue;
						}
						high = source.charAt(++i);
						if (!Character.isHighSurrogate(high)) {
							Log.w(TAG, "bad unicode character? " + high);
							continue;
						}
						low = source.charAt(++i);
						if (!Character.isSurrogatePair(high, low)) {
							Log.d(TAG, "Bogus unicode surrogate " + high + ", "
									+ low);
							continue;
						}
						int codePoint2 = Character.toCodePoint(high, low);
						//String label = String.format("U+%X U+%X", codePoint, codePoint2);
						codePoint = ((long)codePoint << 16) | codePoint2;
					}
				} else {
					Log.d(TAG, "Bogus unicode");
				}
			}

			if (mEmojiMap.containsKey(codePoint)) {
				Bitmap b = mStickerCache.get(codePoint);
				if (b != null) {
					DynamicDrawableSpan im = createStickerSpan(b);
					span.setSpan(im, i, i + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				} else {
					Log.d(TAG, "failed to decode bitmap for codepoints: " + codePoint);
				}
			}
		}
	}

	private StickerSpan createStickerSpan(Bitmap b) {
		return new StickerSpan(mContext, b);
	}

	private Long getEmojiCode(String label) {
		String[] parts = label.split(" ");
		if (parts.length == 0 || parts.length > 2) {
			return null;
		}
		long code = 0;
		
		if (parts.length == 2) {
			code = (Long.parseLong(parts[0].replace("U+", ""), 16) << 16);
			code |= Long.parseLong(parts[1].replace("U+", ""), 16); 
		} else {
			code = (Long.parseLong(parts[0].replace("U+", ""), 16));
		}
		return code;
	}

	/**
	 * Blocks until the factory has been populated with emoji.
	 */
	private void waitForEmoji() {
		if (!mEmojiPrepared) {
			synchronized (this) {
				while (!mEmojiPrepared) {
					try {
						EmojiSpannableFactory.this.wait();
					} catch (InterruptedException e) {}
				}
			}
		}
	}

	class PrepareEmojiAsyncTask extends AsyncTask<Void, Void, Void> {
		@Override
		protected Void doInBackground(Void... params) {
			try {
				InputStream inStream = mContext.getAssets().open("emoji/stickers.json");
				String jsonSrc = IOUtils.toString(inStream);
				JSONObject json;
				int unicodeIndex = 0;
				int sbIndex = 0;
				try {
					mEmojiMap = new HashMap<Long, Integer>();
					json = new JSONObject(jsonSrc);
					JSONArray list = json.getJSONArray("sheets");
					int len = list.length();
					for (int i = 0; i < len; i++) {
						JSONArray stickers = list.getJSONObject(i).getJSONArray("unicode");
						int stickerCount = stickers.length();
						for (int j = 0; j < stickerCount; j++) {
							Long label = getEmojiCode(stickers.getString(j));
							if (label == null) {
								Log.e(TAG, "Bad emoji " + stickers.getString(j));
								unicodeIndex++;
								continue;
							}
							mEmojiMap.put(label, unicodeIndex++);
						}
	
						JSONArray sbStickers = list.getJSONObject(i).getJSONArray("sb");
						if (sbStickers != null && sbStickers.length() == stickers.length()) {
							for (int j = 0; j < stickerCount; j++) {
								try {
									Integer label = Integer.parseInt(sbStickers.getString(j), 16);
									if (!mEmojiMap.containsKey(label)) {
										mEmojiMap.put(label.longValue(), sbIndex++);
									}
								} catch (NumberFormatException e) {
									Log.e(TAG, "Bad sb code", e);
									sbIndex++;
								}
							}
						}
					}
				} catch (JSONException e) {
					throw new IOException(e);
				}
			} catch (IOException e) {
				Log.e(TAG, "Error loading emoji", e);
				mEmojiMap = null;
			}

			synchronized (EmojiSpannableFactory.this) {
				EmojiSpannableFactory.this.mEmojiPrepared = true;
				EmojiSpannableFactory.this.notify();
			}
			return null;
		}
	}

	class StickerCache extends LruCache<Long, Bitmap> {
		public StickerCache(int capacity) {
			super(capacity);
		}

		@Override
		protected Bitmap create(Long label) {
			try {
				Map<Long, Integer> emojiMap = mEmojiMap;
				Integer pos = emojiMap.get(label);
				if (pos == null) {
					return null;
				}
				// TODO: parameters in json
				int emojiSize = 30;
				int emojiPerRow = 10;

				int leftPos = (pos % emojiPerRow) * emojiSize;
				int topPos = (pos / emojiPerRow) * emojiSize;

				Bitmap sheet = getEmojiBitmap();
				Bitmap sticker = Bitmap.createBitmap(sheet, leftPos, topPos, emojiSize, emojiSize);
				return sticker;
			} catch (IOException e) {
				Log.e(TAG, "asset error", e);
				return null;
			}
		}

		Bitmap getEmojiBitmap() throws IOException {
			if (mEmojiBitmap != null) {
				return mEmojiBitmap;
			}
			InputStream emoStream = mContext.getAssets().open("emoji/stickers.png");
			Bitmap b = BitmapFactory.decodeStream(emoStream);
			mEmojiBitmap = b;
			return b;
		}
	}

	static class StickerSpan extends DynamicDrawableSpan {
		final FontMetricsInt mFontMetricsInt;
		Drawable mDrawable;

		public StickerSpan(Context context, Bitmap bitmap) {
			super(DynamicDrawableSpan.ALIGN_BASELINE);
			setBitmap(context, bitmap);
			mFontMetricsInt = new FontMetricsInt();
		}

		public void setBitmap(Context context, Bitmap bitmap) {
			mDrawable = new BitmapDrawable(context.getResources(), bitmap);
			int width = mDrawable.getIntrinsicWidth();
	        int height = mDrawable.getIntrinsicHeight();
	        mDrawable.setBounds(0, 0, width > 0 ? width : 0, height > 0 ? height : 0); 
		}

		@Override
		public Drawable getDrawable() {
			return mDrawable;
		}

	    @Override
	    public void draw(Canvas canvas, CharSequence text,
	                     int start, int end, float x, 
	                     int top, int y, int bottom, Paint paint) {
	        Drawable b = mDrawable;
	        canvas.save();
	        
	        int transY = bottom - b.getBounds().bottom;
	        paint.getFontMetricsInt(mFontMetricsInt);
	        if (mVerticalAlignment == ALIGN_BASELINE) {
	        	int textLength = text.length();
	        	for (int i = 0; i < textLength; i++) {
	        		if (Character.isLetterOrDigit(text.charAt(i))) {
	        			transY -= mFontMetricsInt.descent;
	        			break;
	        		}
	        	}
	        }

	        canvas.translate(x, transY);
	        b.draw(canvas);
	        canvas.restore();
	    }
	}
}