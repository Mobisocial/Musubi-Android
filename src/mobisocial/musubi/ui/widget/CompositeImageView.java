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

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.widget.ImageView;

public class CompositeImageView extends ImageView {
	private final ArrayList<Bitmap> mImages;
	private final Paint mWhitePaint;
	private final Rect mSourceRect;
	private final Rect mDestRect;

	public CompositeImageView(Context context) {
		super(context);
		mImages = new  ArrayList<Bitmap>();
		mWhitePaint = new Paint();
		mWhitePaint.setColor(Color.WHITE);
		mSourceRect = new Rect();
		mDestRect = new Rect();
	}

	public CompositeImageView(Context context, AttributeSet attr) {
		super(context, attr);
		mImages = new  ArrayList<Bitmap>();
		mWhitePaint = new Paint();
		mWhitePaint.setColor(Color.WHITE);
		mSourceRect = new Rect();
		mDestRect = new Rect();
	}

	public CompositeImageView(Context context, AttributeSet attr, int styleDef) {
		super(context, attr, styleDef);
		mImages = new  ArrayList<Bitmap>();
		mWhitePaint = new Paint();
		mWhitePaint.setColor(Color.WHITE);
		mSourceRect = new Rect();
		mDestRect = new Rect();
	}

	/**
	 * Must be called from ui thread.
	 */
	public void setImageBitmaps(List<Bitmap> bitmaps) {
		mImages.clear();
		mImages.addAll(bitmaps);
		invalidate();
	}

	@Override
	public void setImageBitmap(Bitmap bm) {
		mImages.clear();
		mImages.add(bm);
		invalidate();
	}

	@Override
	protected void onDraw(Canvas canvas) {
		int saveCount = canvas.getSaveCount();
        canvas.save();

        canvas.translate(getPaddingLeft(), getPaddingTop());
        drawCompositeImage(canvas);
        canvas.restoreToCount(saveCount);
	}

	private void drawCompositeImage(Canvas canvas) {
		int size = Math.min(getMeasuredWidth(), getMeasuredHeight());
		// fill white
		canvas.drawRect(0, 0, size, size, mWhitePaint);

		int rows = mImages.size() - 1;
		int colSize = (rows == 1) ? size / 2 : size / 3; // (size / Math.min(3, rows+1));
		int colOffset = size - colSize + 1; // 1px border

		Bitmap thumb = mImages.get(0);
		int dim = Math.min(thumb.getWidth(), thumb.getHeight());
		Rect src = mSourceRect;
		src.left = src.top = 0;
		src.right = src.bottom = dim;

		Rect dst = mDestRect;
		dst.left = dst.top = 0;
		dst.right = dst.bottom = size;
		canvas.drawBitmap(mImages.get(0), src, dst, null);
		if (mImages.size() == 1) {
			return;
		}

		canvas.drawLine(colSize+1, 0f, colSize+1, size, mWhitePaint);
		int cellHeight = size / rows;
		dst.left = 0;
		dst.right = colSize;
		dst.top = 0;
		dst.bottom = cellHeight;

		float aspect = (float)(size - colOffset) / cellHeight;
		for (int row = 0; row < rows; row++) {
			thumb = mImages.get(row + 1);
			dim = Math.min(thumb.getWidth(), thumb.getHeight());
			float myAspect = (float)thumb.getWidth() / thumb.getHeight();

			int width, height;
			if (aspect > myAspect) {
				width = thumb.getWidth();
				height = (int)(thumb.getHeight() / myAspect);
			} else {
				height = thumb.getHeight();
				width = (int)(thumb.getWidth() * aspect);
			}
			src.left = (thumb.getWidth() - width) / 2;
			src.right = src.left + width;
			src.top = (thumb.getHeight() - height) / 2;
			src.bottom = src.top + height;

			canvas.drawBitmap(thumb, src, dst, null);
			canvas.drawLine(0, row * cellHeight, colSize+1, row * cellHeight, mWhitePaint);
			// next
			dst.top += cellHeight;
			dst.bottom += cellHeight;
		}
	}
}
