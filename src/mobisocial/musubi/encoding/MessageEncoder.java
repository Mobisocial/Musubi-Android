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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import mobisocial.crypto.CorruptIdentity;
import mobisocial.crypto.IBEncryptionScheme;
import mobisocial.crypto.IBHashedIdentity;
import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.crypto.IBSignatureScheme;
import mobisocial.musubi.encoding.DiscardMessage.Corrupted;
import mobisocial.musubi.model.MEncodedMessage;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MOutgoingSecret;
import mobisocial.musubi.protocol.Message;
import mobisocial.musubi.protocol.Recipient;
import mobisocial.musubi.protocol.Secret;
import mobisocial.musubi.protocol.Sender;
import mobisocial.musubi.util.Util;

import org.codehaus.jackson.map.ObjectMapper;

import de.undercouch.bson4jackson.BsonFactory;

//TODO: broadcast flag, app id, signature changes
public class MessageEncoder {
	final IBEncryptionScheme mEncryptionScheme;
	final IBSignatureScheme mSignatureScheme;
	final long mDeviceName;
	final TransportDataProvider mTdp;
	ObjectMapper mMapper; // final but lazy
	
	public MessageEncoder(TransportDataProvider tdp) {
		mEncryptionScheme = tdp.getEncryptionScheme();
		mSignatureScheme = tdp.getSignatureScheme();
		mDeviceName = tdp.getDeviceName();
		mTdp = tdp;
	}

	private ObjectMapper getObjectMapper() {
	    if (mMapper == null) {
	        mMapper = new ObjectMapper(new BsonFactory());
	    }
	    return mMapper;
	}

	byte[] encodeMessage(Message m) throws Corrupted {
		try {
			return getObjectMapper().writeValueAsBytes(m);
		} catch (IOException e) {
			throw new DiscardMessage.Corrupted("Failed to encode BSON of outer message", e);
		}
	}
	byte[] encodeSecret(Secret s) throws Corrupted {
		try {
			return getObjectMapper().writeValueAsBytes(s);
		} catch (IOException e) {
			throw new DiscardMessage.Corrupted("Failed to encode BSON of inner recipient secret block", e);
		}
	}
	
	byte[] encryptBody(byte[] messageKey, byte[] data, byte[] iv) {
		Cipher cipher;
		AlgorithmParameterSpec iv_spec;
		SecretKeySpec sks;
		try {
			//since the length of the message is not included in the format, we have
			//to use a normal padding scheme that preserves length
			cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
		} catch (Exception e) {
			throw new RuntimeException("AES not supported on this platform", e);
		}
		try {
			iv_spec = new IvParameterSpec(iv);
		    sks = new SecretKeySpec(messageKey, "AES");
			cipher.init(Cipher.ENCRYPT_MODE, sks, iv_spec);
		} catch (Exception e) {
			throw new RuntimeException("bad iv or key on encode", e);
		}
		try {
	        return cipher.doFinal(data);
		} catch (Exception e) {
			throw new RuntimeException("body encryption failed", e);
		}
	}
	private byte[] computeFullSignature(byte[] hash, byte[] app,
			boolean blind, Recipient[] rs) {
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("your platform does not support sha256", e);
		}
		md.update(hash);
		md.update(app);
		md.update(blind ? (byte)1 : (byte)0);
		if(!blind) {
			for(Recipient r : rs) {
				md.update(r.i);
			}
		}
		return md.digest();
	}

	byte[] encryptRecipientSecret(MOutgoingSecret secret, byte[] data, byte[] iv) {
		Cipher cipher;
		AlgorithmParameterSpec iv_spec;
		SecretKeySpec sks;
		try {
			//TODO: do random byte padding
			cipher = Cipher.getInstance("AES/CBC/ZeroBytePadding");
		} catch (Exception e) {
			throw new RuntimeException("AES not supported on this platform", e);
		}
		try {
			iv_spec = new IvParameterSpec(iv);
		    sks = new SecretKeySpec(secret.key_, "AES");
			cipher.init(Cipher.ENCRYPT_MODE, sks, iv_spec);
		} catch (Exception e) {
			throw new RuntimeException("bad iv or key on encode recip", e);
		}
		try {
	        return cipher.doFinal(data);
		} catch (Exception e) {
			throw new RuntimeException("recip secret encryption failed", e);
		}
	}
	byte[] randomSymetricCipherBlock() {
		byte[] b = new byte[16];
		Random rand = new Random();
		rand.nextBytes(b);
		return b;
	}
	MOutgoingSecret addOutgoingSecret(MIdentity from, MIdentity to, IBHashedIdentity me, IBHashedIdentity you) throws NeedsKey.Signature {
		//TODO: make sure not to waste time computing the same secret twice if someone uses
		//this in a multi-threaded way
		MOutgoingSecret os = mTdp.lookupOutgoingSecret(from, to, me, you);
		if(os != null)
			return os;

		os = new MOutgoingSecret();
		os.myIdentityId_ = from.id_;
		os.otherIdentityId_ = to.id_;

		IBEncryptionScheme.ConversationKey ck = mEncryptionScheme.randomConversationKey(you);
		os.key_ = ck.key_;
		os.encryptedKey_ = ck.encryptedKey_;
		os.encryptionWhen_ = you.temporalFrame_;

		MessageDigest md;
		try {
			md = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("your platform does not support sha256", e);
		}
		md.update(os.encryptedKey_);
		ByteBuffer bb = ByteBuffer.wrap(new byte[8]);
		bb.putLong(mDeviceName);
		byte[] hash = md.digest(bb.array());
		os.signatureWhen_ = me.temporalFrame_;
		os.signature_ = mSignatureScheme.sign(me, mTdp.getSignatureKey(from, me), hash);
		
		mTdp.insertOutgoingSecret(me, you, os);
		return os;
	}

	long assignSequenceNumber(MIdentity to) {
		long next = to.nextSequenceNumber_;
		mTdp.incrementSequenceNumber(to);
		return next;
	}

	public MEncodedMessage processMessage(OutgoingMessage om) throws DiscardMessage, NeedsKey {
		Message m = new Message();

		m.v = 0 /* version # */;
		m.i = randomSymetricCipherBlock();
		byte[] message_key = randomSymetricCipherBlock();

		m.a = om.app_;
		m.l = om.blind_;

		m.s = new Sender();
		IBHashedIdentity me = new IBHashedIdentity(om.fromIdentity_.type_, om.fromIdentity_.principalHash_, mTdp.getSignatureTime(om.fromIdentity_));
		m.s.i = me.identity_;
		ByteBuffer bb = ByteBuffer.wrap(new byte[8]);
		bb.putLong(mDeviceName);
		m.s.d = bb.array();
		
		TLongLongHashMap sequence_numbers = new TLongLongHashMap();
		m.r = new Recipient[om.recipients_.length];
		for(int i = 0; i < om.recipients_.length; ++i) {
			m.r[i] = new Recipient();
			IBHashedIdentity other = new IBHashedIdentity(om.recipients_[i].type_, om.recipients_[i].principalHash_, mTdp.getEncryptionTime(om.recipients_[i]));
			//don't let anyone try to send with a local authority
			if(other.authority_ == Authority.Local)
				throw new DiscardMessage.InvalidAuthority();
			m.r[i].i = other.identity_;
		}
		
	    assert(Arrays.equals(om.hash_, Util.sha256(om.data_)));
	    byte[] full_hash = computeFullSignature(om.hash_, om.app_, om.blind_, m.r);

	    mTdp.beginTransaction();
		for(int i = 0; i < om.recipients_.length; ++i) {
			long q = assignSequenceNumber(om.recipients_[i]);
			sequence_numbers.put(om.recipients_[i].id_, q);
		}
		mTdp.setTransactionSuccessful();
		mTdp.endTransaction();
		for(int i = 0; i < om.recipients_.length; ++i) {
			IBHashedIdentity other;
			try {
				other = new IBHashedIdentity(m.r[i].i);
			} catch (CorruptIdentity e) {
				throw new RuntimeException("impossible situation on encode", e); 
			}
			MOutgoingSecret os = addOutgoingSecret(om.fromIdentity_, om.recipients_[i], me, other);
			m.r[i].k = os.encryptedKey_;
			m.r[i].s = os.signature_;
			Secret s = new Secret();
			s.h = full_hash;
			s.k = message_key;
			s.q = sequence_numbers.get(om.recipients_[i].id_);
			m.r[i].d = encryptRecipientSecret(os, encodeSecret(s), m.i);
		}
		m.d = encryptBody(message_key, om.data_, m.i);
		MEncodedMessage encoded = new MEncodedMessage();
		encoded.encoded_ = encodeMessage(m);
		encoded.hash_ = Util.sha256(encoded.encoded_);
     	encoded.processed_ = false;
	    //this table is used to decide whetehr or not to bother decoding, and we want to dedupe stuff from ourself
		encoded.fromIdentityId_ = om.fromIdentity_.id_;
		encoded.fromDevice_ = mTdp.addDevice(om.fromIdentity_, mDeviceName).id_;
		encoded.shortHash_ = Util.shortHash(encoded.hash_);
		encoded.outbound_ = true;
		mTdp.beginTransaction();
		mTdp.insertEncodedMessage(om, encoded);
		mTdp.storeSequenceNumbers(encoded, sequence_numbers);
		mTdp.setTransactionSuccessful();
		mTdp.endTransaction();
		return encoded;
	}

}
