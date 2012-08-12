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

package mobisocial.musubi.feed.presence;

import mobisocial.musubi.Helpers;
import mobisocial.musubi.feed.iface.FeedPresence;
import mobisocial.musubi.objects.StoryObj;
import mobisocial.socialkit.obj.MemObj;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.widget.Toast;

public class DiivaPresence extends FeedPresence {
    private boolean mShareDiiva = false;

    @Override
    public String getName() {
        return "DiiVA";
    }

    @Override
    public void onPresenceUpdated(final Context context, final Uri feedUri, boolean present) {
        if (mShareDiiva) {
            if (getFeedsWithPresence().size() == 0) {
                context.getApplicationContext().unregisterReceiver(mReceiver);
                Toast.makeText(context, "No longer sharing DiiVA", Toast.LENGTH_SHORT).show();
                mShareDiiva = false;
            }
        } else {
            if (getFeedsWithPresence().size() > 0) {
                IntentFilter iF = new IntentFilter();
                iF.addAction("edu.stanford.mobisocial.Musubi.DiiVA");
                context.getApplicationContext().registerReceiver(mReceiver, iF);
                Toast.makeText(context, "Now sharing DiiVA", Toast.LENGTH_SHORT).show();
                mShareDiiva = true;
            }
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            if (mShareDiiva) {
            	
                String url = intent.getStringExtra("url");
                String mimetype = intent.getType();
                String title = intent.getStringExtra("title");
                MemObj obj = StoryObj.from(null, url, mimetype, null, title, null, null);
                
                for (Uri feedUri : getFeedsWithPresence()) {
                    Helpers.sendToFeed(context.getApplicationContext(), obj, feedUri);
                }
            }
        }
    };
}
