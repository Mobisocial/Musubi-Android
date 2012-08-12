package mobisocial.musubi.model.helpers;

import android.database.sqlite.SQLiteOpenHelper;
import mobisocial.test.TestBase;

public class ContactDataVersionManagerTest extends TestBase {
    private SQLiteOpenHelper mDbh;

	public void setUp() {
        mDbh = new DatabaseFile(getContext(), null, new DebugSQLiteCursorFactory());
    }
    public void tearDown() {
    	mDbh.close();
    }

    public void testGetFail() {
        ContactDataVersionManager cdvh = new ContactDataVersionManager(mDbh);
        assertEquals(-1, cdvh.getVersion(123));
    }
    public void testSetGetOk() {
        ContactDataVersionManager cdvh = new ContactDataVersionManager(mDbh);
        cdvh.setVersion(123, 10);
        assertEquals(10, cdvh.getVersion(123));
        cdvh.setVersion(123, 11);
        assertEquals(11, cdvh.getVersion(123));
    }
}
