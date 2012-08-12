package mobisocial.musubi.model.helpers;

import mobisocial.musubi.model.MApp;
import mobisocial.test.TestBase;
import android.database.sqlite.SQLiteOpenHelper;

public class AppManagerTest extends TestBase {
    private SQLiteOpenHelper mDbh;

	public void setUp() {
        mDbh = new DatabaseFile(getContext(), null, new DebugSQLiteCursorFactory());
    }
    public void tearDown() {
    	mDbh.close();
    }

    public void testGetFail() {
    	AppManager am = new AppManager(mDbh);
        assertNull(am.getAppBasics(123));
    }
    public void testSetGetOk() {
    	AppManager am = new AppManager(mDbh);
        MApp app0 = am.ensureApp("tuple");
        MApp app1 = am.ensureApp("tuple");
        assertEquals(app0.id_, app1.id_);
        MApp app2 = am.getAppBasics(app0.id_);
        assertEquals(app0.id_, app2.id_);
        assertEquals("tuple", am.getAppIdentifier(app0.id_));
    }

    public void testUpdate() {
        AppManager am = new AppManager(mDbh);
        MApp app = am.ensureApp("theApp");
        assertNull(app.name_);
        assertNull(app.androidPackage_);
        assertNull(app.webAppUrl_);

        app.name_ = "aname";
        app.androidPackage_ = "com.awesome";
        app.webAppUrl_ = "http://sweet.ums";
        app.deleted_ = true;
        am.updateApp(app);

        MApp lookup = am.lookupApp(app.id_);
        assertEquals(app.appId_, lookup.appId_);
        assertEquals(app.name_, lookup.name_);
        assertEquals(app.androidPackage_, lookup.androidPackage_);
        assertEquals(app.webAppUrl_, lookup.webAppUrl_);
        assertEquals(lookup.deleted_, true);

        app.name_ = null;
        app.androidPackage_ = null;
        app.webAppUrl_ = null;
        app.deleted_ = false;
        am.updateApp(app);

        lookup = am.lookupApp(app.id_);
        assertNull(lookup.name_);
        assertNull(lookup.androidPackage_);
        assertNull(lookup.webAppUrl_);
        assertEquals(lookup.deleted_, true);

        am.deleteAppWithId(lookup.appId_);
        lookup = am.lookupApp(app.id_);
        assertNull(lookup);
    }
}
