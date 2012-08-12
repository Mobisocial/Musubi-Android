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

package mobisocial.musubi.ui;

import java.io.File;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;

public class PhotoEditorActivity extends FragmentActivity {

	Uri mImageUri;

	static final String EXTRA_FILE_URI = "fileUri";

	static final int REQUEST_GET_CONTENT = 1;
	static final int REQUEST_IMAGE_CAPTURE = 2;
	static final int REQUEST_EDIT_PHOTO = 3;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (mImageUri == null) {
			showDialog(ChooseImageDialogFragment.newInstance());
		} else {
			showDialog(EditImageDialogFragment.newInstance());
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		switch (requestCode) {
		case REQUEST_GET_CONTENT: {
			if (resultCode == RESULT_OK) {
				mImageUri = data.getData();
			}
			break;
		}
		case REQUEST_IMAGE_CAPTURE: {
			mImageUri = getTempFileUri();
			break;
		}
		case REQUEST_EDIT_PHOTO:
			if (resultCode == RESULT_OK) {
				// make sure edited content is at mImageUri
			}
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putParcelable(EXTRA_FILE_URI, mImageUri);
	}
	
	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		mImageUri = savedInstanceState.getParcelable(EXTRA_FILE_URI);
	}

	void showDialog(DialogFragment dialog) {
	    // DialogFragment.show() will take care of adding the fragment
	    // in a transaction.  We also want to remove any currently showing
	    // dialog, so make our own transaction and take care of that here.
	    FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
	    Fragment prev = getSupportFragmentManager().findFragmentByTag("dialog");
	    if (prev != null) {
	        ft.remove(prev);
	    }
	    ft.addToBackStack(null);

	    dialog.show(ft, "dialog");
	}

	public static class EditImageDialogFragment extends DialogFragment {
		public static EditImageDialogFragment newInstance() {
			EditImageDialogFragment f = new EditImageDialogFragment();
			Bundle args = new Bundle();
			f.setArguments(args);
			return f;
		}

		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			String[] choices = new String[] { "PicSay", "Sketch" };
			return new AlertDialog.Builder(getActivity())
				.setTitle("Choose Editor")
				.setItems(choices, null)
				.setOnCancelListener(new DialogInterface.OnCancelListener() {
					@Override
					public void onCancel(DialogInterface dialog) {
						getActivity().finish();
						dismiss();
					}
				}).create();
			}
	}

	public static class ChooseImageDialogFragment extends DialogFragment 
			implements DialogInterface.OnClickListener {

		public static ChooseImageDialogFragment newInstance() {
			ChooseImageDialogFragment f = new ChooseImageDialogFragment();
			Bundle args = new Bundle();
			f.setArguments(args);
			return f;
		}

		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			String[] choices = new String[] { "From Camera", "From Gallery" };
			return new AlertDialog.Builder(getActivity())
				.setTitle("Choose Image")
				.setItems(choices, this)
				.setOnCancelListener(new DialogInterface.OnCancelListener() {
					@Override
					public void onCancel(DialogInterface dialog) {
						getActivity().finish();
						dismiss();
					}
				}).create();
		}

		@Override
		public void onClick(DialogInterface dialog, int which) {
			Intent intent = new Intent();
			int requestCode = 0;
			switch (which) {
			case 0: {
				intent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
				intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, getTempFileUri());
				requestCode = REQUEST_IMAGE_CAPTURE;
				break;
			}
			case 1: {
				intent.setAction(Intent.ACTION_GET_CONTENT);
				intent.setType("image/*");
				requestCode = REQUEST_GET_CONTENT;
				break;
			}
			}

			getActivity().startActivityForResult(intent, requestCode);
		}
	}

	static Uri getTempFileUri() {
		return Uri.fromFile(new File(Environment.getExternalStorageDirectory(), "edit-tmp"));
	}
}
