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

package mobisocial.musubi.identity;

import mobisocial.crypto.IBEncryptionScheme;
import mobisocial.crypto.IBHashedIdentity;
import mobisocial.crypto.IBIdentity;
import mobisocial.crypto.IBSignatureScheme;

//methods intended to be invoked in a background handler job
public interface IdentityProvider {
	public IBEncryptionScheme getEncryptionScheme();
	public IBSignatureScheme getSignatureScheme();
	public boolean initiateTwoPhaseClaim(IBIdentity ident, String key, int requestId);
	public IBSignatureScheme.UserKey syncGetSignatureKey(IBIdentity ident) throws IdentityProviderException;
	public IBEncryptionScheme.UserKey syncGetEncryptionKey(IBIdentity ident) throws IdentityProviderException;
	//These ones may have to do an implicit lookup because the real principal
	//may be required to fetch a key, e.g. aphid
	public IBSignatureScheme.UserKey syncGetSignatureKey(IBHashedIdentity ident) throws IdentityProviderException;
	public IBEncryptionScheme.UserKey syncGetEncryptionKey(IBHashedIdentity ident) throws IdentityProviderException;
}
