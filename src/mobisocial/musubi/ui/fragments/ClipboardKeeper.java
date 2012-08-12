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

import mobisocial.musubi.App;
import mobisocial.socialkit.Obj;
import android.app.Activity;
import android.widget.Toast;

public class ClipboardKeeper {
    static final String TAG = "ClipboardKeeper"; 
    final Activity mContext;

    public ClipboardKeeper(Activity context)  {
        mContext = context;
    }

    public void  store(Obj obj) {
        store(obj, true);
    }

    public void store(Obj obj, boolean toast) {
        App.clipboardObject = obj;

        if (toast) {
            Toast.makeText(mContext,
                    "Put new content on your clipboard. Use the pin menu to paste it.",
                    Toast.LENGTH_LONG).show();
        }
    }

    public Obj get() {
        return App.clipboardObject;
    }
}
