package mobisocial.musubi.identity;

import java.util.Arrays;

import mobisocial.crypto.IBEncryptionScheme;
import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.crypto.IBIdentity;
import mobisocial.crypto.IBSignatureScheme;
import mobisocial.musubi.identity.AphidIdentityProvider;
import mobisocial.musubi.identity.IdentityProvider;
import mobisocial.musubi.identity.UnverifiedIdentityProvider;
import mobisocial.test.TestBase;

public class IdentityProviderTest extends TestBase {
	private void testIdentityProviderSignatures(IdentityProvider idp) {
		IBSignatureScheme signatureScheme = idp.getSignatureScheme();

        IBIdentity ident = new IBIdentity(IBIdentity.Authority.Facebook, "100003478698404", 1);

        //normally we'd pass a hash but i am lazy
        byte[] data;
        try {
            data = "a message to sign".getBytes();
        } catch(Exception e) {
            throw new RuntimeException(e);
        }

        try {
	        IBSignatureScheme.UserKey user_key = idp.syncGetSignatureKey(ident);
	        byte[] sig = signatureScheme.sign(ident, user_key, data);
	        boolean ok = signatureScheme.verify(ident, sig, data);
	
	        assertTrue("sign => verify (right identity) : failed to match", ok);
	        sig[9]++;
	        ok = signatureScheme.verify(ident, sig, data);
	        assertFalse("sign => verify (wrong identity) : failed to mismatch", ok);
        } catch (IdentityProviderException.Auth e) {
        	fail("Account authoirzation failure");
        } catch (IdentityProviderException e) {
        	fail("Signature key not obtained");
        }
    }
	private void testIdentityProviderEncryption(IdentityProvider idp) {
		IBEncryptionScheme encryptionScheme = idp.getEncryptionScheme();
        IBIdentity ident = new IBIdentity(IBIdentity.Authority.Facebook, "100003478698404", 1);

        try {
	        IBEncryptionScheme.UserKey user_key = idp.syncGetEncryptionKey(ident);
	        IBEncryptionScheme.ConversationKey conv_key = encryptionScheme.randomConversationKey(ident);
	
	        byte[] key = encryptionScheme.decryptConversationKey(user_key, conv_key.encryptedKey_);
	        
	        assertTrue("encrypt => decrypt (right identity) : failed to match conversation key", Arrays.equals(key, conv_key.key_));
	        
	        IBIdentity ident2 = new IBIdentity(IBIdentity.Authority.Facebook, "100003569923517", 2);
	        IBEncryptionScheme.UserKey user_key2 = idp.syncGetEncryptionKey(ident2);
	        
	        key = encryptionScheme.decryptConversationKey(user_key2, conv_key.encryptedKey_);
	        assertFalse("encrypt => decrypt (wrong identity): failed to mismatch conversation key", Arrays.equals(key, conv_key.key_));
        } catch (IdentityProviderException.Auth e) {
        	fail("Account authoirzation failure");
        } catch (IdentityProviderException e) {
        	fail("Signature key not obtained");
        }
	}
	
	public void testAphidEncryption() {
		AphidIdentityProvider provider = new AphidIdentityProvider(getContext());
		provider.setTokenForUser(Authority.Facebook, "100003478698404",
			"AAAEJgrNu5P0BAJ035XvHphGUvnrdPhnvmh8m3qZAvcvC08Jy9TuA4iC9ZBvHzmxQOoCHjkN" + 
			"Hg5G5luuyTiGVUcJbn1UjLoRZBHhAuP0oQZDZD"
		);
		provider.setTokenForUser(Authority.Facebook, "100003569923517",
			"AAAEJgrNu5P0BAGasTuCZAKVX130aavXNtsTPZCLZC9ybXk7nRVfIuKA3qTNeFgkBWPzmIW2" + 
			"x8MbFdP1juMOowezrmxkbMW1hQ88Nd5ZA8QZDZD"
		);
		testIdentityProviderEncryption(provider);
	}
	public void testAphidSignature() {
		AphidIdentityProvider provider = new AphidIdentityProvider(getContext());
		provider.setTokenForUser(Authority.Facebook, "100003478698404",
			"AAAEJgrNu5P0BAJ035XvHphGUvnrdPhnvmh8m3qZAvcvC08Jy9TuA4iC9ZBvHzmxQOoCHjkN" + 
			"Hg5G5luuyTiGVUcJbn1UjLoRZBHhAuP0oQZDZD"
		);
		provider.setTokenForUser(Authority.Facebook, "100003569923517",
			"AAAEJgrNu5P0BAGasTuCZAKVX130aavXNtsTPZCLZC9ybXk7nRVfIuKA3qTNeFgkBWPzmIW2" + 
			"x8MbFdP1juMOowezrmxkbMW1hQ88Nd5ZA8QZDZD"
		);
		testIdentityProviderSignatures(provider);
	}
	public void testUnverifiedEncryption() {
		testIdentityProviderEncryption(new UnverifiedIdentityProvider());
		
	}
	public void testUnverifiedSignature() {
		testIdentityProviderSignatures(new UnverifiedIdentityProvider());
	}
}
