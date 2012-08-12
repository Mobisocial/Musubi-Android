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

import android.util.Base64;
import mobisocial.crypto.IBEncryptionScheme;
import mobisocial.crypto.IBHashedIdentity;
import mobisocial.crypto.IBIdentity;
import mobisocial.crypto.IBSignatureScheme;
import mobisocial.crypto.IBSignatureScheme.UserKey;

public class UnverifiedIdentityProvider implements IdentityProvider {
	static final String ENCRYPTION_MASTER_KEY = 
		"IaDYdb1KUmMXryyF0cM3SYa2lFcDsQ+c0H04ZaxSyiix6/T//+KT6AA=";
	static final String ENCRYPTION_PUBLIC_PARAMETERS = 
		"FQdHvz/VMGL6KLP2b4GXiujvVGQjO204iqUt2MirtxlTQ2IND7gAFQAZQYaG3nTV5JFoXi1RmvAC" +
		"y3PEZRv3v6hcTwJogfd8dK9CAajIOKuAAQNj2qFFLhWSwDEXf05eTlcGqyysAEZ6OGNqRPVVwc1M" +
		"wxmq8togOVYACLA5aAHF8oNIH29Xy+XY5WzZEW0BG0mTX7IqSliIZXoQrK/I/swEAjwBFZMP7ma7" +
		"v7f+JxpeiXJ4z7oK8BIBEvbPC00grFRFquyGd93c5STDI1oRXhPMmZwFRt7y+wn0Z353pST1ZhkN" +
		"yyy/7OQ4Eug46S31MZcXkL68I5JAe1aZcaW3KX35PRBcL546liQDVoPfruuzvV2NI1roROPabi0C" +
		"FCBVQw8vcFBlegN9L/WQ9kAnhNODBke3XZRi7Eohs0FxVDlAEtXu5j8WSvPEcx5mb2rh9FQcteRL" +
		"qqeaeBL9v+OdxUOdMZVK1XksGj+kNiWfEUqgUpK8WnFMEh5Idna98AeFQtoOUYrdNYmoAa9BxjTf" +
		"Ttb/VtcANCO9/ZkUtUARTP5Zpb2nHaUis9Q+";
	static final String SIGNATURE_MASTER_KEY =
		"DkYMfelGGsxk8harZ0Ga/rIMeC4=";
	static final String SIGNATURE_PUBLIC_PARAMETERS =
		"Dd9pJec6fLTT2kpDu4xZaTYQDxQRdG0Yh3gC7WX4Vt3gb4BPMz42cgEQNOT5i0ihwacJxQ+6Clg5" +
		"TTbKUBZrv9zwj1lRe9f8AQ23YMC3RpXpAA==";
	
	IBEncryptionScheme encryptionScheme_ = new IBEncryptionScheme(
		Base64.decode(ENCRYPTION_PUBLIC_PARAMETERS, Base64.DEFAULT),
		new IBEncryptionScheme.MasterKey(Base64.decode(ENCRYPTION_MASTER_KEY, Base64.DEFAULT))
	);

	IBSignatureScheme signatureScheme_ = new IBSignatureScheme(
		Base64.decode(SIGNATURE_PUBLIC_PARAMETERS, Base64.DEFAULT),
		new IBSignatureScheme.MasterKey(Base64.decode(SIGNATURE_MASTER_KEY, Base64.DEFAULT))
	);

	public IBEncryptionScheme getEncryptionScheme() {
		return encryptionScheme_;
	}

	public IBSignatureScheme getSignatureScheme() {
		return signatureScheme_;
	}
	
	public boolean initiateTwoPhaseClaim(IBIdentity ident, String key, int requestId) {
	    // The unverified provider does not support this.
	    return false;
	}
	
	public UserKey syncGetSignatureKey(IBIdentity ident) {
		return signatureScheme_.userKey(ident);
	}
	public mobisocial.crypto.IBEncryptionScheme.UserKey syncGetEncryptionKey(IBIdentity ident) {
		return encryptionScheme_.userKey(ident);
	}
	public UserKey syncGetSignatureKey(IBHashedIdentity ident) {
		return signatureScheme_.userKey(ident);
	}
	public mobisocial.crypto.IBEncryptionScheme.UserKey syncGetEncryptionKey(IBHashedIdentity ident) {
		return encryptionScheme_.userKey(ident);
	}
}
