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

import mobisocial.musubi.R;
import mobisocial.musubi.ui.fragments.FeedListFragment;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/**
 * Displays a list of all user-accessible threads (feeds).
 */
public class PickFeedActivity extends MusubiBaseActivity implements
        FeedListFragment.OnFeedSelectedListener {

    public static final boolean DBG = true;
    public static final String TAG = "Pick Feed Activity";

    private FeedListFragment mFeedListFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_feed_list);
        mFeedListFragment = new FeedListFragment();
        Bundle args = new Bundle();
        args.putBoolean("no_nearby", true);
        mFeedListFragment.setArguments(args);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
            .replace(R.id.feed_list, mFeedListFragment).commit();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
    	super.onSaveInstanceState(outState);
    }
    
    @Override
    public void onFeedSelected(Uri feedUri) {
    	Intent result = new Intent();
    	result.setData(feedUri);
    	setResult(RESULT_OK, result);
    	finish();
    }
}
