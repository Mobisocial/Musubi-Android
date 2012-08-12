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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

//an IBIdentity can only be created by someone who knows the real identifier
//the hashed identity is the one the network uses for transport
public class IBIdentity extends IBHashedIdentity
{
    public final String principal_;
    public IBIdentity(Authority authority, String principal, long temporalFrame)
    {
        super(authority, digestPrincipal(principal), temporalFrame);
        this.principal_ = principal;
    }
    public static byte[] digestPrincipal(String principal) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(principal.getBytes());
            return md.digest();
        } catch(NoSuchAlgorithmException e) {
            throw new RuntimeException("Platform doesn't support sha256?!?!", e);
        }
    }
    public IBIdentity at(long temporalFrame) {
    	return new IBIdentity(authority_, principal_, temporalFrame);
    }
}