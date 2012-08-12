package mobisocial.musubi.sync;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.test.mock.MockContext;


/**
 * The DelegatedMockContext.
 *
 */
public class DelegatedMockContext extends MockContext {
	private final static String PREFIX = "test";
	private Context mDelegatedContext;

	public DelegatedMockContext(Context context) {
		mDelegatedContext = context;
	}

	@Override
	public SharedPreferences getSharedPreferences(String name, int mode) {
		return mDelegatedContext.getSharedPreferences(PREFIX + name, mode);
	}

	@Override
	public SQLiteDatabase openOrCreateDatabase (String file, int mode, SQLiteDatabase.CursorFactory factory) {
		return mDelegatedContext.openOrCreateDatabase(PREFIX+file, mode, factory);
	}

	@Override
	public ContentResolver getContentResolver () {
		return mDelegatedContext.getContentResolver();
	}
	
	@Override
	public Resources getResources() {
		return mDelegatedContext.getResources();
	}
	
	@Override
    public Object getSystemService(String name) {
        return mDelegatedContext.getSystemService(name);
    }
	
	@Override
	public String getPackageName() {
		return mDelegatedContext.getPackageName();
	}
}
