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
import mobisocial.musubi.feed.iface.Activator;
import mobisocial.musubi.feed.iface.DbEntryHandler;
import mobisocial.musubi.obj.ObjHelpers;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.provider.MusubiContentProvider.Provided;
import mobisocial.socialkit.musubi.DbObj;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

public class ViewObjectActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        DbObj obj = App.getMusubi(this).objForUri(getIntent().getData());
        if (obj == null) {
            Toast.makeText(this, "No data to view.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        main.setPackage(getPackageName());
        //startActivity(main);

        Uri feedUri = obj.getContainingFeed().getUri();
        Intent feed = new Intent(Intent.ACTION_VIEW);
        feed.setDataAndType(feedUri, MusubiContentProvider.getType(Provided.FEEDS_ID));
        startActivity(feed);

        DbEntryHandler helper = ObjHelpers.forType(obj.getType());
        if (helper instanceof Activator) {
            ((Activator)helper).activate(this, obj);
        }

        finish();
    }
}
