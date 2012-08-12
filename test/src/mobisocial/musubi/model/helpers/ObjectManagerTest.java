package mobisocial.musubi.model.helpers;

import java.util.Arrays;

import mobisocial.musubi.model.MApp;
import mobisocial.musubi.model.MDevice;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MObject;
import mobisocial.musubi.util.Util;
import mobisocial.socialkit.Obj;
import mobisocial.socialkit.obj.MemObj;
import mobisocial.test.TestBase;
import mobisocial.test.TestDatabase;

import org.json.JSONObject;

import android.database.sqlite.SQLiteOpenHelper;

public class ObjectManagerTest extends TestBase {
    private ObjectManager mObjectManager;
    private TestDatabase database;
    MIdentity myIdentity;
    MDevice myDevice;

    public void setUp() {
        SQLiteOpenHelper dbh = new DatabaseFile(getContext(), null, new DebugSQLiteCursorFactory());
        mObjectManager = new ObjectManager(dbh);
        database = new TestDatabase(getContext(), dbh);

        myIdentity = database.insertIdentity(randomIBIdentity(), true, true);
        myDevice = new MDevice();
        myDevice.identityId_ = myIdentity.id_;
        myDevice.deviceName_ = database.getDeviceManager().getLocalDeviceName();
        database.getDeviceManager().insertDevice(myDevice);
        database.insertIdentity(randomIBIdentity(), false, true);
        database.insertIdentity(randomIBIdentity(), false, false);
    }

    public void testObjectInsertAndLookup() {
        // direct insert
        MApp appRow = database.getAppManager().ensureApp("rosie.cat");
        byte[] hash = new byte[32];
        r.nextBytes(hash);
        MObject object = new MObject();
        object.id_ = -1;
        object.feedId_ = 540;
        object.identityId_ = myIdentity.id_;
        object.deviceId_ = myDevice.id_;
        object.appId_ = appRow.id_;
        object.type_ = "gato";
        object.universalHash_ = hash;
        object.shortUniversalHash_ = Util.shortHash(hash);
        mObjectManager.insertObject(object);
        assert(object.id_ != -1);

        MObject lookup = mObjectManager.getObjectForId(object.id_);
        assertObjectsEqual(lookup, object);

        // Insert for local user (common case)
        String aString = "Some text";
        MFeed feed = database.createFixedFeed(myIdentity);
        Obj obj = new MemObj("someObject", null, null, null, aString);
        MObject inDatabase = database.insert(feed, obj);

        MObject lookup2 = mObjectManager.getObjectForId(inDatabase.id_);
        assertEquals(lookup2.stringKey_, aString);   

        long hashLookupId = mObjectManager.getObjectIdForHash(inDatabase.universalHash_);
        assertEquals(lookup2.id_, hashLookupId);
    }

    public void testObjectUpdate() {
        MApp appRow = database.getAppManager().ensureApp("rosie.cat");
        byte[] hash = new byte[32];
        r.nextBytes(hash);
        MObject object = new MObject();
        object.id_ = -1;
        object.feedId_ = 540;
        object.identityId_ = myIdentity.id_;
        object.deviceId_ = 1;
        object.appId_ = appRow.id_;
        object.type_ = "gato";
        object.intKey_ = 7;
        object.stringKey_ = "mrow";
        object.universalHash_ = hash;
        object.shortUniversalHash_ = Util.shortHash(hash);

        mObjectManager.insertObject(object);
        assertNotSame(-1, object.id_);
        MObject lookup = mObjectManager.getObjectForId(object.id_);
        assertObjectsEqual(lookup, object);

        object.type_ = "kitty";
        object.json_ = null;
        object.intKey_ = null;
        object.stringKey_ = null;
        mObjectManager.updateObject(object);
        lookup = mObjectManager.getObjectForId(object.id_);
        assertEquals("kitty", object.type_);
        assertEquals(object.type_, lookup.type_);
        assertNull(lookup.json_);
        assertNull(lookup.intKey_);
        assertNull(lookup.stringKey_);
    }

    public void testObjectInsertNulls() throws Exception {
        MApp appRow = database.getAppManager().ensureApp("rosie.cat");
        byte[] hash = new byte[32];
        r.nextBytes(hash);
        MObject object = new MObject();
        object.id_ = -1;
        object.feedId_ = 540;
        object.identityId_ = myIdentity.id_;
        object.deviceId_ = 1;
        object.appId_ = appRow.id_;
        object.type_ = "gato";
        object.intKey_ = null;
        object.stringKey_ = null;
        object.raw_ = null;
        object.json_ = null;
        object.universalHash_ = null;
        object.shortUniversalHash_ = null;

        mObjectManager.insertObject(object);
        assertNotSame(-1, object.id_);
        MObject lookup = mObjectManager.getObjectForId(object.id_);
        assertNull(lookup.intKey_);
        assertNull(lookup.stringKey_);
        assertNull(lookup.raw_);
        assertNull(lookup.json_);
        assertNull(lookup.universalHash_);

        String typeSrc = "kitty";
        String jsonSrc = new JSONObject("{\"a\":\"b\"}").toString();
        Integer intSrc = 134;
        String stringSrc = "one thirty-four";
        byte[] rawSrc = new byte[] { '1', '3', '4' };
        object.type_ = typeSrc;
        object.json_ = jsonSrc;
        object.raw_ = rawSrc;
        object.intKey_ = intSrc;
        object.stringKey_ = stringSrc;
        object.universalHash_ = new byte[] { '1', '2', '3', '4', '5', '6', '7', '8' };
        object.shortUniversalHash_ = Util.shortHash(object.universalHash_);

        mObjectManager.updateObject(object);
        lookup = mObjectManager.getObjectForId(object.id_);
        assertEquals("kitty", object.type_);
        assertEquals(jsonSrc, lookup.json_);
        assertTrue(Arrays.equals(rawSrc, lookup.raw_));
        assertEquals(intSrc, lookup.intKey_);
        assertEquals(stringSrc, lookup.stringKey_);
        assertEquals(object.shortUniversalHash_, (Long)Util.shortHash(lookup.universalHash_));

        object = lookup;
        object.json_ = null;
        object.raw_ = null;
        object.intKey_ = null;
        object.stringKey_ = null;
        object.universalHash_ = null;
        object.shortUniversalHash_ = null;
        mObjectManager.updateObject(object);

        lookup = mObjectManager.getObjectForId(object.id_);
        assertNull(lookup.json_);
        assertNull(lookup.raw_);
        assertNull(lookup.intKey_);
        assertNull(lookup.stringKey_);
        assertNull(lookup.universalHash_);
        assertNull(lookup.shortUniversalHash_);
    }
}
