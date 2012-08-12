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

package mobisocial.musubi.service;

import gnu.trove.list.array.TByteArrayList;

import java.lang.ref.SoftReference;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Random;

import mobisocial.musubi.R;
import mobisocial.musubi.objects.AppStateObj;
import mobisocial.musubi.util.Util;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Picture;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.util.LruCache;
import android.util.DisplayMetrics;
import android.view.View.MeasureSpec;
import android.webkit.WebView;
import android.webkit.WebView.PictureListener;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.LinearLayout;

//TODO: use this to measure
public class WebRenderService extends Service {
	public static final String TAG = "WebRenderService";
	
	public interface SizeReceiver {
		//note this is the size on the local phone and generally
		//we should be processing it and sending it with something that
		//understands any relevant scaling aspect ratio
		public void onSizeComputed(int width, int height);
	}
	WebView mWebView;
	RenderWebViewClient mWebViewClient;
	static WebRenderService sService = null;
	//this is a cheap way to workaround the fact that our current system is calling bind view twice
	//per object when it loads.  we would like to cache these rendered images offline, but for now
	//i would like to avoid additional sources of space usage.
	LruCache<TByteArrayList, SoftReference<Bitmap>> mWebViewCache = new LruCache<TByteArrayList, SoftReference<Bitmap>>(8);

	public static class WebRenderRequest {
	    // input
		String mHtml;
		ImageView mDestionationView;
		int targetWidth;
		int targetHeight;

		// output
		int mWidth;
		int mHeight;
	}
	static LinkedList<WebRenderRequest> sToProcess = new LinkedList<WebRenderRequest>();
	
	class RenderWebViewClient extends WebViewClient {
		WebRenderRequest mCurrent;
		private boolean mPending;
		public RenderWebViewClient() {
			mWebView.setPictureListener(new PictureListener() {
				@Override
				public void onNewPicture(WebView view, Picture picture) {
					if(mCurrent == null || picture.getWidth() == 0 || picture.getHeight() == 0) {
						return;
					}
					mCurrent.mWidth = picture.getWidth();
					mCurrent.mHeight = picture.getHeight();
					float scale = (float) mCurrent.targetHeight / picture.getHeight();

					Bitmap bitmap = Bitmap.createBitmap((int)(scale * mCurrent.mWidth), (int)(scale * mCurrent.mHeight), Bitmap.Config.RGB_565);
					Canvas canvas = new Canvas(bitmap);
					canvas.scale(scale, scale);
					picture.draw(canvas);
					mWebViewCache.put(new TByteArrayList(Util.sha256(mCurrent.mHtml.getBytes())), new SoftReference<Bitmap>(bitmap));
					if(mCurrent.mDestionationView != null) {
				        setImageViewBitmapAndLayout(mCurrent.mDestionationView, bitmap);
					}
					mCurrent = null;
					mPending = false;						
					WebRenderRequest item = sToProcess.peek();
					if(item != null) {
						kickoffJob(item);
					}
				}
			});
		}
		public void addItem(WebRenderRequest item) {
			sToProcess.add(item);
			if(!mPending) {
				kickoffJob(item);
			}
		}
		@Override
		public void onPageFinished(WebView view, String url) {
			mCurrent = sToProcess.poll();
		}
		@Override
		public void onReceivedError(WebView view, int errorCode,
				String description, String failingUrl) {
		}
		@Override
		public void onPageStarted(WebView view, String url, Bitmap favicon) {
		}
		@Override
		public void onScaleChanged(WebView view, float oldScale, float newScale) {
		}
		
		boolean lastWasSpacer = false;
		void kickoffJob(WebRenderRequest item) {
			mWebView.clearView();
			mWebView.clearHistory(); //otherwise this leaks cached copies of the page in memory
			mPending = true;
			mWebView.measure(
					MeasureSpec.makeMeasureSpec((int)(1), MeasureSpec.UNSPECIFIED),
					MeasureSpec.makeMeasureSpec(AppStateObj.MAX_HEIGHT, MeasureSpec.UNSPECIFIED));
			mWebView.layout(0, 0, 1, 1);
			if(!lastWasSpacer) {
				WebRenderRequest dummy = new WebRenderRequest();
				dummy.mHtml = "<html><body>" + new Random().nextLong() + "</body></html>";
				dummy.targetHeight = 1;
				dummy.targetWidth = 1;
				sToProcess.add(0, dummy);
				lastWasSpacer = true;
		        mWebView.loadData(dummy.mHtml, "text/html", "UTF-8");
			} else {
				lastWasSpacer = false;
		        mWebView.loadData(item.mHtml, "text/html", "UTF-8");
			}
		}
	}

	static class WebRenderServiceConnection implements ServiceConnection {
		@Override
		public void onServiceDisconnected(ComponentName name) {
			sService = null;
		}
		
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			sService = ((WebRenderServiceBinder)service).getService();
			if(sToProcess.size() > 0) {
				sService.mWebViewClient.kickoffJob(sToProcess.peek());
			}
		}
	}
	static void bindAndSaveService(Context owner) {
		owner.bindService(new Intent(owner, WebRenderService.class), 
				new WebRenderServiceConnection(), BIND_AUTO_CREATE);
	}

	public static ImageView newLazyImageWeb(Context context, String html, int targetWidth, int targetHeight) {
		ImageView iv = new ImageView(context);
		//try the short cache
		if(sService != null)  {
			SoftReference<Bitmap> srb = sService.mWebViewCache.get(new TByteArrayList(Util.sha256(html.getBytes())));
			if(srb != null) {
				Bitmap b = srb.get();
				if(b != null) {
					sService.setImageViewBitmapAndLayout(iv, b);
					return iv;
				}
			}
		}
		//set a default?
		iv.setImageBitmap(Bitmap.createBitmap(1, 1, Config.RGB_565));
		WebRenderRequest req = new WebRenderRequest();
		req.targetHeight = targetHeight;
		req.targetWidth = targetWidth;
		req.mDestionationView = iv;
		req.mHtml = html;
		if(sService != null)  {
			sService.mWebViewClient.addItem(req);
		} else {
			sToProcess.add(req);
		}
		return iv;
	}

	public void measureAndLayout() {
		//fake pump the layout
		mWebView.measure(
				MeasureSpec.makeMeasureSpec(1, MeasureSpec.UNSPECIFIED),
				MeasureSpec.makeMeasureSpec(AppStateObj.MAX_HEIGHT, MeasureSpec.UNSPECIFIED));
		mWebView.layout(0, 0, 1, AppStateObj.MAX_HEIGHT);
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		mWebView = new WebView(this);
		//set the size before we start
		measureAndLayout();
		mWebViewClient = new RenderWebViewClient();
		mWebView.setWebViewClient(mWebViewClient);
	}
	
	@Override
	public void onDestroy() {
		mWebView.destroy();
		mWebView = null;
		mWebViewClient = null;
		super.onDestroy();
	}
	
	

    public class WebRenderServiceBinder extends Binder {
    	public WebRenderService getService() {
            return WebRenderService.this;
        }
    }
    private final IBinder mBinder = new WebRenderServiceBinder();
	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	private void setImageViewBitmapAndLayout(ImageView imageView, Bitmap bitmap) {
		float scaleFactor;
		if (getResources().getBoolean(R.bool.is_tablet)) {
		    scaleFactor = 3.0f;
		} else {
		    scaleFactor = 2.0f;
		}
		DisplayMetrics dm = getResources().getDisplayMetrics();
		int pixels = dm.widthPixels;
		if (dm.heightPixels < pixels) {
		    pixels = dm.heightPixels;
		}
		int width = (int)(pixels / scaleFactor);
		int height = (int)((float)width / bitmap.getWidth() * bitmap.getHeight());
		int max_height = (int)(AppStateObj.MAX_HEIGHT * dm.density);
		if(height > max_height) {
			width = width * max_height / height;
			height = max_height;
		}
		imageView.setLayoutParams(new LinearLayout.LayoutParams(width, height));
		imageView.setImageBitmap(bitmap);
	}
}
