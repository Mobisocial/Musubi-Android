package mobisocial.crypto;

import java.security.SecureRandom;
import java.util.Date;

import junit.framework.TestCase;

import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.crypto.IBSignatureScheme.UserKey;

public class CryptoPerformanceTest extends TestCase {
	IBEncryptionScheme mEncryptionScheme = new IBEncryptionScheme();
	IBSignatureScheme mSignatureScheme = new IBSignatureScheme();

	final static int ITERATIONS = 100;
	
	public void testGetEncryptionUserKey() {
		IBHashedIdentity hid = new IBIdentity(Authority.Email, "tpurtell@stanford.edu", 123);
		Date start = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			mEncryptionScheme.userKey(hid);
		}
		Date end = new Date();
		System.out.print("Milliseconds per get encryption user key " +(double)(end.getTime() - start.getTime()) / ITERATIONS);
	}
	public void testGetSignatureUserKey() {
		IBHashedIdentity hid = new IBIdentity(Authority.Email, "tpurtell@stanford.edu", 123);
		Date start = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			mSignatureScheme.userKey(hid);
		}
		Date end = new Date();
		System.out.print("Milliseconds per get signature user key " + (double)(end.getTime() - start.getTime()) / ITERATIONS);
	}
	public void testSecureRandom() {
		SecureRandom r = new SecureRandom();
		byte[] sha = new byte[32];
		Date start = new Date();
		for(int i = 0; i < ITERATIONS * 100; ++i) {
			r.nextBytes(sha);
		}
		Date end = new Date();
		System.out.print("Milliseconds per get random nonce " + (double)(end.getTime() - start.getTime()) / (ITERATIONS * 100));
	}
	public void testSign() {
		SecureRandom r = new SecureRandom();
		byte[] sha = new byte[32];
		IBHashedIdentity hid = new IBIdentity(Authority.Email, "tpurtell@stanford.edu", 123);
		UserKey k = mSignatureScheme.userKey(hid);
		Date start = new Date();
		r.nextBytes(sha);
		for(int i = 0; i < ITERATIONS / 10; ++i) {
			mSignatureScheme.sign(hid, k, sha);
		}
		Date end = new Date();
		System.out.print("Milliseconds per signature " + (double)(end.getTime() - start.getTime()) / (ITERATIONS / 10));
	}
	public void testVerify() {
		SecureRandom r = new SecureRandom();
		byte[] sha = new byte[32];
		IBHashedIdentity hid = new IBIdentity(Authority.Email, "tpurtell@stanford.edu", 123);
		UserKey k = mSignatureScheme.userKey(hid);
		Date start = new Date();
		r.nextBytes(sha);
		byte[] sig = mSignatureScheme.sign(hid, k, sha);
		for(int i = 0; i < ITERATIONS / 10; ++i) {
			mSignatureScheme.verify(hid, sig, sha);
		}
		Date end = new Date();
		System.out.print("Milliseconds per verification " + (double)(end.getTime() - start.getTime()) / (ITERATIONS / 10));
	}
}
