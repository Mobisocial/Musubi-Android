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

package org.mobisocial.corral;

import org.mobisocial.corral.CorralDownloadHandler.CorralDownloadFuture;
import org.mobisocial.corral.CorralHelper.DownloadProgressCallback;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Log;

public class BackgroundableDownloadDialogFragment extends DialogFragment
        implements DownloadProgressCallback {
    protected CorralDownloadFuture mFuture;
    protected Uri mResult;
    boolean mBackgrounding = false;

    public BackgroundableDownloadDialogFragment(CorralDownloadFuture future) {
        setRetainInstance(true);
        Bundle args = new Bundle();
        args.putLong("objId", future.getObjId());
        setArguments(args);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        long objId = getArguments().getLong("objId");
        mFuture = CorralDownloadHandler.lookupDownload(getActivity(), objId);
        if (mFuture != null) {
            mFuture.registerCallback(this);
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        ProgressDialog d = new ProgressDialog(getActivity());
        d.setTitle("Fetching file");
        d.setMessage("Preparing to download...");
        d.setIndeterminate(true);
        d.setCancelable(true);
        d.setButton(DialogInterface.BUTTON_POSITIVE, "Background", mBackgroundListener);
        d.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", mCancelListener);
        return d;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        mFuture.unregisterCallback(this);
    }

    public void onPause() {
        super.onPause();
        if (!mBackgrounding) {
            mFuture.registerCallback(new DownloadProgressNotificationCallback(getActivity(), mFuture.mObjId));
            dismiss();
        }
    };

    /**
     * The dialog has been canceled. This is not the same as an explicit cancel of the download.
     */
    public void onCancel(DialogInterface dialog) {
        mBackgrounding = true;
        mFuture.registerCallback(new DownloadProgressNotificationCallback(getActivity(), mFuture.mObjId));
    };

    OnClickListener mBackgroundListener = new OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            mBackgrounding = true;
            mFuture.registerCallback(new DownloadProgressNotificationCallback(getActivity(), mFuture.mObjId));
        }
    };

    OnClickListener mCancelListener = new OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (mFuture != null) {
                mFuture.cancel();
            }
        }
    };

    @Override
    public void onProgress(final DownloadState state, final DownloadChannel channel, final int progress) {
        Dialog dl = getDialog();
        if (dl != null) {
            final ProgressDialog d = (ProgressDialog)dl;
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    switch (state) {
                        case DOWNLOAD_PENDING:
                            d.setMessage("Download pending...");
                            break;
                        case PREPARING_CONNECTION:
                            d.setMessage("Preparing connection...");
                            break;
                        case TRANSFER_IN_PROGRESS:
                            if (progress > 0) {
                                d.setMessage("Downloading file... (" + progress + "%)");
                            } else {
                                d.setMessage("Downloading file...");
                            }
                            break;
                        case TRANSFER_COMPLETE:
                            try {
                                mResult = mFuture.getResult();
                            } catch (InterruptedException e) {
                                Log.e(getClass().getSimpleName(), "impossible interrupt exception", e);
                            }
                            d.dismiss();
                            break;
                    }      
                }
            });
        }
    }

    public Uri getResult() {
        return mResult;
    }
}