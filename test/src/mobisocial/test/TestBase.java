package mobisocial.test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;

import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.crypto.IBIdentity;
import mobisocial.musubi.encoding.IncomingMessage;
import mobisocial.musubi.encoding.OutgoingMessage;
import mobisocial.musubi.model.MObject;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteQuery;
import android.test.AndroidTestCase;
import android.util.Log;

public abstract class TestBase extends AndroidTestCase {
    protected String TAG = "MusubiTestBase";
	protected Random r = new Random();
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
	protected IBIdentity randomIBIdentity() {
	    String principal = randomUniquePrincipal();
	    long temporalFrame = IdentitiesManager.computeTemporalFrameFromPrincipal(principal);
	    return new IBIdentity(Authority.Email, principal, temporalFrame);
	}

	public class DebugSQLiteCursorFactory implements CursorFactory {
	    private boolean debugQueries = true;

	    public DebugSQLiteCursorFactory() {
	        debugQueries = true;
	    }

	    public DebugSQLiteCursorFactory(boolean debugQueries) {
	        this.debugQueries = debugQueries;
	    }

	    @Override
	    public Cursor newCursor(SQLiteDatabase db, SQLiteCursorDriver masterQuery, 
	                            String editTable, SQLiteQuery query) {
	        if (debugQueries) {
	            Log.d("SQL", query.toString());
	        }
	        return new SQLiteCursor(db, masterQuery, editTable, query);
	    }   
	}

	protected void assertMessagesEqual(OutgoingMessage om, IncomingMessage im) {
        assertTrue(Arrays.equals(om.data_, im.data_));

        assertEquals(om.recipients_.length, im.recipients_.length);
        for(int i = 0; i < im.recipients_.length; ++i) {
            assertTrue(Arrays.equals(im.recipients_[i].principalHash_, om.recipients_[i].principalHash_));
            assertEquals(im.recipients_[i].type_, om.recipients_[i].type_);
        }

        assertTrue(Arrays.equals(om.fromIdentity_.principalHash_, im.fromIdentity_.principalHash_));
        assertEquals(im.fromIdentity_.type_, om.fromIdentity_.type_);
    }

	protected void assertObjectsEqual(MObject a, MObject b) {
        if (a.equals(b)) {
            return;
        }
        assertEquals(a.id_, b.id_);
        assertEquals(a.feedId_, b.feedId_);
        assertEquals(a.identityId_, b.identityId_);
        assertEquals(a.deviceId_, b.deviceId_);
        assertTrue(a.parentId_ == null && b.parentId_ == null || a.parentId_.equals(b.parentId_));
        assertEquals(a.appId_, b.appId_);
        assertEquals(a.timestamp_, b.timestamp_);
        assertTrue(Arrays.equals(a.universalHash_, b.universalHash_));
        assertEquals(a.shortUniversalHash_, b.shortUniversalHash_);
        assertEquals(a.type_, b.type_);
        assertTrue(a.json_ == null && b.json_ == null || a.json_.equals(b.json_));
        assertTrue(a.raw_ == null && b.raw_ == null || Arrays.equals(a.raw_, b.raw_));
        assertTrue(a.intKey_ == null && b.intKey_ == null || a.intKey_.equals(b.intKey_));
        assertTrue(a.stringKey_ == null && b.stringKey_ == null || a.stringKey_.equals(b.stringKey_));
        assertEquals(a.lastModifiedTimestamp_, b.lastModifiedTimestamp_);
        assertTrue(a.encodedId_ == null && b.encodedId_ == null || a.encodedId_.equals(b.encodedId_));
        assertEquals(a.deleted_, b.deleted_);
        assertEquals(a.renderable_, b.renderable_);
        assertEquals(a.processed_, b.processed_);
    }
}
