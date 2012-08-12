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

import java.io.IOException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import mobisocial.crypto.IBHashedIdentity;
import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.musubi.App;
import mobisocial.musubi.encoding.NeedsKey;
import mobisocial.musubi.identity.AphidIdentityProvider;
import mobisocial.musubi.identity.IdentityProvider;
import mobisocial.musubi.model.MEncryptionUserKey;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MPendingIdentity;
import mobisocial.musubi.model.MSignatureUserKey;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.model.helpers.PendingIdentityManager;
import mobisocial.musubi.model.helpers.UserKeyManager;
import mobisocial.musubi.service.MusubiService;
import mobisocial.musubi.ui.fragments.AccountLinkDialog;
import mobisocial.musubi.util.Base64;
import mobisocial.musubi.util.Util;

import android.content.Intent;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

/**
 * Adds user keys for the account verified by the provided uri.
 */
public class ClaimKeyActivity extends MusubiBaseActivity {
    public static final String TAG = "ClaimKeyActivity";
    private Uri mUri;
    private IdentitiesManager mIdentitiesManager;
    private PendingIdentityManager mPendingIdentityManager;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View view = new View(this);
        view.setBackgroundColor(Color.TRANSPARENT);
        setContentView(view);
        SQLiteOpenHelper databaseSource = App.getDatabaseSource(this);
        mIdentitiesManager = new IdentitiesManager(databaseSource);
        mPendingIdentityManager = new PendingIdentityManager(databaseSource);
        
    }
    
    @Override
    public void onResume() {
        super.onResume();
        if(getIntent() == null || getIntent().getData() == null) {
            Toast.makeText(this, "No data.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        // Get and validate uri
        mUri = getIntent().getData();
        Log.i(TAG, "Received intent: " + mUri.toString());
        List<String> pathSegments = mUri.getPathSegments();
        if (pathSegments.size() < 8) {
            Toast.makeText(this, "Incomplete data.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Parse the data uri for relevant parameters
        try {
            int requestId = Integer.parseInt(pathSegments.get(1));
            Log.d(TAG, "Request ID: " + requestId);
            Authority authority = Authority.values()[Integer.parseInt(pathSegments.get(2))];
            byte[] hashed = Util.convertToByteArray(pathSegments.get(3));
            long timestamp = Long.parseLong(pathSegments.get(4));
            IBHashedIdentity hid = new IBHashedIdentity(authority, hashed, timestamp);
            Log.d(TAG, "Parsed hashed identity: " + hid);
            byte[] iv = Util.convertToByteArray(pathSegments.get(5));
            Log.d(TAG, "iv: " + pathSegments.get(5));
            // Base64 can encode slashes, breaking segmentation behavior
            String combinedKeys = pathSegments.get(7);
            for (int i = 8; i < pathSegments.size(); i++) {
                combinedKeys += '/';
                combinedKeys += pathSegments.get(i);
            }
            int dividerIndex = combinedKeys.indexOf('|');
            if (dividerIndex == -1) {
                throw new IllegalArgumentException();
            }
            String encryptedEk = combinedKeys.substring(0, dividerIndex);
            String encryptedSk = combinedKeys.substring(dividerIndex + 1, combinedKeys.length());
            Log.d(TAG, "Encrypted encryption key: " + encryptedEk);
            Log.d(TAG, "Encrypted signature key: " + encryptedSk);
            byte[] key = getKeyForIdentity(hid, requestId);
            if (key != null) {
                // Decrypt the keys
                Cipher cipher = getCipher(key, iv);
                byte[] ek = decryptClaimedKey(cipher, encryptedEk);
                byte[] sk = decryptClaimedKey(cipher, encryptedSk);
                Log.d(TAG, "Encryption key: " + Base64.encodeToString(ek, false));
                Log.d(TAG, "Signature key: " + Base64.encodeToString(sk, false));
                
                // Store them somewhere
                clearPendingIdentity(hid);
                claimKeys(hid, sk, ek);
                
                // Present a familiar UI
                Intent launch = new Intent(this, SettingsActivity.class);
                launch.putExtra(SettingsActivity.ACTION,
                        SettingsActivity.SettingsAction.ACCOUNT.toString());
                startActivity(launch);
            }
            else {
                Log.w(TAG, "No key available to handle this request");
            }
            
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Could not parse input URL for key claiming");
            Toast.makeText(this, "Invalid URL.", Toast.LENGTH_SHORT);
            finish();
            return;
        } catch (IOException e) {
            Log.w(TAG, "Could not decrypt user keys");
            Toast.makeText(this, "Invalid credentials", Toast.LENGTH_SHORT);
        }
    }
    
    private void claimKeys(final IBHashedIdentity hid, final byte[] sk, final byte[] ek) {
        // Do this asynchronously since it's slow and doesn't require user interaction
        new Thread() {
            @Override
            public void run() {
                MIdentity mid = mIdentitiesManager.getIdentityForIBHashedIdentity(hid);
                if (mid != null) {
                    // Store user keys if necessary
                    IdentityProvider identityProvider = new AphidIdentityProvider(
                            ClaimKeyActivity.this);
                    UserKeyManager userKeyManager = new UserKeyManager(
                            identityProvider.getEncryptionScheme(),
                            identityProvider.getSignatureScheme(),
                            App.getDatabaseSource(ClaimKeyActivity.this));
                    
                    // If the identity was not known as owned, it should be now
                    if (!mid.owned_ && mid.principal_ != null) {
                        AccountLinkDialog.addAccountToDatabase(
                                ClaimKeyActivity.this,
                                new AccountLinkDialog.AccountDetails(
                                        mid.principal_,
                                        mid.principal_,
                                        AccountLinkDialog.ACCOUNT_TYPE_PHONE,
                                        true));
                    }
                    
                    // Add keys if they haven't been already
                    try {
                        userKeyManager.getEncryptionKey(mid, hid);
                    } catch (NeedsKey.Encryption e) {
                        MEncryptionUserKey key = new MEncryptionUserKey();
                        key.identityId_ = mid.id_;
                        key.userKey_ = ek;
                        key.when_ = hid.temporalFrame_;
                        userKeyManager.insertEncryptionUserKey(key);
                        getContentResolver().notifyChange(MusubiService.ENCODED_RECEIVED, null);
                    }
                    try {
                        userKeyManager.getSignatureKey(mid, hid);
                    } catch (NeedsKey.Signature e) {
                        MSignatureUserKey key = new MSignatureUserKey();
                        key.identityId_ = mid.id_;
                        key.userKey_ = sk;
                        key.when_ = hid.temporalFrame_;
                        userKeyManager.insertSignatureUserKey(key);
                        getContentResolver().notifyChange(MusubiService.PLAIN_OBJ_READY, null);
                    }
                }
            }
        }.start();
    }
    
    private Cipher getCipher(byte[] aesKey, byte[] iv) throws IOException {
        Cipher cipher;
        try {
            //since the length of the message is not included in the format, we have
            //to use a normal padding scheme that preserves length
            cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
        } catch (Exception e) {
            Log.e(TAG, "AES not supported", e);
            throw new IOException(e);
        }
        AlgorithmParameterSpec ivSpec;
        SecretKeySpec sks;
        try {
            ivSpec = new IvParameterSpec(iv);
            sks = new SecretKeySpec(aesKey, "AES");
            cipher.init(Cipher.DECRYPT_MODE, sks, ivSpec);
        } catch (Exception e) {
            Log.e(TAG, "Bad iv or key", e);
            throw new IOException(e);
        }
        return cipher;
    }
    
    private byte[] getKeyForIdentity(IBHashedIdentity hid, int requestId) {
        // Return a key if we were expecting this request
        MIdentity mid = mIdentitiesManager.getIdentityForIBHashedIdentity(hid);
        if (mid != null) {
            MPendingIdentity id = mPendingIdentityManager.lookupIdentity(
                    mid.id_, hid.temporalFrame_, requestId);
            if (id != null) {
                return Util.convertToByteArray(id.key_);
            }
        }
        return null;
    }
    
    private void clearPendingIdentity(IBHashedIdentity hid) {
        MIdentity mid = mIdentitiesManager.getIdentityForIBHashedIdentity(hid);
        if (mid != null) {
            mPendingIdentityManager.deleteIdentity(mid.id_, hid.temporalFrame_);
        }
    }
    
    private byte[] decryptClaimedKey(Cipher cipher, String original)
            throws IOException {
        return decryptClaimedKey(cipher, Base64.decode(original));
    }
    
    private byte[] decryptClaimedKey(Cipher cipher, byte[] original)
            throws IOException {
        try {
            return cipher.doFinal(original);
        } catch (Exception e) {
            Log.w(TAG, "Decryption failed", e);
            throw new IOException(e);
        }
    }
}
