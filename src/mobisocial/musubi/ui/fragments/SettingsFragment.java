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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import mobisocial.musubi.App;
import mobisocial.musubi.R;
import mobisocial.musubi.model.DbContactAttributes;
import mobisocial.musubi.model.helpers.DatabaseFile;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.nearby.broadcast.MulticastBroadcastTask;
import mobisocial.musubi.service.MusubiIntentService;
import mobisocial.musubi.service.WizardStepHandler;
import mobisocial.musubi.ui.MusubiBaseActivity;
import mobisocial.musubi.ui.SettingsActivity;
import mobisocial.musubi.util.InstrumentedActivity;
import mobisocial.musubi.util.Util;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnKeyListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Process;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.SupportActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsFragment extends Fragment {
	private static MulticastBroadcastTask mMulticastBroadcast;
	private static final int MULTICAST_DELAY = 2500;
	private static final int MULTICAST_RETRY = 15000;

	public static final int REQUEST_RINGTONE = 284;

	public static final String PREF_RINGTONE = "ringtone";
	public static final String PREF_SHARE_APPS = "share_apps";
	public static final boolean PREF_SHARE_APPS_DEFAULT = true;
	public static final String PREF_AUTOPLAY = "autoplay";
	public static final boolean PREF_AUTOPLAY_DEFAULT = false;
	public static final String PREF_WIFI_FINGERPRINTING = "wifi_fingerprinting";
	public static final boolean PREF_WIFI_FINGERPRINTING_DEFAULT = false;
	public static final String PREF_VIBRATING = "wifi_fingerprinting";
	public static final boolean PREF_VIBRATING_DEFAULT = true;
	public static final String PREF_ROLLING_DELETE = "rolling_delete";
	public static final int INVALID_HISTORY = -1;
	public static final String[] items = {"Up to one week", "Up to one month", "Up to six months", "Forever"};
	public static final int[] numDays = {7, 30, 180, INVALID_HISTORY};
    
	
	private Activity mActivity;

	@Override
    public void onAttach(SupportActivity activity) {
        super.onAttach(activity);
    	mActivity = activity.asActivity();
    }
	
	@Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_RINGTONE) {
            if (resultCode == Activity.RESULT_OK) {
                Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                if (uri != null) {
                	String ringTonePath = uri.toString();
                	Log.w(TAG, ringTonePath);
                	SharedPreferences settings = mActivity.getSharedPreferences(SettingsActivity.PREFS_NAME, 0);
                	SharedPreferences.Editor editor = settings.edit();
        	    	editor.putString(PREF_RINGTONE, ringTonePath);
        	    	editor.commit();
                }
            }
        }
    }
	
	private final class VacuumDatabaseListener implements OnClickListener {
		@Override
		public void onClick(View v) {
			new VacuumDatabase().execute();
		}
	}

	private final class SetRingtoneListener implements OnClickListener {
		@Override
		public void onClick(View v) {
			String uri = null;
            Intent intent = new Intent( RingtoneManager.ACTION_RINGTONE_PICKER);
            intent.putExtra( RingtoneManager.EXTRA_RINGTONE_TYPE,
            RingtoneManager.TYPE_NOTIFICATION);
            intent.putExtra( RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Tone");

            SharedPreferences settings = mActivity.getSharedPreferences(SettingsActivity.PREFS_NAME, 0);
            uri = settings.getString(PREF_RINGTONE, "none");
            
            if (!uri.equals("none")) {
                 intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse( uri));
            } else {
                 intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, (Uri)null);
            }
        	startActivityForResult(intent, REQUEST_RINGTONE);
		}
	}

	private final class SDCardRestoreListener implements OnClickListener {
		@Override
		public void onClick(View v) {
			new AlertDialog.Builder(mActivity)
					.setTitle("Restore from SD card?")
					.setMessage("You will lose any unsaved data.")
					.setPositiveButton("Yes",
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog,
										int which) {
									new SDCardRestore().execute();
								}
							})
					.setNegativeButton("No", new CancelledDialogListener())
					.show();
		}
	}

	class FreeUpSpaceListener implements OnClickListener {
	    @Override
	    public void onClick(View v) {
	        DialogFragment dialog = new FreeSpaceDialog();
	        ((MusubiBaseActivity)getActivity()).showDialog(dialog);
	    }
	}

	/**
     * @hide
     */
    
	public final class FreeSpaceDialog extends DialogFragment {
		private Integer newNumDays = null;
		private SharedPreferences settings = null;

		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			settings = mActivity.getSharedPreferences(
					SettingsActivity.PREFS_NAME, 0);

			DialogInterface.OnClickListener radioBtnListener = new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {
					newNumDays = numDays[id];
				}
			};

			DialogInterface.OnClickListener okBtnListener = new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {
					if (newNumDays != null) {
						
						SharedPreferences.Editor editor = settings.edit();
						editor.putInt(PREF_ROLLING_DELETE, newNumDays);
						editor.commit();
						
						if (newNumDays != INVALID_HISTORY) {
							new ClearSpaceTask(mActivity).execute();
						}
					}
				}
			};

			int oldNumDays = settings.getInt(PREF_ROLLING_DELETE, INVALID_HISTORY);
			if (oldNumDays == INVALID_HISTORY) {
				SharedPreferences.Editor editor = settings.edit();
				editor.putInt(PREF_ROLLING_DELETE, oldNumDays);
				editor.commit();
			}

			int selectedItem = -1;
			for (int i = 0; i < numDays.length; i++) {
				if (numDays[i] == oldNumDays) {
					selectedItem = i;
				}
			}

			return new AlertDialog.Builder(getActivity())
					.setTitle("Keep messages on device...")
					.setSingleChoiceItems(items, selectedItem, radioBtnListener)
					.setNegativeButton("Cancel", null)
					.setPositiveButton("OK", okBtnListener).create();
		}
	}
	
	class ClearSpaceTask extends AsyncTask<Void, Void, Void> {
	    final Context mContext;
	    ProgressDialog mDialog;

	    public ClearSpaceTask(Context context) {
	        mContext = context;
	    }

	    @Override
	    protected void onPreExecute() {
	        mDialog = new ProgressDialog(mContext);
	        mDialog.setTitle("Deleting old messages...");
	        mDialog.setIndeterminate(true);
	        mDialog.show();
	    }

	    @Override
	    protected Void doInBackground(Void... params) {
	    	MusubiIntentService.doRollingDelete(mActivity);
	    	return null;
	    }

	    @Override
	    protected void onPostExecute(Void result) {
	        mDialog.dismiss();
	    }
	}

	private final class SDCardBackupListener implements OnClickListener {
		@Override
		public void onClick(View v) {
			new AlertDialog.Builder(mActivity)
					.setTitle("Backup to SD card?")
					.setMessage("This will overwrite your existing save.")
					.setPositiveButton("Yes",
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog,
										int which) {
									new SDCardBackup().execute();
								}
							})
					.setNegativeButton("No", new CancelledDialogListener())
					.show();
		}
	}

	private final class CancelledDialogListener implements
			DialogInterface.OnClickListener {
		@Override
		public void onClick(DialogInterface dialog, int which) {

		}
	}

	private final class ShareAppsListener implements OnClickListener {
	    @Override
	    public void onClick(View v) {
	           boolean shareApps = !shareApps_.isChecked();
	           mActivity.getSharedPreferences(SettingsActivity.PREFS_NAME, 0).edit()
                   .putBoolean(PREF_SHARE_APPS, shareApps).commit();
	           shareApps_.setChecked(shareApps);
	    }
	}
	private final class ShareContactListener implements OnClickListener {
	    @Override
	    public void onClick(View v) {
	           boolean shareContactInfo = !shareContactInfo_.isChecked();
	           mActivity.getSharedPreferences(SettingsActivity.PREFS_NAME, 0).edit()
                   .putBoolean(SettingsActivity.PREF_SHARE_CONTACT_ADDRESS, shareContactInfo).commit();
	           shareContactInfo_.setChecked(shareContactInfo);
	    }
	}

	private final class AnonStatsListener implements OnClickListener {
        @Override
        public void onClick(View v) {
               boolean anonStats = !anonStats_.isChecked();
               mActivity.getSharedPreferences(SettingsActivity.PREFS_NAME, 0).edit()
                   .putBoolean(SettingsActivity.PREF_ANONYMOUS_STATS, anonStats).commit();
               anonStats_.setChecked(anonStats);
        }
    }

	private final class GlobalTVModeListener implements OnClickListener {
		public void onClick(View v) {
			boolean global_tv_mode = !globalTVMode_.isChecked();
			mActivity.getSharedPreferences(SettingsActivity.PREFS_NAME, 0).edit()
					.putBoolean(PREF_AUTOPLAY, global_tv_mode).commit();
			globalTVMode_.setChecked(global_tv_mode);

			if (global_tv_mode) {
			    // TODO: put in a service.
			    /*mMulticastBroadcast = new MulticastBroadcastTask(
			            mActivity, MULTICAST_DELAY, MULTICAST_RETRY);
			    mMulticastBroadcast.execute();

			    try {
    			    JSONObject json = new JSONObject();
    			    json.put(DbContactAttributes.ATTR_DEVICE_MODALITY, "tv");
    			    Obj imATV = new MemObj("profileupdate", json);
    			    Helpers.sendToEveryone(mActivity, imATV);
			    } catch (Exception e) {
			        Log.e(TAG, "Error notifying profile update", e);
			    }*/
			} else {
			    /*if (mMulticastBroadcast != null) {
			        mMulticastBroadcast.cancel(true);
			        mMulticastBroadcast = null;
			    }

			    try {
                    JSONObject json = new JSONObject();
                    json.put(DbContactAttributes.ATTR_DEVICE_MODALITY, "phone");
                    Obj imATV = new MemObj("profileupdate", json);
                    Helpers.sendToEveryone(mActivity, imATV);
                } catch (Exception e) {
                    Log.e(TAG, "Error notifying profile update", e);
                }*/
			}
		}
	}

	private final class WifiFingerprintingListener implements OnClickListener {
		public void onClick(View v) {
			boolean wifi_fingerprinting = !wifiFingerprinting_.isChecked();
			mActivity.getSharedPreferences(SettingsActivity.PREFS_NAME, 0).edit()
					.putBoolean(PREF_WIFI_FINGERPRINTING, wifi_fingerprinting).commit();
			wifiFingerprinting_.setChecked(wifi_fingerprinting);

			//reset the wifi fingerprint to nothing if disabling sharing
			String fingerprint = "";
			if (wifi_fingerprinting) {
				WifiManager wifi = (WifiManager) mActivity.getSystemService(Context.WIFI_SERVICE);
				fingerprint = Util.computeWifiFingerprint(wifi.getScanResults());
			}
			
			JSONObject loc = new JSONObject();
	        try {
	            loc.put(DbContactAttributes.ATTR_WIFI_FINGERPRINT, fingerprint);
	        } catch (JSONException e) {
	            // Impossible json exception
	        }
	        //XXX killed for now
	        //App.getMusubi(mActivity).getAppData().postObj(new MemObj("locUpdate", loc));
	        //Log.w(TAG, "sending fingerprint");
		}
	}

	private final class VibratingListener implements OnClickListener {
		public void onClick(View v) {
			Log.w(TAG, "clicked vibrating");
			boolean vibrating = !vibrating_.isChecked();
			mActivity.getSharedPreferences(SettingsActivity.PREFS_NAME, 0).edit()
					.putBoolean(PREF_VIBRATING, vibrating).commit();
			vibrating_.setChecked(vibrating);
		}
	}


	String TAG = "Settings";

	Button primaryColor_;
	Button secondaryColor_;
	Button info_;
	Button devMode_;
	TextView setRingtone_;
	TextView vacuumDatabase_;
	CheckedTextView globalTVMode_;
	CheckedTextView shareApps_;
	CheckedTextView shareContactInfo_;
	CheckedTextView wifiFingerprinting_;
	CheckedTextView vibrating_;
	CheckedTextView anonStats_;

	public static SettingsFragment newInstance() {
		SettingsFragment frag = new SettingsFragment();
        Bundle args = new Bundle();
        frag.setArguments(args);
        return frag;
    }
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		View v = inflater.inflate(R.layout.settings, container, false);

		// save references to the UI elements that show visible state
		globalTVMode_ = (CheckedTextView) v.findViewById(R.id.global_tv_mode);
		wifiFingerprinting_ = (CheckedTextView) v.findViewById(R.id.wifi_fingerprinting_mode);
		shareApps_ = (CheckedTextView) v.findViewById(R.id.share_apps);
		shareContactInfo_ = (CheckedTextView) v.findViewById(R.id.share_contact);
		vacuumDatabase_ = (TextView) v.findViewById(R.id.vacuum_database);
		devMode_ = (Button) v.findViewById(R.id.set_devmode);
		setRingtone_ = (TextView) v.findViewById(R.id.set_ringtone);
		vibrating_ = (CheckedTextView) v.findViewById(R.id.vibrating);
		anonStats_ = (CheckedTextView) v.findViewById(R.id.report_stats);

		// connect the global tv mode toggle to the shared preferences
		vibrating_.setOnClickListener(new VibratingListener());
		globalTVMode_.setOnClickListener(new GlobalTVModeListener());
		wifiFingerprinting_.setOnClickListener(new WifiFingerprintingListener());
		shareApps_.setOnClickListener(new ShareAppsListener());
		shareContactInfo_.setOnClickListener(new ShareContactListener());
		vacuumDatabase_.setOnClickListener(new VacuumDatabaseListener());
		anonStats_.setOnClickListener(new AnonStatsListener());

		
		v.findViewById(R.id.settings_item_ringtone).setOnClickListener(new SetRingtoneListener());
		
		// usage stats
		v.findViewById(R.id.anon_stats_example).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                DialogFragment sample = AnonymousStatsSampleDialog.newInstance();
                ((InstrumentedActivity)getActivity()).showDialog(sample);
            }
        });

		// privacy protection
		v.findViewById(R.id.settings_item_protection).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                DialogFragment sample = PrivacyProtectionDialog.newInstance();
                ((InstrumentedActivity)getActivity()).showDialog(sample);
            }
        });

		// privacy protection
		v.findViewById(R.id.settings_item_feedback).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
            	Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + IdentitiesManager.PRE_INSTALL_MUSUBI_PRINCIPAL + "?subject=Musubi%20Feedback"));
            	startActivity(intent);
            }
        });
		// eula/priv pol
		v.findViewById(R.id.usage_agreements).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
            	EulaFragment eula_fragment = new EulaFragment(false);
                ((InstrumentedActivity)getActivity()).showDialog(eula_fragment);
            }
        });

		
		// dev mode
		devMode_.setOnLongClickListener(new DevModeListener());
		devMode_.setBackgroundColor(Color.TRANSPARENT);


		// connect the local handlers that manage the sd card backup
		v.findViewById(R.id.free_up_space).setOnClickListener(new FreeUpSpaceListener());
		v.findViewById(R.id.sdcard_backup).setOnClickListener(new SDCardBackupListener());
		v.findViewById(R.id.sdcard_restore).setOnClickListener(new SDCardRestoreListener());

		loadValues();

		return v;
	}

	private final class DevModeListener implements OnLongClickListener {
	    @Override
	    public boolean onLongClick(View v) {
	        toggleDevMode();
	        return true;
	    }
	}

	private final class IgnoreSearchKeyListener implements OnKeyListener {
		@Override
		public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
			//don't let search cancel the operation
			if (keyCode == KeyEvent.KEYCODE_SEARCH && event.getRepeatCount() == 0) {
		        return true; // Pretend we processed it
		    }
			return false;
		}
	}

	class VacuumDatabase extends AsyncTask<Void, Void, Void> {
		ProgressDialog progress_;

		@Override
		protected void onPreExecute() {
			progress_ = new ProgressDialog(mActivity);
			progress_.setCancelable(false);
			progress_.setMessage("Vacuuming Database...");
			progress_.show();
			int orientation = getResources().getConfiguration().orientation;
			mActivity.setRequestedOrientation(orientation);
		}
		@Override
		protected Void doInBackground(Void... params) {
			try {
				SQLiteOpenHelper mHelper = App.getDatabaseSource(mActivity);
				((DatabaseFile)mHelper).vacuum();
			} catch (Exception e) {
				Log.e(TAG, "Failure doing chores (vacuuming)", e);
			}
			return null;
		}
		@Override
		protected void onPostExecute(Void result) {
			mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
			progress_.dismiss();
		}
	}
	
	class SDCardBackup extends AsyncTask<Void, Integer, Exception> {
		ProgressDialog progress_;

		@Override
		protected void onPreExecute() {
			progress_ = new ProgressDialog(mActivity);
			progress_.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progress_.setOnKeyListener(new IgnoreSearchKeyListener());
			progress_.setCancelable(false);
			progress_.setMessage("Backing up to SD card.");
			progress_.setMax(100);
			progress_.setIndeterminate(false);
			progress_.show();
			int orientation = getResources().getConfiguration().orientation;
			mActivity.setRequestedOrientation(orientation);
		}
		@Override
		protected void onProgressUpdate(Integer... values) {
			progress_.setProgress(values[0].intValue());
		}

		@Override
		protected Exception doInBackground(Void... params) {
			FileInputStream in = null;
			FileOutputStream out = null;

            SQLiteDatabase db = App.getDatabaseSource(mActivity).getWritableDatabase();
            db.beginTransaction();
			try {
				File currentDB = mActivity.getDatabasePath(DatabaseFile.DEFAULT_DATABASE_NAME);

				String extStorageDirectory = Environment
						.getExternalStorageDirectory().toString()
						+ "/Musubi/";
				File backupDB = new File(extStorageDirectory, DatabaseFile.DEFAULT_DATABASE_NAME);
				File fileDirectory = new File(extStorageDirectory);
				fileDirectory.mkdirs();
				
				long file_size = currentDB.length();
				in = new FileInputStream(currentDB);
				out = new FileOutputStream(backupDB);
				byte[] buf = new byte[65536];
				int len;
				long so_far = 0;
				while ((len = in.read(buf)) > 0) {
					out.write(buf, 0, len);
					so_far += len;
					publishProgress((int) (100 * so_far / (file_size + 1)));
				}
				return null;
			} catch (Exception e) {
				Log.e(TAG, "Failure backing up to SD card", e);
				return e;
	        } finally {
	            db.endTransaction();
	        	try {
					if(in != null) in.close();
					if(out != null) in.close();
				} catch (IOException e) {
					Log.e(TAG, "failed to close streams for backup", e);
				}
	        }
		}

		@Override
		protected void onPostExecute(Exception result) {
			mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
			progress_.dismiss();
			if (result == null) {
				toast("Backup complete!");
			} else {
				toast("Backup failed: " + result.getMessage());
			}

		}
	}

	class SDCardRestore extends AsyncTask<Void, Integer, Exception> {

		ProgressDialog progress_;
		SQLiteOpenHelper helper_;

		@Override
		protected void onPreExecute() {
			helper_ = App.getDatabaseSource(mActivity);
			progress_ = new ProgressDialog(mActivity);
			progress_.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			progress_.setOnKeyListener(new IgnoreSearchKeyListener());
			progress_.setCancelable(false);
			progress_.setMessage("Restoring from SD card.");
			progress_.setMax(100);
			progress_.setIndeterminate(false);
			progress_.show();
			int orientation = getResources().getConfiguration().orientation;
			mActivity.setRequestedOrientation(orientation);
		}
		@Override
		protected void onProgressUpdate(Integer... values) {
			progress_.setProgress(values[0].intValue());
		}

		@Override
		protected Exception doInBackground(Void... params) {
			FileInputStream in = null;
			FileOutputStream out = null;
			try {
				helper_.getWritableDatabase().beginTransaction();
				String extStorageDirectory = Environment
						.getExternalStorageDirectory().toString() + "/Musubi/";
				String dbPath = extStorageDirectory + DatabaseFile.DEFAULT_DATABASE_NAME;

				File newDb = new File(dbPath);
                File oldDb = mActivity.getDatabasePath(DatabaseFile.DEFAULT_DATABASE_NAME + ".torestore");
				if (!newDb.exists()) {
					throw new RuntimeException("Backup database not found");
				}
				in = new FileInputStream(newDb);
				out = new FileOutputStream(oldDb);
				long file_size = newDb.length();
				byte[] buf = new byte[65536];
				int len;
				long so_far = 0;
				while ((len = in.read(buf)) > 0) {
					out.write(buf, 0, len);
					so_far += len;
					publishProgress((int) (100 * so_far / (file_size + 1)));
				}
				in.close();
				out.close();
				//kill because the process so that it restarts and finishes the restore
				
				SharedPreferences settings = mActivity.getSharedPreferences(WizardStepHandler.WIZARD_PREFS_NAME, 0);
            	SharedPreferences.Editor editor = settings.edit();
    	    	editor.putBoolean(WizardStepHandler.DO_RESTORE, true);
    	    	editor.commit();
    	    	
				Process.killProcess(Process.myPid());
				return null;
			} catch (Exception e) {
				Log.e(TAG, "Failure restoring from SD card", e);
				return e;
	        } finally {
	        	try {
					if(in != null) out.close();
					if(out != null) out.close();
				} catch (IOException e) {
					Log.e(TAG, "failed to close streams for backup", e);
				}
	        }
		}

		@Override
		protected void onPostExecute(Exception result) {
			mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
			progress_.dismiss();
			if (result == null) {
				//we'll never get here because it will have restarted
				toast("Restore complete!");
			} else {
				toast("Restore failed: " + result.getMessage());
			}

		}
	}

	/*@Override
	protected void onResume() {
		super.onResume();
		loadValues();
	}*/

	void loadValues() {
		boolean developer_mode = MusubiBaseActivity.isDeveloperModeEnabled(getActivity());
		globalTVMode_.setVisibility(developer_mode ? View.VISIBLE
				: View.GONE);
		wifiFingerprinting_.setVisibility(developer_mode ? View.VISIBLE
				: View.GONE);
		vacuumDatabase_.setVisibility(developer_mode ? View.VISIBLE
				: View.GONE);
		shareApps_.setVisibility(developer_mode ? View.VISIBLE
				: View.GONE);

		SharedPreferences p = mActivity.getSharedPreferences(SettingsActivity.PREFS_NAME, 0);
		boolean global_tv_mode = p.getBoolean(PREF_AUTOPLAY, PREF_AUTOPLAY_DEFAULT);
		globalTVMode_.setChecked(global_tv_mode);
		boolean wifi_fingerprinting = p.getBoolean(PREF_WIFI_FINGERPRINTING, PREF_WIFI_FINGERPRINTING_DEFAULT);
		wifiFingerprinting_.setChecked(wifi_fingerprinting);
		boolean share_apps = p.getBoolean(PREF_SHARE_APPS, PREF_SHARE_APPS_DEFAULT);
		shareApps_.setChecked(share_apps);
		boolean share_contact = p.getBoolean(SettingsActivity.PREF_SHARE_CONTACT_ADDRESS, SettingsActivity.PREF_SHARE_CONTACT_ADDRESS_DEFAULT);
		shareContactInfo_.setChecked(share_contact);

		boolean vibrating = p.getBoolean(PREF_VIBRATING, PREF_VIBRATING_DEFAULT);
		vibrating_.setChecked(vibrating);
		
	}

	public void toast(String msg) {
		Toast error = Toast.makeText(mActivity, msg, Toast.LENGTH_SHORT);
		error.show();
	}

	private void toggleDevMode() {
	    boolean developer_mode = MusubiBaseActivity.isDeveloperModeEnabled(getActivity());
        toast(developer_mode ? "Disabling developer mode."
                : "Enabling developer mode.");
        MusubiBaseActivity.setDeveloperMode(getActivity(), !developer_mode);
        // update the dialog by hiding stuff, etc
        loadValues();
	}

}