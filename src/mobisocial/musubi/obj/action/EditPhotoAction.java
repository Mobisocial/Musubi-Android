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
import mobisocial.musubi.feed.iface.DbEntryHandler;
import mobisocial.musubi.model.DbRelation;
import mobisocial.musubi.model.MApp;
import mobisocial.musubi.model.helpers.AppManager;
import mobisocial.musubi.obj.ObjHelpers;
import mobisocial.musubi.obj.iface.ObjAction;
import mobisocial.musubi.objects.AppObj;
import mobisocial.musubi.objects.PictureObj;
import mobisocial.musubi.ui.fragments.AppSelectDialog;
import mobisocial.musubi.ui.fragments.AppSelectDialog.MusubiWebApp;
import mobisocial.musubi.ui.util.IntentProxyActivity;
import mobisocial.musubi.util.ActivityCallout;
import mobisocial.musubi.util.InstrumentedActivity;
import mobisocial.musubi.util.PhotoTaker;
import mobisocial.socialkit.musubi.DbObj;
import mobisocial.socialkit.musubi.Musubi;
import mobisocial.socialkit.obj.MemObj;

import org.json.JSONException;
import org.json.JSONObject;
import org.mobisocial.corral.ContentCorral;
import org.mobisocial.corral.CorralDownloadClient;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.LabeledIntent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

/**
 * Edits a picture object using the standard Android "EDIT" intent.
 *
 */
public class EditPhotoAction extends ObjAction {
    private static final String TAG = "EditPhotoAction";
    public static final String CATEGORY_IN_PLACE = "mobisocial.intent.category.IN_PLACE";
    static final String PICSAY_PACKAGE_PREFIX = "com.shinycore.picsay";

    @Override
    public void onAct(Context context, DbEntryHandler objType, DbObj obj) {

        ((InstrumentedActivity)context).doActivityForResult(
                new EditCallout((Activity)context, obj));
    }

    @Override
    public String getLabel(Context context) {
        return "Edit";
    }

    @Override
    public boolean isActive(Context context, DbEntryHandler objType, DbObj obj) {
        return (objType instanceof PictureObj);
    }

    static File getTempImagePath() {
        return new File(Environment.getExternalStorageDirectory().getAbsolutePath() +
                "/musubi_edit.png");
    }

    public static class EditCallout implements ActivityCallout {
        final JSONObject mJson;
        final byte[] mRaw;
        final Activity mContext;
        final Uri mObjUri;
        final Uri mFeedUri;
        final Uri mHdUri;
        final String mHash;

        public EditCallout(Activity context, DbObj obj) {
            mObjUri = obj.getUri();
            mHash = obj.getUniversalHashString();
            mJson = obj.getJson();
            mRaw = obj.getRaw();
            mContext = context;
            mFeedUri = obj.getContainingFeed().getUri();
            Uri hd = null;
            CorralDownloadClient client = CorralDownloadClient.getInstance(context);
            if (client.fileAvailableLocally(obj)) {
                hd = client.getAvailableContentUri(obj);
            }
            mHdUri = hd;
        }
        @Override
        public Intent getStartIntent() {
            Uri contentUri;

            File file;
            if (mHdUri != null) {
                // Don't edit in-place to avoid edited images showing up in
                // places like the camera reel.
                FileOutputStream out = null;
                try {
                    file = getTempImagePath();
                    out = new FileOutputStream(file);
                    InputStream is = mContext.getContentResolver().openInputStream(mHdUri);

                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPurgeable = true;
                    options.inSampleSize = 4;
                    Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);
                    Matrix matrix = new Matrix();
                    float rotation = PhotoTaker.rotationForImage(mContext, mHdUri);
                    if (rotation != 0f) {
                        matrix.preRotate(rotation);
                        int width = bitmap.getWidth();
                        int height = bitmap.getHeight();
                        bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
                    }
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                } catch (IOException e) {
                    Toast.makeText(mContext, "Could not edit photo.", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error editing photo", e);
                    return null;
                } finally {
                	try {
						if(out != null) out.close();
					} catch (IOException e) {
						Log.e(TAG, "failed to close output stream for picture", e);
					}
                }
            } else {
                OutputStream out = null;
                try {
                    file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() +
                            "/musubi_edit.png");
                    out = new FileOutputStream(file);
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPurgeable = true;
                    options.inInputShareable = true;
                    Bitmap bitmap = BitmapFactory.decodeByteArray(mRaw, 0, mRaw.length, options);
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
            }
            contentUri = Uri.fromFile(file);
            Log.w(TAG, "uri=" + contentUri);
            return getEditorChooserIntent(mContext, contentUri, "image/png", mFeedUri);
        }

        @Override
        public void handleResult(int resultCode, final Intent data) {
            if (resultCode == Activity.RESULT_OK) {
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            ComponentName cn = data.getParcelableExtra(IntentProxyActivity.EXTRA_RESOLVED_COMPONENT);
                            Uri result;
                            if (cn.getPackageName().startsWith(PICSAY_PACKAGE_PREFIX)) {
                                result = data.getData();
                            } else {
                                // IN_PLACE
                                result = Uri.fromFile(getTempImagePath());
                            }
                            boolean reference = true;
                            Uri stored = ContentCorral.storeContent(mContext, result);
                            if (stored == null) {
                                Log.w(TAG, "Error storing content in corral");
                                stored = result;
                                reference = false;
                            } else {
                                new File(result.getPath()).delete();
                            }
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

                            Helpers.sendToFeed(mContext, outboundObj, mFeedUri);                            
                        } catch (IOException e) {
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

            targetedShareIntents.addAll(getBundledEditorIntents(context, mObjUri, feedUri));
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
            targetedShareIntents.addAll(getWebEditorIntents(context, mObjUri, feedUri));

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
