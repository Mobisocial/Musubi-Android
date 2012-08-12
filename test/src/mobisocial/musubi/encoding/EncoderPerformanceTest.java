package mobisocial.musubi.encoding;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Random;

import mobisocial.crypto.IBEncryptionScheme;
import mobisocial.crypto.IBEncryptionScheme.ConversationKey;
import mobisocial.crypto.IBEncryptionScheme.UserKey;
import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.crypto.IBIdentity;
import mobisocial.crypto.IBSignatureScheme;
import mobisocial.musubi.encoding.DiscardMessage.Corrupted;
import mobisocial.musubi.encoding.NeedsKey.Signature;
import mobisocial.musubi.identity.UnverifiedIdentityProvider;
import mobisocial.musubi.model.MDevice;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MIncomingSecret;
import mobisocial.musubi.model.MOutgoingSecret;
import mobisocial.musubi.model.helpers.DatabaseFile;
import mobisocial.musubi.model.helpers.DeviceManager;
import mobisocial.musubi.model.helpers.MessageTransportManager;
import mobisocial.musubi.protocol.Message;
import mobisocial.musubi.protocol.Recipient;
import mobisocial.musubi.protocol.Secret;
import mobisocial.musubi.util.Util;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class EncoderPerformanceTest extends TestBase {
	final static int R = 229;
	UnverifiedIdentityProvider mIdp = new UnverifiedIdentityProvider();
	IBIdentity mMe = new IBIdentity(Authority.Email, randomUniquePrincipal(), 0);
	final static int ITERATIONS = 100;
	SQLiteOpenHelper dbh;
	
	public void setUp() {
		dbh = new DatabaseFile(getContext(), null);
	}
	public void tearDown() {
		dbh.close();
	}
	public void testOutgoingLoadChannelKey() {
		DeviceManager dm = new DeviceManager(dbh);
		MessageTransportManager mtm = new MessageTransportManager(dbh, mIdp.getEncryptionScheme(), mIdp.getSignatureScheme(), dm.getLocalDeviceName());
		MIdentity meIdentity = new MIdentity();
		MDevice meDevice = new MDevice();
		meDevice.id_ = 1;
		MOutgoingSecret os = new MOutgoingSecret();
		os.otherIdentityId_ = 1;
		os.myIdentityId_ = 1;
		os.signatureWhen_ = mMe.temporalFrame_;
		os.encryptionWhen_ = mMe.temporalFrame_;
		os.key_ = new byte[32];
		os.encryptedKey_ = new byte[42];
		os.signature_ = new byte[21];
		new Random().nextBytes(os.signature_);
		mtm.insertOutgoingSecret(mMe, mMe, os);
		//assume 1000 contacts
		for(int i = 0; i < 1000; ++i) {
			os.otherIdentityId_++;
			mtm.insertOutgoingSecret(mMe, mMe, os);
		}
		Date start = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			mtm.lookupOutgoingSecret(meIdentity, meIdentity, mMe, mMe);
		}
		Date end = new Date();
		Log.w(this.getName(), "Milliseconds per lookup " + (double)(end.getTime() - start.getTime()) / (ITERATIONS));
	}
	public void testIncomingLoadChannelKey() {
		DeviceManager dm = new DeviceManager(dbh);
		MessageTransportManager mtm = new MessageTransportManager(dbh, mIdp.getEncryptionScheme(), mIdp.getSignatureScheme(), dm.getLocalDeviceName());
		MIdentity meIdentity = new MIdentity();
		MDevice meDevice = new MDevice();
		meDevice.id_ = 1;
		MIncomingSecret is = new MIncomingSecret();
		is.deviceId_ = 1;
		is.otherIdentityId_ = 1;
		is.myIdentityId_ = 1;
		is.signatureWhen_ = mMe.temporalFrame_;
		is.encryptionWhen_ = mMe.temporalFrame_;
		is.key_ = new byte[32];
		is.encryptedKey_ = new byte[42];
		is.signature_ = new byte[21];
		new Random().nextBytes(is.signature_);
		mtm.insertIncomingSecret(mMe, mMe, is);
		//assume 1000 contacts
		for(int i = 0; i < 1000; ++i) {
			is.otherIdentityId_++;
			mtm.insertIncomingSecret(mMe, mMe, is);
		}
		Date start = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			mtm.lookupIncomingSecret(meIdentity, meDevice, meIdentity, is.signature_, mMe, mMe);
		}
		Date end = new Date();
		Log.w(this.getName(), "Milliseconds per lookup " + (double)(end.getTime() - start.getTime()) / (ITERATIONS));
	}
	public void testComputeOutgoingKey() throws Signature {
		Date start = new Date();
		IBEncryptionScheme enc = mIdp.getEncryptionScheme();
		for(int i = 0; i < ITERATIONS / 10; ++i) {
			enc.randomConversationKey(mMe);
		}
		Date end = new Date();
		Log.w(this.getName(), "Milliseconds per channel key " + (double)(end.getTime() - start.getTime()) / (ITERATIONS / 10));
	}
	public void testSignOutgoingKey() throws Signature {
		TransientTransportDataProvider tdp = new TransientTransportDataProvider(mIdp.getEncryptionScheme(), mIdp.getSignatureScheme(), mMe, null, null, null);
		Date start = new Date();
		IBSignatureScheme sig = mIdp.getSignatureScheme();
		byte[] encrypted_key = new byte[32];
		new Random().nextBytes(encrypted_key);
		long device_name = new Random().nextLong();
		for(int i = 0; i < ITERATIONS / 10; ++i) {
			MessageDigest md;
			try {
				md = MessageDigest.getInstance("SHA-256");
			} catch (NoSuchAlgorithmException e) {
				throw new RuntimeException("your platform does not support sha256", e);
			}
			md.update(encrypted_key);
			ByteBuffer bb = ByteBuffer.wrap(new byte[8]);
			bb.putLong(device_name);
			byte[] hash = md.digest(bb.array());
			sig.sign(mMe, tdp.getSignatureKey(null, mMe), hash);
		}
		Date end = new Date();
		Log.w(this.getName(), "Milliseconds per sign channel key " + (double)(end.getTime() - start.getTime()) / (ITERATIONS / 10));
	}
	public void testVerifyChannelKey() throws Signature {
		TransientTransportDataProvider tdp = new TransientTransportDataProvider(mIdp.getEncryptionScheme(), mIdp.getSignatureScheme(), mMe, null, null, null);
		Date start = new Date();
		IBSignatureScheme sig = mIdp.getSignatureScheme();
		byte[] encrypted_key = new byte[32];
		new Random().nextBytes(encrypted_key);
		long device_name = new Random().nextLong();
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("your platform does not support sha256", e);
		}
		md.update(encrypted_key);
		ByteBuffer bb = ByteBuffer.wrap(new byte[8]);
		bb.putLong(device_name);
		byte[] hash = md.digest(bb.array());
		byte[] s = sig.sign(mMe, tdp.getSignatureKey(null, mMe), hash);
		for(int i = 0; i < ITERATIONS / 10; ++i) {
			try {
				md = MessageDigest.getInstance("SHA-256");
			} catch (NoSuchAlgorithmException e) {
				throw new RuntimeException("your platform does not support sha256", e);
			}
			md.update(encrypted_key);
			bb = ByteBuffer.wrap(new byte[8]);
			bb.putLong(device_name);
			byte[] h = md.digest(bb.array());
			sig.verify(mMe, s, h);
		}
		Date end = new Date();
		Log.w(this.getName(), "Milliseconds per sign channel key " + (double)(end.getTime() - start.getTime()) / (ITERATIONS / 10));
	}
	public void testShaHeaders() throws Corrupted {
		TransientTransportDataProvider tdp = new TransientTransportDataProvider(mIdp.getEncryptionScheme(), mIdp.getSignatureScheme(), mMe, null, null, null);
		MessageEncoder encoder = new MessageEncoder(tdp);

		Random rnd = new Random();
	
		Secret s = new Secret();
		s.h = new byte[32];
		rnd.nextBytes(s.h);
		s.k = new byte[32];
		rnd.nextBytes(s.k);
		s.q = rnd.nextLong();
		
		MOutgoingSecret os = new MOutgoingSecret();
		os.key_ = new byte[32];
		rnd.nextBytes(os.key_);
		
		byte[] iv = encoder.randomSymetricCipherBlock();

		Recipient r = new Recipient();
		r.k = new byte[42];
		rnd.nextBytes(r.k);
		r.s = new byte[21];
		rnd.nextBytes(r.s);
		r.i = mMe.identity_;
		r.d = encoder.encodeSecret(s);
		r.d = encoder.encryptRecipientSecret(os, r.d, iv);
		
		
		Message m = new Message();
		m.d = null;
		m.s = null;
		m.i = null;
		m.l = true;
		m.r = new Recipient[R + 1];
		for(int i = 0; i <= R; ++i) {
			m.r[i] = r;
		}
		byte[] bytes = encoder.encodeMessage(m);
		
		Date start = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Util.sha256(bytes);
		}
		Date end = new Date();
		Log.w(this.getName(), "header size is " + bytes.length);
		Log.w(this.getName(), "Milliseconds per sha recipient " + (double)(end.getTime() - start.getTime()) / (ITERATIONS * m.r.length));
	}
	public void testOverhead() throws Corrupted {
		TransientTransportDataProvider tdp = new TransientTransportDataProvider(mIdp.getEncryptionScheme(), mIdp.getSignatureScheme(), mMe, null, null, null);
		MessageEncoder encoder = new MessageEncoder(tdp);

		Random rnd = new Random();
	
		Secret s = new Secret();
		s.h = new byte[32];
		rnd.nextBytes(s.h);
		s.k = new byte[32];
		rnd.nextBytes(s.k);
		s.q = rnd.nextLong();
		
		MOutgoingSecret os = new MOutgoingSecret();
		os.key_ = new byte[32];
		rnd.nextBytes(os.key_);
		
		byte[] iv = encoder.randomSymetricCipherBlock();

		Recipient r = new Recipient();
		r.k = new byte[42];
		rnd.nextBytes(r.k);
		r.s = new byte[21];
		rnd.nextBytes(r.s);
		r.i = mMe.identity_;
		r.d = encoder.encodeSecret(s);
		r.d = encoder.encryptRecipientSecret(os, r.d, iv);
		
		
		Message m = new Message();
		m.d = null;
		m.s = null;
		m.i = null;
		m.l = true;
		m.r = new Recipient[1 + 1];
		for(int i = 0; i < m.r.length; ++i) {
			m.r[i] = r;
		}
		byte[] bytes = encoder.encodeMessage(m);
		Log.w(this.getName(), "1-1 header size is " + bytes.length);

		m.r = new Recipient[229 + 1];
		for(int i = 0; i < m.r.length; ++i) {
			m.r[i] = r;
		}
		bytes = encoder.encodeMessage(m);
		Log.w(this.getName(), "fb header size is " + bytes.length);

		m.r = new Recipient[20 + 1];
		for(int i = 0; i < m.r.length; ++i) {
			m.r[i] = r;
		}
		bytes = encoder.encodeMessage(m);
		Log.w(this.getName(), "cont header size is " + bytes.length);

		m.r = new Recipient[10000 + 1];
		for(int i = 0; i < m.r.length; ++i) {
			m.r[i] = r;
		}
		bytes = encoder.encodeMessage(m);
		Log.w(this.getName(), "twit header size is " + bytes.length);
	}
	public void testShaPerByte() {
		byte[] data = new byte[50 * 1024];
		
		new Random().nextBytes(data);
		
		Date start = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Util.sha256(data);
		}
		Date end = new Date();
		Log.w(this.getName(), "Milliseconds per sha kbyte " + (double)(end.getTime() - start.getTime()) / (ITERATIONS * data.length / 1024));
	}
	public void testEncryptAESSecret() throws Corrupted {
		TransientTransportDataProvider tdp = new TransientTransportDataProvider(mIdp.getEncryptionScheme(), mIdp.getSignatureScheme(), mMe, null, null, null);
		MessageEncoder encoder = new MessageEncoder(tdp);

		Random rnd = new Random();
		
		Secret s = new Secret();
		s.h = new byte[32];
		rnd.nextBytes(s.h);
		s.k = new byte[32];
		rnd.nextBytes(s.k);
		s.q = rnd.nextLong();
		
		MOutgoingSecret os = new MOutgoingSecret();
		os.key_ = new byte[32];
		rnd.nextBytes(os.key_);
		
		byte[] iv = encoder.randomSymetricCipherBlock();

		byte[] secret = encoder.encodeSecret(s);
		Date start = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			encoder.encryptRecipientSecret(os, secret, iv);
		}
		Date end = new Date();
		Log.w(this.getName(), "Milliseconds per aes secret block " + (double)(end.getTime() - start.getTime()) / (ITERATIONS));
		
	}
	public void testEncryptAESPerByte() {
		TransientTransportDataProvider tdp = new TransientTransportDataProvider(mIdp.getEncryptionScheme(), mIdp.getSignatureScheme(), mMe, null, null, null);
		MessageEncoder encoder = new MessageEncoder(tdp);
		byte[] data = new byte[50 * 1024];
		
		new Random().nextBytes(data);
		
		byte[] key = new byte[32];
		new Random().nextBytes(key);
		
		byte[] iv = encoder.randomSymetricCipherBlock();

		Date start = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			encoder.encryptBody(key, data, iv);
		}
		Date end = new Date();
		Log.w(this.getName(), "Milliseconds per aes kbyte " + (double)(end.getTime() - start.getTime()) / (ITERATIONS * data.length / 1024));
	}

	public void testDecryptAESSecret() throws Corrupted {
		TransientTransportDataProvider tdp = new TransientTransportDataProvider(mIdp.getEncryptionScheme(), mIdp.getSignatureScheme(), mMe, null, null, null);
		MessageEncoder encoder = new MessageEncoder(tdp);
		MessageDecoder decoder = new MessageDecoder(tdp);

		Random rnd = new Random();
		
		Secret s = new Secret();
		s.h = new byte[32];
		rnd.nextBytes(s.h);
		s.k = new byte[32];
		rnd.nextBytes(s.k);
		s.q = rnd.nextLong();
		
		MOutgoingSecret os = new MOutgoingSecret();
		os.key_ = new byte[32];
		rnd.nextBytes(os.key_);
		MIncomingSecret is = new MIncomingSecret();
		is.key_ = os.key_;
		
		byte[] iv = encoder.randomSymetricCipherBlock();

		byte[] secret = encoder.encodeSecret(s);
		secret = encoder.encryptRecipientSecret(os, secret, iv);

		Date start = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			decoder.decryptRecipientSecret(is, secret, iv);
		}
		Date end = new Date();
		Log.w(this.getName(), "Milliseconds per decrypt aes secret block " + (double)(end.getTime() - start.getTime()) / (ITERATIONS));
		
	}
	public void testDecryptAESPerByte() throws Corrupted {
		TransientTransportDataProvider tdp = new TransientTransportDataProvider(mIdp.getEncryptionScheme(), mIdp.getSignatureScheme(), mMe, null, null, null);
		MessageDecoder decoder = new MessageDecoder(tdp);
		MessageEncoder encoder = new MessageEncoder(tdp);
		byte[] data = new byte[50 * 1024];
		new Random().nextBytes(data);
		
		byte[] key = new byte[32];
		new Random().nextBytes(key);
		
		byte[] iv = new byte[16];
		new Random().nextBytes(iv);
		data = encoder.encryptBody(key, data, iv);

		Date start = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			decoder.decryptBody(data, key, iv);
		}
		Date end = new Date();
		Log.w(this.getName(), "Milliseconds per decrypt aes kbyte " + (double)(end.getTime() - start.getTime()) / (ITERATIONS * data.length / 1024));
	}
	public void testDecryptChannelKey() {
		IBEncryptionScheme enc = mIdp.getEncryptionScheme();
		ConversationKey k = enc.randomConversationKey(mMe);
		
		UserKey uk = enc.userKey(mMe);
		Date start = new Date();
		for(int i = 0; i < ITERATIONS / 10; ++i) {
			enc.decryptConversationKey(uk, k.encryptedKey_);
		}
		Date end = new Date();
		Log.w(this.getName(), "Milliseconds per decrypt channel key " + (double)(end.getTime() - start.getTime()) / (ITERATIONS / 10));
	}
}
