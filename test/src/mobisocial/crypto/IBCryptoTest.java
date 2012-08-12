package mobisocial.crypto;

import java.util.Arrays;

import junit.framework.TestCase;

public class IBCryptoTest extends TestCase {
    public void testEncryption() {
        IBEncryptionScheme pp_original = new IBEncryptionScheme();
        IBEncryptionScheme.MasterKey mk = pp_original.masterKey_;
        IBEncryptionScheme pp_user = new IBEncryptionScheme(pp_original.params_);
        IBEncryptionScheme pp_loaded = new IBEncryptionScheme(pp_original.params_, mk);

        IBIdentity ident = new IBIdentity(IBIdentity.Authority.Email, "tpurtell@stanford.edu", 1);

        IBEncryptionScheme.UserKey user_key = pp_loaded.userKey(ident);
        IBEncryptionScheme.ConversationKey conv_key = pp_user.randomConversationKey(ident);

        byte[] key = pp_user.decryptConversationKey(user_key, conv_key.encryptedKey_);
        
        assertTrue("encrypt => decrypt (right identity) : failed to match conversation key", Arrays.equals(key, conv_key.key_));
        
        IBIdentity ident2 = new IBIdentity(IBIdentity.Authority.Email, "stfan@stanford.edu", 2);
        IBEncryptionScheme.UserKey user_key2 = pp_loaded.userKey(ident2);
        
        key = pp_user.decryptConversationKey(user_key2, conv_key.encryptedKey_);
        assertFalse("encrypt => decrypt (wrong identity): failed to mismatch conversation key", Arrays.equals(key, conv_key.key_));
    }
    public void testSignature() {
        IBSignatureScheme pp_original = new IBSignatureScheme();
        IBSignatureScheme.MasterKey mk = pp_original.masterKey_;
        IBSignatureScheme pp_user = new IBSignatureScheme(pp_original.params_);
        IBSignatureScheme pp_loaded = new IBSignatureScheme(pp_original.params_, mk);

        IBIdentity ident = new IBIdentity(IBIdentity.Authority.Email, "tpurtell@stanford.edu", 1);

        //normally we'd pass a hash but i am lazy
        byte[] data;
        try {
            data = "a message to sign".getBytes();
        } catch(Exception e) {
            throw new RuntimeException(e);
        }

        IBSignatureScheme.UserKey user_key = pp_loaded.userKey(ident);
        byte[] sig = pp_user.sign(ident, user_key, data);
        boolean ok = pp_user.verify(ident, sig, data);

        assertTrue("sign => verify (right identity) : failed to match", ok);
        sig[9]++;
        ok = pp_user.verify(ident, sig, data);
        assertFalse("sign => verify (wrong identity) : failed to mismatch", ok);
    }
}
