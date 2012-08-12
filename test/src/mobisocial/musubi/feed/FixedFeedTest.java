package mobisocial.musubi.feed;

import java.util.Arrays;
import java.util.Random;

import mobisocial.crypto.IBEncryptionScheme;
import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.crypto.IBIdentity;
import mobisocial.crypto.IBSignatureScheme;
import mobisocial.musubi.encoding.TransientTransportDataProvider;
import mobisocial.musubi.identity.UnverifiedIdentityProvider;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.helpers.DatabaseFile;
import mobisocial.musubi.model.helpers.FeedManager;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.model.helpers.MessageTransportManager;
import mobisocial.musubi.util.Util;
import mobisocial.test.TestBase;
import android.database.sqlite.SQLiteOpenHelper;

public class FixedFeedTest extends TestBase {
    final String PRINCIPLE_PRINCIPAL = "bjdodson@cs.stanford.edu";
    final IBIdentity me = new IBIdentity(Authority.Email, PRINCIPLE_PRINCIPAL, 0);
    final IBEncryptionScheme encryptionScheme_ = new IBEncryptionScheme();
    final IBSignatureScheme signatureScheme_ = new IBSignatureScheme();
    final TransientTransportDataProvider tdp = new TransientTransportDataProvider(
            encryptionScheme_, signatureScheme_, me, null, null, null); 
    final UnverifiedIdentityProvider idp_ = new UnverifiedIdentityProvider();

    protected void setUp() throws Exception {
    }

    public void testStableName() {
    	SQLiteOpenHelper dbh = new DatabaseFile(getContext(), null);
        IBIdentity ib1 = new IBIdentity(Authority.Email, randomUniquePrincipal(), 0);
        IBIdentity ib2 = new IBIdentity(Authority.Email, randomUniquePrincipal(), 0);
        MIdentity id1 = tdp.addClaimedIdentity(ib1);
        MIdentity id2 = tdp.addClaimedIdentity(ib2);
        byte[] first, second;
        
        MIdentity[] list = new MIdentity[2];
        list[0] = id1;
        list[1] = id2;
        first = FeedManager.computeFixedIdentifier(list);

        list[0] = id2;
        list[1] = id1;
        second = FeedManager.computeFixedIdentifier(list);
        assertTrue(Arrays.equals(first, second));
        dbh.close();
    }

    public void testFeedCreation() {
        Random r = new Random();
        SQLiteOpenHelper dbh = new DatabaseFile(getContext(), null);
        long myDeviceName = r.nextLong();
        IdentitiesManager idm = new IdentitiesManager(dbh);
        MessageTransportManager mtm = new MessageTransportManager(dbh, idp_.getEncryptionScheme(),
                idp_.getSignatureScheme(), myDeviceName);

        MIdentity myid;
        myid = mtm.addClaimedIdentity(me);
        myid.owned_ = true;
        myid.principal_ = me.principal_;
        idm.updateIdentity(myid);

        FeedManager fm = new FeedManager(dbh);
        MIdentity[] participants = new MIdentity[3];
        participants[0] = myid;
        participants[1] = mtm.addClaimedIdentity(generateIBIdentity());
        participants[2] = mtm.addClaimedIdentity(generateIBIdentity());
        MFeed feed = fm.getOrCreateFixedFeed(participants);

        // Verify returned row
        byte[] capability = FeedManager.computeFixedIdentifier(participants);
        assertTrue(Arrays.equals(capability, feed.capability_));
        assertEquals((Long)Util.shortHash(feed.capability_), feed.shortCapability_);

        // Do a lookup to make sure it's really in the database
        MFeed lookup = fm.lookupFeed(feed.id_);
        assertEquals(feed.id_, lookup.id_);
        assertTrue(Arrays.equals(feed.capability_, lookup.capability_));
        assertEquals(feed.shortCapability_, lookup.shortCapability_);
        assertEquals((Long)Util.shortHash(feed.capability_), feed.shortCapability_);
    }

    IBIdentity generateIBIdentity() {
        return new IBIdentity(Authority.Email, randomUniquePrincipal(), 0);
    }
}
