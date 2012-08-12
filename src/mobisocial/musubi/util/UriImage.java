/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mobisocial.musubi.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.provider.MediaStore.Images;
import android.text.TextUtils;
import android.util.Config;
import android.util.Log;
import android.webkit.MimeTypeMap;

public class UriImage {
    /**
     * The quality parameter which is used to compress JPEG images.
     */
    public static final int IMAGE_COMPRESSION_QUALITY = 80;
    /**
     * The minimum quality parameter which is used to compress JPEG images.
     */
    public static final int MINIMUM_IMAGE_COMPRESSION_QUALITY = 50;

    private static final String TAG = "Mms/image";
    private static final boolean DEBUG = true;
    private static final boolean LOCAL_LOGV = DEBUG ? Config.LOGD : Config.LOGV;

    private final Context mContext;
    private final Uri mUri;
    private String mContentType;
    private String mPath;
    private String mSrc;
    private int mWidth;
    private int mHeight;
    private float mRotation;
    private byte[] mByteCache;
    private boolean mDecodedBounds = false;

    public UriImage(Context context, Uri uri) {
        if ((null == context) || (null == uri)) {
            throw new IllegalArgumentException();
        }

        mRotation = PhotoTaker.rotationForImage(context, uri);
        String scheme = uri.getScheme();
        if (scheme.equals("content")) {
            try {
                initFromContentUri(context, uri);
            } catch (Exception e) {
                Log.w(TAG, "last-ditch image params");
                mPath = uri.getPath();
                mContentType = context.getContentResolver().getType(uri);
            }
        } else if (uri.getScheme().equals("file")) {
            initFromFile(context, uri);
        } else {
            mPath = uri.getPath();
        }

        mSrc = mPath.substring(mPath.lastIndexOf('/') + 1);

        if(mSrc.startsWith(".") && mSrc.length() > 1) {
            mSrc = mSrc.substring(1);
        }

        // Some MMSCs appear to have problems with filenames
        // containing a space.  So just replace them with
        // underscores in the name, which is typically not
        // visible to the user anyway.
        mSrc = mSrc.replace(' ', '_');

        mContext = context;
        mUri = uri;
    }

    private void initFromFile(Context context, Uri uri) {
        mPath = uri.getPath();
        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
        String extension = MimeTypeMap.getFileExtensionFromUrl(mPath);
        if (TextUtils.isEmpty(extension)) {
            // getMimeTypeFromExtension() doesn't handle spaces in filenames nor can it handle
            // urlEncoded strings. Let's try one last time at finding the extension.
            int dotPos = mPath.lastIndexOf('.');
            if (0 <= dotPos) {
                extension = mPath.substring(dotPos + 1);
            }
        }
        mContentType = mimeTypeMap.getMimeTypeFromExtension(extension);
        // It's ok if mContentType is null. Eventually we'll show a toast telling the
        // user the picture couldn't be attached.
    }

    private void initFromContentUri(Context context, Uri uri) {
        Cursor c = context.getContentResolver().query(uri, null, null, null, null);

        if (c == null) {
            throw new IllegalArgumentException(
                    "Query on " + uri + " returns null result.");
        }

        try {
            if ((c.getCount() != 1) || !c.moveToFirst()) {
                throw new IllegalArgumentException(
                        "Query on " + uri + " returns 0 or multiple rows.");
            }

            String filePath = c.getString(c.getColumnIndexOrThrow(Images.Media.DATA));
            mContentType = c.getString(c.getColumnIndexOrThrow(Images.Media.MIME_TYPE));
            mPath = filePath;
        } finally {
            c.close();
        }
    }

    private void decodeBoundsInfo() throws IOException {
        InputStream input = null;
        try {
            input = openInputStream(mUri);
            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(input, null, opt);
            mWidth = opt.outWidth;
            mHeight = opt.outHeight;
        } catch (FileNotFoundException e) {
            // Ignore
            Log.e(TAG, "IOException caught while opening stream", e);
        } finally {
            if (null != input) {
                try {
                    input.close();
                } catch (IOException e) {
                    // Ignore
                    Log.e(TAG, "IOException caught while closing stream", e);
                }
            }
        }
    }

    public String getContentType() {
        return mContentType;
    }

    public String getSrc() {
        return mSrc;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }
   
    private static final int NUMBER_OF_RESIZE_ATTEMPTS = 4;

    public byte[] getResizedImageData(int widthLimit, int heightLimit, int byteLimit) throws IOException {
        return getResizedImageData(widthLimit, heightLimit, byteLimit, false);
    }

    /**
     * Returns the bytes for this UriImage. If the uri for the image is remote,
     * then this code must not be run on the main thread.
     */
    public byte[] getResizedImageData(int widthLimit, int heightLimit, int byteLimit, boolean square) throws IOException {
        if (!mDecodedBounds) {
            decodeBoundsInfo();
            mDecodedBounds = true;
        }
        InputStream input = null;
        try {
	        int inDensity = 0;
	        int targetDensity = 0;
	        BitmapFactory.Options read_options = new BitmapFactory.Options();
	        read_options.inJustDecodeBounds = true;
	        input = openInputStream(mUri);
	        BitmapFactory.decodeStream(input, null, read_options);
	        if (read_options.outWidth > widthLimit || read_options.outHeight > heightLimit) {
	        	//we need to scale
	        	if(read_options.outWidth / widthLimit > read_options.outHeight / heightLimit) {
	        		//width is the large edge
	            	if(read_options.outWidth * heightLimit > widthLimit * read_options.outHeight) {
	            		//incoming image is wider than target
	            		inDensity = read_options.outWidth;
	            		targetDensity = widthLimit;
	            	} else {
	            		//incoming image is taller than target
	            		inDensity = read_options.outHeight;
	            		targetDensity = heightLimit;
	            		
	            	}
	        	} else {
	        		//height is the long edge, swap the limits
	            	if(read_options.outWidth * widthLimit > heightLimit * read_options.outHeight) {
	            		//incoming image is wider than target
	            		inDensity = read_options.outWidth;
	            		targetDensity = heightLimit;
	            	} else {
	            		//incoming image is taller than target
	            		inDensity = read_options.outHeight;
	            		targetDensity = widthLimit;
	            		
	            	}
	        	}
	        } else {
	        	//no scale
	        	if(read_options.outWidth > read_options.outHeight) {
	        		inDensity = targetDensity = read_options.outWidth;
	        	} else {
	        		inDensity = targetDensity = read_options.outHeight;
	        	}
	        }
	
        	if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "getResizedImageData: wlimit=" + widthLimit +
                    ", hlimit=" + heightLimit + ", sizeLimit=" + byteLimit +
                    ", mWidth=" + mWidth + ", mHeight=" + mHeight +
                    ", initialRatio=" + targetDensity + "/" + inDensity);
        	}

            ByteArrayOutputStream os = null;
            int attempts = 1;

            int lowMemoryReduce = 1;
            do {
                BitmapFactory.Options options = new BitmapFactory.Options();
            	options.inDensity = inDensity;
            	options.inSampleSize = lowMemoryReduce;
            	options.inScaled = lowMemoryReduce == 1;
            	options.inTargetDensity = targetDensity;
            	//no purgeable because we are only trying to resave this
            	if(input != null)
            		input.close();
                input = openInputStream(mUri);
                int quality = IMAGE_COMPRESSION_QUALITY;
                try {
                    Bitmap b = BitmapFactory.decodeStream(input, null, options);
                    if (b == null) {
                        return null;
                    }
                    if (options.outWidth > widthLimit+1 || options.outHeight > heightLimit+1) {
                        // The decoder does not support the inSampleSize option.
                        // Scale the bitmap using Bitmap library.
                        int scaledWidth;
                        int scaledHeight;
                    	scaledWidth = options.outWidth * targetDensity / inDensity;
                    	scaledHeight = options.outHeight * targetDensity / inDensity; 

                        if (Log.isLoggable(TAG, Log.VERBOSE)) {
                            Log.v(TAG, "getResizedImageData: retry scaling using " +
                                    "Bitmap.createScaledBitmap: w=" + scaledWidth +
                                    ", h=" + scaledHeight);
                        }

                        if (square) {
                        	int w = b.getWidth();
                        	int h = b.getHeight();
                            int dim = Math.min(w, h);
                            b = Bitmap.createBitmap(b, (w - dim) / 2, (h - dim) / 2, dim, dim);
                            scaledWidth = dim;
                            scaledHeight = dim;
                        }
                        Bitmap b2 = Bitmap.createScaledBitmap(b, scaledWidth,
                                scaledHeight, false);
                        b.recycle();
                        b = b2;
                        if (b == null) {
                            return null;
                        }
                    }

                    Matrix matrix = new Matrix();  
                    if (mRotation != 0f) {
                        matrix.preRotate(mRotation);     
                    }

                    Bitmap old = b;
                    b = Bitmap.createBitmap(old, 0, 0, old.getWidth(), old.getHeight(), matrix, true);
                    
                    // Compress the image into a JPG. Start with MessageUtils.IMAGE_COMPRESSION_QUALITY.
                    // In case that the image byte size is still too large reduce the quality in
                    // proportion to the desired byte size. Should the quality fall below
                    // MINIMUM_IMAGE_COMPRESSION_QUALITY skip a compression attempt and we will enter
                    // the next round with a smaller image to start with.
                    os = new ByteArrayOutputStream();
                    b.compress(CompressFormat.JPEG, quality, os);
                    int jpgFileSize = os.size();
                    if (jpgFileSize > byteLimit) {
                        int reducedQuality = quality * byteLimit / jpgFileSize;
                        //always try to squish it before computing the new size
                        if (reducedQuality < MINIMUM_IMAGE_COMPRESSION_QUALITY) {
                        	reducedQuality = MINIMUM_IMAGE_COMPRESSION_QUALITY;
                        }
                        quality = reducedQuality;

                        if (Log.isLoggable(TAG, Log.VERBOSE)) {
                            Log.v(TAG, "getResizedImageData: compress(2) w/ quality=" + quality);
                        }

                        os = new ByteArrayOutputStream();
                        b.compress(CompressFormat.JPEG, quality, os);
                    }
                    b.recycle();        // done with the bitmap, release the memory
                } catch (java.lang.OutOfMemoryError e) {
                    Log.w(TAG, "getResizedImageData - image too big (OutOfMemoryError), will try "
                            + " with smaller scale factor, cur scale factor", e);
                    lowMemoryReduce *= 2;
                    // fall through and keep trying with a smaller scale factor.
                }
                if (true || Log.isLoggable(TAG, Log.VERBOSE)) {
                    Log.v(TAG, "attempt=" + attempts
                            + " size=" + (os == null ? 0 : os.size())
                            + " width=" + options.outWidth
                            + " height=" + options.outHeight
                            + " Ratio=" + targetDensity + "/" + inDensity
                            + " quality=" + quality);
                }
                //move halfway to the target
            	targetDensity = (os == null) ? (int) (targetDensity * .8) : (targetDensity * byteLimit / os.size() + targetDensity) / 2;
                attempts++;
            } while ((os == null || os.size() > byteLimit) && attempts < NUMBER_OF_RESIZE_ATTEMPTS);

            return os == null ? null : os.toByteArray();
        } catch (Throwable t) {
            Log.e(TAG, t.getMessage(), t);
            return null;
		} finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
        }
    }

    private InputStream openInputStream(Uri uri) throws IOException {
        String scheme = uri.getScheme();
        if ("content".equals(scheme)) {
            return mContext.getContentResolver().openInputStream(mUri);
        } else if (scheme.startsWith("http")) {
            if (mByteCache == null) {
                DefaultHttpClient c = new DefaultHttpClient();
                HttpGet get = new HttpGet(uri.toString());
                HttpResponse response = c.execute(get);
                mByteCache = IOUtils.toByteArray(response.getEntity().getContent());    
            }
            return new ByteArrayInputStream(mByteCache);
        } else if (scheme.equals("file")) {
            return new FileInputStream(uri.getPath());
        } else {
            throw new IOException("Unmatched uri scheme " + scheme);
        }
    }
}
