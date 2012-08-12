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

package mobisocial.musubi.ui.util;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LabeledIntent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.util.Log;

/**
 * A utility class that launches a resolved intent for a result
 * and returns back to the caller the component used to carry out the intent.
 */
public class IntentProxyActivity extends Activity {
    static final String TAG = "IntentProxy";
    public static final String ACTION_PROXY = "musubi.intent.action.PROXY_INTENT";

    /**
     * The intent to proxy.
     */
    public static final String EXTRA_REAL_INTENT = "intent";

    /**
     * The component that was resolved for the given intent,
     * available when returning a result.
     */
    public static final String EXTRA_RESOLVED_COMPONENT = "cn";

    private ComponentName mResolvedComponent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!getIntent().hasExtra(EXTRA_REAL_INTENT)) {
            throw new IllegalArgumentException("Must set real intent");
        }
        // XXX Intents seem to have some horrid resolution issues without the copy constructor
        // likely due to LabeledIntents / sourcePackage.
        Intent real = new Intent((Intent)getIntent().getParcelableExtra(EXTRA_REAL_INTENT));
        ResolveInfo info = getPackageManager().resolveActivity(real,
                PackageManager.MATCH_DEFAULT_ONLY);
        if (info == null) {
            Log.w(TAG, "couldn't resolve intent " + real);
            finish();
            return;
        }
        mResolvedComponent = new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
        if(real instanceof LabeledIntent) {
        	real = new Intent(real);
        }
        startActivityForResult(real, 0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (data == null) data = new Intent();
        data.putExtra(EXTRA_RESOLVED_COMPONENT, mResolvedComponent);
        setResult(resultCode, data);
        finish();
    }

    static int count=0;
    public static Intent getProxyIntent(Context context, Intent orig) {
        Intent intent;
        if (orig instanceof LabeledIntent) {
            LabeledIntent labeled = (LabeledIntent) orig;
            intent = new LabeledIntent(orig, labeled.getSourcePackage(), labeled.getNonLocalizedLabel(),
                    labeled.getIconResource());
        } else {
            ResolveInfo r = context.getPackageManager().resolveActivity(
                    orig, PackageManager.MATCH_DEFAULT_ONLY);
            if (r == null) {
                return null;
            } else {
                String pkg = r.activityInfo.packageName;
                CharSequence label = r.loadLabel(context.getPackageManager());
                int iconRes = r.getIconResource();
                intent = new LabeledIntent(pkg, label, iconRes);
            }
        }

        intent.setAction(ACTION_PROXY);
        intent.setComponent(null);
        intent.setClass(context, IntentProxyActivity.class);
        intent.putExtra(EXTRA_REAL_INTENT, orig);
        intent.setPackage(context.getPackageName());        
        return intent;
    }
}
