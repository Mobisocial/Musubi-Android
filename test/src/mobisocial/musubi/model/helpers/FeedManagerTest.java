package mobisocial.musubi.model.helpers;

import java.util.Arrays;
import java.util.Date;

import mobisocial.crypto.IBIdentity;
import mobisocial.musubi.model.MApp;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MFeedApp;
import mobisocial.musubi.model.MIdentity;
import mobisocial.test.TestBase;
import mobisocial.test.TestDatabase;
import android.database.Cursor;
import android.database.sqlite.SQLiteOpenHelper;

public class FeedManagerTest extends TestBase {
    private TestDatabase database;
    private MIdentity myIdentity;
    private MIdentity friend1;
    private MIdentity friend2;
    private MIdentity stranger;

    public void setUp() {
        SQLiteOpenHelper dbh = new DatabaseFile(getContext(), null, new DebugSQLiteCursorFactory());
        database = new TestDatabase(getContext(), dbh);

        IBIdentity me = randomIBIdentity();
        myIdentity = database.insertIdentity(me, true, true);
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

    public void testFeedCreate() {
        MIdentity[] identities = new MIdentity[] { myIdentity, friend1, friend2 };
        byte[] capability = FeedManager.computeFixedIdentifier(identities);
        MFeed feed = database.getFeedManager().getOrCreateFixedFeed(myIdentity, friend1, friend2);

        assertTrue(Arrays.equals(capability, feed.capability_));

        MFeed lookup = database.getFeedManager().lookupFeed(feed.id_);
        assertNotNull(lookup);
        assertTrue(Arrays.equals(capability, lookup.capability_));
    }

    public void testFeedDelete() {
        FeedManager fm = database.getFeedManager();
        MFeed feed = fm.getOrCreateFixedFeed(myIdentity, friend1, friend2);

        MFeed lookup1 = database.getFeedManager().lookupFeed(feed.id_);
        assertNotNull(lookup1);
        assertEquals(3, fm.getFeedMembers(lookup1).length);

        fm.deleteFeedAndMembers(feed);
        MFeed lookup2 = database.getFeedManager().lookupFeed(feed.id_);
        assertNull(lookup2);
        assertEquals(0, fm.getFeedMembers(lookup1).length);
    }

    public void testFeedUpdate() {
        FeedManager fm = database.getFeedManager();
        MFeed feed = database.getFeedManager().getOrCreateFixedFeed(myIdentity, friend1, friend2);
        long feedId = feed.id_;

        MFeed lookup = fm.lookupFeed(feedId);
        lookup.name_ = "newName";
        database.getFeedManager().updateFeed(lookup);
        feed = fm.lookupFeed(feedId);

        assertEquals(lookup.name_, feed.name_);
        assertEquals(lookup.shortCapability_, feed.shortCapability_);
        assertTrue(lookup.shortCapability_ != 0);

        MIdentity[] members = fm.getFeedMembers(feed);
        assertEquals(3, members.length);
    }

    public void testFeedUpdateNullables() {
        FeedManager fm = database.getFeedManager();
        MFeed feed = database.getFeedManager().getOrCreateFixedFeed(myIdentity, friend1, friend2);
        long feedId = feed.id_;
        Long time = new Date().getTime();
        Long id = 4L;
        String name = "foobarred";

        MFeed lookup = fm.lookupFeed(feedId);
        lookup.name_ = name;
        lookup.latestRenderableObjId_ = id;
        lookup.latestRenderableObjTime_ = time;
        lookup.accepted_ = true;
        database.getFeedManager().updateFeed(lookup);
        feed = fm.lookupFeed(feedId);

        assertEquals(name, feed.name_);
        assertEquals(lookup.shortCapability_, feed.shortCapability_);
        assertTrue(feed.accepted_);
        assertTrue(feed.shortCapability_ != 0);
        assertEquals(id, feed.latestRenderableObjId_);
        assertEquals(time, feed.latestRenderableObjTime_);

        feed.latestRenderableObjId_ = null;
        feed.latestRenderableObjTime_ = null;
        feed.name_ = null;
        feed.accepted_ = false;

        database.getFeedManager().updateFeed(feed);
        lookup = fm.lookupFeed(feedId);
        assertFalse(lookup.accepted_);
        assertNull(lookup.name_);
        assertNull(lookup.latestRenderableObjId_);
        assertNull(lookup.latestRenderableObjTime_);
    }

    public void testFixedCapabilityGeneration() {
        MIdentity[] ids = new MIdentity[] { myIdentity, friend1, friend2 };
        byte[] c1 = FeedManager.computeFixedIdentifier(ids);
        
        ids = new MIdentity[] { friend1, friend2, myIdentity };
        byte[] c2 = FeedManager.computeFixedIdentifier(ids);
        assertTrue(Arrays.equals(c1, c2));

        ids = new MIdentity[] { friend1, friend2 };
        byte[] c3 = FeedManager.computeFixedIdentifier(ids);
        assertFalse(Arrays.equals(c1, c3));

        ids = new MIdentity[] { friend1, friend2, friend2, friend1};
        byte[] c4 = FeedManager.computeFixedIdentifier(ids);
        assertTrue(Arrays.equals(c3, c4));
    }

    public void testFeedApp() {
        MFeed feed = database.getFeedManager().getOrCreateFixedFeed(myIdentity, friend1, friend2);
        MApp app = database.getAppManager().ensureApp("some.app");
        Cursor c;

        c = getFeedApps(feed.id_);
        try {
            assertEquals(0, c.getCount());
        } finally {
            c.close();
        }

        database.getFeedManager().ensureFeedApp(feed.id_, app.id_);
        c = getFeedApps(feed.id_);
        try {
            assertEquals(1, c.getCount());
        } finally {
            c.close();
        }

        // reinsert should be noop
        database.getFeedManager().ensureFeedApp(feed.id_, app.id_);
        c = getFeedApps(feed.id_);
        try {
            assertEquals(1, c.getCount());
        } finally {
            c.close();
        }

        // make sure delete works
        database.getFeedManager().deleteFeedApp(feed.id_, app.id_);
        c = getFeedApps(feed.id_);
        try {
            assertEquals(0, c.getCount());
        } finally {
            c.close();
        }

        // make sure delete doesnt barf
        database.getFeedManager().deleteFeedApp(feed.id_, app.id_);
        c = getFeedApps(feed.id_);
        try {
            assertEquals(0, c.getCount());
        } finally {
            c.close();
        }
    }

    Cursor getFeedApps(long feed) {
        String selection = MFeedApp.COL_FEED_ID + "=?";
        String[] selectionArgs = new String[] { Long.toString(feed) };
        return database.getReadableDatabase().query(MFeedApp.TABLE, null, selection, selectionArgs,
                null, null, null);
    }
}
