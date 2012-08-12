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

package mobisocial.musubi.ui.fragments;

import org.apache.commons.io.output.ByteArrayOutputStream;

import mobisocial.musubi.objects.PictureObj;
import mobisocial.musubi.util.InstrumentedActivity;
import mobisocial.musubi.util.PhotoTaker;
import mobisocial.musubi.util.UriImage;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.widget.Toast;

public class ChooseImageDialog extends DialogFragment {
	static final int REQUEST_PROFILE_PICTURE = 71;
	static final int REQUEST_GALLERY_THUMBNAIL = 72;
	static final String EXTRA_THUMBNAIL = "thumbnail";

	public static ChooseImageDialog newInstance() {
		Bundle args = new Bundle();
		ChooseImageDialog spd = new ChooseImageDialog();
		spd.setArguments(args);
		return spd;
	}

	// for framework
	public ChooseImageDialog() {
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		return new AlertDialog.Builder(getActivity())
				.setTitle("Choose an Image...")
				.setItems(new String[] { "From Camera", "From Gallery" },
						new DialogInterface.OnClickListener() {
							@SuppressWarnings("deprecation")
							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								switch (which) {
								case 0:
									final Activity activity = getActivity();
									Toast.makeText(activity,
											"Loading camera...",
											Toast.LENGTH_SHORT).show();
									((InstrumentedActivity) activity)
											.doActivityForResult(new PhotoTaker(
													activity,
													new PhotoTaker.ResultHandler() {
														@Override
														public void onResult(
																Uri imageUri) {
															Log.d(getClass()
																	.getSimpleName(),
																	"Updating thumbnail...");

															try {
																UriImage image = new UriImage(
																		activity,
																		imageUri);
																byte[] data = image
																		.getResizedImageData(
																				512,
																				512,
																				PictureObj.MAX_IMAGE_SIZE / 2);
																// profile
																Bitmap sourceBitmap = BitmapFactory
																		.decodeByteArray(
																				data,
																				0,
																				data.length);
																int width = sourceBitmap
																		.getWidth();
																int height = sourceBitmap
																		.getHeight();
																int cropSize = Math
																		.min(width,
																				height);
																Bitmap cropped = Bitmap
																		.createBitmap(
																				sourceBitmap,
																				0,
																				0,
																				cropSize,
																				cropSize);
																ByteArrayOutputStream baos = new ByteArrayOutputStream();
																cropped.compress(
																		Bitmap.CompressFormat.JPEG,
																		90,
																		baos);
																cropped.recycle();
																sourceBitmap
																		.recycle();

																Bundle bundle = new Bundle();
																bundle.putByteArray(
																		EXTRA_THUMBNAIL,
																		baos.toByteArray());
																Intent res = new Intent();
																res.putExtras(bundle);
																getTargetFragment()
																		.onActivityResult(
																				REQUEST_PROFILE_PICTURE,
																				Activity.RESULT_OK,
																				res);
															} catch (Throwable t) {
																Log.e("ViewProfile",
																		"failed to generate thumbnail of profile",
																		t);
																Toast.makeText(
																		activity,
																		"Profile picture capture failed.  Try again.",
																		Toast.LENGTH_SHORT)
																		.show();
															}
														}
													}, 200, false));
									break;
								case 1:
									Intent gallery = new Intent(
											Intent.ACTION_GET_CONTENT);
									gallery.setType("image/*");
									// god damn fragments.
									getTargetFragment()
											.startActivityForResult(
													Intent.createChooser(
															gallery, null),
													REQUEST_GALLERY_THUMBNAIL);
									break;
								}
							}
						}).create();
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.e("FRAGMENTED", "got a result abck");
		// getTargetFragment().onActivityResult(requestCode, resultCode, data);
	}
}