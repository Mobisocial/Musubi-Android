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

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import mobisocial.musubi.model.MPendingIdentity;
import mobisocial.musubi.util.Util;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;

/**
 * 
 * @see MPendingIdentity
 *
 */

public class PendingIdentityManager extends ManagerBase {
    private SQLiteStatement sqlUpdateIdentity;
    private SQLiteStatement sqlInsertIdentity;
    
    private static final int _id = 0;
    private static final int identityId = 1;
    private static final int key = 2;
    private static final int notified = 3;
    private static final int requestId = 4;
    private static final int timestamp = 5;
    
    private static final String[] STANDARD_FIELDS = new String[] {
            MPendingIdentity.COL_ID,
            MPendingIdentity.COL_IDENTITY_ID,
            MPendingIdentity.COL_KEY,
            MPendingIdentity.COL_NOTIFIED,
            MPendingIdentity.COL_REQUEST_ID,
            MPendingIdentity.COL_TIMESTAMP
    };
    
    private static final int KEY_SIZE = 32;
    
    public PendingIdentityManager(SQLiteOpenHelper databaseSource) {
        super(databaseSource);
    }

    public PendingIdentityManager(SQLiteDatabase db) {
        super(db);
    }

    public void insertIdentity(MPendingIdentity identity) {
        SQLiteDatabase db = initializeDatabase();
        if (sqlInsertIdentity == null) {
            synchronized(this) {
                StringBuilder sql = new StringBuilder(200)
                    .append(" INSERT INTO ").append(MPendingIdentity.TABLE).append("(")
                    .append(MPendingIdentity.COL_IDENTITY_ID).append(",")
                    .append(MPendingIdentity.COL_KEY).append(",")
                    .append(MPendingIdentity.COL_NOTIFIED).append(",")
                    .append(MPendingIdentity.COL_REQUEST_ID).append(",")
                    .append(MPendingIdentity.COL_TIMESTAMP)
                    .append(") VALUES (?,?,?,?,?)");
                sqlInsertIdentity = db.compileStatement(sql.toString());
            }
        }
        
        synchronized(sqlInsertIdentity) {
            bindField(sqlInsertIdentity, identityId, identity.identityId_);
            bindField(sqlInsertIdentity, key, identity.key_);
            bindField(sqlInsertIdentity, notified, identity.notified_ ? 1 : 0);
            bindField(sqlInsertIdentity, requestId, identity.requestId_);
            bindField(sqlInsertIdentity, timestamp, identity.timestamp_);
            identity.id_ = sqlInsertIdentity.executeInsert();
        }
    }
    
    public void updateIdentity(MPendingIdentity identity) {
        SQLiteDatabase db = initializeDatabase();
        if (sqlUpdateIdentity == null) {
            synchronized(this) {
                StringBuilder sql = new StringBuilder("UPDATE ")
                    .append(MPendingIdentity.TABLE)
                    .append(" SET ")
                    .append(MPendingIdentity.COL_IDENTITY_ID).append("=?,")
                    .append(MPendingIdentity.COL_KEY).append("=?,")
                    .append(MPendingIdentity.COL_NOTIFIED).append("=?,")
                    .append(MPendingIdentity.COL_REQUEST_ID).append("=?,")
                    .append(MPendingIdentity.COL_TIMESTAMP).append("=?")
                    .append(" WHERE ").append(MPendingIdentity.COL_ID).append("=?");
                sqlUpdateIdentity = db.compileStatement(sql.toString());
            }
        }
        
        synchronized(sqlUpdateIdentity) {
            bindField(sqlUpdateIdentity, identityId, identity.identityId_);
            bindField(sqlUpdateIdentity, key, identity.key_);
            bindField(sqlUpdateIdentity, notified, identity.notified_ ? 1 : 0);
            bindField(sqlUpdateIdentity, requestId, identity.requestId_);
            bindField(sqlUpdateIdentity, timestamp, identity.timestamp_);
            bindField(sqlUpdateIdentity, 6, identity.id_);
            sqlUpdateIdentity.execute();
        }
    }
    
    public Set<MPendingIdentity> lookupIdentities(Long identityId) {
        SQLiteDatabase db = initializeDatabase();
        String table = MPendingIdentity.TABLE;
        String selection = MPendingIdentity.COL_IDENTITY_ID + "=?";
        String[] selectionArgs = new String[] { identityId.toString() };
        String groupBy = null, having = null, orderBy = null;
        Cursor c = db.query(table, STANDARD_FIELDS, selection, selectionArgs,
                groupBy, having, orderBy);
        try {
            HashSet<MPendingIdentity> ids = new HashSet<MPendingIdentity>();
            while (c.moveToNext()) {
                ids.add(fillInStandardFields(c));
            }
            return ids;
        } finally {
            c.close();
        }
    }
    
    public MPendingIdentity lookupIdentity(Long identityId, Long timestamp) {
        SQLiteDatabase db = initializeDatabase();
        String table = MPendingIdentity.TABLE;
        String selection = MPendingIdentity.COL_IDENTITY_ID +
                "=? AND " + MPendingIdentity.COL_TIMESTAMP + "=?";
        String[] selectionArgs = new String[] { identityId.toString(), timestamp.toString() };
        String groupBy = null, having = null, orderBy = null;
        Cursor c = db.query(table, STANDARD_FIELDS, selection, selectionArgs,
                groupBy, having, orderBy);
        try {
            if (c.moveToFirst()) {
                return fillInStandardFields(c);
            }
            else {
                return null;
            }
        } finally {
            c.close();
        }
    }
    
    public MPendingIdentity lookupIdentity(Long identityId, Long timestamp, Integer requestId) {
        SQLiteDatabase db = initializeDatabase();
        String table = MPendingIdentity.TABLE;
        String selection = MPendingIdentity.COL_IDENTITY_ID +
                "=? AND " + MPendingIdentity.COL_TIMESTAMP +
                "=? AND " + MPendingIdentity.COL_REQUEST_ID + "=?";
        String[] selectionArgs = new String[] { identityId.toString(),
                timestamp.toString(), requestId.toString() };
        String groupBy = null, having = null, orderBy = null;
        Cursor c = db.query(table, STANDARD_FIELDS, selection, selectionArgs,
                groupBy, having, orderBy);
        try {
            if (c.moveToFirst()) {
                return fillInStandardFields(c);
            }
            else {
                return null;
            }
        } finally {
            c.close();
        }
    }
    
    public List<MPendingIdentity> getUnnotifiedIdentities() {
        SQLiteDatabase db = initializeDatabase();
        Cursor c = db.query(
                MPendingIdentity.TABLE, STANDARD_FIELDS,
                MPendingIdentity.COL_NOTIFIED + "=0",
                null, null, null, null);
        try {
            LinkedList<MPendingIdentity> ids = new LinkedList<MPendingIdentity>();
            while (c.moveToNext()) {
                ids.add(fillInStandardFields(c));
            }
            return ids;
        } finally {
            c.close();
        }
    }
    
    public List<MPendingIdentity> getUnnotifiedIdentities(Long identityId) {
        SQLiteDatabase db = initializeDatabase();
        Cursor c = db.query(
                MPendingIdentity.TABLE, STANDARD_FIELDS,
                MPendingIdentity.COL_NOTIFIED + "=0 AND " + MPendingIdentity.COL_IDENTITY_ID + "=?",
                new String[] { identityId.toString() },
                null, null, null);
        try {
            LinkedList<MPendingIdentity> ids = new LinkedList<MPendingIdentity>();
            while (c.moveToNext()) {
                ids.add(fillInStandardFields(c));
            }
            return ids;
        } finally {
            c.close();
        }
    }
    
    public boolean deleteIdentity(long id) {
        SQLiteDatabase db = initializeDatabase();
        String table = MPendingIdentity.TABLE;
        String whereClause = MPendingIdentity.COL_ID + "=?";
        String[] whereArgs = new String[] { Long.toString(id) };
        return db.delete(table, whereClause, whereArgs) > 0;
    }
    
    public boolean deleteIdentity(long identityId, long timestamp) {
        SQLiteDatabase db = initializeDatabase();
        String table = MPendingIdentity.TABLE;
        String whereClause = MPendingIdentity.COL_IDENTITY_ID +
                "=? AND " + MPendingIdentity.COL_TIMESTAMP + "=?";
        String[] whereArgs = new String[] 
                { Long.toString(identityId), Long.toString(timestamp) };
        return db.delete(table, whereClause, whereArgs) > 0;
    }
    
    public MPendingIdentity fillPendingIdentity(long identityId, long timestamp) {
        MPendingIdentity id = new MPendingIdentity();
        id.identityId_ = identityId;
        id.timestamp_ = timestamp;
        id.notified_ = false;
        
        // Random bytes for encryption and a random request ID
        byte[] keyInBytes = new byte[KEY_SIZE];
        Random random = new Random();
        random.nextBytes(keyInBytes);
        id.key_ = Util.convertToHex(keyInBytes);
        id.requestId_ = random.nextInt();
        return id;
    }
    
    private MPendingIdentity fillInStandardFields(Cursor c) {
        MPendingIdentity id = new MPendingIdentity();
        id.id_ = c.getLong(_id);
        id.identityId_ = c.getLong(identityId);
        id.key_ = c.getString(key);
        id.notified_ = c.getLong(notified) != 0;
        id.requestId_ = c.getInt(requestId);
        id.timestamp_ = c.getLong(timestamp);
        return id;
    }

    @Override
    public void close() {
    	if (sqlUpdateIdentity != null) {
    		sqlUpdateIdentity.close();
    		sqlUpdateIdentity = null;
    	}
    	if (sqlInsertIdentity != null) {
    		sqlInsertIdentity.close();
    		sqlInsertIdentity = null;
    	}
    }
}
