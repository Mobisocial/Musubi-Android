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

import java.util.LinkedHashSet;

import mobisocial.metrics.MusubiMetrics;
import mobisocial.musubi.App;
import mobisocial.musubi.Helpers;
import mobisocial.musubi.R;
import mobisocial.musubi.model.MApp;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.helpers.AppManager;
import mobisocial.musubi.model.helpers.FeedManager;
import mobisocial.musubi.model.helpers.MyAccountManager;
import mobisocial.musubi.objects.IntroductionObj;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.provider.MusubiContentProvider.Provided;
import mobisocial.musubi.ui.util.UiUtil;
import mobisocial.musubi.ui.widget.MultiIdentitySelector;
import mobisocial.musubi.util.ObjFactory;
import mobisocial.socialkit.Obj;
import android.app.Activity;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.RelativeLayout;

public class CreateAppFeedActivity extends Activity {

    RelativeLayout mWindow;

    MultiIdentitySelector mIdentitySelector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mWindow = new RelativeLayout(this);
        LayoutParams fill = new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
        mWindow.setLayoutParams(fill);

        // Identity multi-select
        mIdentitySelector = new MultiIdentitySelector(this);
        RelativeLayout.LayoutParams selectorParams = new RelativeLayout.LayoutParams(
                LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
        selectorParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        mIdentitySelector.setLayoutParams(selectorParams);
        mIdentitySelector.setId(R.id.people);
        mWindow.addView(mIdentitySelector);

        Button go = new Button(this);
        go.setText(R.string.go);
        go.setOnClickListener(mCreateFeedListener);
        RelativeLayout.LayoutParams goParams = new RelativeLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        goParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        goParams.addRule(RelativeLayout.BELOW, R.id.people);
        go.setLayoutParams(goParams);
        mWindow.addView(go);

        setContentView(mWindow);
    }

    View.OnClickListener mCreateFeedListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            LinkedHashSet<MIdentity> identities = mIdentitySelector.getSelectedIdentities();
            if (identities.size() == 0) {
                return;
            }
            Activity activity = CreateAppFeedActivity.this;
            //explicit user control of identity is handled by putting yourself in the feed list
            SQLiteDatabase db = App.getDatabaseSource(activity).getReadableDatabase();
            FeedManager fm = new FeedManager(db);
            MyAccountManager am = new MyAccountManager(db);

            MFeed feed = fm.createExpandingFeed(identities.toArray(new MIdentity[]{}));
            feed.accepted_ = false;
            fm.updateFeed(feed);

            String appName = ObjFactory.getCallerAppId(activity, getIntent());
            if (appName == null) {
                setResult(RESULT_CANCELED);
                finish();
                return;
            }

            MApp app = new AppManager(db).ensureApp(appName);
            fm.ensureFeedApp(feed.id_, app.id_);

            Uri feedUri = MusubiContentProvider.uriForItem(Provided.FEEDS, feed.id_);
            UiUtil.addToWhitelistsIfNecessary(fm, am, fm.getFeedMembers(feed), true);

            //introduce your buddies so they have names for each other
            Obj invitedObj = IntroductionObj.from(identities, true);
            Helpers.sendToFeed(activity, invitedObj, feedUri);

            App.getUsageMetrics(activity).report(MusubiMetrics.FEED_CREATED_APP);

            Intent data = new Intent();
            data.setData(feedUri);
            setResult(RESULT_OK, data);
            finish();
        }
    };
}
