package mobisocial.musubi.model.helpers;

import android.database.Cursor;
import android.database.sqlite.SQLiteOpenHelper;
import mobisocial.musubi.model.MApp;
import mobisocial.musubi.model.MFact;
import mobisocial.musubi.model.MFactType;
import mobisocial.test.TestBase;
import mobisocial.test.TestDatabase;

public class FactManagerTest extends TestBase {
	SQLiteOpenHelper dbh;
    private TestDatabase database;

    public void setUp() {
        dbh = new DatabaseFile(getContext(), null, new DebugSQLiteCursorFactory());
        database = new TestDatabase(getContext(), dbh);
    }
    public void tearDown() {
    	dbh.close();
    }

    public void testLearnFacts() {
        FactManager fm = database.getFactManager();
        String appId = "super.app";
        MApp app = database.getAppManager().ensureApp(appId);
        String typeAttr = "mimeType";
        String typeEdit = "editOf";
        String typeScore = "score";
        Object scoreField = null;

        MFactType ftAttr = fm.ensureFactType(typeAttr);
        assertEquals(typeAttr, ftAttr.factType_);
        assertTrue(ftAttr.id_ >= 0);

        MFactType ftEdit = fm.ensureFactType(typeEdit);
        assertEquals(typeEdit, ftEdit.factType_);
        assertTrue(ftEdit.id_ > ftAttr.id_);
        MFactType ftEdit2 = fm.ensureFactType(typeEdit);
        assertEquals(ftEdit.id_, ftEdit2.id_);

        MFactType ftScore = fm.ensureFactType(typeScore);
        assertEquals(typeScore, ftScore.factType_);
        assertTrue(ftScore.id_ > ftEdit.id_);

        MFact mimeType = fm.ensureFact(app, ftAttr, "image/jpeg", "file/image");
        MFact dupe = fm.ensureFact(app, ftEdit, 303, 412);
        MFact score = fm.ensureFact(app, ftScore, 1337, "level 1", 20.8, scoreField, "twenty.eight");
        
        assertTrue(mimeType.id_ > -1);
        assertTrue(dupe.id_ > mimeType.id_);
        assertTrue(score.id_ > dupe.id_);

        MFact mimeLookup = fm.getFact(mimeType.id_);
        assertEquals(app.id_, mimeLookup.appId_);
        assertEquals(ftAttr.id_, mimeLookup.fact_type_id);
        assertEquals("file/image", mimeLookup.A_);
        assertEquals("image/jpeg", mimeLookup.V_);
        assertNull(mimeLookup.B_);
        assertNull(mimeLookup.C_);
        assertNull(mimeLookup.D_);
        MFact dupeLookup = fm.getFact(dupe.id_);
        assertEquals(ftEdit.id_, dupeLookup.fact_type_id);
        assertEquals(303, dupeLookup.V_);
        assertEquals(412, dupeLookup.A_);
        assertNull(dupeLookup.B_);
        assertNull(dupeLookup.C_);
        assertNull(dupeLookup.D_);
        MFact scoreLookup = fm.getFact(score.id_);
        assertEquals(ftScore.id_, scoreLookup.fact_type_id);
        assertEquals(1337, scoreLookup.V_);
        assertEquals("level 1", scoreLookup.A_);
        assertEquals(20.8, scoreLookup.B_);
        assertEquals(scoreField, scoreLookup.C_);
        assertEquals("twenty.eight", scoreLookup.D_);

        fm.ensureFact(app, ftEdit, "V", "A", "B", "C", "D");
        Cursor c = database.getReadableDatabase().rawQuery("select count(*) from facts", null);
        c.moveToNext();
        assertEquals(4, c.getInt(0));

        // Dupe rows
        fm.ensureFact(app, ftEdit, "V", "A", "B", "C", "D");
        fm.ensureFact(app, ftEdit, 303, 412);
        fm.ensureFact(app, ftScore, 1337, "level 1", 20.8, scoreField, "twenty.eight");
        c = database.getReadableDatabase().rawQuery("select count(*) from facts", null);
        c.moveToNext();
        assertEquals(4, c.getInt(0));
    }
}
