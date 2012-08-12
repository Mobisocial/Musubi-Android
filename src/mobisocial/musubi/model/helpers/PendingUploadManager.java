/*
 * Copyright 2012 The Stanford MobiSocial Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mobisocial.musubi.model.helpers;

import mobisocial.musubi.model.MPendingUpload;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class PendingUploadManager extends ManagerBase {

    public PendingUploadManager(SQLiteDatabase db) {
        super(db);
    }

    public PendingUploadManager(SQLiteOpenHelper helper) {
        super(helper);
    }

    public long[] getPendingUploadObjects() {
        String table = MPendingUpload.TABLE;
        String[] columns = new String[] { MPendingUpload.COL_OBJECT_ID };
        String selection = null;
        String[] selectionArgs = null;
        String groupBy = null;
        String having = null;
        String orderBy = MPendingUpload.COL_ID + " desc";
        Cursor c = initializeDatabase().query(
                table, columns, selection, selectionArgs, groupBy, having, orderBy);
        try {
            long[] ids = new long[c.getCount()];
            int i = 0;
            while (c.moveToNext()) {
                ids[i++] = c.getLong(0);
            }
            return ids;
        } finally {
            c.close();
        }
    }

    public boolean hasPendingUpload(long objId) {
        String table = MPendingUpload.TABLE;
        String[] columns = new String[] { MPendingUpload.COL_OBJECT_ID };
        String selection = MPendingUpload.COL_OBJECT_ID + "=?";
        String[] selectionArgs = new String[] { Long.toString(objId) };
        String groupBy = null;
        String having = null;
        String orderBy = MPendingUpload.COL_ID + " desc LIMIT 1";
        Cursor c = initializeDatabase().query(
                table, columns, selection, selectionArgs, groupBy, having, orderBy);
        try {
            return c.getCount() > 0;
        } finally {
            c.close();
        }
    }

    @Override
    public synchronized void close() {
    }
}
