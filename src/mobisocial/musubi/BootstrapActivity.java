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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import mobisocial.metrics.UsageMetrics;
import mobisocial.musubi.model.helpers.DatabaseFile;
import mobisocial.musubi.model.helpers.EncodedMessageManager;
import mobisocial.musubi.service.AddressBookUpdateHandler;
import mobisocial.musubi.service.MusubiService;
import mobisocial.musubi.ui.SettingsActivity;
import mobisocial.musubi.ui.fragments.EulaFragment;
import mobisocial.musubi.util.CertifiedHttpClient;
import mobisocial.socialkit.musubi.Musubi;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.widget.TextView;

public class BootstrapActivity extends FragmentActivity {
    static final String TAG = BootstrapActivity.class.getSimpleName();
    public static final String EXTRA_ORIGINAL_INTENT = "original-intent";
    public static final String PREF_INSTALLED_VERSION = "installed_version";
    public static final String PREF_LAST_UPDATE_CHECK = "version_checked";
    static final long ONE_DAY = 86400000;

	static boolean sBootstrapped = false;
	boolean mNeedsEULA;
	boolean mNeedsIntro;
	boolean mNeedsFriends;

	EulaFragment.Callback mEulaCallback = new EulaFragment.Callback() {
    	@Override
    	public void eulaResult(boolean accepted) {
    		if(accepted) {
        		EulaFragment.acceptedEULA(BootstrapActivity.this);
    			mNeedsEULA = false;
    		} else {
    			setResult(RESULT_CANCELED);
    			finish();
    		}
    	}
    };
	/**
	 * finishes the caller if bootstrapping is necessary
	 * @param activity
	 * @return true if the bootstrap activity will be started
	 */
    public static boolean bootstrapIfNecessary(Activity activity) {
        if (sBootstrapped) {
            return false;
        }
        Intent intent = new Intent(activity, BootstrapActivity.class);
        intent.putExtra(EXTRA_ORIGINAL_INTENT, (Parcelable)activity.getIntent());
        intent.setFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
        activity.startActivity(intent);
        activity.finish();
        return true;
    }

    public static boolean isBootstrapped() {
        return sBootstrapped;
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new InitializeMusubiTask().execute();
    }
    
    //TODO: do these settings really belong here? maybe...
    //TODO: add these settings to the database instead?
    private void ensureRingtone() {
        SharedPreferences settings = getSharedPreferences(SettingsActivity.PREFS_NAME, 0);
        if (settings.getString("ringtone", null) != null) {
            return;   
        }

        RingtoneManager ringtoneManager = new RingtoneManager(this);
        ringtoneManager.setType(RingtoneManager.TYPE_NOTIFICATION);
        String ringtoneUri = null;
        String backupUri = null;
        Cursor cursor = ringtoneManager.getCursor();
        try {
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                String ringtone = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX);
                if (ringtone.equalsIgnoreCase("dDeneb")) {
                    ringtoneUri = cursor.getString(RingtoneManager.URI_COLUMN_INDEX) + "/" +
                            cursor.getString(RingtoneManager.ID_COLUMN_INDEX); 
                
                    break;
                }
                if (backupUri == null) {
                    backupUri = cursor.getString(RingtoneManager.URI_COLUMN_INDEX) + "/" +
                            cursor.getString(RingtoneManager.ID_COLUMN_INDEX); 
                }               
                cursor.moveToNext();
            }
        } finally {
            cursor.deactivate();
        }


        SharedPreferences.Editor editor = settings.edit();
        if (ringtoneUri != null) {
            editor.putString("ringtone", ringtoneUri);
        } else {
            if (backupUri != null) {
                editor.putString("ringtone", backupUri.toString());
            } else {
                editor.putString("ringtone", "none");
            }
        }
        editor.commit();
    }

    Integer getRequiredVersion() {
    	if (UsageMetrics.CHIRP_VERSIONING_ENDPOINT == null) {
    		return null;
    	}

        HttpClient http = new CertifiedHttpClient(this);
        HttpParams params = http.getParams();
        HttpProtocolParams.setUseExpectContinue(params, false);
        HttpConnectionParams.setConnectionTimeout(params, 6000);
        HttpConnectionParams.setSoTimeout(params, 6000);

        HttpGet get = new HttpGet(UsageMetrics.CHIRP_VERSIONING_ENDPOINT);
        try {
            HttpResponse response = http.execute(get);
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    response.getEntity().getContent()));
            StringBuffer sb = new StringBuffer("");
            String line;
            while ((line = in.readLine()) != null) {
                sb.append(line);
            }
            in.close();
            JSONObject json = new JSONObject(sb.toString());
            return json.getInt("required");
        } catch (IOException e) {
            Log.e(TAG, "Error getting versioning info", e);
            return null;
        } catch (JSONException e) {
            Log.e(TAG, "Bad versioning format", e);
            return null;
        }
    }

	/**
     * Attempts to make a database connection in order to trigger a
     * database upgrade. Before doing so, we display a DialogFragment
     * alerting the user of an upgrade, and afterwards we dismiss the dialog.
     * The dialog is not displayed if the connection is done quickly.
     */
    class InitializeMusubiTask extends AsyncTask<Void, String, Integer> {
        final Integer RESULT_INITIALIZED_OK = 0;
        final Integer RESULT_NEEDS_UPGRADE = 1;

        SharedPreferences mPreferences;
        int mInstalledVersion;
        int mPreviousVersion;

        boolean mNeedsVersionCheck;
        boolean mVersionChanged;
        boolean mQuickBootsrap = false;

        @Override
        protected void onPreExecute() {
            mPreferences = getSharedPreferences(SettingsActivity.PREFS_NAME, 0);
            mPreviousVersion = mPreferences.getInt(PREF_INSTALLED_VERSION, 0);
            try {
                PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
                mInstalledVersion = info.versionCode;
            } catch (NameNotFoundException e) {
                throw new RuntimeException("Couldn't find my own package", e);
            }

            long time = System.currentTimeMillis();
            long versionChecked = mPreferences.getLong(PREF_LAST_UPDATE_CHECK, 0);
            mNeedsVersionCheck = (time - versionChecked > ONE_DAY);
            mVersionChanged = (mPreviousVersion != mInstalledVersion);
            mNeedsEULA = EulaFragment.needsEULA(BootstrapActivity.this);

            mQuickBootsrap = !(mNeedsVersionCheck || mVersionChanged || mNeedsEULA);
            if (!mQuickBootsrap) {
                setContentView(R.layout.splash);
            }
        }

        @Override
        protected Integer doInBackground(Void... params) {
            SQLiteOpenHelper databaseSource = App.getDatabaseSource(BootstrapActivity.this);
            if (databaseSource instanceof DatabaseFile) {
                DatabaseFile dbf = (DatabaseFile)databaseSource;
                dbf.setActivityForEmergencyUI(BootstrapActivity.this);
            }

            if (mQuickBootsrap) {
                return RESULT_INITIALIZED_OK;
            }

        	publishProgress("Loading...");            
            if (mNeedsVersionCheck) {
                publishProgress("Checking for updates...");
                // Make sure this version is okay to use
                Integer requiredVersion = getRequiredVersion();
                if (requiredVersion != null && mInstalledVersion < requiredVersion) {
                    // Update required.
                    return RESULT_NEEDS_UPGRADE;
                }
                if (requiredVersion != null) {
                    // verified latest version.
                    long time = System.currentTimeMillis();
                    mPreferences.edit().putLong(PREF_LAST_UPDATE_CHECK, time).commit();
                }
            }

            if (!mVersionChanged) {
                // No need to check for further bootstrapping.
                // The goal is to entirely skip the splash screen even if the system
                // has killed our application.
                return RESULT_INITIALIZED_OK;
            }

            // Handle basic initial settings
            ensureRingtone();

            // Force database upgrade
            publishProgress("Preparing database...");
            databaseSource.getReadableDatabase();

            // Require EULA
            boolean neededEula = mNeedsEULA;
            if (mNeedsEULA) {
                FragmentManager fm = getSupportFragmentManager();
                EulaFragment eula = (EulaFragment)fm.findFragmentByTag("eula");
                if (eula == null) {
                    eula = new EulaFragment(true);
                    eula.show(fm, "eula");
                }
                eula.addCallback(mEulaCallback);
            }

            while (mNeedsEULA) {
                publishProgress("Laweyering up...");
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
            }

            if (neededEula) {
                // wait for first message to become available
                mNeedsIntro = true;
                publishProgress("Preparing greeting...");

                Uri firstPost = MusubiService.WIZARD_READY;
                ContentObserver intro = new ContentObserver(new Handler(getMainLooper())) {
                    @Override
                    public void onChange(boolean selfChange) {
                        mNeedsIntro = false;
                        getContentResolver().unregisterContentObserver(this);
                    }
                };
                getContentResolver().registerContentObserver(firstPost, false, intro);
                startService(new Intent(BootstrapActivity.this, MusubiService.class));
                while (mNeedsIntro) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                    }
                }
            }

            return RESULT_INITIALIZED_OK;
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (result == RESULT_INITIALIZED_OK) {
                if (mPreviousVersion != mInstalledVersion) {
                    mPreferences.edit().putInt(PREF_INSTALLED_VERSION, mInstalledVersion).commit();
                }
                sBootstrapped = true;
                new CleanUpMusubiTask().execute();

                Intent original = (Intent)getIntent().getParcelableExtra(EXTRA_ORIGINAL_INTENT);
                original.setFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
                startActivity(original);
                finish();
            } else if (result == RESULT_NEEDS_UPGRADE) {
                FragmentManager fm = getSupportFragmentManager();
                UpgradeDialog upgrade = (UpgradeDialog)fm.findFragmentByTag("upgrade");
                if (upgrade == null) {
                    upgrade = UpgradeDialog.newInstance();
                    upgrade.show(fm, "upgrade");
                }
            }
        }
        
        @Override
        protected void onProgressUpdate(String... values) {
        	TextView loading_text_view = (TextView)findViewById(mobisocial.musubi.R.id.loading_text);
        	loading_text_view.setText(values[0]);
        }
    }

    class CleanUpMusubiTask extends AsyncTask<Void, Void, Void> {
    	@Override
    	protected Void doInBackground(Void... params) {
    		long startTime = System.currentTimeMillis();
    		SQLiteOpenHelper db = App.getDatabaseSource(getApplicationContext());
    		new EncodedMessageManager(db).deleteProcessedOldItems(7);
    		long totalTime = System.currentTimeMillis() - startTime;
    		Log.d(TAG, String.format("++++ Object truncation took %d ms.", totalTime));
    		return null;
    	}
    }

    static class UpgradeDialog extends DialogFragment {
        public static UpgradeDialog newInstance() {
            Bundle b = new Bundle();
            UpgradeDialog d = new UpgradeDialog();
            d.setArguments(b);
            return d;
        }

        public UpgradeDialog() {
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity()).setTitle("Upgrade Required")
                    .setMessage("An important update is required to use Musubi. Upgrade now?")
                    .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent market = Musubi.getMarketIntent();
                            getActivity().startActivity(market);
                            getActivity().finish();
                        }
                    }).setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            getActivity().finish();
                        }
                    }).create();
        }
    }
}