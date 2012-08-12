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

import mobisocial.metrics.UsageMetrics;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MMyAccount;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

/**
 * @see MMyAccount
 *
 */
public class MyAccountManager extends ManagerBase {
    private SQLiteStatement sqlUpdateAccount;
    private SQLiteStatement sqlInsertAccount;

    private static final int _id = 0;
    private static final int accountName = 1;
    private static final int accountType = 2;
    private static final int identityId = 3;
    private static final int feedId = 4;

    String[] STANDARD_FIELDS = new String[] {
            MMyAccount.COL_ID,
            MMyAccount.COL_ACCOUNT_NAME,
            MMyAccount.COL_ACCOUNT_TYPE,
            MMyAccount.COL_IDENTITY_ID,
            MMyAccount.COL_FEED_ID };

    public MyAccountManager(SQLiteOpenHelper databaseSource) {
        super(databaseSource);
    }

    public MyAccountManager(SQLiteDatabase db) {
        super(db);
	}

	public void insertAccount(MMyAccount account) {
        SQLiteDatabase db = initializeDatabase();
        if (sqlInsertAccount == null) {
            synchronized (this) {
                StringBuilder sql = new StringBuilder(100)
                    .append(" INSERT INTO ").append(MMyAccount.TABLE).append("(")
                    .append(MMyAccount.COL_ACCOUNT_NAME).append(",")
                    .append(MMyAccount.COL_ACCOUNT_TYPE).append(",")
                    .append(MMyAccount.COL_IDENTITY_ID).append(",")
                    .append(MMyAccount.COL_FEED_ID)
                    .append(") VALUES (?,?,?,?)");
                sqlInsertAccount = db.compileStatement(sql.toString());
            }
        }

        synchronized (sqlInsertAccount) {
            bindField(sqlInsertAccount, accountName, account.accountName_);
            bindField(sqlInsertAccount, accountType, account.accountType_);
            bindField(sqlInsertAccount, identityId, account.identityId_);
            bindField(sqlInsertAccount, feedId, account.feedId_);
            account.id_ = sqlInsertAccount.executeInsert();
        }
    }

	public MMyAccount lookupAccount(long accountId) {
        SQLiteDatabase db = initializeDatabase();
        String table = MMyAccount.TABLE;
        String selection = MMyAccount.COL_ID + "=?";
        String[] selectionArgs = new String[] { Long.toString(accountId) };
        String groupBy = null, having = null, orderBy = null;
        Cursor c = db.query(table, STANDARD_FIELDS, selection, selectionArgs, groupBy, having, orderBy);
        try {
            if (c.moveToFirst()) {
                // Existing account
                MMyAccount acc = new MMyAccount();
                acc.id_ = c.getLong(0);
                acc.accountName_ = c.getString(MyAccountManager.accountName);
                acc.accountType_ = c.getString(MyAccountManager.accountType);
                if (!c.isNull(MyAccountManager.identityId)) {
                    acc.identityId_ = c.getLong(MyAccountManager.identityId);
                }
                if (!c.isNull(MyAccountManager.feedId)) {
                    acc.feedId_ = c.getLong(MyAccountManager.feedId);
                }
                return acc;
            } else {
                return null;
            }
        } finally {
            c.close();
        }
    }

    public MMyAccount lookupAccount(String accountName, String accountType) {
        SQLiteDatabase db = initializeDatabase();
        String table = MMyAccount.TABLE;
        String selection = MMyAccount.COL_ACCOUNT_NAME + "=? AND " + MMyAccount.COL_ACCOUNT_TYPE + "=?";
        String[] selectionArgs = new String[] { accountName, accountType };
        String groupBy = null, having = null, orderBy = null;
        Cursor c = db.query(table, STANDARD_FIELDS, selection, selectionArgs, groupBy, having, orderBy);
        try {
            if (c.moveToFirst()) {
                // Existing account
                if (c.isNull(MyAccountManager.accountName)) {
                    // Somehow this is happening even though the field is non-nullable.
                    Log.e("MyAccountManager", "Null account name for " + accountType +
                            " : " + accountName);
                    return null;
                }
                MMyAccount acc = new MMyAccount();
                acc.id_ = c.getLong(0);
                acc.accountName_ = c.getString(MyAccountManager.accountName);
                acc.accountType_ = c.getString(MyAccountManager.accountType);
                if (!c.isNull(MyAccountManager.identityId)) {
                    acc.identityId_ = c.getLong(MyAccountManager.identityId);
                }
                if (!c.isNull(MyAccountManager.feedId)) {
                    acc.feedId_ = c.getLong(MyAccountManager.feedId);
                }
                return acc;
            } else {
                return null;
            }
        } finally {
            c.close();
        }
    }

    //TODO: change this to be a compiled query that only looks up the feed id
    //or just cache the whole table in memory, its just 10ish entries
    public MMyAccount lookupAccount(String accountName, String accountType, long identityId) {
        SQLiteDatabase db = initializeDatabase();
        String table = MMyAccount.TABLE;
        String selection = MMyAccount.COL_ACCOUNT_NAME + "=? AND " + MMyAccount.COL_ACCOUNT_TYPE + "=? AND " + MMyAccount.COL_IDENTITY_ID + "=?";
        String[] selectionArgs = new String[] { accountName, accountType, String.valueOf(identityId) };
        String groupBy = null, having = null, orderBy = null;
        Cursor c = db.query(table, STANDARD_FIELDS, selection, selectionArgs, groupBy, having, orderBy);
        try {
            if (c.moveToFirst()) {
                // Existing account
                MMyAccount acc = new MMyAccount();
                acc.id_ = c.getLong(0);
                acc.accountName_ = c.getString(MyAccountManager.accountName);
                acc.accountType_ = c.getString(MyAccountManager.accountType);
                if (!c.isNull(MyAccountManager.identityId)) {
                    acc.identityId_ = c.getLong(MyAccountManager.identityId);
                }
                if (!c.isNull(MyAccountManager.feedId)) {
                    acc.feedId_ = c.getLong(MyAccountManager.feedId);
                }
                return acc;
            } else {
                return null;
            }
        } finally {
            c.close();
        }
    }

    public void updateAccount(MMyAccount account) {
        SQLiteDatabase db = initializeDatabase();
        if (sqlUpdateAccount == null) {
            synchronized (this) {
                if (sqlUpdateAccount == null) {
                    StringBuilder sql = new StringBuilder("UPDATE ").append(MMyAccount.TABLE)
                        .append(" SET ")
                        .append(MMyAccount.COL_ACCOUNT_NAME).append("=?,")
                        .append(MMyAccount.COL_ACCOUNT_TYPE).append("=?,")
                        .append(MMyAccount.COL_IDENTITY_ID).append("=?,")
                        .append(MMyAccount.COL_FEED_ID).append("=?")
                        .append(" WHERE ").append(MMyAccount.COL_ID).append("=?");
                    sqlUpdateAccount = db.compileStatement(sql.toString());
                }
            }
        }

        synchronized (sqlUpdateAccount) {
            bindField(sqlUpdateAccount, accountName, account.accountName_);
            bindField(sqlUpdateAccount, accountType, account.accountType_);
            bindField(sqlUpdateAccount, identityId, account.identityId_);
            bindField(sqlUpdateAccount, feedId, account.feedId_);
            bindField(sqlUpdateAccount, 5, account.id_);
            sqlUpdateAccount.execute();
        }
    }

    public MMyAccount[] getMyAccounts() {
        SQLiteDatabase db = initializeDatabase();
        String selection = null;
        String[] selectionArgs = null;
        String groupBy = null, having = null, orderBy = null;
        Cursor c = db.query(MMyAccount.TABLE, STANDARD_FIELDS, selection, selectionArgs,
                groupBy, having, orderBy);
        MMyAccount[] accounts = new MMyAccount[c.getCount()];
        int i = 0;
        while (c.moveToNext()) {
            MMyAccount account = new MMyAccount();
            account.id_ = c.getLong(_id);
            account.accountName_ = c.getString(accountName);
            account.accountType_ = c.getString(accountType);
            if (!c.isNull(identityId)) {
                account.identityId_ = c.getLong(identityId);
            }
            if (!c.isNull(feedId)) {
                account.feedId_ = c.getLong(feedId);
            }

            accounts[i++] = account;
        }
        return accounts;
    }

    public MMyAccount[] getMyAccounts(String type) {
        SQLiteDatabase db = initializeDatabase();
        String selection = MMyAccount.COL_ACCOUNT_TYPE + "=?";
        String[] selectionArgs = new String[] { type };
        String groupBy = null, having = null, orderBy = null;
        Cursor c = db.query(MMyAccount.TABLE, STANDARD_FIELDS, selection, selectionArgs,
                groupBy, having, orderBy);
        MMyAccount[] accounts = new MMyAccount[c.getCount()];
        int i = 0;
        while (c.moveToNext()) {
            MMyAccount account = new MMyAccount();
            account.id_ = c.getLong(_id);
            account.accountName_ = c.getString(accountName);
            account.accountType_ = c.getString(accountType);
            if (!c.isNull(identityId)) {
                account.identityId_ = c.getLong(identityId);
            }
            if (!c.isNull(feedId)) {
                account.feedId_ = c.getLong(feedId);
            }

            accounts[i++] = account;
        }
        return accounts;
    }

    /**
     * An account is "claimed" if it has an associated identity that is marked as "owned".
     * @param type the type of account. May be null;
     */
    public MMyAccount[] getClaimedAccounts(String type) {
        String[] selectionArgs;
        StringBuilder sql = new StringBuilder(200)
            .append("SELECT ")
            .append(MMyAccount.TABLE).append(".").append(MMyAccount.COL_ID).append(",")
            .append(MMyAccount.TABLE).append(".").append(MMyAccount.COL_ACCOUNT_NAME).append(",")
            .append(MMyAccount.TABLE).append(".").append(MMyAccount.COL_ACCOUNT_TYPE).append(",")
            .append(MMyAccount.TABLE).append(".").append(MMyAccount.COL_IDENTITY_ID).append(",")
            .append(MMyAccount.TABLE).append(".").append(MMyAccount.COL_FEED_ID)
            .append(" FROM ").append(MMyAccount.TABLE)
            .append(" INNER JOIN ").append(MIdentity.TABLE).append(" ON ")
            .append(MMyAccount.TABLE).append(".").append(MMyAccount.COL_IDENTITY_ID)
            .append("=").append(MIdentity.TABLE).append(".").append(MIdentity.COL_ID)
            .append(" WHERE ").append(MIdentity.TABLE).append(".").append(MIdentity.COL_OWNED).append("=1");
        if (type != null) {
            sql.append(" AND ").append(MMyAccount.TABLE).append(".")
                .append(MMyAccount.COL_ACCOUNT_TYPE).append("=?");
            selectionArgs = new String[] { type };
        } else {
            selectionArgs = null;
        }

        SQLiteDatabase db = initializeDatabase();
        Cursor c = db.rawQuery(sql.toString(), selectionArgs);
        MMyAccount[] accounts = new MMyAccount[c.getCount()];
        int i = 0;
        while (c.moveToNext()) {
            MMyAccount account = new MMyAccount();
            account.id_ = c.getLong(_id);
            account.accountName_ = c.getString(accountName);
            account.accountType_ = c.getString(accountType);
            if (!c.isNull(identityId)) {
                account.identityId_ = c.getLong(identityId);
            }
            if (!c.isNull(feedId)) {
                account.feedId_ = c.getLong(feedId);
            }

            accounts[i++] = account;
        }
        return accounts;
    }

	public MMyAccount getProvisionalWhitelistForIdentity(long identityId) {
		return lookupAccount(MMyAccount.PROVISIONAL_WHITELIST_ACCOUNT, MMyAccount.INTERNAL_ACCOUNT_TYPE, identityId);
	}
	public MMyAccount getWhitelistForIdentity(long identityId) {
		return lookupAccount(MMyAccount.LOCAL_WHITELIST_ACCOUNT, MMyAccount.INTERNAL_ACCOUNT_TYPE, identityId);
	}

	@Override
	public synchronized void close() {
	    if (sqlUpdateAccount != null) {
	    	sqlUpdateAccount.close();
	    	sqlUpdateAccount = null;
	    }
	    if (sqlInsertAccount != null) {
	    	sqlInsertAccount.close();
	    	sqlInsertAccount = null;
	    }
	}
}
