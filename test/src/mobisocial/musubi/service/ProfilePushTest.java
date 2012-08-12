package mobisocial.musubi.service;

import gnu.trove.list.linked.TLongLinkedList;
import mobisocial.crypto.IBIdentity;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MMyAccount;
import mobisocial.musubi.model.helpers.DatabaseFile;
import mobisocial.test.TestBase;
import mobisocial.test.TestDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class ProfilePushTest extends TestBase {
    TestDatabase database;
    SQLiteOpenHelper dbh;
    private MMyAccount myAccount;
    private MIdentity myIdentity;
    private MIdentity friend1;
    private MIdentity friend2;
    private MIdentity stranger;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        dbh = new DatabaseFile(getContext(), null);
        database = new TestDatabase(getContext(), dbh);

        IBIdentity me = randomIBIdentity();
        myIdentity = database.insertIdentity(me, true, true);
        myAccount = new MMyAccount();
        myAccount.accountName_ = "bjdodson@gmail.com";
        myAccount.accountType_ = "google";
        myAccount.identityId_ = myIdentity.id_;
        database.getMyAccountManager().insertAccount(myAccount);
        MFeed feed = new MFeed();
        feed.accepted_ = false; //not visible
        feed.type_ = MFeed.FeedType.ASYMMETRIC;
        feed.name_ = myAccount.accountName_;
        database.getFeedManager().insertFeed(feed);
        myAccount.feedId_ = feed.id_;
        database.getMyAccountManager().updateAccount(myAccount);

        friend1 = database.insertIdentity(randomIBIdentity(), false, true);
        friend2 = database.insertIdentity(randomIBIdentity(), false, false);
        stranger = database.insertIdentity(randomIBIdentity(), false, true);

        friend1.whitelisted_ = true;
        friend2.whitelisted_ = true;
        stranger.whitelisted_ = true;
        database.getIdentityManager().updateIdentity(friend1);
        database.getIdentityManager().updateIdentity(friend2);
        database.getIdentityManager().updateIdentity(stranger);
    }

    public void testProfilePush() {
        ProfilePushProcessor push = ProfilePushProcessor.newInstance(getContext(), dbh);
        MFeed sync = push.prepareFeedForSync(myAccount, myIdentity, 1L, true);
        assertNull(sync);

        assertNotNull(myAccount.feedId_);
        database.getFeedManager().ensureFeedMember(myAccount.feedId_, friend1.id_);
        database.getFeedManager().ensureFeedMember(myAccount.feedId_, friend2.id_);
        MFeed accountFeed = database.getFeedManager().lookupFeed(myAccount.feedId_);
        MIdentity[] ids = database.getFeedManager().getFeedMembers(accountFeed);
        assertEquals(2, ids.length);
        assertEquals(0, friend1.receivedProfileVersion_);
        assertEquals(0, friend2.receivedProfileVersion_);

        MFeed newFeed = new MFeed();
        newFeed.type_ = MFeed.FeedType.ONE_TIME_USE; 
        newFeed.id_ = -1;
        database.getFeedManager().insertFeed(newFeed);
        assertNotSame(-1, newFeed.id_);

        sync = push.prepareFeedForSync(myAccount, myIdentity, 1L, true);
        assertNotNull(sync);
        MIdentity[] members = database.getFeedManager().getFeedMembers(sync);
        TLongLinkedList memberIds = new TLongLinkedList();
        for(MIdentity member : members) {
        	memberIds.add(member.id_);
        }

        push.markIdentitiesSynced(memberIds.toArray(), 1L);
        MIdentity friend1Lookup = database.getIdentityManager().getIdentityForId(friend1.id_);
        assertEquals(1L, friend1Lookup.sentProfileVersion_);

        sync = push.prepareFeedForSync(myAccount, myIdentity, 1L, true);
        assertNull(sync);

        // All accounts have been synced at least once.
        sync = push.prepareFeedForSync(myAccount, myIdentity, 2L, true);
        assertNull(sync);

        sync = push.prepareFeedForSync(myAccount, myIdentity, 2L, false);
        assertNotNull(sync);
    }
}