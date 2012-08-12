/*
 * Copyright (C) 2011 Wglxy.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

import mobisocial.musubi.App;
import mobisocial.musubi.BootstrapActivity;
import mobisocial.musubi.RemoteControlReceiver;
import mobisocial.musubi.model.PresenceAwareNotify;
import mobisocial.musubi.service.MusubiService;
import mobisocial.musubi.util.ActivityCallout;
import mobisocial.musubi.util.InstrumentedActivity;
import mobisocial.musubi.util.RemoteControlRegistrar;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.MenuItem;
import android.view.KeyEvent;
import android.widget.Toast;

/**
 * This is the base class for activities in the dashboard application. It
 * implements methods that are useful to all top level activities. That
 * includes: (1) stub methods for all the activity lifecycle methods; (2)
 * onClick methods for clicks on home, search, feature 1, feature 2, etc. (3) a
 * method for displaying a message to the screen via the Toast class.
 */

public abstract class MusubiBaseActivity extends FragmentActivity
        implements InstrumentedActivity {
    protected static final String TAG = "MusubiActivity";
    private static int REQUEST_ACTIVITY_CALLOUT = 39;
    private static ActivityCallout mCurrentCallout;
    public static final boolean DBG = false;
    //if this is set then this is a dead activity, i.e. it will be finished before
    //the end of onCreate
    protected boolean mBootstrapping;

    /**
     * onCreate - called when the activity is first created. Called when the
     * activity is first created. This is where you should do all of your normal
     * static set up: create views, bind data to lists, etc. This method also
     * provides you with a Bundle containing the activity's previously frozen
     * state, if there was one. Always followed by onStart().
     */

    protected SQLiteOpenHelper mHelper;
    private RemoteControlRegistrar remoteControlRegistrar;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBootstrapping = BootstrapActivity.bootstrapIfNecessary(this);
        mHelper = App.getDatabaseSource(this);
        remoteControlRegistrar = new RemoteControlRegistrar(this, RemoteControlReceiver.class);
        //in case there was an FC, we must restart the service whenever one of our dialogs is opened.
        if (!mBootstrapping) {
            startService(new Intent(this, MusubiService.class));
        }
    }

    /**
     * onDestroy The final call you receive before your activity is destroyed.
     * This can happen either because the activity is finishing (someone called
     * finish() on it, or because the system is temporarily destroying this
     * instance of the activity to save space. You can distinguish between these
     * two scenarios with the isFinishing() method.
     */

    protected void onDestroy() {
        super.onDestroy();
    }

    /**
     * onPause Called when the system is about to start resuming a previous
     * activity. This is typically used to commit unsaved changes to persistent
     * data, stop animations and other things that may be consuming CPU, etc.
     * Implementations of this method must be very quick because the next
     * activity will not be resumed until this method returns. Followed by
     * either onResume() if the activity returns back to the front, or onStop()
     * if it becomes invisible to the user.
     */

    protected void onPause() {
        super.onPause();
        mResumed = false;
    }

    /**
     * onRestart Called after your activity has been stopped, prior to it being
     * started again. Always followed by onStart().
     */

    protected void onRestart() {
        super.onRestart();
    }

    /**
     * onResume Called when the activity will start interacting with the user.
     * At this point your activity is at the top of the activity stack, with
     * user input going to it. Always followed by onPause().
     */
    protected void onResume() {
        super.onResume();
        //any time an activity changes reset out network related stuff
        getContentResolver().notifyChange(MusubiService.USER_ACTIVITY_RESUME, null);
        mResumed = true;
    }

    /**
     * onStart Called when the activity is becoming visible to the user.
     * Followed by onResume() if the activity comes to the foreground, or
     * onStop() if it becomes hidden.
     */

    protected void onStart() {
        super.onStart();

        remoteControlRegistrar.registerRemoteControl();
        KeyguardManager kg = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (!kg.inKeyguardRestrictedInputMode()) {
            new PresenceAwareNotify(this).cancelAll();
        }
    }

    /**
     * onStop Called when the activity is no longer visible to the user because
     * another activity has been resumed and is covering this one. This may
     * happen either because a new activity is being started, an existing one is
     * being brought in front of this one, or this one is being destroyed.
     * Followed by either onRestart() if this activity is coming back to
     * interact with the user, or onDestroy() if this activity is going away.
     */

    protected void onStop() {
        super.onStop();
        remoteControlRegistrar.unregisterRemoteControl();
    }

    /**
     * Go back to the home activity.
     * 
     * @param context Context
     * @return void
     */
    private void goHome() {
        final Intent intent = new Intent(this, FeedListActivity.class);
        if (Build.VERSION.SDK_INT < 11) {
        	intent.setFlags (Intent.FLAG_ACTIVITY_CLEAR_TOP);
        } else { 
    		intent.setFlags (Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        startActivity(intent);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            goHome();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void toast(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MusubiBaseActivity.this, text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void showDialog(DialogFragment newFragment) {
        showDialog(newFragment, true);
    }

    public void showDialog(DialogFragment newFragment, boolean keepBackstack) {
     // DialogFragment.show() will take care of adding the fragment
        // in a transaction.  We also want to remove any currently showing
        // dialog, so make our own transaction and take care of that here.
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        Fragment prev = getSupportFragmentManager().findFragmentByTag("dialog");
        if (prev != null) {
            ft.remove(prev);
        }
        if (keepBackstack) {
            ft.addToBackStack(null);
        }
        newFragment.show(ft, "dialog");
    }

    public void doActivityForResult(ActivityCallout callout) {
        mCurrentCallout = callout;
        Intent launch = callout.getStartIntent();
        if(launch != null)
        	startActivityForResult(launch, REQUEST_ACTIVITY_CALLOUT);
        else {
        	Toast.makeText(this, "Callback for object type failed! " + callout.getClass().getName(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // TODO: Hack for weirdness in Fragment onActivityResult...
        Fragment f = getSupportFragmentManager().findFragmentByTag("dialog");
        if (f != null) {
            f.onActivityResult(requestCode, resultCode, data);
        }

        if (requestCode == REQUEST_ACTIVITY_CALLOUT) {
            mCurrentCallout.handleResult(resultCode, data);
        }
    }

    public boolean isDeveloperModeEnabled() {
        return getSharedPreferences("main", 0).getBoolean("dev_mode", false);
    }

    public static boolean isTVModeEnabled(Context c) {
        return c.getSharedPreferences("main", 0).getBoolean("autoplay", false);
    }

    public static boolean isDeveloperModeEnabled(Context c) {
        return c.getSharedPreferences("main", 0).getBoolean("dev_mode", false);
    }

    public static void setDeveloperMode(Context context, boolean enabled) {
        context.getSharedPreferences("main", 0).edit().putBoolean("dev_mode", enabled).commit();
    }

    public RemoteControlRegistrar getRemoteControlRegistrar() {
        return remoteControlRegistrar;
    }

    private static boolean mResumed;
    public static boolean isResumed() {
        return mResumed;
    }

    private KeyEvent.Callback mOnKeyListener;
    public void setOnKeyListener(KeyEvent.Callback listener) {
        mOnKeyListener = listener;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (mOnKeyListener != null) {
            if (mOnKeyListener.onKeyUp(keyCode, event)) {
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (mOnKeyListener != null) {
            if (mOnKeyListener.onKeyLongPress(keyCode, event)) {
                return true;
            }
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mOnKeyListener != null) {
            if (mOnKeyListener.onKeyDown(keyCode, event)) {
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) {
        if (mOnKeyListener != null) {
            if  (mOnKeyListener.onKeyMultiple(keyCode, repeatCount, event)) {
                return true;
            }
        }
        return super.onKeyMultiple(keyCode, repeatCount, event);
    }
}