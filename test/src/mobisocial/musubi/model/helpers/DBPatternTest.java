package mobisocial.musubi.model.helpers;

import gnu.trove.list.linked.TLongLinkedList;

import java.util.Arrays;
import java.util.Date;
import java.util.Random;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.test.AndroidTestCase;
import android.util.Base64;
import android.util.Log;

/**
 * This class provides tests that assess the efficiency of various 
 * different ways of accessing the local android SQL database
 */
public class DBPatternTest extends AndroidTestCase {
	static final int ITERATIONS = 1000;

	static class MTest {
		static final String TABLE = "test";
		static final String ID = "_id";
		static final String INTEGER = "int_field";
		static final String SHORT_STRING = "short_string";
		static final String LONG_STRING = "long_string";
		static final String SHORT_BYTES = "short_bytes";
		static final String LONG_BYTES = "long_bytes";
		
		static final String[] ALL = new String[] {
			ID, INTEGER, SHORT_STRING, LONG_STRING, SHORT_BYTES, LONG_BYTES
		};
		
		long id_;
		long integer_;
		String shortString_;
		String longString_;
		byte[] shortBytes_;
		byte[] longBytes_;
	}
	
	SQLiteOpenHelper mDatabaseSource;
	public void setUp() {
		mDatabaseSource = new SQLiteOpenHelper(getContext(), null, null, 1) {
			@Override
			public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
				throw new RuntimeException("doesn't make sense yo");
			}
			@Override
			public void onCreate(SQLiteDatabase db) {
				db.execSQL(
					"CREATE TABLE " + MTest.TABLE + " (" +
						MTest.ID + " INTEGER PRIMARY KEY," +
						MTest.INTEGER + " INTEGER," +
						MTest.SHORT_STRING + " STRING," +
						MTest.LONG_STRING + " STRING," +
						MTest.SHORT_BYTES + " BLOB," +
						MTest.LONG_BYTES + " BLOB" +
					")"
				);
								
			}
		};
	}
	
	long[] mSequentialOrder;
	long[] mRandomOrder;
	void insertTestData(int count, boolean randomOrder, boolean doInt, int doShortStringLength, int doLongStringLength, int doShortByteLength, int doLongBytesLength) {
		TLongLinkedList ids = new TLongLinkedList();
		SQLiteDatabase db = mDatabaseSource.getWritableDatabase();
		ContentValues cv = new ContentValues();
		Random r = new Random();
		byte[] ss = null, ls = null, sb = null, lb = null;
		
		if(doShortStringLength >= 0) {
			ss = new byte[doShortStringLength * 6 / 8];
		}
		if(doLongStringLength >= 0) {
			ls = new byte[doLongStringLength * 6 / 8];
		}
		if(doShortByteLength >= 0) {
			sb = new byte[doShortByteLength * 6 / 8];
		}
		if(doShortByteLength >= 0) {
			lb = new byte[doLongBytesLength * 6 / 8];
		}

		db.beginTransaction();
		for(int i = 0; i < count; ++i) {
			if(randomOrder) {
				cv.put(MTest.ID, r.nextLong());
			}
			if(doInt) {
				cv.put(MTest.INTEGER, r.nextLong());
			}
			if(doShortStringLength >= 0) {
				r.nextBytes(ss);
				cv.put(MTest.SHORT_STRING, Base64.encodeToString(ss, Base64.DEFAULT));
			}
			if(doLongStringLength >= 0) {
				r.nextBytes(ls);
				cv.put(MTest.SHORT_STRING, Base64.encodeToString(ls, Base64.DEFAULT));
			}
			if(doShortByteLength >= 0) {
				r.nextBytes(sb);
				cv.put(MTest.SHORT_BYTES, sb);
			}
			if(doShortByteLength >= 0) {
				r.nextBytes(lb);
				cv.put(MTest.LONG_BYTES, lb);
			}
			long id = db.insert(MTest.TABLE, null, cv);
			ids.add(id);
		}
		mRandomOrder = ids.toArray();
		Arrays.sort(mRandomOrder);
		mSequentialOrder = ids.toArray();
		db.setTransactionSuccessful();
		db.endTransaction();
	}

	public void tearDown() {
		mDatabaseSource.close();
	}
	
	public void testCursorAllRandomIntegerMap() {
		SQLiteDatabase db = mDatabaseSource.getWritableDatabase();
		insertTestData(ITERATIONS, true, true, -1, -1, -1, -1);
		
		Date s, e;
		double ms;

		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.query(MTest.TABLE, MTest.ALL, MTest.ID + "=?", new String[] {String.valueOf(i)}, null, null, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(0);
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor query all miss ms/op: " + ms / ITERATIONS);
		
		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.query(MTest.TABLE, MTest.ALL, MTest.ID + "=?", new String[] {String.valueOf(mSequentialOrder[i])}, null, null, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(0);
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor query all hit seq ms/op: " + ms / ITERATIONS);

		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.query(MTest.TABLE, MTest.ALL, MTest.ID + "=?", new String[] {String.valueOf(mRandomOrder[i])}, null, null, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(0);
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor query all hit rand ms/op: " + ms / ITERATIONS);

		
		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.query(MTest.TABLE, MTest.ALL, MTest.ID + "=?", new String[] {String.valueOf(i)}, null, null, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(c.getColumnIndexOrThrow(MTest.ID));
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor query all by name miss ms/op: " + ms / ITERATIONS);
		
		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.query(MTest.TABLE, MTest.ALL, MTest.ID + "=?", new String[] {String.valueOf(mSequentialOrder[i])}, null, null, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(c.getColumnIndexOrThrow(MTest.ID));
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor query all by name hit seq ms/op: " + ms / ITERATIONS);
		
		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.query(MTest.TABLE, MTest.ALL, MTest.ID + "=?", new String[] {String.valueOf(mRandomOrder[i])}, null, null, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(c.getColumnIndexOrThrow(MTest.ID));
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor query all by name hit rand ms/op: " + ms / ITERATIONS);
	}
	public void testCursorAllRandomIntegerFull() {
		SQLiteDatabase db = mDatabaseSource.getWritableDatabase();
		insertTestData(ITERATIONS, true, true, -1, -1, -1, -1);
		
		Date s, e;
		double ms;
		int ITERATIONS = DBPatternTest.ITERATIONS / 1000;

		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.query(MTest.TABLE, MTest.ALL, null, null, null, null, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(0);
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor full all miss ms/op: " + ms / ITERATIONS);
		
		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.query(MTest.TABLE, MTest.ALL, null, null, null, null, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(0);
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor full all hit seq ms/op: " + ms / ITERATIONS);

		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.query(MTest.TABLE, MTest.ALL, null, null, null, null, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(0);
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor full all hit rand ms/op: " + ms / ITERATIONS);

		
		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.query(MTest.TABLE, MTest.ALL, null, null, null, null, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(c.getColumnIndexOrThrow(MTest.ID));
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor full all by name miss ms/op: " + ms / ITERATIONS);
		
		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.query(MTest.TABLE, MTest.ALL, null, null, null, null, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(c.getColumnIndexOrThrow(MTest.ID));
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor full all by name hit seq ms/op: " + ms / ITERATIONS);
		
		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.query(MTest.TABLE, MTest.ALL, null, null, null, null, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(c.getColumnIndexOrThrow(MTest.ID));
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor full all by name hit rand ms/op: " + ms / ITERATIONS);
	}

	
	public void testCursorRandomIntegerMap() {
		SQLiteDatabase db = mDatabaseSource.getWritableDatabase();
		insertTestData(ITERATIONS, true, true, -1, -1, -1, -1);
		
		Date s, e;
		double ms;

		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.query(MTest.TABLE, new String[] {MTest.ID}, MTest.ID + "=?", new String[] {String.valueOf(i)}, null, null, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(0);
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor query miss ms/op: " + ms / ITERATIONS);
		
		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.query(MTest.TABLE, new String[] {MTest.ID}, MTest.ID + "=?", new String[] {String.valueOf(mSequentialOrder[i])}, null, null, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(0);
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor query hit seq ms/op: " + ms / ITERATIONS);

		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.query(MTest.TABLE, new String[] {MTest.ID}, MTest.ID + "=?", new String[] {String.valueOf(mRandomOrder[i])}, null, null, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(0);
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor query hit rand ms/op: " + ms / ITERATIONS);

		
		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.query(MTest.TABLE, new String[] {MTest.ID}, MTest.ID + "=?", new String[] {String.valueOf(i)}, null, null, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(c.getColumnIndexOrThrow(MTest.ID));
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor query by name miss ms/op: " + ms / ITERATIONS);
		
		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.query(MTest.TABLE, new String[] {MTest.ID}, MTest.ID + "=?", new String[] {String.valueOf(mSequentialOrder[i])}, null, null, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(c.getColumnIndexOrThrow(MTest.ID));
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor query by name hit seq ms/op: " + ms / ITERATIONS);
		
		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.query(MTest.TABLE, new String[] {MTest.ID}, MTest.ID + "=?", new String[] {String.valueOf(mRandomOrder[i])}, null, null, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(c.getColumnIndexOrThrow(MTest.ID));
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor query by name hit rand ms/op: " + ms / ITERATIONS);
	}
	public void testCursorRandomIntegerFull() {
		SQLiteDatabase db = mDatabaseSource.getWritableDatabase();
		insertTestData(ITERATIONS, true, true, -1, -1, -1, -1);
		
		int ITERATIONS = DBPatternTest.ITERATIONS / 1000;

		Date s, e;
		double ms;

		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.query(MTest.TABLE, MTest.ALL, null, null, null, null, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(0);
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor full miss ms/op: " + ms / ITERATIONS);
		
		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.query(MTest.TABLE, MTest.ALL, null, null, null, null, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(0);
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor full hit seq ms/op: " + ms / ITERATIONS);

		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.query(MTest.TABLE, MTest.ALL, null, null, null, null, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(0);
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor full hit rand ms/op: " + ms / ITERATIONS);

		
		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.query(MTest.TABLE, MTest.ALL, null, null, null, null, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(c.getColumnIndexOrThrow(MTest.ID));
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor full by name miss ms/op: " + ms / ITERATIONS);
		
		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.query(MTest.TABLE, MTest.ALL, null, null, null, null, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(c.getColumnIndexOrThrow(MTest.ID));
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor full by name hit seq ms/op: " + ms / ITERATIONS);
		
		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.query(MTest.TABLE, MTest.ALL, null, null, null, null, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(c.getColumnIndexOrThrow(MTest.ID));
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor full by name hit rand ms/op: " + ms / ITERATIONS);
	}
	
	public void testCursorRawRandomIntegerMap() {
		SQLiteDatabase db = mDatabaseSource.getWritableDatabase();
		insertTestData(ITERATIONS, true, true, -1, -1, -1, -1);
		
		Date s, e;
		double ms;

		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.rawQuery("SELECT " + MTest.ID + " FROM " + MTest.TABLE + " WHERE " + MTest.ID + "=?", new String[] {String.valueOf(i)});
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(0);
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor raw query miss ms/op: " + ms / ITERATIONS);
		
		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.rawQuery("SELECT " + MTest.ID + " FROM " + MTest.TABLE + " WHERE " + MTest.ID + "=?", new String[] {String.valueOf(mSequentialOrder[i])});
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(0);
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor raw query hit seq ms/op: " + ms / ITERATIONS);

		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.rawQuery("SELECT " + MTest.ID + " FROM " + MTest.TABLE + " WHERE " + MTest.ID + "=?", new String[] {String.valueOf(mRandomOrder[i])});
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(0);
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor raw query hit rand ms/op: " + ms / ITERATIONS);

		
		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.rawQuery("SELECT " + MTest.ID + " FROM " + MTest.TABLE + " WHERE " + MTest.ID + "=?", new String[] {String.valueOf(i)});
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(c.getColumnIndexOrThrow(MTest.ID));
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor raw query by name miss ms/op: " + ms / ITERATIONS);
		
		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.rawQuery("SELECT " + MTest.ID + " FROM " + MTest.TABLE + " WHERE " + MTest.ID + "=?", new String[] {String.valueOf(mSequentialOrder[i])});
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(c.getColumnIndexOrThrow(MTest.ID));
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor raw query by name hit seq ms/op: " + ms / ITERATIONS);
		
		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.rawQuery("SELECT " + MTest.ID + " FROM " + MTest.TABLE + " WHERE " + MTest.ID + "=?", new String[] {String.valueOf(mRandomOrder[i])});
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(c.getColumnIndexOrThrow(MTest.ID));
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor raw query by name hit rand ms/op: " + ms / ITERATIONS);
	}

	
	public void testCursorRawRandomIntegerFull() {
		SQLiteDatabase db = mDatabaseSource.getWritableDatabase();
		insertTestData(ITERATIONS, true, true, -1, -1, -1, -1);
		
		Date s, e;
		double ms;

		int ITERATIONS = DBPatternTest.ITERATIONS / 1000;

		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.rawQuery("SELECT " + MTest.ID + " FROM " + MTest.TABLE, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(0);
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor raw query miss ms/op: " + ms / ITERATIONS);
		
		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.rawQuery("SELECT " + MTest.ID + " FROM " + MTest.TABLE, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(0);
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor raw query hit seq ms/op: " + ms / ITERATIONS);

		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.rawQuery("SELECT " + MTest.ID + " FROM " + MTest.TABLE, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(0);
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor raw query hit rand ms/op: " + ms / ITERATIONS);

		
		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.rawQuery("SELECT " + MTest.ID + " FROM " + MTest.TABLE, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(c.getColumnIndexOrThrow(MTest.ID));
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor raw query by name miss ms/op: " + ms / ITERATIONS);
		
		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.rawQuery("SELECT " + MTest.ID + " FROM " + MTest.TABLE, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(c.getColumnIndexOrThrow(MTest.ID));
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor raw query by name hit seq ms/op: " + ms / ITERATIONS);
		
		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.rawQuery("SELECT " + MTest.ID + " FROM " + MTest.TABLE, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(c.getColumnIndexOrThrow(MTest.ID));
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor raw query by name hit rand ms/op: " + ms / ITERATIONS);
	}

	public void testCompiledsRandomIntegerMap() {
		SQLiteDatabase db = mDatabaseSource.getWritableDatabase();
		SQLiteStatement st = db.compileStatement("SELECT " + MTest.ID + " FROM " + MTest.TABLE + " WHERE " + MTest.ID + "=?");
		insertTestData(ITERATIONS, true, true, -1, -1, -1, -1);
		
		Date s, e;
		double ms;

		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			st.bindLong(1, i);
			try {
				@SuppressWarnings("unused")
				long integer = st.simpleQueryForLong();
			} catch (SQLiteDoneException x){}
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "compiled query miss ms/op: " + ms / ITERATIONS);
		
		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			st.bindLong(1, mSequentialOrder[i]);
			@SuppressWarnings("unused")
			long integer = st.simpleQueryForLong();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "compiled query hit seq ms/op: " + ms / ITERATIONS);

		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			st.bindLong(1, mRandomOrder[i]);
			@SuppressWarnings("unused")
			long integer = st.simpleQueryForLong();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "compiled query hit rand ms/op: " + ms / ITERATIONS);
	}




	public void testWithExtrasCursorAllRandomIntegerMap() {
		SQLiteDatabase db = mDatabaseSource.getWritableDatabase();
		insertTestData(ITERATIONS, true, true, -1, -1, -1, 2048);
		
		Date s, e;
		double ms;

		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.query(MTest.TABLE, MTest.ALL, MTest.ID + "=?", new String[] {String.valueOf(i)}, null, null, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(0);
				@SuppressWarnings("unused")
				byte[] r = c.getBlob(5);
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor query all (~2k+) miss ms/op: " + ms / ITERATIONS);
		
		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.query(MTest.TABLE, MTest.ALL, MTest.ID + "=?", new String[] {String.valueOf(mSequentialOrder[i])}, null, null, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(0);
				@SuppressWarnings("unused")
				byte[] r = c.getBlob(5);
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor query all (~2k+) hit seq ms/op: " + ms / ITERATIONS);

		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.query(MTest.TABLE, MTest.ALL, MTest.ID + "=?", new String[] {String.valueOf(mRandomOrder[i])}, null, null, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(0);
				@SuppressWarnings("unused")
				byte[] r = c.getBlob(5);
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor query all (~2k+) hit rand ms/op: " + ms / ITERATIONS);

		
		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.query(MTest.TABLE, MTest.ALL, MTest.ID + "=?", new String[] {String.valueOf(i)}, null, null, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(c.getColumnIndexOrThrow(MTest.ID));
				@SuppressWarnings("unused")
				byte[] r = c.getBlob(5);
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor query all (~2k+) by name miss ms/op: " + ms / ITERATIONS);
		
		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.query(MTest.TABLE, MTest.ALL, MTest.ID + "=?", new String[] {String.valueOf(mSequentialOrder[i])}, null, null, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(c.getColumnIndexOrThrow(MTest.ID));
				@SuppressWarnings("unused")
				byte[] r = c.getBlob(5);
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor query all (~2k+) by name hit seq ms/op: " + ms / ITERATIONS);
		
		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.query(MTest.TABLE, MTest.ALL, MTest.ID + "=?", new String[] {String.valueOf(mRandomOrder[i])}, null, null, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(c.getColumnIndexOrThrow(MTest.ID));
				@SuppressWarnings("unused")
				byte[] r = c.getBlob(5);
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor query all (~2k+) by name hit rand ms/op: " + ms / ITERATIONS);
	}
	public void testWithExtrasStringCursorAllRandomIntegerMap() {
		SQLiteDatabase db = mDatabaseSource.getWritableDatabase();
		insertTestData(ITERATIONS, true, true, 32, 2048, -1, -1);
		
		Date s, e;
		double ms;

		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.query(MTest.TABLE, MTest.ALL, MTest.ID + "=?", new String[] {String.valueOf(i)}, null, null, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(0);
				@SuppressWarnings("unused")
				String r = c.getString(3);
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor query all (~2k+s) miss ms/op: " + ms / ITERATIONS);
		
		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.query(MTest.TABLE, MTest.ALL, MTest.ID + "=?", new String[] {String.valueOf(mSequentialOrder[i])}, null, null, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(0);
				@SuppressWarnings("unused")
				String r = c.getString(3);
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor query all (~2k+s) hit seq ms/op: " + ms / ITERATIONS);

		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.query(MTest.TABLE, MTest.ALL, MTest.ID + "=?", new String[] {String.valueOf(mRandomOrder[i])}, null, null, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(0);
				@SuppressWarnings("unused")
				String r = c.getString(3);
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor query all (~2k+s) hit rand ms/op: " + ms / ITERATIONS);

		
		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.query(MTest.TABLE, MTest.ALL, MTest.ID + "=?", new String[] {String.valueOf(i)}, null, null, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(c.getColumnIndexOrThrow(MTest.ID));
				@SuppressWarnings("unused")
				String r = c.getString(3);
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor query all (~2k+s) by name miss ms/op: " + ms / ITERATIONS);
		
		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.query(MTest.TABLE, MTest.ALL, MTest.ID + "=?", new String[] {String.valueOf(mSequentialOrder[i])}, null, null, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(c.getColumnIndexOrThrow(MTest.ID));
				@SuppressWarnings("unused")
				String r = c.getString(3);
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor query all (~2k+s) by name hit seq ms/op: " + ms / ITERATIONS);
		
		////////////////////////////////
		s = new Date();
		for(int i = 0; i < ITERATIONS; ++i) {
			Cursor c = db.query(MTest.TABLE, MTest.ALL, MTest.ID + "=?", new String[] {String.valueOf(mRandomOrder[i])}, null, null, null);
			while(c.moveToNext()) {
				@SuppressWarnings("unused")
				long integer = c.getLong(c.getColumnIndexOrThrow(MTest.ID));
				@SuppressWarnings("unused")
				String r = c.getString(3);
			}
			c.close();
		}
		e = new Date();
		ms = e.getTime() - s.getTime();
		Log.e(getName(), "cursor query all (~2k+s) by name hit rand ms/op: " + ms / ITERATIONS);
	}
}
