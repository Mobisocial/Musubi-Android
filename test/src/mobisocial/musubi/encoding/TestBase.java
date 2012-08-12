package mobisocial.musubi.encoding;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;

import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.crypto.IBIdentity;
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

	void assertMessagesEqual(OutgoingMessage om, IncomingMessage im) {
		assertTrue(Arrays.equals(om.data_, im.data_));

		assertEquals(om.recipients_.length, im.recipients_.length);
		for(int i = 0; i < im.recipients_.length; ++i) {
			assertTrue(Arrays.equals(im.recipients_[i].principalHash_, om.recipients_[i].principalHash_));
			assertEquals(im.recipients_[i].type_, om.recipients_[i].type_);
		}

		assertTrue(Arrays.equals(om.fromIdentity_.principalHash_, im.fromIdentity_.principalHash_));
		assertEquals(im.fromIdentity_.type_, om.fromIdentity_.type_);
	}

	public class DebugSQLiteCursorFactory implements CursorFactory {
	    private boolean debugQueries = true;

	    public DebugSQLiteCursorFactory() {
	        Log.d("gggggg", "DEBUGDEBUGDEBUG");
	        debugQueries = true;
	    }

	    public DebugSQLiteCursorFactory(boolean debugQueries) {
	        this.debugQueries = debugQueries;
	    }

	    public Cursor newCursor(SQLiteDatabase db, SQLiteCursorDriver masterQuery, 
	                            String editTable, SQLiteQuery query) {
	        Log.d("KAAAAAAAAAAAAATE", "yyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyyy");
	        if (debugQueries) {
	            Log.d("SQL", query.toString());
	        }
	        return new SQLiteCursor(db, masterQuery, editTable, query);
	    }
	    
	}
}
