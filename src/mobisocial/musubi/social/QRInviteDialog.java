/*
 * Copyright 2012 The Stanford MobiSocial Laboratory
 * Copyright 2010 ZXing Project
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

package mobisocial.musubi.social;

import java.util.List;

import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.metrics.MusubiMetrics;
import mobisocial.metrics.UsageMetrics;
import mobisocial.musubi.App;
import mobisocial.musubi.R;
import mobisocial.musubi.model.MDevice;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.ui.EmailInviteActivity;
import mobisocial.musubi.ui.util.UiUtil;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.WriterException;
import com.google.zxing.client.android.Contents;
import com.google.zxing.client.android.FinishListener;
import com.google.zxing.client.android.Intents;
import com.google.zxing.client.android.encode.QRCodeEncoder;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

/**
 * Presents a QR code allowing two devices to perform a key exchange.
 *
 * With code from ZXing, {@see EncodeActivity}
 */
public final class QRInviteDialog extends DialogFragment {
    private static final String TAG = QRInviteDialog.class.getSimpleName();
    private QRCodeEncoder qrCodeEncoder;

    /**
     * Create a new instance of MyDialogFragment, providing "num"
     * as an argument.
     */
    public static QRInviteDialog newInstance() {
        QRInviteDialog f = new QRInviteDialog();
        Bundle args = new Bundle();
        f.setArguments(args);
        return f;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.qr_invite, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Activity context = getActivity();
        getDialog().setTitle("Exchange Info");
        // This assumes the view is full screen, which is a bad assumption
        WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = manager.getDefaultDisplay();
        int width = display.getWidth();
        int height = display.getHeight();
        int smallerDimension = width < height ? width : height;

        Uri uri = EmailInviteActivity.getInvitationUrl(context);
		if (uri.getQueryParameter("n") == null) {
            Toast.makeText(getActivity(), "You must set up an account and a enter a name to connect with friends.", Toast.LENGTH_LONG).show();
            dismiss();
            return;
        }
		if(uri.getQueryParameter("t") == null) {
			dismiss();
			Toast.makeText(getActivity(), "No identities to share", Toast.LENGTH_SHORT).show();
			return;
		}
        Intent intent = new Intent(Intents.Encode.ACTION);
        intent.setClass(getActivity(), QRInviteDialog.class);
        intent.putExtra(Intents.Encode.TYPE, Contents.Type.TEXT);
        intent.putExtra(Intents.Encode.DATA, uri.toString());

        try {
            qrCodeEncoder = new QRCodeEncoder(context, intent, smallerDimension);
            //setTitle(getString(R.string.app_name) + " - " + qrCodeEncoder.getTitle());
            Bitmap bitmap = qrCodeEncoder.encodeAsBitmap();
            ImageView image = (ImageView) view.findViewById(R.id.image_view);
            image.setImageBitmap(bitmap);
            TextView contents = (TextView) view.findViewById(R.id.contents_text_view);
            contents.setText(qrCodeEncoder.getDisplayContents());
            view.findViewById(R.id.ok_button).setOnClickListener(mCameraListener);
        } catch (WriterException e) {
            Log.e(TAG, "Could not encode barcode", e);
            showErrorMessage(R.string.msg_encode_contents_failed);
            qrCodeEncoder = null;
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Could not encode barcode", e);
            showErrorMessage(R.string.msg_encode_contents_failed);
            qrCodeEncoder = null;
        }
    }

    private void showErrorMessage(int message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(message);
        builder.setPositiveButton(R.string.button_ok, new FinishListener(getActivity()));
        builder.setOnCancelListener(new FinishListener(getActivity()));
        builder.show();
    }

    private OnClickListener mCameraListener = new OnClickListener() {
        @Override
        public void onClick(android.view.View v) {
            new UsageMetrics(getActivity()).report(MusubiMetrics.CLICKED_QR_SCAN);
            IntentIntegrator.initiateScan(getActivity());
        }
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == IntentIntegrator.REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                IntentResult result =
                        IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
                try {
                    Uri uri = Uri.parse(result.getContents());
                    Intent i = new Intent(Intent.ACTION_VIEW, uri);
                    i.setPackage(getActivity().getPackageName());
                    startActivity(i);
                } catch (IllegalArgumentException e) {
                }
            }
        }
    };
}
