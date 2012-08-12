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

public class IBSignatureScheme
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
    
    public IBSignatureScheme(byte[] params, MasterKey key) {
    }
    public IBSignatureScheme(byte[] params) {
    }
    public IBSignatureScheme() {
    }
    public UserKey userKey(IBHashedIdentity user)
    {
        return new UserKey(new byte[1]);
    }
    //user key is a param because users will have multiple keys under the same scheme
    public byte[] sign(IBHashedIdentity from, UserKey from_key, byte[] data)
    {
        return new byte[1];
    }
    public boolean verify(IBHashedIdentity from, byte[] signature, byte[] data)
    {
        return true;
    }
}
