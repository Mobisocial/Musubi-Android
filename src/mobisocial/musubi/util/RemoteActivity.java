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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

public class RemoteActivity implements ActivityCallout {
    private static final String ACTION_LAUNCH_TAPBOARD = "mobisocial.db.action.UPDATE_STATUS";
	private final ResultHandler mResultHandler;
	@SuppressWarnings("unused")
	private final Context mContext;

	public RemoteActivity(Context c, ResultHandler handler) {
		mContext = c;
		mResultHandler = handler;
	}

	@Override
	public Intent getStartIntent() {
		final Intent intent = new Intent(ACTION_LAUNCH_TAPBOARD); // TODO
		return intent;
	}

	@Override
	public void handleResult(int resultCode, Intent resultData) {
		if (resultCode != Activity.RESULT_OK) {
			return;
		}
		String data = resultData.getStringExtra(Intent.EXTRA_TEXT);
		mResultHandler.onResult(data);
	}

	public interface ResultHandler {
		public void onResult(String data);
	}
}