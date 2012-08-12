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

package mobisocial.musubi.feed.action;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import mobisocial.musubi.App;
import mobisocial.musubi.Helpers;
import mobisocial.musubi.R;
import mobisocial.musubi.feed.iface.FeedAction;
import mobisocial.musubi.model.DbRelation;
import mobisocial.musubi.model.MApp;
import mobisocial.musubi.model.helpers.AppManager;
import mobisocial.musubi.obj.ObjHelpers;
import mobisocial.musubi.obj.action.EditPhotoAction.EditCallout;
import mobisocial.musubi.objects.AppObj;
import mobisocial.musubi.objects.PictureObj;
import mobisocial.musubi.objects.VideoObj;
import mobisocial.musubi.service.WizardStepHandler;
import mobisocial.musubi.ui.fragments.AppSelectDialog;
import mobisocial.musubi.ui.fragments.AppSelectDialog.MusubiWebApp;
import mobisocial.musubi.ui.util.IntentProxyActivity;
import mobisocial.musubi.util.ActivityCallout;
import mobisocial.musubi.util.InstrumentedActivity;
import mobisocial.musubi.util.PhotoTaker;
import mobisocial.musubi.util.UriImage;
import mobisocial.socialkit.Obj;
import mobisocial.socialkit.musubi.DbObj;
import mobisocial.socialkit.musubi.Musubi;
import mobisocial.socialkit.obj.MemObj;

import org.json.JSONException;
import org.json.JSONObject;
import org.mobisocial.corral.ContentCorral;
import org.mobisocial.corral.CorralDownloadClient;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.LabeledIntent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

/**
 * Captures an image to share with a feed.
 *
 */
public class CameraAction extends FeedAction {
    private static final String TAG = "CameraAction";

    public static final String CATEGORY_IN_PLACE = "mobisocial.intent.category.IN_PLACE";
    static final String PICSAY_PACKAGE_PREFIX = "com.shinycore.picsay";
    private static final int REQUEST_CAPTURE_MEDIA = 9412;
    private Uri mFeedUri;
    private Uri mImageUri;
    private String mType;

    static final String ACTION_MEDIA_CAPTURE = "mobisocial.intent.action.MEDIA_CAPTURE";

    @Override
    public String getName() {
        return "Camera";
    }

    @Override
    public Drawable getIcon(Context c) {
        return c.getResources().getDrawable(R.drawable.ic_attach_capture_picture_holo_light);
    }

    @Override
    public void onClick(final Context context, final Uri feedUri) {
        mFeedUri = feedUri;
        mImageUri = Uri.fromFile(PhotoTaker.getTempFile(getActivity()));
        mType = "image/jpeg";
        Intent intent = new Intent(ACTION_MEDIA_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, mImageUri);
        intent.putExtra(Musubi.EXTRA_FEED_URI, feedUri);

        try {
            startActivityForResult(intent, REQUEST_CAPTURE_MEDIA);
        } catch (ActivityNotFoundException e) {
            intent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
            startActivityForResult(intent, REQUEST_CAPTURE_MEDIA);
        }
    }

    @Override
    public boolean isActive(Context c) {
        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, final Intent data) {
        if (requestCode == REQUEST_CAPTURE_MEDIA) {
            if (resultCode != Activity.RESULT_OK) {
                return;
            }

            new CameraCaptureTask(null).execute();
            // needs UI overhaul for production.
            /*
            final Context context = getActivity();
            final Dialog dialog = new Dialog(context, R.style.Dialog_Fullscreen);
            dialog.setContentView(R.layout.image_comment);
            dialog.setCancelable(true);

            final EditText editText = (EditText) dialog.findViewById(R.id.image_comment);

    		ImageView image = (ImageView) dialog.findViewById(R.id.image);
    		image.setImageResource(R.drawable.image_preview);
            new LoadImageTask(dialog).execute();
            ((Button)dialog.findViewById(R.id.image_comment_send)).setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    new CameraCaptureTask(editText.getText().toString()).execute();
                    dialog.dismiss();
                }
            });
            ((Button)dialog.findViewById(R.id.image_comment_cancel)).setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });
            ((Button)dialog.findViewById(R.id.image_comment_edit)).setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                	((InstrumentedActivity)context).doActivityForResult(
                            new EditCallout((Activity)context, mImageUri, mFeedUri, dialog));
                }
            });
            dialog.show();
            */
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState != null) {
            mFeedUri = savedInstanceState.getParcelable("feed");
            mImageUri = savedInstanceState.getParcelable("image");
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putParcelable("feed", mFeedUri);
        outState.putParcelable("image", mImageUri);
    }

    class LoadImageTask extends AsyncTask<Void, Void, Bitmap> {
    	ImageView mImage;
    	Dialog mDialog;
    	
    	public LoadImageTask(Dialog dialog) {
    		mDialog = dialog;
    		mImage = (ImageView) dialog.findViewById(R.id.image);
    	}
    	
		@Override
		protected Bitmap doInBackground(Void... params) {
			UriImage image = new UriImage(getActivity(), mImageUri);
            try {
	            byte[] imageData = image.getResizedImageData(PictureObj.MAX_IMAGE_WIDTH, PictureObj.MAX_IMAGE_HEIGHT, PictureObj.MAX_IMAGE_SIZE);   
	            return BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
            }
            catch (Exception e) {
            	return null;
            }
		}
		
		@Override
		protected void onPostExecute(Bitmap result) {
			mImage.setImageBitmap(result);
		}
    	
    }
    
    class CameraCaptureTask extends AsyncTask<Void, Void, Boolean> {
        Throwable mError;
        Obj mObj;
        String mComment;

        public CameraCaptureTask(String comment) {
            mComment = comment;
            mObj = null;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            
            Uri storedUri = ContentCorral.storeContent(getActivity(), mImageUri, mType);
            try {
                mObj = PictureObj.from(getActivity(), storedUri, true, mComment);
            } catch (IOException e) {
                Log.e(TAG, "Corral photo action had an issue", e);
                try {
                    mObj = PictureObj.from(getActivity(), mImageUri, true, mComment);
                } catch(Throwable t) {
                    Log.e(TAG, "fallback photo action had an issue", t);
                }
            }

            if (mObj == null) {
                return false;
            }

            Helpers.sendToFeed(getActivity(), mObj, mFeedUri);
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (result) {
                Helpers.emailUnclaimedMembers(getActivity(), mObj, mFeedUri);
                WizardStepHandler.accomplishTask(getActivity(), WizardStepHandler.TASK_TAKE_PICTURE);
            } else {
                Toast.makeText(getActivity(), "Failed to capture media.",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    static File getTempImagePath() {
        return new File(Environment.getExternalStorageDirectory().getAbsolutePath() +
                "/musubi_edit.png");
    }
    
    public static class EditCallout implements ActivityCallout {
        final Activity mContext;
        final Uri mImageUri;
        final Uri mFeedUri;
        final Dialog mDialog;

        public EditCallout(Activity context, Uri imageUri, Uri feedUri, Dialog dialog) {
        	mImageUri = imageUri;
            mContext = context;
            mFeedUri = feedUri;
            mDialog = dialog;
        }
        @Override
        public Intent getStartIntent() {
        	Uri contentUri;
        	File file;
        	
        	OutputStream out = null;
            try {
                file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() +
                        "/musubi_edit.png");
                out = new FileOutputStream(file);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPurgeable = true;
                options.inInputShareable = true;
                UriImage image = new UriImage(mContext, mImageUri);
                byte[] imageData = image.getResizedImageData(image.getWidth(), image.getHeight(), PictureObj.MAX_IMAGE_SIZE);   
	            Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
                if(bitmap == null)
                	return null;
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                out.flush();
                out.close();

                bitmap.recycle();
                bitmap = null;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            } finally {
            	try {
					if(out != null) out.close();
				} catch (IOException e) {
					Log.e(TAG, "failed to close output stream for picture", e);
				}
            }
            contentUri = Uri.fromFile(file);
            Log.w(TAG, "uri=" + contentUri);
            return getEditorChooserIntent(mContext, contentUri, "image/png", mFeedUri);
        }
        
        class LoadImageTask extends AsyncTask<Void, Void, Bitmap> {
        	ImageView mImage;
        	Dialog mDialog;
        	Uri imageUri;
        	
        	public LoadImageTask(Dialog dialog, Uri image) {
        		Log.w(TAG, "new load image task");
        		mDialog = dialog;
        		mImage = (ImageView) dialog.findViewById(R.id.image);
        		imageUri = image;
        	}
        	
    		@Override
    		protected Bitmap doInBackground(Void... params) {
    			UriImage image = new UriImage(mContext, imageUri);
    			Log.w(TAG, "new uri = " + imageUri);
                try {
    	            byte[] imageData = image.getResizedImageData(PictureObj.MAX_IMAGE_WIDTH, PictureObj.MAX_IMAGE_HEIGHT, PictureObj.MAX_IMAGE_SIZE);   
    	            Log.w(TAG, "returning bitmap");
    	            return BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
                }
                catch (Exception e) {
                	Log.w(TAG, "returning null");
                	return null;
                }
    		}
    		
    		@Override
    		protected void onPostExecute(Bitmap result) {
    			Log.w(TAG, "setting bitmap");
    			mImage.setImageResource(R.drawable.image_preview);
    			mImage.setImageBitmap(null);
    			//mImage.setImageBitmap(result);
    		}
        	
        }

        @Override
        public void handleResult(int resultCode, final Intent data) {
            if (resultCode == Activity.RESULT_OK) {
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            Uri result = Uri.fromFile(getTempImagePath());

                            Log.w(TAG, "result = "  + result);
                            /*boolean reference = true;
                            Uri stored = ContentCorral.storeContent(mContext, result);
                            if (stored == null) {
                                Log.w(TAG, "Error storing content in corral");
                                stored = result;
                                reference = false;
                            } else {
                                new File(result.getPath()).delete();
                            }
*/
                        	new LoadImageTask(mDialog, result).execute();
                            /*
                            MemObj outboundObj = PictureObj.from(mContext, stored, reference);
                            try {
                                JSONObject json = outboundObj.getJson();
                                json.put(ObjHelpers.TARGET_HASH, mHash);
                                json.put(ObjHelpers.TARGET_RELATION, DbRelation.RELATION_EDIT);

                                json.put(AppObj.ANDROID_PACKAGE_NAME, cn.getPackageName());
                                json.put(AppObj.CLAIMED_APP_ID, cn.getPackageName());
                                try {
                                    ActivityInfo info = mContext.getPackageManager()
                                            .getActivityInfo(cn, 0);
                                    json.put(AppObj.APP_NAME, info.loadLabel(
                                            mContext.getPackageManager()));
                                } catch (NameNotFoundException e) {
                                }
                            } catch (JSONException e) {}

                            Helpers.sendToFeed(mContext, outboundObj, mFeedUri);     */
                        	Log.w(TAG, "picture edited");
                        } catch (Exception e) {
                            Log.e(TAG, "Error reading photo data.", e);
                            toast("Error reading photo data.");
                        }
                    }
                }.start();
            }
        }

        private final void toast(final String text) {
            mContext.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(mContext, text, Toast.LENGTH_SHORT).show();
                }
            });
        }

        /**
         * Returns a chooser intent for editing a photo with results. Includes apps
         * that support the RETURN_RESULT category as well as PicSay, which is known
         * to return a result.
         */
        Intent getEditorChooserIntent(Context context, Uri contentUri, String mimeType, Uri feedUri) {
            List<Intent> targetedShareIntents = new ArrayList<Intent>();
            Intent editIntent = new Intent(android.content.Intent.ACTION_EDIT);
            editIntent.addCategory(CATEGORY_IN_PLACE);
            editIntent.setDataAndType(contentUri, mimeType);
            String title = "Edit with...";
            Intent picsayIntent = getPicsayIntent(context, contentUri, mimeType);

            //targetedShareIntents.addAll(getBundledEditorIntents(context, mObjUri, feedUri));
            assert(targetedShareIntents.size() != 0);

            List<ResolveInfo> resInfo = context.getPackageManager().queryIntentActivities(
                    editIntent, PackageManager.MATCH_DEFAULT_ONLY);
            if (!resInfo.isEmpty()){
                for (ResolveInfo resolveInfo : resInfo) {
                    String packageName = resolveInfo.activityInfo.packageName;
                    Intent targetedShareIntent = new Intent(Intent.ACTION_EDIT);
                    targetedShareIntent.setDataAndType(contentUri, mimeType);
                    targetedShareIntent.putExtra(Musubi.EXTRA_FEED_URI, feedUri);
                    targetedShareIntent.addCategory(CATEGORY_IN_PLACE);
    
                    targetedShareIntent.setPackage(packageName);
                    targetedShareIntents.add(targetedShareIntent);
                }

                if (picsayIntent != null) {
                    targetedShareIntents.add(picsayIntent);
                }
            } else {
                if (picsayIntent != null) {
                    targetedShareIntents.add(picsayIntent);
                } else {
                    LabeledIntent picsayMarket = new LabeledIntent(context.getPackageName(), "PicSay", R.drawable.picsay_icon);
                    picsayMarket.setAction(Intent.ACTION_VIEW);
                    picsayMarket.setData(Uri.parse("market://details?id=com.shinycore.picsayfree"));
                    targetedShareIntents.add(picsayMarket);
                }
            }
            //targetedShareIntents.addAll(getWebEditorIntents(context, mObjUri, feedUri));

            // XXX First intent must not be a LabeledIntent.
            // See getBundledEditorIntents().
            // Sketch doens't have to be proxied because it doesn't return a result.
            Intent first = targetedShareIntents.remove(0);
            Intent[] later = new Intent[targetedShareIntents.size()];
            int i = 0;
            for (Intent intent : targetedShareIntents) {
                later[i++] = IntentProxyActivity.getProxyIntent(context, intent);
            }

            Intent chooserIntent = Intent.createChooser(first, title);
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, later);
            return chooserIntent;
        }

        Intent getPicsayIntent(Context context, Uri contentUri, String mimeType) {
            Intent edit = new Intent(Intent.ACTION_EDIT);
            edit.setDataAndType(contentUri, mimeType);
            List<ResolveInfo> resInfo = context.getPackageManager().queryIntentActivities(edit, PackageManager.MATCH_DEFAULT_ONLY);
            for (ResolveInfo i : resInfo) {
                if (i.activityInfo.packageName.startsWith(PICSAY_PACKAGE_PREFIX)) {
                    Intent picsay = new Intent();
                    picsay.setAction(Intent.ACTION_EDIT);
                    picsay.setPackage(i.activityInfo.packageName);
                    picsay.setDataAndType(contentUri, mimeType);
                    return picsay;
                }
            }
            return null;
        }

        /**
         * XXX This is largely a workaround for a nasty bug somewhere between here
         * and Android's ResolverActivity. You cannot send a LabeledIntent as the
         * first intent in Intent.createChooser(), or bad things happen. Here we
         * force the first intent to be an Intent serviced by our application.
         */
        Set<Intent> getBundledEditorIntents(Context context, Uri objUri, Uri feedUri) {
            Set<Intent> editors = new HashSet<Intent>();

            // Sketch
            MApp sketch = new AppManager(App.getDatabaseSource(context))
                .lookupAppByAppId(AppSelectDialog.SKETCH_APP_ID);
            if (sketch != null) {
                MusubiWebApp app = new MusubiWebApp(context, sketch.name_, sketch.appId_,
                        sketch.webAppUrl_, R.drawable.sketch);
                Intent intent = app.getLaunchIntent(context, feedUri);

                Intent wrapper = new Intent("musubi.intent.action.SKETCH");
                wrapper.setData(objUri);
                wrapper.putExtras(intent.getExtras());
                editors.add(wrapper);
            }

            return editors;
        }

        /**
         * This is a poor approximation of an edit intent over a SocialDB object.
         * The generalization is an app that supports the EDIT intent for objects
         * of type "picture" (ObjHelper.mimeTypeOf("picture") == "vnd.musubi.obj/picture")
         */
        Set<Intent> getWebEditorIntents(Context context, Uri objUri, Uri feedUri) {
           Set<Intent> editors = new HashSet<Intent>();

           List<MApp> apps = new AppManager(App.getDatabaseSource(context))
           		.lookupAppForAction(PictureObj.TYPE, "edit");
           
	       for(MApp app : apps) {
	           MusubiWebApp web_app = new MusubiWebApp(context, app.name_, app.appId_,
	                   app.webAppUrl_, R.drawable.ic_menu_globe);
	           Intent intent = web_app.getLaunchIntent(context, feedUri);
	           intent.setData(objUri);
	           editors.add(intent);
        	}
           return editors;
        }
    }
}
