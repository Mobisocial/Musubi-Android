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

package mobisocial.crypto;

import java.util.Random;

public class IBEncryptionScheme
{
    public static class MasterKey {
        public final byte[] key_;
        public MasterKey(byte[] key) {
            key_ = key;
        }
    }
    public static class UserKey {
        public final byte[] key_;
        public UserKey(byte[] key) {
            key_ = key;
        }
    }
    public static class ConversationKey 
    {
        public final byte[] encryptedKey_;
        public final byte[] key_;
        public ConversationKey(byte[] encryptedKey, byte[] key)
        {
            encryptedKey_ = encryptedKey;
            key_ = key;
        }
    }    
    
    public IBEncryptionScheme(byte[] params, MasterKey key) {
    }
    public IBEncryptionScheme(byte[] params) {
    }
    public IBEncryptionScheme() {
    }
    public UserKey userKey(IBHashedIdentity user)
    {
    	return new UserKey(new byte[1]);
    }
    public ConversationKey randomConversationKey(IBHashedIdentity to)
    {
        //me not likely doing it like this, but there isnt a great way to return
        //multiple parameters, without instantiating pair classes or somethijng
        //like that
        byte[] key = new byte[32];
        new Random().nextBytes(key);
        byte[] encrypted_key = key;
        return new ConversationKey(encrypted_key, key);
    }
    //there may be multiple user keys
    public byte[] decryptConversationKey(UserKey uk, byte[] encryptedKey)
    {
        return encryptedKey;
    }
}