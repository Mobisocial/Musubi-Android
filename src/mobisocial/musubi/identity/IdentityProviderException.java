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

import mobisocial.crypto.IBHashedIdentity;

/**
 * Describe the ways an identity provider can fail
 */
public class IdentityProviderException extends Exception {
	private static final long serialVersionUID = -2074587259036492987L;
	public final IBHashedIdentity identity;
	
	public IdentityProviderException(IBHashedIdentity ident) {
		identity = new IBHashedIdentity(ident.authority_,
				ident.hashed_, ident.temporalFrame_);
	}
	// Remote servers may be functional, but the user credentials are invalid
	public static class Auth extends IdentityProviderException {
		private static final long serialVersionUID = 1980354000515173623L;

		public Auth(IBHashedIdentity ident) {
			super(ident);
		}
	}
    // User credentials may be valid, but remote servers are not functional
    public static class NeedsRetry extends IdentityProviderException {
        private static final long serialVersionUID = -1597375918037678426L;

        public NeedsRetry(IBHashedIdentity ident) {
            super(ident);
        }
    }
    // Identity provider cannot return keys without more work from the user
    public static class TwoPhase extends IdentityProviderException {
        private static final long serialVersionUID = 1748295146128123538L;

        public TwoPhase(IBHashedIdentity ident) {
            super(ident);
        }
    }
}
