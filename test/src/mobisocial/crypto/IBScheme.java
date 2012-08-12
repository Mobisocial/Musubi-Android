package mobisocial.crypto;

import android.util.Base64;
import android.util.Log;
import junit.framework.TestCase;

public class IBScheme extends TestCase {
	public static final String TAG = "SCHEMEGEN";
	public void testEncryptionScheme() throws Exception {
		IBEncryptionScheme s = new IBEncryptionScheme();
		Log.i(TAG, "enc master key: " + Base64.encodeToString(s.masterKey_.key_, Base64.DEFAULT));
		Log.i(TAG, "enc public parameters: " + Base64.encodeToString(s.params_, Base64.DEFAULT));
	}
	public void testSignatureScheme() throws Exception {
		IBSignatureScheme s = new IBSignatureScheme();
		Log.i(TAG, "sig master key: " + Base64.encodeToString(s.masterKey_.key_, Base64.DEFAULT));
		Log.i(TAG, "sig public parameters: " + Base64.encodeToString(s.params_, Base64.DEFAULT));
	}
}
