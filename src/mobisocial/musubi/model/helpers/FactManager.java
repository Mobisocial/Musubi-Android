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

import mobisocial.musubi.model.MApp;
import mobisocial.musubi.model.MFact;
import mobisocial.musubi.model.MFactType;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;

public class FactManager extends ManagerBase {
    final static int MAX_NUM_FIELDS = 4;
    
    SQLiteStatement mSqlInsertFT;
    SQLiteStatement mSqlInsertFact;
    SQLiteStatement mSqlSelectFact;
    SQLiteStatement mSqlGetFactType;

    final int _id = 0;
    final int appId = 1;
    final int factTypeId = 2;
    final int V = 3;
    final int A = 4;
    final int B = 5;
    final int C = 6;
    final int D = 7;
    
    public FactManager(SQLiteOpenHelper databaseSource) {
        super(databaseSource);
    }

    /**
     * Example usage:
     * <code>
     * ensureFact(musubi_id, TYPE_MIME_TYPE, "image/jpeg", "842")
     * ensureFact(musubi_id, TYPE_EDIT, null, "842", "981")
     * ensureFact(game_id, TYPE_SCORE, "1199", "intermediate", idFor("bjdodson@mail.com"))
     * </code>
     */
    public MFact ensureFact(MApp app, MFactType factType, Object value, Object... fields) {
        if (fields.length > MAX_NUM_FIELDS) {
            throw new IllegalArgumentException("Too many fields (max=" + MAX_NUM_FIELDS + ")");
        }
        SQLiteDatabase db = initializeDatabase();

        // Check existing
        if (mSqlSelectFact == null) {
            synchronized(this) {
                StringBuilder sql = new StringBuilder(100);
                sql.append("SELECT ").append(MFact.COL_ID).append(" FROM ").append(MFact.TABLE)
                    .append(" WHERE ").append(MFact.COL_APP_ID).append("=?")
                    .append(" AND ").append(MFact.COL_FACT_TYPE_ID).append("=?")
                    .append(" AND ").append(MFact.COL_V).append(" IS ?")
                    .append(" AND ").append(MFact.COL_A).append(" IS ?")
                    .append(" AND ").append(MFact.COL_B).append(" IS ?")
                    .append(" AND ").append(MFact.COL_C).append(" IS ?")
                    .append(" AND ").append(MFact.COL_D).append(" IS ?");
                mSqlSelectFact = db.compileStatement(sql.toString());
            }
        }

        long id;
        synchronized(mSqlSelectFact) {
            bindField(mSqlSelectFact, appId, app.id_);
            bindField(mSqlSelectFact, factTypeId, factType.id_);
            bindField(mSqlSelectFact, V, value);

            int i = A;
            for (Object f : fields) {
                bindField(mSqlSelectFact, i++, f);
            }
            while (i <= D) {
                bindField(mSqlSelectFact, i++, null);
            }
            try {
                id = mSqlSelectFact.simpleQueryForLong();
            } catch (SQLiteDoneException e) {
                id = -1;
            }
        }

        // Insert new fact
        MFact fact = new MFact();
        fact.id_ = id;
        fact.appId_ = app.id_;
        fact.fact_type_id = factType.id_;
        fact.V_ = value;
        if (fields.length > 0) {
            fact.A_ = fields[0];
            if (fields.length > 1) {
                fact.B_ = fields[1];
                if (fields.length > 2) {
                    fact.C_ = fields[2];
                    if (fields.length > 3) {
                        fact.D_ = fields[3];
                    }
                }
            }
        }
        if (id == -1) {
            insertFact(fact);
        }
        return fact;
    }

    public MFactType ensureFactType(String factType) {
        SQLiteDatabase db = initializeDatabase();
		if(mSqlGetFactType == null) {
			synchronized(this) {
				if(mSqlGetFactType == null) {
					mSqlGetFactType = db.compileStatement(
						"SELECT " + MFactType.COL_ID + 
						" FROM " + MFactType.TABLE + 
						" WHERE " + MFactType.COL_FACT_TYPE + "=?"
					);
				}
			}
		}
		synchronized (mSqlGetFactType) {
			try {
				mSqlGetFactType.bindString(1, factType);
				long id = mSqlGetFactType.simpleQueryForLong();
				MFactType ft = new MFactType();
				ft.id_ = id;
				ft.factType_ = factType;
				return ft;
			} catch(SQLiteDoneException e) {
				//must insert
			}
		}
    	
        MFactType ft = new MFactType();
        ft.factType_ = factType;
        insertFactType(ft);
        return ft;
    }

    void insertFact(MFact fact) {
        SQLiteDatabase db = initializeDatabase();
        if (mSqlInsertFact == null) {
            synchronized (this) {
                if(mSqlInsertFact == null) {
                    String sql = new StringBuilder()
                        .append("INSERT INTO ").append(MFact.TABLE).append("(")
                        .append(MFact.COL_APP_ID).append(",")
                        .append(MFact.COL_FACT_TYPE_ID).append(",")
                        .append(MFact.COL_V).append(",")
                        .append(MFact.COL_A).append(",")
                        .append(MFact.COL_B).append(",")
                        .append(MFact.COL_C).append(",")
                        .append(MFact.COL_D)
                        .append(") VALUES (?,?,?,?,?,?,?)").toString();
                    mSqlInsertFact = db.compileStatement(sql);
                }
            }
        }
                
        synchronized (mSqlInsertFact) {
            mSqlInsertFact.bindLong(appId, fact.appId_);
            mSqlInsertFact.bindLong(factTypeId, fact.fact_type_id);
            bindField(mSqlInsertFact, V, fact.V_);
            bindField(mSqlInsertFact, A, fact.A_);
            bindField(mSqlInsertFact, B, fact.B_);
            bindField(mSqlInsertFact, C, fact.C_);
            bindField(mSqlInsertFact, D, fact.D_);
            fact.id_ = mSqlInsertFact.executeInsert();
        }
    }

    void insertFactType(MFactType type) {
        SQLiteDatabase db = initializeDatabase();
        if (mSqlInsertFT == null) {
            synchronized (this) {
                if(mSqlInsertFT == null) {
                    String sql = new StringBuilder()
                        .append("INSERT INTO ").append(MFactType.TABLE).append("(")
                        .append(MFactType.COL_FACT_TYPE)
                        .append(") VALUES (?)").toString();
                    mSqlInsertFT = db.compileStatement(sql);
                }
            }
        }
                
        synchronized (mSqlInsertFT) {
            mSqlInsertFT.bindString(1, type.factType_);
            type.id_ = mSqlInsertFT.executeInsert();
        }
    }
    
    public MFact getFact(long factId) {
        SQLiteDatabase db = initializeDatabase();
        String table = MFact.TABLE;
        String[] columns = new String[] { MFact.COL_ID, MFact.COL_APP_ID, MFact.COL_FACT_TYPE_ID, 
                MFact.COL_A, typeOf(MFact.COL_A), MFact.COL_B, typeOf(MFact.COL_B),
                MFact.COL_C, typeOf(MFact.COL_C), MFact.COL_D, typeOf(MFact.COL_D),
                MFact.COL_V, typeOf(MFact.COL_V)};
        String selection = MFact.COL_ID + " = ?";
        String[] selectionArgs = new String[] { Long.toString(factId) };
        String groupBy = null, having = null, orderBy = null;
        Cursor c = db.query(table, columns, selection, selectionArgs, groupBy, having, orderBy);
        try {
            if (c.moveToFirst()) {
                MFact fact = new MFact();
                fact.id_ = c.getLong(0);
                fact.appId_ = c.getLong(1);
                fact.fact_type_id = c.getLong(2);
                fact.A_ = getFactField(c, 3, 4);
                fact.B_ = getFactField(c, 5, 6);
                fact.C_ = getFactField(c, 7, 8);
                fact.D_ = getFactField(c, 9, 10);
                fact.V_ = getFactField(c, 11, 12);
                return fact;
            }
            return null;
        } finally {
            c.close();
        }
    }

    public boolean delete(long factId) {
        SQLiteDatabase db = initializeDatabase();
        String table = MFact.TABLE;
        String whereClause = MFact.COL_ID + " = ?";
        String[] whereArgs = new String[] { Long.toString(factId) };
        return db.delete(table, whereClause, whereArgs) > 0;
    }

    private String typeOf(String col) {
        return new StringBuilder(col.length() + 8)
        .append("typeof(").append(col).append(")").toString();
    }

    private Object getFactField(Cursor c, int col, int type) {
        String typeStr = c.getString(type);
        if (typeStr.equals("text")) {
            return c.getString(col);
        }
        if (typeStr.equals("integer")) {
            return c.getInt(col);
        }
        if (typeStr.equals("blob")) {
            return c.getBlob(col);
        }
        if (typeStr.equals("real")) {
            return c.getDouble(col);
        }
        return null;
    }

    @Override
    public synchronized void close() {
    	if (mSqlInsertFT != null) {
    		mSqlInsertFT.close();
    		mSqlInsertFT = null;
    	}
    	if (mSqlInsertFact != null) {
    		mSqlInsertFact.close();
    		mSqlInsertFact = null;
    	}
    	if (mSqlSelectFact != null) {
    		mSqlSelectFact.close();
    		mSqlSelectFact = null;
    	}
    	if (mSqlGetFactType != null) {
    		mSqlGetFactType.close();
    		mSqlGetFactType = null;
    	}
    }
}