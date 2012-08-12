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
import java.util.List;

import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.musubi.App;
import mobisocial.musubi.R;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.ui.util.UiUtil;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

/**
 * Pick contacts and/or groups for various purposes.
 * TODO: Remove TabActivity in favor of fragments;
 * Make activity a floating window.
 * 
 * TODO: Picker should return personId, not id.
 */
public class EmailInviteActivity {

    public static final Intent getInviteIntentForEmail(Context context, String email) {
        String message = getInvitationMessageFull(context);

        Intent intent = new Intent(Intent.ACTION_SEND);       
        intent.setType("text/plain");
        if (email != null) {
            intent.putExtra(Intent.EXTRA_EMAIL, new String[] { email });
        }
        intent.putExtra(Intent.EXTRA_SUBJECT, "Join me on Musubi!");
        intent.putExtra(Intent.EXTRA_TEXT, message);
        return intent;
    }

    public static Intent getInviteIntent(Context context) {
        return getInviteIntentForEmail(context, null);
    }

    public static Uri getInvitationUrl(Context context) {
        IdentitiesManager identitiesManager = new IdentitiesManager(App.getDatabaseSource(context));
        List<MIdentity> ids = identitiesManager.getOwnedIdentities();
        String name = null;
        for (MIdentity id : ids) {
            if(id.type_ == Authority.Local) {
                continue;
            }
            name = UiUtil.internalSafeNameForIdentity(id);
            if (name != null) break;
        }
        Uri.Builder builder = new Uri.Builder().scheme("https").authority("musubi.us")
                .appendPath("intro");
        if (name != null) {
            builder.appendQueryParameter("n", name);
        }

        for(MIdentity id : ids) {
            if(id.type_ == Authority.Local) {
                continue;
            }
            builder
                .appendQueryParameter("t", "" + id.type_.ordinal())
                .appendQueryParameter("p", id.principal_);
        }
        return builder.build();
    }

    public static String getInvitationMessageFull(Context context ) {
        return new StringBuilder(250).append(context.getResources().getString(R.string.join_email_body))
                .append("\n")
                .append(EmailUnclaimedMembersActivity.MUSUBI_MARKET_URL)
                .append("\n").append("\n")
                .append(context.getResources().getString(R.string.join_email_subtext))
                .append("\n")
                .append(getInvitationUrl(context)).toString();
    }
}