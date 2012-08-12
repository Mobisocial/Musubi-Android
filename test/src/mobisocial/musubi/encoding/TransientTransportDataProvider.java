package mobisocial.musubi.encoding;

import gnu.trove.list.array.TByteArrayList;
import gnu.trove.list.linked.TLongLinkedList;
import gnu.trove.map.hash.TLongLongHashMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.map.hash.TObjectLongHashMap;
import gnu.trove.procedure.TLongLongProcedure;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Random;

import mobisocial.crypto.IBEncryptionScheme;
import mobisocial.crypto.IBHashedIdentity;
import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.crypto.IBIdentity;
import mobisocial.crypto.IBSignatureScheme;
import mobisocial.musubi.model.MDevice;
import mobisocial.musubi.model.MEncodedMessage;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MIncomingSecret;
import mobisocial.musubi.model.MOutgoingSecret;

import org.javatuples.Pair;
import org.javatuples.Quartet;
import org.javatuples.Triplet;

public class TransientTransportDataProvider implements TransportDataProvider {
	public interface BlacklistProvider {
		boolean isBlacklisted(IBHashedIdentity hid);
	}
	public interface SignatureController {
		boolean hasSignatureKey(IBHashedIdentity hid);
		long signingTime(IBHashedIdentity hid);
	}
	public interface EncryptionController {
		boolean hasEncryptionKey(IBHashedIdentity hid);
		long encryptionTime(IBHashedIdentity hid);
	}
	IBEncryptionScheme encryptionScheme_;
	IBSignatureScheme signatureScheme_;
	SignatureController signatureController_;
	EncryptionController encryptionController_;
	long deviceName_ = new Random().nextLong();
	IBIdentity me_;
	TLongObjectHashMap<MIdentity> identities_ = new TLongObjectHashMap<MIdentity>();
	HashMap<Pair<Authority, TByteArrayList>, MIdentity> identityLookup_ = new HashMap<Pair<Authority, TByteArrayList>, MIdentity>();
	BlacklistProvider blacklist_;
	TLongObjectHashMap<MDevice> devices_ = new TLongObjectHashMap<MDevice>();
	HashMap<Pair<Long /*id*/, Long /*dev*/>, MDevice> deviceLookup_ = new HashMap<Pair<Long,Long>, MDevice>();
	HashMap<Pair<Long /*from*/, Long /*device*/>, TLongLinkedList> missingSequenceNumber_ = new HashMap<Pair<Long,Long>, TLongLinkedList>();
	HashMap<TByteArrayList, MEncodedMessage> encodedMessageLookup_ = new HashMap<TByteArrayList, MEncodedMessage>();
	TLongObjectHashMap<MEncodedMessage> encodedMessages_ = new TLongObjectHashMap<MEncodedMessage>();
	HashMap<
		Quartet<Long /*from*/, Long /*to*/, Long /*sign time*/, Long /*enc time*/>, 
		MOutgoingSecret
	> outgoingSecrets_ = new HashMap<Quartet<Long, Long, Long, Long>, MOutgoingSecret>();
	HashMap<
		Triplet<Long /*from*/, Long /*to*/, TByteArrayList /*sig*/>, 
		MIncomingSecret
	> incomingSecrets_ = new HashMap<Triplet<Long, Long, TByteArrayList>, MIncomingSecret>();
	
	TObjectLongHashMap<Pair<Long /*to*/, Long /*seq*/>> encodedMessageForPersonBySequenceNumber = new TObjectLongHashMap<Pair<Long,Long>>();
		
	/* pass in an encryption scheme because you need on identity provider per user */
	public TransientTransportDataProvider(IBEncryptionScheme encryptionScheme, IBSignatureScheme signatureScheme, IBIdentity me, BlacklistProvider blacklist, SignatureController signatureController, EncryptionController encryptionController) {
		encryptionScheme_ = encryptionScheme;
		signatureScheme_ = signatureScheme;
		me_ = me;
		if(blacklist != null) {
			blacklist_ = blacklist;
		} else {
			//default to blank blacklist
			blacklist_ = new BlacklistProvider() {
				public boolean isBlacklisted(IBHashedIdentity hid) {
					return false;
				}
			};
		}
		if(signatureController != null) {
			signatureController_ = signatureController;
		} else {
			signatureController_ = new SignatureController() {
				public boolean hasSignatureKey(IBHashedIdentity hid) {
					return true;
				}
				public long signingTime(IBHashedIdentity hid) {
					return ByteBuffer.wrap(hid.hashed_).getLong();
				}
			};
		}
		if(encryptionController != null) {
			encryptionController_ = encryptionController;
		} else {
			encryptionController_ = new EncryptionController() {
				public boolean hasEncryptionKey(IBHashedIdentity hid) {
					return true;
				}
				public long encryptionTime(IBHashedIdentity hid) {
					return ByteBuffer.wrap(hid.hashed_).getLong();
				}
			};
		}
		MIdentity ident = new MIdentity();
		ident.id_ = identities_.size() + 1;
		ident.claimed_ = true;
		ident.owned_ = true;
		ident.type_ = Authority.Email;
		ident.principal_ = me_.principal_;
		ident.principalHash_ = me_.hashed_;
		ident.principalShortHash_ = ByteBuffer.wrap(me_.hashed_).getLong();
		identities_.put(ident.id_, ident);		
		identityLookup_.put(Pair.with(ident.type_, new TByteArrayList(ident.principalHash_)), ident);
		addDevice(ident, deviceName_);
	}
	
	
	public IBEncryptionScheme getEncryptionScheme() {
		return encryptionScheme_;
	}

	public IBSignatureScheme getSignatureScheme() {
		return signatureScheme_;
	}

	public IBSignatureScheme.UserKey getSignatureKey(MIdentity from, IBHashedIdentity me) throws NeedsKey.Signature {
		if(!signatureController_.hasSignatureKey(me))
			throw new NeedsKey.Signature(me);
		return signatureScheme_.userKey(me);
	}

	public IBEncryptionScheme.UserKey getEncryptionKey(MIdentity to, IBHashedIdentity me) throws NeedsKey.Encryption {
		if(!encryptionController_.hasEncryptionKey(me))
			throw new NeedsKey.Encryption(me);
		return encryptionScheme_.userKey(me);
	}

	public long getSignatureTime(MIdentity from) {
		return signatureController_.signingTime(new IBHashedIdentity(from.type_, from.principalHash_, 0));
	}

	public long getEncryptionTime(MIdentity to) {
		return encryptionController_.encryptionTime(new IBHashedIdentity(to.type_, to.principalHash_, 0));
	}

	public long getDeviceName() {
		return deviceName_;
	}
	
	public MIdentity addUnclaimedIdentity(IBHashedIdentity hid) {
		MIdentity ident = identityLookup_.get(Pair.with(hid.authority_, new TByteArrayList(hid.hashed_)));
		if(ident != null)
			return ident;
		ident = new MIdentity();
		ident.id_ = identities_.size() + 1;
		ident.claimed_ = false;
		ident.owned_ = false;
		ident.type_ = Authority.Email;
		ident.principalHash_ = hid.hashed_;
		ident.principalShortHash_ = ByteBuffer.wrap(hid.hashed_).getLong();
		identities_.put(ident.id_, ident);
		identityLookup_.put(Pair.with(ident.type_, new TByteArrayList(ident.principalHash_)), ident);
		return ident;
	}
	public MIdentity addClaimedIdentity(IBHashedIdentity hid) {
		MIdentity ident = identityLookup_.get(Pair.with(hid.authority_, new TByteArrayList(hid.hashed_)));
		if(ident != null)
			return ident;
		ident = new MIdentity();
		ident.id_ = identities_.size() + 1;
		ident.claimed_ = true;
		ident.owned_ = false;
		ident.type_ = Authority.Email;
		ident.principalHash_ = hid.hashed_;
		ident.principalShortHash_ = ByteBuffer.wrap(hid.hashed_).getLong();
		identities_.put(ident.id_, ident);
		identityLookup_.put(Pair.with(ident.type_, new TByteArrayList(ident.principalHash_)), ident);
		return ident;
	}
	public MDevice addDevice(MIdentity ident, long deviceName) {
		MDevice d = deviceLookup_.get(Pair.with(ident.id_, deviceName));
		if(d != null)
			return d;
		d = new MDevice();
		d.id_ = devices_.size() + 1;
		d.identityId_ = ident.id_;
		d.deviceName_ = deviceName;
		d.maxSequenceNumber_ = -1;
		devices_.put(d.id_, d);
		deviceLookup_.put(Pair.with(d.identityId_, d.deviceName_), d);
		return d;
	}
	public MEncodedMessage insertEncodedMessage(byte[] encodedData) {
		MEncodedMessage encoded = new MEncodedMessage();
		encoded.encoded_ = encodedData;
		encodedMessages_.put(encoded.id_, encoded);
		return encoded;
	}

	public MOutgoingSecret lookupOutgoingSecret(MIdentity from,
			MIdentity to, IBHashedIdentity me, IBHashedIdentity you) {
		return outgoingSecrets_.get(new Quartet<Long, Long, Long, Long>(from.id_, to.id_, me.temporalFrame_, you.temporalFrame_));
	}

	public void insertOutgoingSecret(IBHashedIdentity me, IBHashedIdentity you, MOutgoingSecret os) {
		 outgoingSecrets_.put(new Quartet<Long, Long, Long, Long>(os.myIdentityId_, os.otherIdentityId_, me.temporalFrame_, you.temporalFrame_), os);
	}

	public MIncomingSecret lookupIncomingSecret(MIdentity from, MDevice fromDevice,
			MIdentity to, byte[] signature, IBHashedIdentity you, IBHashedIdentity me) {
		return incomingSecrets_.get(new Quartet<Long, Long, TByteArrayList, IBHashedIdentity>(fromDevice.id_, to.id_, new TByteArrayList(signature), you));
	}

	public void insertIncomingSecret(IBHashedIdentity you, IBHashedIdentity me, MIncomingSecret is) {
		incomingSecrets_.put(new Triplet<Long, Long, TByteArrayList>(is.deviceId_, is.myIdentityId_, new TByteArrayList(is.signature_)), is);
	}

	public void incrementSequenceNumber(MIdentity to) {
		MIdentity ident = identities_.get(to.id_);
		ident.nextSequenceNumber_++;
	}
	public void storeSequenceNumbers(final MEncodedMessage encoded, TLongLongHashMap sequence_numbers) {
		sequence_numbers.forEachEntry(new TLongLongProcedure() {
			public boolean execute(long identityId, long sequenceNumber) {
				encodedMessageForPersonBySequenceNumber.put(Pair.with(identityId, sequenceNumber), encoded.id_);
				return true;
			}
		});
	}


	public void receivedSequenceNumber(MDevice from, long sequenceNumber) {
		Pair<Long, Long> k = new Pair<Long, Long>(from.identityId_, from.deviceName_);
		long maxSequenceNumber = devices_.get(from.id_).maxSequenceNumber_;
		if(sequenceNumber > maxSequenceNumber)
			devices_.get(from.id_).maxSequenceNumber_ = maxSequenceNumber;
		TLongLinkedList missing = missingSequenceNumber_.get(k);
		if(missing != null)
			missing.remove(sequenceNumber);
		if(sequenceNumber > maxSequenceNumber + 1) {
			if(missing == null) {
				missing = new TLongLinkedList();
				missingSequenceNumber_.put(k, missing);
			}
			for(long q = maxSequenceNumber + 1; q < sequenceNumber; ++q) {
				missing.add(q);
			}
		}
	}

	public boolean haveHash(byte[] hash) {
		MEncodedMessage encoded = encodedMessageLookup_.get(new TByteArrayList(hash));
		return encoded != null;
	}

	public boolean isBlacklisted(MIdentity from) {
		return blacklist_.isBlacklisted(new IBHashedIdentity(from.type_, from.principalHash_, 0));
	}

	public boolean isMe(IBHashedIdentity ibHashedIdentity) {
		return ibHashedIdentity.equalsStable(me_);
	}

	public void updateEncodedMetadata(MEncodedMessage encoded) {
		encodedMessageLookup_.put(new TByteArrayList(encoded.hash_), encoded);
	}


	public void insertEncodedMessage(OutgoingMessage om, MEncodedMessage encoded) {
		assert(encoded.id_ == 0);
		encodedMessages_.put(encoded.id_, encoded);
		encodedMessageLookup_.put(new TByteArrayList(encoded.hash_), encoded);
	}


	@Override
	public void setTransactionSuccessful() {
	}


	@Override
	public void beginTransaction() {
	}


	@Override
	public void endTransaction() {
	}
}
