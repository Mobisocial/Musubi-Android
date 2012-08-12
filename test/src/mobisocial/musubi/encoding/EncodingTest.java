package mobisocial.musubi.encoding;

import mobisocial.crypto.IBEncryptionScheme;
import mobisocial.crypto.IBHashedIdentity;
import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.crypto.IBIdentity;
import mobisocial.crypto.IBSignatureScheme;
import mobisocial.musubi.encoding.TransientTransportDataProvider.BlacklistProvider;
import mobisocial.musubi.encoding.TransientTransportDataProvider.EncryptionController;
import mobisocial.musubi.encoding.TransientTransportDataProvider.SignatureController;
import mobisocial.musubi.model.MEncodedMessage;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.util.Util;
import mobisocial.test.TestBase;

public class EncodingTest extends TestBase {
	IBEncryptionScheme encryptionScheme_ = new IBEncryptionScheme();
	IBSignatureScheme signatureScheme_ = new IBSignatureScheme();

	public void testSelfMessageDetectedAsDuplicate() throws Exception {
		IBIdentity me = new IBIdentity(Authority.Email, randomUniquePrincipal(), 0);
		TransientTransportDataProvider tdp = new TransientTransportDataProvider(encryptionScheme_, signatureScheme_, me, null, null, null);
		
		//encode a message
		MessageEncoder encoder = new MessageEncoder(tdp);
		OutgoingMessage om = new OutgoingMessage();
		om.fromIdentity_ = tdp.addClaimedIdentity(me);
		om.recipients_ = new MIdentity[] { om.fromIdentity_ };
		om.data_ = new byte[16];
     	r.nextBytes(om.data_);
		om.app_ = new byte[32];
		r.nextBytes(om.app_);
     	om.hash_ = Util.sha256(om.data_);
     	MEncodedMessage encodedOutgoing;
		try {
			encodedOutgoing = encoder.processMessage(om);
		} catch (Exception e) {
			throw e;
		}
		
		//pop the message into the transient provider
		MEncodedMessage encodedIncoming = tdp.insertEncodedMessage(encodedOutgoing.encoded_);

     	//decode it
		MessageDecoder decoder = new MessageDecoder(tdp);
		try {
			decoder.processMessage(encodedIncoming);
			fail("this should have been detected as a duplicate");
		} catch (DiscardMessage.Duplicate e) {
			//GOOD!
		} catch (DiscardMessage e) {
			throw e;
		} catch (NeedsKey e) {
			throw e;
		}
	}
	public void testSelfMessageAcrossDevices() throws Exception {
		IBIdentity me = new IBIdentity(Authority.Email, randomUniquePrincipal(), 0);
		TransientTransportDataProvider tdp_device0 = new TransientTransportDataProvider(encryptionScheme_, signatureScheme_, me, null, null, null);
		TransientTransportDataProvider tdp_device1 = new TransientTransportDataProvider(encryptionScheme_, signatureScheme_, me, null, null, null);
		
		//encode a message
		MessageEncoder encoder = new MessageEncoder(tdp_device0);
		OutgoingMessage om = new OutgoingMessage();
		om.fromIdentity_ = tdp_device0.addClaimedIdentity(me);
		om.recipients_ = new MIdentity[] { om.fromIdentity_ };
		om.data_ = new byte[16];
     	r.nextBytes(om.data_);
		om.app_ = new byte[32];
		r.nextBytes(om.app_);
     	om.hash_ = Util.sha256(om.data_);
     	MEncodedMessage encodedOutgoing;
		try {
			encodedOutgoing = encoder.processMessage(om);
		} catch (Exception e) {
			throw e;
		}
		
		//pop the message into the transient provider
		MEncodedMessage encodedIncoming = tdp_device1.insertEncodedMessage(encodedOutgoing.encoded_);

     	//decode it
		MessageDecoder decoder = new MessageDecoder(tdp_device1);
		IncomingMessage im;
		try {
			im = decoder.processMessage(encodedIncoming);
		} catch (Exception e) {
			throw e;
		}
		assertMessagesEqual(om, im);
	}
	public void testMessageBetweenFriends() throws Exception {
		IBIdentity me = new IBIdentity(Authority.Email, randomUniquePrincipal(), 0);
		IBIdentity you = new IBIdentity(Authority.Email, randomUniquePrincipal(), 0);
		TransientTransportDataProvider tdp_user0 = new TransientTransportDataProvider(encryptionScheme_, signatureScheme_, me, null, null, null);
		TransientTransportDataProvider tdp_user1 = new TransientTransportDataProvider(encryptionScheme_, signatureScheme_, you, null, null, null);
		
		//encode a message
		MessageEncoder encoder = new MessageEncoder(tdp_user0);
		OutgoingMessage om = new OutgoingMessage();
		om.fromIdentity_ = tdp_user0.addClaimedIdentity(me);
		om.recipients_ = new MIdentity[] { tdp_user0.addClaimedIdentity(you) };
		om.data_ = new byte[16];
     	r.nextBytes(om.data_);
		om.app_ = new byte[32];
		r.nextBytes(om.app_);
     	om.hash_ = Util.sha256(om.data_);
     	MEncodedMessage encodedOutgoing;
		try {
			encodedOutgoing = encoder.processMessage(om);
		} catch (Exception e) {
			throw e;
		}
		
		//pop the message into the transient provider
		MEncodedMessage encodedIncoming = tdp_user1.insertEncodedMessage(encodedOutgoing.encoded_);

     	//decode it
		MessageDecoder decoder = new MessageDecoder(tdp_user1);
		IncomingMessage im;
		try {
			im = decoder.processMessage(encodedIncoming);
		} catch (Exception e) {
			throw e;
		}
		assertMessagesEqual(om, im);
	}
	public void testBroadcastMessageBetweenFriends() throws Exception {
		IBIdentity me = new IBIdentity(Authority.Email, randomUniquePrincipal(), 0);
		IBIdentity you = new IBIdentity(Authority.Email, randomUniquePrincipal(), 0);
		TransientTransportDataProvider tdp_user0 = new TransientTransportDataProvider(encryptionScheme_, signatureScheme_, me, null, null, null);
		TransientTransportDataProvider tdp_user1 = new TransientTransportDataProvider(encryptionScheme_, signatureScheme_, you, null, null, null);
		
		//encode a message
		MessageEncoder encoder = new MessageEncoder(tdp_user0);
		OutgoingMessage om = new OutgoingMessage();
		om.fromIdentity_ = tdp_user0.addClaimedIdentity(me);
		om.recipients_ = new MIdentity[] { tdp_user0.addClaimedIdentity(you) };
		om.data_ = new byte[16];
     	r.nextBytes(om.data_);
		om.app_ = new byte[32];
		r.nextBytes(om.app_);
     	om.hash_ = Util.sha256(om.data_);
     	
     	//this changes the signature, computation, so we use this 
     	//test to verify that it works right
     	om.blind_ = true;
     	
     	MEncodedMessage encodedOutgoing;
		try {
			encodedOutgoing = encoder.processMessage(om);
		} catch (Exception e) {
			throw e;
		}
		
		//pop the message into the transient provider
		MEncodedMessage encodedIncoming = tdp_user1.insertEncodedMessage(encodedOutgoing.encoded_);

     	//decode it
		MessageDecoder decoder = new MessageDecoder(tdp_user1);
		IncomingMessage im;
		try {
			im = decoder.processMessage(encodedIncoming);
		} catch (Exception e) {
			throw e;
		}
		assertMessagesEqual(om, im);
	}
	public void testMessageMisrouted() throws Exception {
		IBIdentity me = new IBIdentity(Authority.Email, randomUniquePrincipal(), 0);
		IBIdentity you = new IBIdentity(Authority.Email, randomUniquePrincipal(), 0);
		IBIdentity bob = new IBIdentity(Authority.Email, randomUniquePrincipal(), 0);
		TransientTransportDataProvider tdp_user0 = new TransientTransportDataProvider(encryptionScheme_, signatureScheme_, me, null, null, null);
		TransientTransportDataProvider tdp_user1 = new TransientTransportDataProvider(encryptionScheme_, signatureScheme_, you, null, null, null);
		
		//encode a message
		MessageEncoder encoder = new MessageEncoder(tdp_user0);
		OutgoingMessage om = new OutgoingMessage();
		om.fromIdentity_ = tdp_user0.addClaimedIdentity(me);
		om.recipients_ = new MIdentity[] { tdp_user0.addClaimedIdentity(bob) };
		om.data_ = new byte[16];
     	r.nextBytes(om.data_);
		om.app_ = new byte[32];
		r.nextBytes(om.app_);
     	om.hash_ = Util.sha256(om.data_);
     	MEncodedMessage encodedOutgoing;
		try {
			encodedOutgoing = encoder.processMessage(om);
		} catch (Exception e) {
			throw e;
		}
		
		//pop the message into the transient provider
		MEncodedMessage encodedIncoming = tdp_user1.insertEncodedMessage(encodedOutgoing.encoded_);

     	//decode it
		MessageDecoder decoder = new MessageDecoder(tdp_user1);
		@SuppressWarnings("unused")
		IncomingMessage im;
		try {
			im = decoder.processMessage(encodedIncoming);
			fail("message was not addressed to you and this should have failed");
		} catch(DiscardMessage.NotToMe e) {
			//GOOD!
		} catch (Exception e) {
			throw e;
		}
	}
	public void testMessageBlacklist() throws Exception {
		final IBIdentity me = new IBIdentity(Authority.Email, randomUniquePrincipal(), 0);
		IBIdentity you = new IBIdentity(Authority.Email, randomUniquePrincipal(), 0);
		TransientTransportDataProvider tdp_user0 = new TransientTransportDataProvider(encryptionScheme_, signatureScheme_, me, null, null, null);
		BlacklistProvider blacklist = new BlacklistProvider() {
			public boolean isBlacklisted(IBHashedIdentity hid) {
				return hid.equals(me);
			}
		};
		TransientTransportDataProvider tdp_user1 = new TransientTransportDataProvider(encryptionScheme_, signatureScheme_, you, blacklist, null, null);
		
		//encode a message
		MessageEncoder encoder = new MessageEncoder(tdp_user0);
		OutgoingMessage om = new OutgoingMessage();
		om.fromIdentity_ = tdp_user0.addClaimedIdentity(me);
		om.recipients_ = new MIdentity[] { tdp_user0.addClaimedIdentity(you) };
		om.data_ = new byte[16];
     	r.nextBytes(om.data_);
		om.app_ = new byte[32];
		r.nextBytes(om.app_);
		om.hash_ = Util.sha256(om.data_);
     	MEncodedMessage encodedOutgoing;
		try {
			encodedOutgoing = encoder.processMessage(om);
		} catch (Exception e) {
			throw e;
		}
		
		//pop the message into the transient provider
		MEncodedMessage encodedIncoming = tdp_user1.insertEncodedMessage(encodedOutgoing.encoded_);

     	//decode it
		MessageDecoder decoder = new MessageDecoder(tdp_user1);
		@SuppressWarnings("unused")
		IncomingMessage im;
		try {
			im = decoder.processMessage(encodedIncoming);
			fail("message to a blacklisted user, this should have failed");
		} catch(DiscardMessage.Blacklist e) {
			//GOOD!
		} catch (Exception e) {
			throw e;
		}
	}
	public void testMissingSigningKey() throws Exception {
		final IBIdentity me = new IBIdentity(Authority.Email, randomUniquePrincipal(), 0);
		IBIdentity you = new IBIdentity(Authority.Email, randomUniquePrincipal(), 0);
		SignatureController signature_controller = new SignatureController() {
			public int t = 0;
			public long signingTime(IBHashedIdentity hid) {
				return t++;
			}
			public boolean hasSignatureKey(IBHashedIdentity hid) {
				return hid.temporalFrame_ == 0;
			}
		};
		TransientTransportDataProvider tdp_user0 = new TransientTransportDataProvider(encryptionScheme_, signatureScheme_, me, null, signature_controller, null);
		
		//encode a message
		MessageEncoder encoder = new MessageEncoder(tdp_user0);
		OutgoingMessage om = new OutgoingMessage();
		om.fromIdentity_ = tdp_user0.addClaimedIdentity(me);
		om.recipients_ = new MIdentity[] { tdp_user0.addClaimedIdentity(you) };
		om.data_ = new byte[16];
     	r.nextBytes(om.data_);
		om.app_ = new byte[32];
		r.nextBytes(om.app_);
     	om.hash_ = Util.sha256(om.data_);
     	@SuppressWarnings("unused")
		MEncodedMessage encodedOutgoing;
		try {
			encodedOutgoing = encoder.processMessage(om);
		} catch (Exception e) {
			throw e;
		}

		//second time it will try with the new signing time, which should fail
		try {
			encodedOutgoing = encoder.processMessage(om);
			fail("signature key should not have been available");
		} catch(NeedsKey.Signature e) {
			//GOOD!
		} catch (Exception e) {
			throw e;
		}
	}
	public void testMissingEncryptionKey() throws Exception {
		final IBIdentity me = new IBIdentity(Authority.Email, randomUniquePrincipal(), 0);
		IBIdentity you = new IBIdentity(Authority.Email, randomUniquePrincipal(), 0);
		EncryptionController encryption_controller = new EncryptionController() {
			public int t = 0;
			public long encryptionTime(IBHashedIdentity hid) {
				return t++;
			}
			public boolean hasEncryptionKey(IBHashedIdentity hid) {
				return hid.temporalFrame_ == 0;
			}
		};
		TransientTransportDataProvider tdp_user0 = new TransientTransportDataProvider(encryptionScheme_, signatureScheme_, me, null, null, encryption_controller);
		TransientTransportDataProvider tdp_user1 = new TransientTransportDataProvider(encryptionScheme_, signatureScheme_, you, null, null, encryption_controller);
		
		//encode a message
		MessageEncoder encoder = new MessageEncoder(tdp_user0);
		OutgoingMessage om = new OutgoingMessage();
		om.fromIdentity_ = tdp_user0.addClaimedIdentity(me);
		om.recipients_ = new MIdentity[] { tdp_user0.addClaimedIdentity(you) };
		om.data_ = new byte[16];
     	r.nextBytes(om.data_);
		om.app_ = new byte[32];
		r.nextBytes(om.app_);
     	om.hash_ = Util.sha256(om.data_);
     	MEncodedMessage encodedOutgoing;
		try {
			encodedOutgoing = encoder.processMessage(om);
		} catch (Exception e) {
			throw e;
		}

		MEncodedMessage encodedOutgoingUnreadable;
		//second time it will try with the new signing time, which should fail
		try {
			encodedOutgoingUnreadable = encoder.processMessage(om);
		} catch (Exception e) {
			throw e;
		}
		
		//pop the message into the transient provider
		MEncodedMessage encodedIncoming = tdp_user1.insertEncodedMessage(encodedOutgoing.encoded_);

     	//decode it
		MessageDecoder decoder = new MessageDecoder(tdp_user1);
		@SuppressWarnings("unused")
		IncomingMessage im;
		try {
			im = decoder.processMessage(encodedIncoming);
		} catch(DiscardMessage.Blacklist e) {
			//GOOD!
		} catch (Exception e) {
			throw e;
		}

		//try to decode the one we wont have a key for
		encodedIncoming = tdp_user1.insertEncodedMessage(encodedOutgoingUnreadable.encoded_);
		try {
			im = decoder.processMessage(encodedIncoming);
			fail("message should have needed a different encryption key");
		} catch(NeedsKey.Encryption e) {
			//GOOD!
		} catch (Exception e) {
			throw e;
		}
	}
	public void testCorruptedPacket() throws Exception {
		IBIdentity me = new IBIdentity(Authority.Email, randomUniquePrincipal(), 0);
		TransientTransportDataProvider tdp_device0 = new TransientTransportDataProvider(encryptionScheme_, signatureScheme_, me, null, null, null);
		TransientTransportDataProvider tdp_device1 = new TransientTransportDataProvider(encryptionScheme_, signatureScheme_, me, null, null, null);
		
		//encode a message
		MessageEncoder encoder = new MessageEncoder(tdp_device0);
		OutgoingMessage om = new OutgoingMessage();
		om.fromIdentity_ = tdp_device0.addClaimedIdentity(me);
		om.recipients_ = new MIdentity[] { om.fromIdentity_ };
		om.data_ = new byte[16];
     	r.nextBytes(om.data_);
		om.app_ = new byte[32];
		r.nextBytes(om.app_);
     	om.hash_ = Util.sha256(om.data_);
     	MEncodedMessage encodedOutgoing;
		try {
			encodedOutgoing = encoder.processMessage(om);
		} catch (Exception e) {
			throw e;
		}
		
		for(int i = 0; i < encodedOutgoing.encoded_.length; ++i) {
			encodedOutgoing.encoded_[i] += 37;
		}
		//pop the message into the transient provider
		MEncodedMessage encodedIncoming = tdp_device1.insertEncodedMessage(encodedOutgoing.encoded_);

     	//decode it
		MessageDecoder decoder = new MessageDecoder(tdp_device1);
		try {
			decoder.processMessage(encodedIncoming);
			fail("this should have been detected as corrupted");
		} catch (DiscardMessage.Corrupted e) {
			//GOOD!
		} catch (Exception e) {
			throw e;
		}
	}
	public void testCorruptedBody() throws Exception {
		IBIdentity me = new IBIdentity(Authority.Email, randomUniquePrincipal(), 0);
		TransientTransportDataProvider tdp_device0 = new TransientTransportDataProvider(encryptionScheme_, signatureScheme_, me, null, null, null);
		TransientTransportDataProvider tdp_device1 = new TransientTransportDataProvider(encryptionScheme_, signatureScheme_, me, null, null, null);
		
		//encode a message
		MessageEncoder encoder = new MessageEncoder(tdp_device0);
		OutgoingMessage om = new OutgoingMessage();
		om.fromIdentity_ = tdp_device0.addClaimedIdentity(me);
		om.recipients_ = new MIdentity[] { om.fromIdentity_ };
		om.data_ = new byte[16];
     	r.nextBytes(om.data_);
		om.app_ = new byte[32];
		r.nextBytes(om.app_);
     	om.hash_ = Util.sha256(om.data_);
     	MEncodedMessage encodedOutgoing;
		try {
			encodedOutgoing = encoder.processMessage(om);
		} catch (Exception e) {
			throw e;
		}
		
		//pop the message into the transient provider
		encodedOutgoing.encoded_[encodedOutgoing.encoded_.length - 17] += 37;
		MEncodedMessage encodedIncoming = tdp_device1.insertEncodedMessage(encodedOutgoing.encoded_);

     	//decode it
		MessageDecoder decoder = new MessageDecoder(tdp_device1);
		@SuppressWarnings("unused")
		IncomingMessage im;
		try {
			im = decoder.processMessage(encodedIncoming);
		} catch (DiscardMessage.BadSignature e) {
			//GOOD!
		} catch (Exception e) {
			throw e;
		}
	}
}
