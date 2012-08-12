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

package mobisocial.musubi;

import java.io.IOException;
import java.io.InputStream;

import mobisocial.musubi.model.MObject;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.model.helpers.ObjectManager;
import mobisocial.musubi.obj.action.EditPhotoAction;
import mobisocial.musubi.obj.action.SharePhotoAction;
import mobisocial.musubi.objects.PictureObj;
import mobisocial.musubi.ui.MusubiBaseActivity;
import mobisocial.musubi.util.CommonLayouts;
import mobisocial.musubi.util.InstrumentedActivity;
import mobisocial.musubi.util.PhotoTaker;
import mobisocial.musubi.util.SimpleCursorLoader;
import mobisocial.musubi.util.SlowGallery;
import mobisocial.socialkit.musubi.DbObj;
import mobisocial.socialkit.musubi.Musubi;

import org.json.JSONException;
import org.json.JSONObject;
import org.mobisocial.corral.CorralDownloadClient;
import org.mobisocial.corral.CorralDownloadHandler;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.support.v4.view.MenuItem;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.CursorAdapter;
import android.widget.Gallery;
import android.widget.ImageView;

/**
 * A gallery for viewing all photos in a feed.
 *
 */
//TODO: if someone deletes a picture from the feed while it is being shown, weird
//things happen
public class ImageGalleryActivity extends MusubiBaseActivity implements LoaderCallbacks<Cursor>,
        InstrumentedActivity, OnItemSelectedListener {
    public static final String EXTRA_DEFAULT_OBJECT_ID = "objectId";
    static final String EXTRA_SELECTION = "selection";
    private static final String TAG = "imageGallery";
    private static final boolean DBG = false;

	private Gallery mGallery;
	private ImageGalleryAdapter mAdapter;
	private Uri mFeedUri;
	private long mInitialObjId;
	private int mInitialSelection = -1;
	private CorralDownloadClient mCorralClient;
	private CorralHandler mCorralHandler;
	private Musubi mMusubi;
	int mScreenWidth;
	int mScreenHeight;
	long mCurrentObjId;
	SQLiteOpenHelper mDatabaseSource;
	ObjectManager mObjectManager;

    public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, 
                                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        DisplayMetrics dm = getResources().getDisplayMetrics();
        mScreenWidth = dm.widthPixels;
        mScreenHeight = dm.heightPixels;
        
        mCorralClient = CorralDownloadClient.getInstance(this);

        mFeedUri = getIntent().getData();
        mInitialObjId = getIntent().getLongExtra(EXTRA_DEFAULT_OBJECT_ID, -1);

        mGallery = new SlowGallery(this);
        mGallery.setBackgroundColor(Color.BLACK);
        mGallery.setOnItemSelectedListener(this);
        addContentView(mGallery, CommonLayouts.FULL_SCREEN);
        if (savedInstanceState != null) {
            mInitialSelection = savedInstanceState.getInt(EXTRA_SELECTION);
        }
        mMusubi = App.getMusubi(this);
        mDatabaseSource = App.getDatabaseSource(this);
        mObjectManager = new ObjectManager(mDatabaseSource);
        getSupportLoaderManager().initLoader(0, null, this);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(EXTRA_SELECTION, mGallery.getSelectedItemPosition());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (MusubiBaseActivity.isTVModeEnabled(this)) {
            mGallery.setSelection(0);
        }
    }

    // Cursor must be ordered ASC.
    // The sort order and search order are opposite!
    private static int binarySearch(Cursor c, long id, int colId) {
        long test;
        int first = 0;
        int max = c.getCount();
        while (first < max) {
            int mid = (first + max) / 2;
            c.moveToPosition(mid);
            test = c.getLong(colId);
            if (id < test) {
                max = mid;
            } else if (id > test) {
                first = mid + 1;
            } else {
                return mid;
            }
        }
        return 0;
    }

    @Override
    protected void onResume() {
        super.onResume();

        mCorralHandler = new CorralHandler();
        mCorralHandler.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        cleanCorralImage();
        shutdownCorralThread();
    }
    @Override
    protected void onDestroy() {
    	super.onDestroy();
    }

	private void shutdownCorralThread() {
		if(mCorralHandler != null) {
	        Message msg = mCorralHandler.obtainQuitMessage();
	        mCorralHandler.mHandler.sendMessage(msg);
	        mCorralHandler = null;
        }
	}

    private class ImageGalleryAdapter extends CursorAdapter {
        private final Context mContext;
        private final int mInitialSelection;
        private final int COL_ID;

        public int getInitialSelection() {
            return mInitialSelection;
        }

        private ImageGalleryAdapter(Context context, Cursor c, int init) {
            super(context, c);
            mContext = context;
            mInitialSelection = init;
            COL_ID = c.getColumnIndexOrThrow(MObject.COL_ID);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            DbObj obj = mMusubi.objForId(cursor.getLong(0));
            //shouldn't happen unless someone deletes a picture.
            if(obj == null)
            	return;
            ImageView im = (ImageView)view;
            im.setTag(cursor.getLong(COL_ID));

            byte[] bytes = obj.getRaw();
            if (bytes == null) {
                Log.e(TAG, "Null image bytes for " + im.getTag(), new Throwable());
                return;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPurgeable = true;
            options.inInputShareable = true;
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            im.setImageBitmap(bitmap);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            ImageView im = new ImageView(mContext);
            im.setLayoutParams(new Gallery.LayoutParams(
                    Gallery.LayoutParams.MATCH_PARENT,
                    Gallery.LayoutParams.MATCH_PARENT));
            im.setScaleType(ImageView.ScaleType.FIT_CENTER);
            im.setBackgroundColor(Color.BLACK);
            return im;
        }
    }

    private static final int MENU_EDIT = 3;
    private static final int MENU_SHARE = 4;
    private static final int MENU_SET_PROFILE = 5;    

    @Override
    public boolean onCreateOptionsMenu(android.support.v4.view.Menu menu) {
        menu.add(0, MENU_EDIT, 1, "Edit");
        menu.add(0, MENU_SHARE, 2, "Share");
        menu.add(0, MENU_SET_PROFILE, 3, "Set as Profile"); // XXX Bug prevents last menu entry
        // from showing up on phones without a hardware menu button.
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_SET_PROFILE: {
                new Thread() {
                    public void run() {
                        long objId = (Long)mGallery.getSelectedView().getTag();
                        DbObj obj = mMusubi.objForId(objId);
                        byte[] picBytes = obj.getRaw();
                        IdentitiesManager idMan = new IdentitiesManager(
                                App.getDatabaseSource(ImageGalleryActivity.this));
                        idMan.updateMyProfileThumbnail(ImageGalleryActivity.this, picBytes, true);
                        toast("Set profile picture.");
                    };
                }.start(); 
                return true;
            }
            case MENU_SHARE: {
                long objId = (Long)mGallery.getSelectedView().getTag();
                DbObj obj = mMusubi.objForId(objId);
                new SharePhotoAction().actOn(this, new PictureObj(), obj);
                return true;
            }
            case MENU_EDIT: {
                long objId = (Long)mGallery.getSelectedView().getTag();
                DbObj obj = mMusubi.objForId(objId);
                doActivityForResult(new EditPhotoAction.EditCallout(this, obj));
                return true;
            }
            default:
                return false;
        }
    }

    Uri loadFromCorral(CorralHandler handler, final DbObj obj) {
    	if (MusubiBaseActivity.isDeveloperModeEnabled(this)) {
	        try {
	            // Fetch via remote
	            return CorralDownloadHandler.startOrFetchDownload(this, obj).getResult();
	        } catch (InterruptedException e) {
	            Log.i(TAG, "Failed to get hd content", e);
	        }
    	}
    	// Local-only
        return mCorralClient.getAvailableContentUri(obj);
    }

	private void cleanCorralImage() {
		if(mCorralImage != null && mCorralImage.getBitmap() != null) {
			mCorralView.setImageDrawable(mOriginalImage);
			mCorralImage.getBitmap().recycle();
			mCorralImage = null;
			mOriginalImage = null;
			mCorralView = null;
		}
	}
    void injectImage(Uri fileUri, final ImageView imageView, final DbObj mObj) {
        try {
            if ((Long)imageView.getTag() == mObj.getLocalId()) {
                if (DBG) Log.d(TAG, "Opening HD file " + fileUri);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if ((Long)imageView.getTag() == mObj.getLocalId()) {
                            cleanCorralImage();
                        }
                    }
                });

                final BitmapDrawable image = getBitmap(fileUri);
                if(image == null) {
                	return;
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                    	if ((Long)imageView.getTag() == mObj.getLocalId()) {
                			mCorralImage = image;
                    		mOriginalImage = (BitmapDrawable)imageView.getDrawable();
                    		mCorralView = imageView;
                            imageView.setImageDrawable(image);
                    	}
                    }
                });
            }
        } catch (OutOfMemoryError e) {
            App.getUsageMetrics(this).report(e);
            Log.e(TAG, "error loading data from corral", e);
        }
    }
    class RotatedBitmapDrawable extends BitmapDrawable {
    	float mRotation;
    	public RotatedBitmapDrawable(Bitmap b, float rotation) {
    		super(b);
    		mRotation = rotation;
		}
    	
    	@Override
    	public int getIntrinsicHeight() {
            if(mRotation > 89 && mRotation < 91 || mRotation > 269 && mRotation < 271) {
            	return super.getIntrinsicWidth();
            } else {
            	return super.getIntrinsicHeight();
            }
    	}
    	@Override
    	public int getIntrinsicWidth() {
            if(mRotation > 89 && mRotation < 91 || mRotation > 269 && mRotation < 271) {
            	return super.getIntrinsicHeight();
            } else {
            	return super.getIntrinsicWidth();
            }
    	}
    	@Override
    	public void draw(Canvas canvas) {
            int saveCount = canvas.save();

            Rect bounds = super.getBounds();

            canvas.rotate(mRotation, bounds.centerX(), bounds.centerY());
            if(mRotation > 89 && mRotation < 91 || mRotation > 269 && mRotation < 271) {
            	canvas.scale((float)getIntrinsicHeight() / getIntrinsicWidth(), (float)getIntrinsicWidth() / getIntrinsicHeight(), bounds.centerX(), bounds.centerY());
            }

    		super.draw(canvas);

            canvas.restoreToCount(saveCount);
    	}
    }
    //get rotated bitmap allowing the data to be shared
    BitmapDrawable getBitmap(Uri uri) {
        InputStream in = null;
        try {
            in = getContentResolver().openInputStream(uri);

            // Decode image size
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, o);
            in.close();

            in = getContentResolver().openInputStream(uri);

            float rotation = PhotoTaker.rotationForImage(this, uri);

            
            DisplayMetrics dm = getResources().getDisplayMetrics();
            int screen_width = dm.widthPixels;
            int screen_height = dm.heightPixels;
            int scale = 1;
            if(rotation > 89 && rotation < 91 || rotation > 269 && rotation < 271) {
            	int t = screen_width;
            	screen_width = screen_height;
            	screen_height = t;
            }
            while (o.outWidth / (scale + 1) >= screen_width && o.outHeight / (scale + 1) >= screen_height) {
                scale++;
            }

            o = new BitmapFactory.Options();
            o.inPurgeable = true;
            o.inInputShareable = true;
            o.inSampleSize = scale;
            Bitmap b = BitmapFactory.decodeStream(in, null, o);
            return new RotatedBitmapDrawable(b, rotation);
        } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new SimpleCursorLoader(this) {
			
			@Override
			public Cursor loadInBackground() {
				long feedId = Long.parseLong(mFeedUri.getLastPathSegment());
				return mObjectManager.getTypedIdCursorForFeed(PictureObj.TYPE, feedId);
			}
		};
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        if (mAdapter == null) {
            int init = binarySearch(cursor, mInitialObjId, 0);
        	cursor.moveToPosition(-1);
            mAdapter = new ImageGalleryAdapter(this, cursor, init);
            mGallery.setAdapter(mAdapter);
            mGallery.setSelection((mInitialSelection == -1)
                    ? mAdapter.getInitialSelection() : mInitialSelection);
        } else {
            mAdapter.changeCursor(cursor);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> arg0) {
    }

    BitmapDrawable mCorralImage;
    BitmapDrawable mOriginalImage;
    ImageView mCorralView;
    
    @Override
    public void onItemSelected(AdapterView<?> adapter, View view, int position, long id) {
    	if (view.getTag() == null) {
    		return;
    	}
        mCurrentObjId = (Long)view.getTag();
        DbObj obj = mMusubi.objForId(mCurrentObjId);
        //should happen unless someone deletes an obj from the feed.
        if(obj == null) {
        	return;
        }
        if (obj.getJson() != null) {
        	//this can get called after onPause so we have to check that there is a corral handler still
            if (obj.getJson().has(CorralDownloadClient.OBJ_LOCAL_URI) && mCorralHandler != null) {
                Message msg = mCorralHandler.obtainLoadImageMessage();
                msg.obj = new CorralArg(mCurrentObjId, (ImageView)view);
                mCorralHandler.mHandler.sendMessage(msg);
            }
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> arg0) {
    }

    class CorralHandler extends Thread {
        final int LOAD_IMAGE = 0;
        final int QUIT = 1;
        public Handler mHandler;
        boolean mLoaded = false;
        boolean mQuit = false;

        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_LOWEST);
            Looper.prepare();

            mHandler = new Handler() {
                public void handleMessage(Message msg) {
                	if(hasMessages(QUIT)) {
                		//don't handle other buffered loads if possible
                		mQuit = true;
                		getLooper().quit();
                		return;
                	}
                    switch (msg.what) {
                        case LOAD_IMAGE:
                            CorralArg arg = (CorralArg)msg.obj;
                            if (arg.objId != mCurrentObjId) {
                                return;
                            }
                            DbObj obj = mMusubi.objForId(arg.objId);
                            if (obj == null) {
                                Log.w(TAG, "null object: " + arg.objId);
                                return;
                            }
                            Uri fileUri = loadFromCorral(CorralHandler.this, obj);
                            if(fileUri == null)
                            	break;
                            if(hasMessages(LOAD_IMAGE) || hasMessages(QUIT))
                            	break;
                            injectImage(fileUri, arg.imageView, obj);
                            arg.imageView = null;
                            arg.objId = -1;
                            break;
                        case QUIT:
                            mQuit = true;
                            getLooper().quit();
                            break;
                    }
                    
                }
            };
            mLoaded = true;
            Looper.loop();
        }

        public Message obtainQuitMessage() {
            prepareHandler();
            Message msg = mHandler.obtainMessage();
            msg.what = QUIT;
            return msg;
        }

        public Message obtainLoadImageMessage() {
            prepareHandler();
            Message msg = mHandler.obtainMessage();
            msg.what = LOAD_IMAGE;
            return msg;
        }

        void prepareHandler() {
            while (!mLoaded) {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {}
            }
        }
    }

    class CorralArg {
        long objId;
        ImageView imageView;

        public CorralArg(long objId, ImageView image) {
            this.objId = objId;
            this.imageView = image;
        }
    }
}