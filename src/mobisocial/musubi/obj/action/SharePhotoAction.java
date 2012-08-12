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
import java.io.OutputStream;

import mobisocial.musubi.R;
import mobisocial.musubi.feed.iface.DbEntryHandler;
import mobisocial.musubi.obj.iface.ObjAction;
import mobisocial.musubi.objects.PictureObj;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.ui.SendContentActivity;
import mobisocial.socialkit.musubi.DbObj;

import org.mobisocial.corral.CorralDownloadClient;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore.Images.Media;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

/**
 * Sends a picture object using the standard Android "SEND" intent.
 *
 */
public class SharePhotoAction extends ObjAction {
	public static final String TAG = "ExportPhotoAction";
	
    @Override
    public void onAct(Context context, DbEntryHandler objType, DbObj obj) {
        new ExportPhotoTask(context, obj).execute();
    }

    @Override
    public String getLabel(Context context) {
        return "Share";
    }

    @Override
    public boolean isActive(Context context, DbEntryHandler objType, DbObj obj) {
        return (objType instanceof PictureObj);
    }

    class ExportPhotoTask extends AsyncTask<Void, Void, Boolean> {
        Context context;
        DbObj obj;
        final ProgressDialog mDialog;

        public ExportPhotoTask(Context context, DbObj obj) {
            this.context = context;
            this.obj = obj;
            mDialog = new ProgressDialog(context);
            mDialog.setTitle("Preparing photo...");
            mDialog.setIndeterminate(true);
        }

        @Override
        protected void onPreExecute() {
            mDialog.show();
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            Uri imageUri = null;
            CorralDownloadClient client = CorralDownloadClient.getInstance(context);
            if (client.fileAvailableLocally(obj)) {
                Log.d(TAG, "hifi content available");
                imageUri = client.getAvailableContentUri(obj);
            }

            if (imageUri == null) {
                Log.w(TAG, "Using lofi content");
                byte[] raw = obj.getRaw();
                if (raw == null) {
                    String b64Bytes = obj.getJson().optString(PictureObj.DATA);
                    raw = Base64.decode(b64Bytes, Base64.DEFAULT);
                }
                OutputStream out = null;
                File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath()+"/musubi_share.jpg");
                try {
                    out = new FileOutputStream(file);
                    
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPurgeable = true;
                    options.inInputShareable = true;
                    Bitmap bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.length, options);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
                    out.flush();
                    out.close();

                    bitmap.recycle();
                    bitmap = null;
                    System.gc();
                    
                    String url = Media.insertImage(context.getContentResolver(), file.getAbsolutePath(), file.getName(), file.getName());
                    imageUri = Uri.parse(url);
                } catch  (IOException e) {
                    Log.e(TAG, "Error preparing photo", e);
                } finally {
                    try {
                        if(out != null) out.close();
                    } catch (IOException e) {
                        Log.e(getClass().getName(), "failed to close output stream for picture", e);
                    }
                }
            }

            if (imageUri != null) {
                Resources res = context.getResources();
                Intent intent = new Intent(android.content.Intent.ACTION_SEND);
                intent.putExtra(SendContentActivity.EXTRA_CALLING_APP, MusubiContentProvider.SUPER_APP_ID);
                intent.putExtra(Intent.EXTRA_SUBJECT, res.getString(R.string.shared_from_musubi));
                intent.putExtra(Intent.EXTRA_TEXT, res.getString(R.string.shared_from_musubi_blurb));
                intent.putExtra(Intent.EXTRA_STREAM, imageUri);
                intent.setType("image/jpeg"); 
                context.startActivity(Intent.createChooser(intent, "Share using..."));
            }

            return imageUri != null;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (mDialog.isShowing()) {
                mDialog.dismiss();
            }

            if (!result) {
                Toast.makeText(context, "Error preparing image for sharing.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
