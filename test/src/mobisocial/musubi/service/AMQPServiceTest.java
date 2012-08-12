package mobisocial.musubi.service;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Random;

import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.crypto.IBIdentity;
import mobisocial.musubi.encoding.MessageEncoder;
import mobisocial.musubi.encoding.OutgoingMessage;
import mobisocial.musubi.identity.UnverifiedIdentityProvider;
import mobisocial.musubi.model.MEncodedMessage;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MSignatureUserKey;
import mobisocial.musubi.model.helpers.EncodedMessageManager;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.model.helpers.MessageTransportManager;
import mobisocial.musubi.model.helpers.UserKeyManager;
import mobisocial.musubi.util.Util;
import mobisocial.test.MockMusubiAppContext;
import android.content.Intent;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.IBinder;
import android.test.ServiceTestCase;

public class AMQPServiceTest extends ServiceTestCase<AMQPService> {
	UnverifiedIdentityProvider mIdp = new UnverifiedIdentityProvider();

	public AMQPServiceTest() {
		super(AMQPService.class);
	}
    @Override
    protected void setUp() throws Exception {
        super.setUp();
    	setContext(new MockMusubiAppContext(getContext()));
    }
	public void testStartable() {
        Intent startIntent = new Intent();
        startIntent.setClass(getContext(), AMQPService.class);
        startService(startIntent); 
    }
    public void testDefaultDatabaseBindable() {
        Intent startIntent = new Intent();
        startIntent.setClass(getContext(), AMQPService.class);
        bindService(startIntent); 
    }
    public void testCustomDatabaseBindable() {
        Intent startIntent = new Intent();
        startIntent.setClass(getContext(), AMQPService.class);
        IBinder service = bindService(startIntent); 
        @SuppressWarnings("unused")
		AMQPService amqpService = ((AMQPService.AMQPServiceBinder)service).getService();
    }
    boolean waitForConnection(AMQPService amqpService, long millis) throws InterruptedException {
        long start = new Date().getTime();
        for(;;) {
        	if(new Date().getTime() - start > millis)
        		break;
        	//success
        	if(amqpService.isConnectionReady())
        		return true;
        	Thread.sleep(1000);
        }
        return false;
    }
    public void testConnectSuccessful() throws InterruptedException {
        Intent startIntent = new Intent();
        startIntent.setClass(getContext(), AMQPService.class);
        IBinder service = bindService(startIntent); 
        AMQPService amqpService = ((AMQPService.AMQPServiceBinder)service).getService();
    	//allow 20 seconds to connect
        assertTrue(waitForConnection(amqpService, 20 * 1000));
    }
	Random r = new Random();
	final HashSet<String> usedNames = new HashSet<String>();
	String randomUniquePrincipal() {
		for(;;) {
			int length = r.nextInt(16);
			StringBuilder sb = new StringBuilder();
			for(int i = 0; i < length; ++i) {
				char c = (char) ('a' + r.nextInt('z' - 'a'));
				sb.append(c);
			}
			sb.append("@gmail.com");
			String result = sb.toString();
			if(usedNames.contains(result))
				continue;
			usedNames.add(result);
			return result;
		}		
	}    
   
	public void testSendAtoBwithBFirst() throws Exception {
    	MockMusubiAppContext c0 = new MockMusubiAppContext(getContext());
    	
    	//set up identity and signature user secret
    	final SQLiteOpenHelper dbh0 = c0.getDatabaseSource();
		final IBIdentity me = new IBIdentity(Authority.Email, randomUniquePrincipal(), 0);
		long myDeviceName0 = r.nextLong();
		IdentitiesManager idm0 = new IdentitiesManager(dbh0);
		MessageTransportManager mtm0 = new MessageTransportManager(dbh0, mIdp.getEncryptionScheme(),
				mIdp.getSignatureScheme(), myDeviceName0);
		MIdentity myid0 = mtm0.addClaimedIdentity(me);
		myid0.owned_ = true;
		myid0.principal_ = me.principal_;
		idm0.updateIdentity(myid0);
		UserKeyManager sm0 = new UserKeyManager(mIdp.getEncryptionScheme(), mIdp.getSignatureScheme(), dbh0);
		MSignatureUserKey sigKey = new MSignatureUserKey();
		sigKey.identityId_ = myid0.id_;
		IBIdentity required_key = me.at(mtm0.getSignatureTime(myid0));
		sigKey.userKey_ = mIdp.syncGetSignatureKey(required_key).key_;
		sigKey.when_ = required_key.temporalFrame_;
		sm0.insertSignatureUserKey(sigKey);

    	setContext(c0);
        Intent startIntent0 = new Intent();
        startIntent0.setClass(c0, AMQPService.class);
        IBinder service0 = bindService(startIntent0); 
        AMQPService amqpService0 = ((AMQPService.AMQPServiceBinder)service0).getService();
        @SuppressWarnings("unused")
		EncodedMessageManager emm0 = new EncodedMessageManager(dbh0);

    	//pick the other identity 
		final IBIdentity you = new IBIdentity(Authority.Email, randomUniquePrincipal(), 0);
        //set up the background service instance
		AMQPListener listener = new AMQPListener(getContext(), mIdp, you);
        
        
		//make up a message
        OutgoingMessage om = new OutgoingMessage();
        om.data_ = new byte[32];
        r.nextBytes(om.data_);
		om.app_ = new byte[32];
		r.nextBytes(om.app_);
        om.fromIdentity_ = idm0.getOwnedIdentities().get(1);
        om.recipients_ = new MIdentity[] { mtm0.addClaimedIdentity(you) };
        om.hash_ = Util.sha256(om.data_);
        
        
		//encode the message, implicitly inserts it, so amqp should now be able
        //to get access to it
		MessageEncoder encoder = new MessageEncoder(mtm0);
		MEncodedMessage encodedOutgoing;
		try {
			encodedOutgoing = encoder.processMessage(om);
		} catch (Exception e) {
			throw e;
		}

		c0.getContentResolver().notifyChange(MusubiService.PREPARED_ENCODED, null);
		
    	//allow 20 seconds to connect and it will automatically send messages
		//when the connection is established
        assertTrue(waitForConnection(amqpService0, 20 * 1000));

		MEncodedMessage encodedIncoming = listener.waitForNthMessage(1, 20 * 1000);
		assertNotNull(encodedIncoming);
		assertTrue(Arrays.equals(encodedOutgoing.encoded_, encodedIncoming.encoded_));
		
		listener.destroy();
    }
	public void testSendAtoBwithAFirst() throws Exception {
    	UnverifiedIdentityProvider idp = new UnverifiedIdentityProvider();
    	MockMusubiAppContext c0 = new MockMusubiAppContext(getContext());
    	
    	//set up identity and signature user secret
    	final SQLiteOpenHelper dbh0 = c0.getDatabaseSource();
		final IBIdentity me = new IBIdentity(Authority.Email, randomUniquePrincipal(), 0);
		long myDeviceName0 = r.nextLong();
		IdentitiesManager idm0 = new IdentitiesManager(dbh0);
		MessageTransportManager mtm0 = new MessageTransportManager(dbh0, idp.getEncryptionScheme(),
				idp.getSignatureScheme(), myDeviceName0);
		MIdentity myid0 = mtm0.addClaimedIdentity(me);
		myid0.owned_ = true;
		myid0.principal_ = me.principal_;
		idm0.updateIdentity(myid0);
		UserKeyManager sm0 = new UserKeyManager(mIdp.getEncryptionScheme(), mIdp.getSignatureScheme(), dbh0);
		MSignatureUserKey sigKey = new MSignatureUserKey();
		sigKey.identityId_ = myid0.id_;
		IBIdentity required_key = me.at(mtm0.getSignatureTime(myid0));
		sigKey.userKey_ = idp.syncGetSignatureKey(required_key).key_;
		sigKey.when_ = required_key.temporalFrame_;
		sm0.insertSignatureUserKey(sigKey);

    	setContext(c0);
        Intent startIntent0 = new Intent();
        startIntent0.setClass(c0, AMQPService.class);
        IBinder service0 = bindService(startIntent0); 
        AMQPService amqpService0 = ((AMQPService.AMQPServiceBinder)service0).getService();
        @SuppressWarnings("unused")
		EncodedMessageManager emm0 = new EncodedMessageManager(dbh0);

    	//pick the other identity 
		final IBIdentity you = new IBIdentity(Authority.Email, randomUniquePrincipal(), 0);
        
        
		//make up a message
        OutgoingMessage om = new OutgoingMessage();
        om.data_ = new byte[32];
        r.nextBytes(om.data_);
		om.app_ = new byte[32];
		r.nextBytes(om.app_);
        om.fromIdentity_ = idm0.getOwnedIdentities().get(1);
        om.recipients_ = new MIdentity[] { mtm0.addClaimedIdentity(you) };
        om.hash_ = Util.sha256(om.data_);
        
        
		//encode the message, implicitly inserts it, so amqp should now be able
        //to get access to it
		MessageEncoder encoder = new MessageEncoder(mtm0);
		MEncodedMessage encodedOutgoing;
		try {
			encodedOutgoing = encoder.processMessage(om);
		} catch (Exception e) {
			throw e;
		}

		c0.getContentResolver().notifyChange(MusubiService.PREPARED_ENCODED, null);
		
    	//allow 20 seconds to connect and it will automatically send messages
		//when the connection is established
        assertTrue(waitForConnection(amqpService0, 20 * 1000));

        //set up the background service instance
		AMQPListener listener = new AMQPListener(getContext(), idp, you);
        
		MEncodedMessage encodedIncoming = listener.waitForNthMessage(1, 20 * 1000);
		assertNotNull(encodedIncoming);
		assertTrue(Arrays.equals(encodedOutgoing.encoded_, encodedIncoming.encoded_));

		listener.destroy();
    }
	public void testExactlyTwoMessages() throws Exception {
    	UnverifiedIdentityProvider idp = new UnverifiedIdentityProvider();

    	MockMusubiAppContext c0 = new MockMusubiAppContext(getContext());
    	//set up identity and signature user secret
    	final SQLiteOpenHelper dbh0 = c0.getDatabaseSource();
		final IBIdentity me = new IBIdentity(Authority.Email, randomUniquePrincipal(), 0);
		long myDeviceName0 = r.nextLong();
		IdentitiesManager idm0 = new IdentitiesManager(dbh0);
		MessageTransportManager mtm0 = new MessageTransportManager(dbh0, idp.getEncryptionScheme(),
				idp.getSignatureScheme(), myDeviceName0);
		MIdentity myid0 = mtm0.addClaimedIdentity(me);
		myid0.owned_ = true;
		myid0.principal_ = me.principal_;
		idm0.updateIdentity(myid0);
		UserKeyManager sm0 = new UserKeyManager(mIdp.getEncryptionScheme(), mIdp.getSignatureScheme(), dbh0);
		MSignatureUserKey sigKey = new MSignatureUserKey();
		sigKey.identityId_ = myid0.id_;
		IBIdentity required_key = me.at(mtm0.getSignatureTime(myid0));
		sigKey.userKey_ = idp.syncGetSignatureKey(required_key).key_;
		sigKey.when_ = required_key.temporalFrame_;
		sm0.insertSignatureUserKey(sigKey);

    	setContext(c0);
        Intent startIntent0 = new Intent();
        startIntent0.setClass(c0, AMQPService.class);
        IBinder service0 = bindService(startIntent0); 
        AMQPService amqpService0 = ((AMQPService.AMQPServiceBinder)service0).getService();
        @SuppressWarnings("unused")
		EncodedMessageManager emm0 = new EncodedMessageManager(dbh0);

    	//pick the other identity 
		final IBIdentity you = new IBIdentity(Authority.Email, randomUniquePrincipal(), 0);
        
        
		//make up a message
        OutgoingMessage om = new OutgoingMessage();
        om.data_ = new byte[32];
        r.nextBytes(om.data_);
		om.app_ = new byte[32];
		r.nextBytes(om.app_);
        om.fromIdentity_ = idm0.getOwnedIdentities().get(1);
        om.recipients_ = new MIdentity[] { mtm0.addClaimedIdentity(you) };
        om.hash_ = Util.sha256(om.data_);
        
        
		//encode the message, implicitly inserts it
		MessageEncoder encoder = new MessageEncoder(mtm0);
		MEncodedMessage encodedOutgoing;
		try {
			encodedOutgoing = encoder.processMessage(om);
		} catch (Exception e) {
			throw e;
		}

		//encoded message available
		c0.getContentResolver().notifyChange(MusubiService.PREPARED_ENCODED, null);
		
    	//allow 20 seconds to connect and it will automatically send messages
		//when the connection is established
        assertTrue(waitForConnection(amqpService0, 20 * 1000));

        //set up the background service instance
		AMQPListener listener = new AMQPListener(getContext(), idp, you);
        
		MEncodedMessage encodedIncoming = listener.waitForNthMessage(1, 20 * 1000);
		assertNotNull(encodedIncoming);
		assertTrue(Arrays.equals(encodedOutgoing.encoded_, encodedIncoming.encoded_));

		//make up a message
        OutgoingMessage omB = new OutgoingMessage();
        omB.data_ = new byte[32];
        r.nextBytes(omB.data_);
		omB.app_ = new byte[32];
		r.nextBytes(om.app_);
        omB.fromIdentity_ = idm0.getOwnedIdentities().get(1);
        omB.recipients_ = new MIdentity[] { mtm0.addClaimedIdentity(you) };
        omB.hash_ = Util.sha256(omB.data_);
        
        
		//encode the message, implicitly inserts it, so amqp should now be able
        //to get access to it
		MEncodedMessage encodedOutgoingB;
		try {
			encodedOutgoingB = encoder.processMessage(omB);
		} catch (Exception e) {
			throw e;
		}
		
		c0.getContentResolver().notifyChange(MusubiService.PREPARED_ENCODED, null);
	
		MEncodedMessage encodedIncomingB = listener.waitForNthMessage(2, 20 * 1000);
		assertNotNull(encodedIncomingB);
		assertTrue(Arrays.equals(encodedOutgoingB.encoded_, encodedIncomingB.encoded_));

		listener.destroy();
	}
}
