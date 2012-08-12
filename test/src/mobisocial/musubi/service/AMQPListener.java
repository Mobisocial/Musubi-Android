package mobisocial.musubi.service;

import java.util.Date;
import java.util.Random;

import mobisocial.crypto.IBIdentity;
import mobisocial.musubi.identity.IdentityProvider;
import mobisocial.musubi.identity.IdentityProviderException;
import mobisocial.musubi.model.MEncodedMessage;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MSignatureUserKey;
import mobisocial.musubi.model.helpers.DatabaseFile;
import mobisocial.musubi.model.helpers.EncodedMessageManager;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.model.helpers.MessageTransportManager;
import mobisocial.musubi.model.helpers.UserKeyManager;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.test.mock.MockContentResolver;

public class AMQPListener {
	IBIdentity mMe;
	IdentityProvider mIdp;
	AMQPService mAMQP;
	SQLiteOpenHelper mDbh;
	Random r = new Random();
	public AMQPListener(Context original_context, IdentityProvider idp, IBIdentity me) {
		mMe = me;
		mIdp = idp;
    	//set up identity and signature user secret
		mDbh = new DatabaseFile(original_context, null);
		long myDeviceName0 = r.nextLong();
		IdentitiesManager idm0 = new IdentitiesManager(mDbh);
		MessageTransportManager mtm0 = new MessageTransportManager(mDbh, mIdp.getEncryptionScheme(),
				mIdp.getSignatureScheme(), myDeviceName0);
		MIdentity myid0 = mtm0.addClaimedIdentity(me);
		myid0.owned_ = true;
		myid0.principal_ = me.principal_;
		idm0.updateIdentity(myid0);
		
		try
		{
			UserKeyManager sm0 = new UserKeyManager(mIdp.getEncryptionScheme(), mIdp.getSignatureScheme(), mDbh);
			MSignatureUserKey sigKey = new MSignatureUserKey();
			sigKey.identityId_ = myid0.id_;
			IBIdentity required_key = me.at(mtm0.getSignatureTime(myid0));
			sigKey.userKey_ = mIdp.syncGetSignatureKey(required_key).key_;
			sigKey.when_ = required_key.temporalFrame_;
			sm0.insertSignatureUserKey(sigKey);
		} catch (IdentityProviderException e) {}

		mAMQP = new AMQPService(mDbh) {
			@Override
			public ContentResolver getContentResolver() {
				return new MockContentResolver() {
					@Override
					public void notifyChange(Uri uri, ContentObserver observer) {
						//we dont need notifies because we aren't waking up the
						//obj decoder, we aren't being woken up by the obj encoder
						//and we don't need to listen for network status changes
						//during testing.
					}
				};
			}
		};
	}

    MEncodedMessage waitForNthMessage(long n, long millis) throws InterruptedException {
		EncodedMessageManager emm = new EncodedMessageManager(mDbh);
        long start = new Date().getTime();
        for(;;) {
        	if(new Date().getTime() - start > millis)
        		break;
        	//success
        	if(emm.lookupById(n) != null)
        		return emm.lookupById(n);
        	Thread.sleep(1000);
        }
        return null;
    }

	public void destroy() {
		// if the internals change, this may need to change	
		mAMQP.onDestroy();
		mDbh.close();
	}
	
	
}
