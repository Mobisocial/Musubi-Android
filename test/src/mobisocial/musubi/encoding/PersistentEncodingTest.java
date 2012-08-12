package mobisocial.musubi.encoding;

import android.database.sqlite.SQLiteOpenHelper;
import mobisocial.crypto.IBEncryptionScheme;
import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.crypto.IBIdentity;
import mobisocial.crypto.IBSignatureScheme;
import mobisocial.musubi.identity.UnverifiedIdentityProvider;
import mobisocial.musubi.model.MEncodedMessage;
import mobisocial.musubi.model.MEncryptionUserKey;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MSignatureUserKey;
import mobisocial.musubi.model.helpers.DatabaseFile;
import mobisocial.musubi.model.helpers.EncodedMessageManager;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.model.helpers.MessageTransportManager;
import mobisocial.musubi.model.helpers.UserKeyManager;
import mobisocial.musubi.util.Util;
import mobisocial.test.TestBase;

public class PersistentEncodingTest extends TestBase {
	IBEncryptionScheme encryptionScheme_ = new IBEncryptionScheme();
	IBSignatureScheme signatureScheme_ = new IBSignatureScheme();
	UnverifiedIdentityProvider idp_ = new UnverifiedIdentityProvider();
	SQLiteOpenHelper dbh, dbh0, dbh1;
	public void setUp() {
		dbh = new DatabaseFile(getContext(), null);		
		dbh0 = new DatabaseFile(getContext(), null);		
		dbh1 = new DatabaseFile(getContext(), null);		
	}
	public void tearDown() {
		dbh.close();
		dbh0.close();
		dbh1.close();
	}
	public void testMissingSigningKeyMissingEncryptionKeyAndDuplicateMessage() throws Exception {
		final IBIdentity me = new IBIdentity(Authority.Email, randomUniquePrincipal(), 0);
		long myDeviceName = r.nextLong();
		IdentitiesManager idm = new IdentitiesManager(dbh);
		MessageTransportManager mtm = new MessageTransportManager(dbh, idp_.getEncryptionScheme(),
				idp_.getSignatureScheme(), myDeviceName);
		MIdentity myid = mtm.addClaimedIdentity(me);
		myid.owned_ = true;
		myid.principal_ = me.principal_;
		idm.updateIdentity(myid);
		
		
		//encode a message
		MessageEncoder encoder = new MessageEncoder(mtm);
		OutgoingMessage om = new OutgoingMessage();
		om.fromIdentity_ = myid;
		om.recipients_ = new MIdentity[] { mtm.addClaimedIdentity(me) };
		om.data_ = new byte[16];
     	r.nextBytes(om.data_);
		om.app_ = new byte[32];
		r.nextBytes(om.app_);
     	om.hash_ = Util.sha256(om.data_);

     	MEncodedMessage encodedOutgoing;
		try {
			encodedOutgoing = encoder.processMessage(om);
			fail("should have required signing key");
		} catch(NeedsKey.Signature e) {
			// GOOD
			UserKeyManager sm = new UserKeyManager(idp_.getEncryptionScheme(), idp_.getSignatureScheme(), dbh);
			MSignatureUserKey sigKey = new MSignatureUserKey();
			sigKey.identityId_ = myid.id_;
			IBIdentity required_key = idm.getIBIdentityForIBHashedIdentity(e.identity_);
			sigKey.userKey_ = idp_.syncGetSignatureKey(required_key).key_;
			sigKey.when_ = required_key.temporalFrame_;
			sm.insertSignatureUserKey(sigKey);
		} catch (Exception e) {
			throw e;
		}

		//second time it will try with the new key inserted which should work
		try {
			encodedOutgoing = encoder.processMessage(om);
		} catch (Exception e) {
			throw e;
		}
		
		EncodedMessageManager emm = new EncodedMessageManager(dbh);

		long id0 = encodedOutgoing.id_;
		//add a new dup
		encodedOutgoing.fromDevice_ = null;
		encodedOutgoing.fromIdentityId_ = null;
		encodedOutgoing.hash_ = null;
		encodedOutgoing.outbound_ = false;
		encodedOutgoing.processed_ = false;
		emm.insertEncoded(encodedOutgoing);
		
		MessageDecoder decoder = new MessageDecoder(mtm);
     	//decode it
		try {
			decoder.processMessage(encodedOutgoing);
			fail("this should have been detected as a duplicate");
		} catch (DiscardMessage.Duplicate e) {
			//GOOD!
		} catch (Exception e) {
			throw e;
		}
		
		emm.delete(id0);
		emm.delete(encodedOutgoing.id_);
		encodedOutgoing.fromDevice_ = null;
		encodedOutgoing.fromIdentityId_ = null;
		encodedOutgoing.hash_ = null;
		encodedOutgoing.outbound_ = false;
		encodedOutgoing.processed_ = false;
		emm.updateEncodedMetadata(encodedOutgoing);
		
     	//decode it
		try {
			decoder.processMessage(encodedOutgoing);
			fail("this should have required an encryption key");
		} catch (NeedsKey.Encryption e) {
			//GOOD!
			UserKeyManager sm = new UserKeyManager(idp_.getEncryptionScheme(), idp_.getSignatureScheme(), dbh);
			MEncryptionUserKey encKey = new MEncryptionUserKey();
			encKey.identityId_ = myid.id_;
			IBIdentity required_key = idm.getIBIdentityForIBHashedIdentity(e.identity_);
			encKey.userKey_ = idp_.syncGetEncryptionKey(required_key).key_;
			encKey.when_ = required_key.temporalFrame_;
			sm.insertEncryptionUserKey(encKey);
		} catch (Exception e) {
			throw e;
		}

		encodedOutgoing.fromDevice_ = null;
		encodedOutgoing.fromIdentityId_ = null;
		encodedOutgoing.hash_ = null;
		encodedOutgoing.outbound_ = false;
		encodedOutgoing.processed_ = false;
		emm.insertEncoded(encodedOutgoing);
		
     	//decode it again
		try {
			decoder.processMessage(encodedOutgoing);
		} catch (DiscardMessage.Duplicate e) {
			throw e;
		} catch (Exception e) {
			throw e;
		}
	}
	public void testSendToDifferentDevice() throws Exception {
		final IBIdentity me = new IBIdentity(Authority.Email, randomUniquePrincipal(), 0);
		long myDeviceName0 = r.nextLong();
		IdentitiesManager idm0 = new IdentitiesManager(dbh0);
		MessageTransportManager mtm0 = new MessageTransportManager(dbh0, idp_.getEncryptionScheme(),
				idp_.getSignatureScheme(), myDeviceName0);
		MIdentity myid0 = mtm0.addClaimedIdentity(me);
		myid0.owned_ = true;
		myid0.principal_ = me.principal_;
		idm0.updateIdentity(myid0);
		long myDeviceName1 = r.nextLong();
		IdentitiesManager idm1 = new IdentitiesManager(dbh1);
		MessageTransportManager mtm1 = new MessageTransportManager(dbh1, idp_.getEncryptionScheme(),
				idp_.getSignatureScheme(), myDeviceName1);
		MIdentity myid1 = mtm1.addClaimedIdentity(me);
		myid1.owned_ = true;
		myid1.principal_ = me.principal_;
		idm1.updateIdentity(myid1);
		
		
		//encode a message
		MessageEncoder encoder = new MessageEncoder(mtm0);
		OutgoingMessage om = new OutgoingMessage();
		om.fromIdentity_ = myid0;
		om.recipients_ = new MIdentity[] { mtm0.addClaimedIdentity(me) };
		om.data_ = new byte[16];
     	r.nextBytes(om.data_);
		om.app_ = new byte[32];
		r.nextBytes(om.app_);
     	om.hash_ = Util.sha256(om.data_);

		UserKeyManager sm0 = new UserKeyManager(idp_.getEncryptionScheme(), idp_.getSignatureScheme(), dbh0);
		MSignatureUserKey sigKey = new MSignatureUserKey();
		sigKey.identityId_ = myid0.id_;
		IBIdentity required_key = me.at(mtm0.getSignatureTime(myid0));
		sigKey.userKey_ = idp_.syncGetSignatureKey(required_key).key_;
		sigKey.when_ = required_key.temporalFrame_;
		sm0.insertSignatureUserKey(sigKey);
		
		UserKeyManager sm1 = new UserKeyManager(idp_.getEncryptionScheme(), idp_.getSignatureScheme(), dbh1);
		MEncryptionUserKey encKey = new MEncryptionUserKey();
		encKey.identityId_ = myid1.id_;
		required_key = me.at(mtm1.getEncryptionTime(myid1));
		encKey.userKey_ = idp_.syncGetEncryptionKey(required_key).key_;
		encKey.when_ = required_key.temporalFrame_;
		sm1.insertEncryptionUserKey(encKey);

		
		MEncodedMessage encodedOutgoing;
		try {
			encodedOutgoing = encoder.processMessage(om);
		} catch (Exception e) {
			throw e;
		}
		
		EncodedMessageManager emm1 = new EncodedMessageManager(dbh1);
		MEncodedMessage encodedIncoming = new MEncodedMessage();
		encodedIncoming.encoded_ = encodedOutgoing.encoded_;
		emm1.insertEncoded(encodedIncoming);

     	//decode it
		MessageDecoder decoder = new MessageDecoder(mtm1);
		IncomingMessage im;
		try {
			im = decoder.processMessage(encodedIncoming);
		} catch (Exception e) {
			throw e;
		}
		
		assertMessagesEqual(om, im);
	}

	public void testSendToAFriend() throws Exception {
		final IBIdentity me = new IBIdentity(Authority.Email, randomUniquePrincipal(), 0);
		long myDeviceName0 = r.nextLong();
		IdentitiesManager idm0 = new IdentitiesManager(dbh0);
		MessageTransportManager mtm0 = new MessageTransportManager(dbh0, idp_.getEncryptionScheme(),
				idp_.getSignatureScheme(), myDeviceName0);
		MIdentity myid0 = mtm0.addClaimedIdentity(me);
		myid0.owned_ = true;
		myid0.principal_ = me.principal_;
		idm0.updateIdentity(myid0);

		
		final IBIdentity you = new IBIdentity(Authority.Email, randomUniquePrincipal(), 0);
		long myDeviceName1 = r.nextLong();
		IdentitiesManager idm1 = new IdentitiesManager(dbh1);
		MessageTransportManager mtm1 = new MessageTransportManager(dbh1, idp_.getEncryptionScheme(),
				idp_.getSignatureScheme(), myDeviceName1);
		MIdentity myid1 = mtm1.addClaimedIdentity(you);
		myid1.owned_ = true;
		myid1.principal_ = you.principal_;
		idm1.updateIdentity(myid1);
		
		
		//encode a message
		MessageEncoder encoder = new MessageEncoder(mtm0);
		OutgoingMessage om = new OutgoingMessage();
		om.fromIdentity_ = myid0;
		om.recipients_ = new MIdentity[] { mtm0.addClaimedIdentity(you) };
		om.data_ = new byte[16];
     	r.nextBytes(om.data_);
		om.app_ = new byte[32];
		r.nextBytes(om.app_);
     	om.hash_ = Util.sha256(om.data_);

		UserKeyManager sm0 = new UserKeyManager(idp_.getEncryptionScheme(), idp_.getSignatureScheme(), dbh0);
		MSignatureUserKey sigKey = new MSignatureUserKey();
		sigKey.identityId_ = myid0.id_;
		IBIdentity required_key = me.at(mtm0.getSignatureTime(myid0));
		sigKey.userKey_ = idp_.syncGetSignatureKey(required_key).key_;
		sigKey.when_ = required_key.temporalFrame_;
		sm0.insertSignatureUserKey(sigKey);
		
		UserKeyManager sm1 = new UserKeyManager(idp_.getEncryptionScheme(), idp_.getSignatureScheme(), dbh1);
		MEncryptionUserKey encKey = new MEncryptionUserKey();
		encKey.identityId_ = myid1.id_;
		required_key = you.at(mtm1.getEncryptionTime(myid1));
		encKey.userKey_ = idp_.syncGetEncryptionKey(required_key).key_;
		encKey.when_ = required_key.temporalFrame_;
		sm1.insertEncryptionUserKey(encKey);

		
		MEncodedMessage encodedOutgoing;
		try {
			encodedOutgoing = encoder.processMessage(om);
		} catch (Exception e) {
			throw e;
		}
		
		EncodedMessageManager emm1 = new EncodedMessageManager(dbh1);
		MEncodedMessage encodedIncoming = new MEncodedMessage();
		encodedIncoming.encoded_ = encodedOutgoing.encoded_;
		emm1.insertEncoded(encodedIncoming);

     	//decode it
		MessageDecoder decoder = new MessageDecoder(mtm1);
		IncomingMessage im;
		try {
			im = decoder.processMessage(encodedIncoming);
		} catch (Exception e) {
			throw e;
		}
		
		assertMessagesEqual(om, im);
	}

}
