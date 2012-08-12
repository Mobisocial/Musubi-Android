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

package mobisocial.musubi.util;

import java.util.Date;

import android.database.ContentObserver;
import android.os.Handler;

public class LessSpammyContentObserver extends ContentObserver {
    //at most 4 / minute
    private static final int ONCE_PER_PERIOD = 15 * 1000;
	private long mLastRun;
	private boolean mScheduled;
	private Handler mHandler;

    public LessSpammyContentObserver(Handler handler) {
        super(handler);
        mHandler = handler;
    }
    public void resetTimeout() {
    	mLastRun = 0;
    }
    @Override
    public final void onChange(boolean selfChange) {
        long now = new Date().getTime();
    	if(mLastRun + ONCE_PER_PERIOD > now) {
    		//wake up when the period expires
    		if(!mScheduled) {
    			mHandler.postDelayed(new Runnable() {
					@Override
					public void run() {
				        mScheduled = false;
						dispatchChange(false);
					}
				}, ONCE_PER_PERIOD - (now - mLastRun) + 1);
    		}
    		mScheduled = true;
    		//skip this update
    		return;
    	}
    	mLastRun = now;
    	lessSpammyOnChange(selfChange);
    }

	public void lessSpammyOnChange(boolean selfChange) {};
}
