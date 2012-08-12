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

import mobisocial.crypto.IBHashedIdentity;
import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.musubi.App;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MMyAccount;
import mobisocial.musubi.model.helpers.FeedManager;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.model.helpers.MyAccountManager;
import mobisocial.musubi.obj.ObjHelpers;
import mobisocial.musubi.service.MusubiService;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Intents.Insert;
import android.util.Log;
import android.view.View;

public class AddToWhitelistListener implements View.OnClickListener {
    final Context context;
    final MIdentity sender;

    public AddToWhitelistListener(Context context, MIdentity sender) {
        this.context = context;
        this.sender = sender;
    }

    @Override
    public void onClick(View v) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        //name = cursor.getString(cursor.getColumnIndexOrThrow(Contact.NAME));

        SQLiteOpenHelper databaseSource = App.getDatabaseSource(context);
        final IdentitiesManager identityManager = new IdentitiesManager(databaseSource);
        final FeedManager feedManager = new FeedManager(databaseSource);
        final MyAccountManager accountManager = new MyAccountManager(databaseSource);
         
        String name = UiUtil.safeNameForIdentity(sender);
        builder = new AlertDialog.Builder(context);
        //name = cursor.getString(cursor.getColumnIndexOrThrow(Contact.NAME));
        builder.setMessage("Do you want to add " + name + " to your friends list?")
               .setCancelable(true)
               .setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                       sender.whitelisted_ = true;
                       sender.blocked_ = false;
                       //force a profile exchange to ensure we get an icon after a user action
                       sender.sentProfileVersion_ = 0;
                       identityManager.updateIdentity(sender);
                       feedManager.acceptFeedsFromMember(context, sender.id_);
                       //stop sending them broadcasts, e.g. profiles
                       MMyAccount[] accounts =  accountManager.getMyAccounts();
                       boolean found_specific_account = false;
                       for(MMyAccount account : accounts) {
                           //TODO: someday you can remove these checks
                           assert(account.identityId_ != null);
                           if(account.identityId_ == null) continue;
                           assert(account.feedId_ != null);
                           if(account.feedId_ == null) continue;
                           
                           //check if this member is on a provisional whitelist, that's the identity we want to put them in
                           if(!account.accountType_.equals(MMyAccount.INTERNAL_ACCOUNT_TYPE))
                               continue;
                           if(!account.accountName_.equals(MMyAccount.PROVISIONAL_WHITELIST_ACCOUNT))
                               continue;
                           MMyAccount whitelist_account = accountManager.getWhitelistForIdentity(account.identityId_);
                           assert(whitelist_account != null); //should be guaranteed
                           assert(whitelist_account.feedId_ != null); //should be guaranteed
                           feedManager.ensureFeedMember(whitelist_account.feedId_, sender.id_);
                           found_specific_account = true;
                       }
                       //put them in an unassociated group which will always use the default identity whatever that is
                       if(!found_specific_account) {
                           feedManager.ensureFeedMember(MFeed.NONIDENTITY_SPECIFIC_WHITELIST_ID, sender.id_);
                       }
                      context.getContentResolver().notifyChange(MusubiService.WHITELIST_APPENDED, null);
                     context.getContentResolver().notifyChange(MusubiService.COLORLIST_CHANGED, null);
                       if(sender.type_ != Authority.Facebook && //TODO:facebook ids should be handled by adding an FB friend 
                               sender.principal_ != null && 
                               sender.androidAggregatedContactId_ == null) {
                           //Can whitelist by adding a contact
                           Intent i = new Intent(Intent.ACTION_INSERT_OR_EDIT);
                           i.setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE);
                           
                           if(sender.musubiName_ != null) {
                               i.putExtra(Insert.NAME, sender.musubiName_);
                           }
                           if (sender.type_ == IBHashedIdentity.Authority.Email) {
                               i.putExtra(Insert.EMAIL, UiUtil.safePrincipalForIdentity(sender));
                           }
                           Log.w(ObjHelpers.TAG, UiUtil.safePrincipalForIdentity(sender));
                           Log.w(ObjHelpers.TAG, sender.type_.toString());
                           context.startActivity(i);
                       }
                       context.getContentResolver().notifyChange(MusubiService.WHITELIST_APPENDED, null);
                       dialog.cancel();
                   }
               })
               .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                   public void onClick(DialogInterface dialog, int id) {
                       dialog.cancel();
                   }
               });
        AlertDialog alert = builder.create();
        alert.show();
    }
}
