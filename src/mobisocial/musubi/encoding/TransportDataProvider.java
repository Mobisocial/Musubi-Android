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

package mobisocial.musubi.encoding;

import gnu.trove.map.hash.TLongLongHashMap;
import mobisocial.crypto.IBEncryptionScheme;
import mobisocial.crypto.IBHashedIdentity;
import mobisocial.crypto.IBSignatureScheme;
import mobisocial.musubi.model.MDevice;
import mobisocial.musubi.model.MEncodedMessage;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MIncomingSecret;
import mobisocial.musubi.model.MOutgoingSecret;

public interface TransportDataProvider {
	/* ibe secrets */
	public IBEncryptionScheme getEncryptionScheme();
	public IBSignatureScheme getSignatureScheme();
	public IBSignatureScheme.UserKey getSignatureKey(MIdentity from, IBHashedIdentity me) throws NeedsKey.Signature;
	public IBEncryptionScheme.UserKey getEncryptionKey(MIdentity to, IBHashedIdentity me) throws NeedsKey.Encryption;
	
	/* compute times given an identity, might consult for revocation etc */
	public long getSignatureTime(MIdentity from);
	public long getEncryptionTime(MIdentity to);
	
	/* my one and only */
	public long getDeviceName();
	
	/* channel secret management */
	public MOutgoingSecret lookupOutgoingSecret(MIdentity from, MIdentity to, IBHashedIdentity me, IBHashedIdentity you);
	public void insertOutgoingSecret(IBHashedIdentity me, IBHashedIdentity you, MOutgoingSecret os);
	public MIncomingSecret lookupIncomingSecret(MIdentity from, MDevice fromDevice, MIdentity to, byte[] signature, IBHashedIdentity you, IBHashedIdentity me);
	public void insertIncomingSecret(IBHashedIdentity you, IBHashedIdentity me, MIncomingSecret is);

	/* sequence number manipulation */
	public void incrementSequenceNumber(MIdentity to);
	public void receivedSequenceNumber(MDevice from, long sequenceNumber);
	public boolean haveHash(byte[] hash);
	public void storeSequenceNumbers(MEncodedMessage encoded,
			TLongLongHashMap sequence_numbers);

	
	/* misc identity info queries */
	public boolean isBlacklisted(MIdentity from);
	public boolean isMe(IBHashedIdentity ibHashedIdentity);
	public MIdentity addClaimedIdentity(IBHashedIdentity hid);
	public MIdentity addUnclaimedIdentity(IBHashedIdentity hid);
	public MDevice addDevice(MIdentity ident, long deviceId);
	
	/* final message handled */
	public void updateEncodedMetadata(MEncodedMessage encoded);
	public void insertEncodedMessage(OutgoingMessage om, MEncodedMessage encoded);
	
	/* some operations should involve us batching to save index modification time */
	public void setTransactionSuccessful();
	public void beginTransaction();
	public void endTransaction();
}
