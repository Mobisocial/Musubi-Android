package mobisocial.musubi.provider;

import java.util.HashSet;
import java.util.Random;

import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.crypto.IBIdentity;
import mobisocial.musubi.model.MApp;
import mobisocial.musubi.model.MDevice;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MObject;
import mobisocial.musubi.obj.ObjHelpers;
import mobisocial.musubi.provider.MusubiContentProvider.Provided;
import mobisocial.socialkit.Obj;
import mobisocial.socialkit.musubi.DbObj;
import mobisocial.socialkit.obj.MemObj;
import mobisocial.test.MusubiProviderTestCase;
import mobisocial.test.TestDatabase;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;

public class ContentProviderTest extends MusubiProviderTestCase<MusubiContentProvider> {
    private SQLiteOpenHelper mDbHelper;
    TestDatabase database;
    MDevice myDevice;
    MIdentity myIdentity;
    MIdentity friend1;
    MIdentity friend2;
    MIdentity stranger;

    public ContentProviderTest() {
        super(MusubiContentProvider.class, MusubiContentProvider.AUTHORITY);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDbHelper = getProvider().getSQLiteOpenHelper();
        database = new TestDatabase(getContext(), mDbHelper);

        IBIdentity me = randomIBIdentity();
        myIdentity = database.insertIdentityAndDevice(me, true, true);
        friend1 = database.insertIdentityAndDevice(randomIBIdentity(), false, true);
        friend2 = database.insertIdentityAndDevice(randomIBIdentity(), false, false);
        stranger = database.insertIdentityAndDevice(randomIBIdentity(), false, true);

        friend1.whitelisted_ = true;
        friend2.whitelisted_ = true;
        stranger.whitelisted_ = false;
        database.getIdentityManager().updateIdentity(friend1);
        database.getIdentityManager().updateIdentity(friend2);
        database.getIdentityManager().updateIdentity(stranger);
    }

    /**
     * Insertion to a non-existed feed_id should fail
     */
    public void testInsertFail() {
        Obj obj = new BasicTestObj();
        ContentValues values = DbObj.toContentValues(Uri.parse("something://nothing/" + Long.MAX_VALUE), null, obj);
        Uri uri = getMockContentResolver().insert(
                MusubiContentProvider.uriForDir(Provided.OBJECTS), values);
        assertNull(uri);
    }

    public void testFeedInsertAndQuery() {
        // Create test feed
        MFeed f = database.getFeedManager().getOrCreateFixedFeed(myIdentity, friend1, friend2);
        Uri uri = MusubiContentProvider.uriForDir(Provided.OBJECTS);

        // Make sure no objects for the new feed
        String[] projection = null;
        String selection = MObject.COL_FEED_ID + " = ?";
        String[] selectionArgs = new String[] { Long.toString(f.id_) };
        String sortOrder = null;
        Cursor c = getMockContentResolver().query(uri, projection, selection, selectionArgs, sortOrder);
        assertEquals(0, c.getCount());

        // Insert into objects
        Uri feedUri = MusubiContentProvider.uriForItem(Provided.FEEDS, f.id_);
        ContentValues v = DbObj.toContentValues(feedUri, null, new BasicTestObj());
        getMockContentResolver().insert(uri, v);
        c = getMockContentResolver().query(uri, projection, selection, selectionArgs, sortOrder);

        assertEquals(1, c.getCount());
    }

    public void testAppCanAccessIdentity() {
        assertEquals(MusubiContentProvider.SUPER_APP_ID, getProvider().getCallingActivityId());
        final String APP = "some.app";

        // Create test feed and app
        MFeed f = database.getFeedManager().getOrCreateFixedFeed(myIdentity, friend1, friend2);
        Uri uri = MusubiContentProvider.uriForDir(Provided.OBJECTS);
        MApp app = database.getAppManager().ensureApp(APP);

        // Make sure no objects for the new feed
        String[] projection = null;
        String selection = MObject.COL_FEED_ID + " = ?";
        String[] selectionArgs = new String[] { Long.toString(f.id_) };
        String sortOrder = null;
        Cursor c = getMockContentResolver().query(uri, projection, selection, selectionArgs, sortOrder);
        assertEquals(0, c.getCount());

        // Make sure app not allowed but super is
        assertTrue(getProvider().appAllowedForIdentity(MusubiContentProvider.SUPER_APP_ID, friend1.id_));
        assertFalse(getProvider().appAllowedForIdentity(APP, friend1.id_));

        // Insert into objects
        Uri feedUri = MusubiContentProvider.uriForItem(Provided.FEEDS, f.id_);
        ContentValues v = DbObj.toContentValues(feedUri, null, new BasicTestObj());
        // Inject an alternative app id
        v.put(ObjHelpers.CALLER_APP_ID, APP);
        getMockContentResolver().insert(uri, v);

        // Check object's app id
        c = getMockContentResolver().query(uri, projection, selection, selectionArgs, sortOrder);
        try {
            assertTrue(c.moveToFirst());
            long insertedAppId = c.getLong(c.getColumnIndexOrThrow(MObject.COL_APP_ID));
            MApp lookupApp = database.getAppManager().getAppBasics(insertedAppId);
            assertEquals(APP, lookupApp.appId_);
            assertEquals(app.id_, insertedAppId);
        } finally {
            c.close();
        }

        // Check app allowed
        assertTrue(getProvider().appAllowedForIdentity(APP, friend1.id_));
    }

    public void testAppCanAccessFeed() {
        assertEquals(MusubiContentProvider.SUPER_APP_ID, getProvider().getCallingActivityId());
        final String APP = "some.app";

        // Create test feed and app
        MFeed f = database.getFeedManager().getOrCreateFixedFeed(myIdentity, friend1, friend2);
        Uri uri = MusubiContentProvider.uriForDir(Provided.OBJECTS);
        MApp app = database.getAppManager().ensureApp(APP);

        // Make sure no objects for the new feed
        String[] projection = null;
        String selection = MObject.COL_FEED_ID + " = ?";
        String[] selectionArgs = new String[] { Long.toString(f.id_) };
        String sortOrder = null;
        Cursor c = getMockContentResolver().query(uri, projection, selection, selectionArgs, sortOrder);
        assertEquals(0, c.getCount());

        // Make sure app not allowed but super is
        assertTrue(getProvider().appAllowedForFeed(MusubiContentProvider.SUPER_APP_ID, friend1.id_));
        assertFalse(getProvider().appAllowedForFeed(APP, f.id_));

        // Insert into objects
        Uri feedUri = MusubiContentProvider.uriForItem(Provided.FEEDS, f.id_);
        ContentValues v = DbObj.toContentValues(feedUri, null, new BasicTestObj());
        // Inject an alternative app id
        v.put(ObjHelpers.CALLER_APP_ID, APP);
        getMockContentResolver().insert(uri, v);

        // Check object's app id
        c = getMockContentResolver().query(uri, projection, selection, selectionArgs, sortOrder);
        try {
            assertTrue(c.moveToFirst());
            long insertedAppId = c.getLong(c.getColumnIndexOrThrow(MObject.COL_APP_ID));
            MApp lookupApp = database.getAppManager().getAppBasics(insertedAppId);
            assertEquals(APP, lookupApp.appId_);
            assertEquals(app.id_, insertedAppId);
        } finally {
            c.close();
        }

        // Check app allowed
        assertTrue(getProvider().appAllowedForFeed(APP, f.id_));
    }

    public void testQueryFeedMembers() {
        MFeed f = database.getFeedManager().getOrCreateFixedFeed(myIdentity, friend1, friend2);
        Uri uri = MusubiContentProvider.uriForItem(Provided.FEED_MEMBERS_ID, f.id_);
        String[] projection = null;
        String selection = null;
        String[] selectionArgs = null;
        String sortOrder = null;
        Cursor c = getMockContentResolver().query(uri, projection, selection, selectionArgs, sortOrder);
        assertEquals(3, c.getCount());
    }

    class BasicTestObj extends MemObj {
        public BasicTestObj() {
            super("BasicTest");
        }
    }

    IBIdentity randomIBIdentity() {
        return new IBIdentity(Authority.Email, randomUniquePrincipal(), 0);
    }

    Random r = new Random();
    private final HashSet<String> usedNames = new HashSet<String>();
    protected String randomUniquePrincipal() {
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
}
