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

import java.util.ArrayList;

import mobisocial.metrics.MusubiMetrics;
import mobisocial.metrics.UsageMetrics;
import mobisocial.musubi.BJDNotImplementedException;
import mobisocial.musubi.R;
import mobisocial.musubi.model.DbContactAttributes;
import mobisocial.musubi.nearby.NearbyLookup;
import mobisocial.musubi.nearby.NearbyLookup.NearbyResultListener;
import mobisocial.musubi.nearby.broadcast.MulticastBroadcastTask;
import mobisocial.musubi.nearby.item.NearbyItem;
import mobisocial.musubi.util.ActivityCallout;
import mobisocial.musubi.util.BluetoothBeacon;
import mobisocial.musubi.util.InstrumentedActivity;
import mobisocial.musubi.util.Util;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

/**
 * Shows groups and users that have been found nearby.
 * 
 * TODO: GetNearbyDevicesTask
 *    onDeviceDiscovered(User d) {
 *    }
 */
public class NearbyActivity extends MusubiBaseActivity implements
					InstrumentedActivity, OnItemClickListener {
    private static final String TAG = "Nearby";
    private static boolean DBG = true;

    private NearbyAdapter mAdapter;
    private final ArrayList<NearbyItem> mNearbyList = new ArrayList<NearbyItem>();
    private static final int RESULT_BT_ENABLE = 1;

    private MulticastBroadcastTask mMulticastBroadcaster;
    private WifiManager mWifiManager;
    NearbyLookup.LookupFuture mNearbyLookup;

    public void onClickHome (View v)
    {
        goHome (this);
    }

    public void goHome(Context context) 
    {
        final Intent intent = new Intent(context, FeedListActivity.class);
        if(Build.VERSION.SDK_INT < 11)
        	intent.setFlags (Intent.FLAG_ACTIVITY_CLEAR_TOP);
    	else 
    		intent.setFlags (Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity (intent);
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nearby);

        mWifiManager = (WifiManager)getSystemService(Context.WIFI_SERVICE);
		
        findViewById(R.id.qr).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                IntentIntegrator.initiateScan(NearbyActivity.this);
            }
        });
        findViewById(R.id.qr).setVisibility(View.GONE);
        findViewById(R.id.go).setVisibility(View.GONE);
        
        DBG = MusubiBaseActivity.isDeveloperModeEnabled(this);
        setTitle("Nearby");
        mAdapter = new NearbyAdapter(this, R.layout.nearby_groups_item, mNearbyList);

        ListView lv = (ListView)findViewById(android.R.id.list); 
        lv.setAdapter(mAdapter);
        lv.setOnItemClickListener(this);

        if (true) {
            findViewById(R.id.social).setVisibility(View.GONE);
        } else {
            setupMulticastScanner();
        }
        EditText tv = (EditText)findViewById(R.id.password);
        tv.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}
			@Override
			public void afterTextChanged(Editable s) {
                synchronized (NearbyActivity.this) {
                    mNearbyList.clear();
                    mAdapter.notifyDataSetChanged();
                }
				scanNearby();
			}
		});

        
        if (savedInstanceState == null) {
        	UsageMetrics.getUsageMetrics(this).report(MusubiMetrics.VISITED_NEARBY);
        }
        
        String provider = Settings.Secure.getString(getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
		if (provider == null || !provider.contains("gps") || !provider.contains("network")) { 
			new AlertDialog.Builder(this)
				.setTitle("Location Settings")
				.setMessage("You should enable both network-based and GPS-based location services to ensure you can find nearby groups.")
				.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
					}
				})
				.setPositiveButton("Go to Settings", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						try {
							 Intent myIntent = new Intent( Settings.ACTION_LOCATION_SOURCE_SETTINGS);
							 startActivity(myIntent);
						} catch(Throwable t) { Log.e(TAG, "failed to launch location settings", t);}
					}
				}).show();
		}        
    }

	private void setupMulticastScanner() {
		CheckBox checkbox = (CheckBox)findViewById(R.id.social);
		mMulticastBroadcaster = MulticastBroadcastTask.getInstance(NearbyActivity.this);
		if (mMulticastBroadcaster.isRunning()) {
		    checkbox.setChecked(true);
		}
		checkbox.setOnCheckedChangeListener(
		    new CompoundButton.OnCheckedChangeListener() {
		        @Override
		        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
		            // TODO: Generalize to NearbyBroadcaster; do multicast, bt, gps, dns
		            if (buttonView.isChecked()) {
		                if (mMulticastBroadcaster == null) {
		                    mMulticastBroadcaster =
		                            MulticastBroadcastTask.getInstance(NearbyActivity.this);
		                }
		                mMulticastBroadcaster.execute();
		            } else {
		                mMulticastBroadcaster.cancel(true);
		                mMulticastBroadcaster = null;
		            }
		        }
		    });
	}
    
    @Override
    public boolean onCreateOptionsMenu(android.support.v4.view.Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.nearby_activity, menu);
        menu.findItem(R.id.menu_pin).setVisible(false);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.support.v4.view.MenuItem item) {
        Log.d(TAG, " item " + item);
        switch (item.getItemId()) {
            case R.id.menu_refresh: {
                synchronized (NearbyActivity.this) {
                    mNearbyList.clear();
                    mAdapter.notifyDataSetChanged();
                }
                scanNearby();
                return true;
            }
            case R.id.menu_pin: {
                doCheckin();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private void doCheckin() {
        WifiInfo wifi = mWifiManager.getConnectionInfo();
        String myWifiName = wifi.getSSID();
        if (myWifiName == null) {
            /**
             * TODO:
             * Depending on desired outcome, either help them get on wifi
             * or try connecting to gps server, etc.
             */
            toast("No wifi network available.");
            return;
        }

        String myWifiId = wifi.getBSSID();
        String myIp = formatIp(wifi.getIpAddress());
		String myWifiFingerprint = Util.computeWifiFingerprint(mWifiManager.getScanResults());
		
        if (DBG) Log.d(TAG, "Checking in to " + myWifiName + "...");
        JSONObject loc = new JSONObject();
        try {
            loc.put(DbContactAttributes.ATTR_WIFI_SSID, myWifiName);
            if (myWifiId != null) {
                loc.put(DbContactAttributes.ATTR_WIFI_BSSID, myWifiId);
            }
            if (myIp != null) {
                loc.put(DbContactAttributes.ATTR_LAN_IP, myIp);
            }
            loc.put(DbContactAttributes.ATTR_WIFI_FINGERPRINT, myWifiFingerprint);
        } catch (JSONException e) {
            // Impossible json exception
        }
        
        //XXX killed for now
        //mMusubi.getAppFeed().postObj(new MemObj("locUpdate", loc));
        //toast("Checked in to '" + myWifiName + "'.");
    }

    private String formatIp(int ip) {
        return String.format("%d.%d.%d.%d",
                (ip & 0xff), (ip >> 8 & 0xff), (ip >> 16 & 0xff), (ip >> 24 & 0xff));
    }

    private void scanNearby() {
        if (DBG) Log.d(TAG, "initiating nearby scan...");
        String password = ((EditText) findViewById(R.id.password)).getText().toString();

        if (mNearbyLookup != null) {
            mNearbyLookup.cancel(true);
        }
        NearbyLookup lookup = new NearbyLookup(this, password);
        mNearbyLookup = lookup.doLookup(mLookupResultListener);
    }

    @Override
    protected void onResume() {
        super.onResume();

        mAdapter.notifyDataSetChanged();
        scanNearby();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mNearbyLookup != null) {
            mNearbyLookup.cancel(true);
            mNearbyLookup = null;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ACTIVITY_CALLOUT) {
            mCurrentCallout.handleResult(resultCode, data);
            return;
        }

        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null && result.getContents() != null) {
            try {
                Uri uri = Uri.parse(result.getContents());
                Intent i = new Intent(Intent.ACTION_VIEW, uri);
                i.setPackage(getPackageName());
                startActivity(i);
                finish();
            } catch (IllegalArgumentException e) {
            }
            return;
        }
        if (requestCode == RESULT_BT_ENABLE) {
            if (resultCode == Activity.RESULT_CANCELED) {
                finish();
            } else {
                findBluetooth();
            }
        }
    }

    private NearbyResultListener mLookupResultListener = new NearbyResultListener() {
        @Override
        public void onItemDiscovered(NearbyItem item) {
            mNearbyList.add(item);
            mAdapter.notifyDataSetChanged();
        }
        
        @Override
        public void onDiscoveryComplete() {
        }

        @Override
        public void onDiscoveryBegin() {
            mNearbyList.clear();
            mAdapter.notifyDataSetChanged();
        }
    };

    private class NearbyAdapter extends ArrayAdapter<NearbyItem> {
        private ArrayList<NearbyItem> nearby;

        public NearbyAdapter(Context context, int textViewResourceId, ArrayList<NearbyItem> groups) {
            super(context, textViewResourceId, groups);
            this.nearby = groups;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (convertView == null) {
                LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                row = vi.inflate(R.layout.nearby_groups_item, null);
            }
            final NearbyItem g = nearby.get(position);
            TextView text = (TextView) row.findViewById(R.id.name_text);
            text.setText(g.name);
            TextView dtext = (TextView) row.findViewById(R.id.detail_text);
            dtext.setText(g.getDetail());
            ((ImageView)row.findViewById(R.id.icon)).setImageBitmap(g.getIcon());
            return row;
        }
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
        NearbyItem g = mAdapter.getItem(position);
        g.view(this);
    }

    private void findBluetooth() {
        if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            Intent bt = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(bt, RESULT_BT_ENABLE);
            return;
        }

        // Create a BroadcastReceiver for ACTION_FOUND
        final IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);

        final BroadcastReceiver receiver = new BroadcastReceiver() {
            public void onReceive(final Context context, final Intent intent) {
                String action = intent.getAction();
                // When discovery finds a device
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    new Thread() {
                        public void run() {
                            BluetoothBeacon.OnDiscovered discovered = new BluetoothBeacon.OnDiscovered() {
                                @Override
                                public void onDiscovered(final byte[] data) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                JSONObject obj = new JSONObject(new String(data));
                                                BJDNotImplementedException.except("bluetooth not implemented");
//                                                mNearbyList.add(new NearbyFeed(NearbyActivity.this,
//                                                        obj.getString("name"), Uri.parse(obj
//                                                                .getString("dynuri"))));
                                                mAdapter.notifyDataSetChanged();
                                            } catch (JSONException e) {
                                                Log.e(TAG,
                                                        "Error getting group info over bluetooth",
                                                        e);
                                            }
                                        }
                                    });
                                }
                            };
                            // Get the BluetoothDevice object from the Intent
                            BluetoothDevice device = intent
                                    .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                            BluetoothBeacon.discover(NearbyActivity.this, device, discovered);
                        };
                    }.start();
                }
                if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                    unregisterReceiver(this);
                }
            }
        };

        registerReceiver(receiver, filter); // Don't forget to unregister during
                                            // onDestroy
        BluetoothAdapter.getDefaultAdapter().startDiscovery();
        Toast.makeText(this, "Scanning Bluetooth...", 500).show();
    }

    private static int REQUEST_ACTIVITY_CALLOUT = 39;
    private static ActivityCallout mCurrentCallout;

    public void doActivityForResult(ActivityCallout callout) {
        mCurrentCallout = callout;
        Intent launch = callout.getStartIntent();
        startActivityForResult(launch, REQUEST_ACTIVITY_CALLOUT);
    }
}
