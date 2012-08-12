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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.ArrayList;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import mobisocial.crypto.CorruptIdentity;
import mobisocial.crypto.IBEncryptionScheme;
import mobisocial.crypto.IBHashedIdentity;
import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.crypto.IBSignatureScheme;
import mobisocial.musubi.encoding.DiscardMessage.BadSignature;
import mobisocial.musubi.encoding.DiscardMessage.Corrupted;
import mobisocial.musubi.encoding.DiscardMessage.Duplicate;
import mobisocial.musubi.model.MDevice;
import mobisocial.musubi.model.MEncodedMessage;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MIncomingSecret;
import mobisocial.musubi.protocol.Message;
import mobisocial.musubi.protocol.Recipient;
import mobisocial.musubi.protocol.Secret;
import mobisocial.musubi.protocol.Sender;
import mobisocial.musubi.util.Util;

import org.codehaus.jackson.map.ObjectMapper;

import android.util.Base64;
import de.undercouch.bson4jackson.BsonFactory;
import de.undercouch.bson4jackson.BsonParser.Feature;

public class MessageDecoder {
	final IBEncryptionScheme mEncryptionScheme;
	final IBSignatureScheme mSignatureScheme;
	final TransportDataProvider mTdp;
	//since we aren't trying to do streaming large object processing, we enforce
	//the document length.  this lets us easily ignore the data at the end
	//with increasing the secret block size with a funky padding scheme.
	ObjectMapper mMapper; // final but lazy

	public MessageDecoder(TransportDataProvider tdp) {
		mEncryptionScheme = tdp.getEncryptionScheme();
		mSignatureScheme = tdp.getSignatureScheme();
		mTdp = tdp;
	}

	private ObjectMapper getObjectMapper() {
	    if (mMapper == null) {
	        mMapper = new ObjectMapper(new BsonFactory().enable(Feature.HONOR_DOCUMENT_LENGTH));
	    }
	    return mMapper;
	}
	Message decodeMessage(byte[] raw) throws Corrupted {
		try {
			return getObjectMapper().readValue(raw, Message.class);
		} catch (IOException e) {
			throw new DiscardMessage.Corrupted("Failed to parse BSON of outer message", e);
		}
	}
	Secret decodeSecret(byte[] raw) throws Corrupted {
		try {
			return getObjectMapper().readValue(raw, Secret.class);
		} catch (IOException e) {
			throw new DiscardMessage.Corrupted("Failed to parse BSON of inner recipient secret block", e);
		}
	}
	
	byte[] decryptBody(byte[] data, byte[] messageKey, byte[] iv) throws Corrupted {
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
			cipher.init(Cipher.DECRYPT_MODE, sks, iv_spec);
		} catch (Exception e) {
			throw new DiscardMessage.Corrupted("bad iv or key", e);
		}
		try {
	        return cipher.doFinal(data);
		} catch (Exception e) {
			throw new DiscardMessage.Corrupted("body decryption failed", e);
		}
	}
	void checkBodySignature(byte[] expected, byte[] hash, byte[] app, boolean blind, Recipient[] rs) throws BadSignature {
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
		byte[] full_hash = md.digest();

		if(!Arrays.equals(full_hash, expected)) {
			throw new BadSignature("signature mismatch for message data was " + Base64.encodeToString(expected, Base64.DEFAULT) + " should be " + Base64.encodeToString(full_hash, Base64.DEFAULT));
		}
	}

	void checkDuplicate(MDevice from, byte[] raw_hash) throws Duplicate {
		if(mTdp.haveHash(raw_hash)) {
			throw new DiscardMessage.Duplicate(from, raw_hash);
		}
	}
	boolean isBlacklisted(MIdentity from) {
		return mTdp.isBlacklisted(from);
	}

	void updateMissingMessages(MDevice from, long sequenceNumber) {
		mTdp.receivedSequenceNumber(from, sequenceNumber);
	}
	byte[] decryptRecipientSecret(MIncomingSecret secret, byte[] data, byte[] iv) throws Corrupted {
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
			cipher.init(Cipher.DECRYPT_MODE, sks, iv_spec);
		} catch (Exception e) {
			throw new DiscardMessage.Corrupted("bad iv or key for recip", e);
		}
		try {
	        return cipher.doFinal(data);
		} catch (Exception e) {
			throw new DiscardMessage.Corrupted("recip secret decryption failed", e);
		}
	}
	boolean isMe(Recipient r) throws CorruptIdentity {
		return mTdp.isMe(new IBHashedIdentity(r.i));
	}
	
	MIdentity addIdentity(byte[] id) throws CorruptIdentity {
		IBHashedIdentity hid = new IBHashedIdentity(id);
		return mTdp.addClaimedIdentity(hid);
	}
	MIdentity addUnclaimedIdentity(IBHashedIdentity hid) {
		return mTdp.addUnclaimedIdentity(hid);
	}
	MDevice addDevice(MIdentity ident, byte[] device) {
		return mTdp.addDevice(ident, ByteBuffer.wrap(device).getLong());
	}
	MIncomingSecret addIncomingSecret(MIdentity from, MDevice device, MIdentity to, Sender s, Recipient me) throws BadSignature, NeedsKey.Encryption, CorruptIdentity {
		IBHashedIdentity me_timed = new IBHashedIdentity(me.i);
		IBHashedIdentity sid = new IBHashedIdentity(s.i);
		//TODO: make sure not to waste time computing the same secret twice if someone uses
		//this in a multi-threaded way
		MIncomingSecret is = mTdp.lookupIncomingSecret(from, device, to, me.s, sid, me_timed);
		if(is != null)
			return is;

		is = new MIncomingSecret();
		is.myIdentityId_ = to.id_;
		is.otherIdentityId_ = from.id_;
		is.deviceId_ = device.id_;
		is.key_ = mEncryptionScheme.decryptConversationKey(mTdp.getEncryptionKey(to, me_timed), me.k);
		is.encryptedKey_ = me.k;
		is.encryptionWhen_ = me_timed.temporalFrame_;

		MessageDigest md;
		try {
			md = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("your platform does not support sha256", e);
		}
		md.update(is.encryptedKey_);
		ByteBuffer deviceId = ByteBuffer.wrap(new byte[8]);
		deviceId.putLong(device.deviceName_);
		byte[] hash = md.digest(deviceId.array());

		is.signatureWhen_ = sid.temporalFrame_;
		is.signature_ = me.s;
		if(!mSignatureScheme.verify(sid, is.signature_, hash)) {
			throw new DiscardMessage.BadSignature("message failed to have a valid signature for my recipient key");
		}
		mTdp.insertIncomingSecret(sid, me_timed, is);
		return is;	
	}
	public IncomingMessage processMessage(MEncodedMessage encoded) throws DiscardMessage, NeedsKey {
		try { 
			return processMessageInternal(encoded);
		} catch (CorruptIdentity e) {
			throw new DiscardMessage("corrupt identity data in message", e);
		}
	}	
	private IncomingMessage processMessageInternal(MEncodedMessage encoded) throws DiscardMessage, NeedsKey, CorruptIdentity {
		IncomingMessage im = new IncomingMessage();
		Message m = decodeMessage(encoded.encoded_);
		ArrayList<Recipient> mine = new ArrayList<Recipient>(8);
		for(Recipient r : m.r) {
			//TODO: dedupe?
			if(isMe(r)) {
				mine.add(r);
			}
		}
		if(mine.size() == 0)
			throw new DiscardMessage.NotToMe("Couldn't find a recipient that matches me");

		//this will add all of the relevant identities and devices to the tables
		im.fromIdentity_ = addIdentity(m.s.i);
		if(isBlacklisted(im.fromIdentity_)) {
			throw new DiscardMessage.Blacklist("received message from blacklisted identity " + im.fromIdentity_.id_);
		}
		im.fromDevice_ = addDevice(im.fromIdentity_, m.s.d);
		byte[] raw_hash = Util.sha256(encoded.encoded_);
		checkDuplicate(im.fromDevice_, raw_hash);

		im.app_ = m.a;
		im.blind_ = m.l;
		im.personas_ = new MIdentity[mine.size()];
		for(int i = 0; i < im.personas_.length; ++i) {
			im.personas_[i] = addIdentity(mine.get(i).i);
		}
		if(im.blind_) {
			//TODO: the server was supposed to strip these out.
			im.recipients_ = im.personas_;
		} else {
			im.recipients_ = new MIdentity[m.r.length];
			for(int i = 0; i < m.r.length; ++i) {
				IBHashedIdentity hid = new IBHashedIdentity(m.r[i].i);
				//don't accept messages from clients that erroneously include
				//a local user in the group
				if(hid.authority_ == Authority.Local)
					throw new DiscardMessage.InvalidAuthority();
				im.recipients_[i] = addUnclaimedIdentity(hid);
			}
		}
		for(int i = 0; i < im.personas_.length; ++i) {
			Recipient me = mine.get(i);
			MIdentity persona = im.personas_[i];
			//checks the secret if it is actually added
			MIncomingSecret inSecret = addIncomingSecret(im.fromIdentity_, im.fromDevice_, persona, m.s, me);
			
			byte[] recipient_secret = decryptRecipientSecret(inSecret, me.d, m.i);
			Secret secret = decodeSecret(recipient_secret);
			
			im.sequenceNumber_ = secret.q;
			
			//This makes it so that we only compute
			//the data and the hash once per message
			if(im.data_ == null) {
				im.data_ = decryptBody(m.d, secret.k, m.i);
			}
			if(im.hash_ == null) {
				im.hash_ = Util.sha256(im.data_);
			}
			
			checkBodySignature(secret.h, im.hash_, im.app_, im.blind_, m.r);
			updateMissingMessages(im.fromDevice_, secret.q);
		}

		encoded.fromDevice_ = im.fromDevice_.id_;
		encoded.fromIdentityId_ = im.fromIdentity_.id_;
		encoded.hash_ = raw_hash;
		encoded.shortHash_ = Util.shortHash(raw_hash);
		//TODO:XXX this appears to be a race condition if the processing crashes in the obj phase
		//in a non-repeatable way.  ideally, we would only update this encoded row once in the caller.
		mTdp.updateEncodedMetadata(encoded);
		return im;
	}
}
