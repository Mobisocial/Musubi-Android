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

import java.security.SecureRandom;

import mobisocial.musubi.model.MDevice;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MMyDeviceName;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;

public class DeviceManager extends ManagerBase {
	SQLiteStatement sqlInsertDevice_;
	SQLiteStatement sqlGetDeviceId_;
	Long mMyDeviceName = null;

    public DeviceManager(SQLiteOpenHelper databaseSource) {
        super(databaseSource);
    }
	public DeviceManager(SQLiteDatabase db) {
		super(db);
	}
	
	public long getIdForDevice(MIdentity id, long deviceName) {
		SQLiteDatabase db = initializeDatabase();
		if(sqlGetDeviceId_ == null) {
			synchronized(this) {
				if(sqlGetDeviceId_ == null) {
					sqlGetDeviceId_ = db.compileStatement(
						"SELECT " + MDevice.COL_ID + " FROM " + MDevice.TABLE + " WHERE " + MDevice.COL_IDENTITY_ID + "=? AND " + 
								MDevice.COL_DEVICE_NAME + "=?"
					);
				}
			}
		}
		synchronized (sqlGetDeviceId_) {
			sqlGetDeviceId_.bindLong(1, id.id_);
			sqlGetDeviceId_.bindLong(2, deviceName);
			return sqlGetDeviceId_.simpleQueryForLong();
		}
	}
	
	public MDevice getDeviceForId(long id) {
		SQLiteDatabase db = initializeDatabase();
		Cursor c = db.query(MDevice.TABLE,
			new String[] {
				MDevice.COL_DEVICE_NAME,
				MDevice.COL_IDENTITY_ID,
				MDevice.COL_MAX_SEQUENCE_NUMBER
			},
			MDevice.COL_ID + "=?",
			new String[] { 
				String.valueOf(id),
			},
			null, null, null
		);
		try {
			while(c.moveToNext()) {
				MDevice dev = new MDevice();
				dev.id_ = id;
				dev.deviceName_ = c.getLong(0);
				dev.identityId_ = c.getLong(1);
				dev.maxSequenceNumber_ = c.getLong(2);
				return dev;
			}
			return null;
		} finally {
			c.close();
		}
	}

	public long generateAndStoreLocalDeviceName() {
	    ContentValues deviceRow = new ContentValues();
        long deviceNameValue = new SecureRandom().nextLong();
        deviceRow.put(MMyDeviceName.COL_DEVICE_NAME, deviceNameValue);
        initializeDatabase().insert(MMyDeviceName.TABLE, null, deviceRow);
        return deviceNameValue;
	}

	/**
     * Returns a generated identifier for this device.
     */
    public long getLocalDeviceName() {
    	if(mMyDeviceName != null)
    		return mMyDeviceName;
        SQLiteDatabase db = initializeDatabase();
        String table = MMyDeviceName.TABLE;
        String[] columns = new String[] { MMyDeviceName.COL_DEVICE_NAME };
        String selection = null;
        String[] selectionArgs = null;
        String groupBy = null, having = null, orderBy = null;
        Cursor c = db.query(table, columns, selection, selectionArgs, groupBy, having, orderBy);
        try {
            if (c.moveToFirst()) {
                mMyDeviceName = c.getLong(0);
                return mMyDeviceName;
            } else {
                throw new RuntimeException("No device name found in database.");
            }
        } finally {
            c.close();
        }
    }

	public MDevice getDeviceForName(long identityId, long deviceName) {
		SQLiteDatabase db = initializeDatabase();
		Cursor c = db.query(MDevice.TABLE,
			new String[] {
				MDevice.COL_ID,
				MDevice.COL_MAX_SEQUENCE_NUMBER
			},
			MDevice.COL_IDENTITY_ID + "=? AND " + MDevice.COL_DEVICE_NAME + "=?",
			new String[] { 
				String.valueOf(identityId),
				String.valueOf(deviceName),
			},
			null, null, null
		);
		try {
			c.moveToFirst();
			if(!c.isAfterLast()) {
				MDevice dev = new MDevice();
				dev.id_ = c.getLong(0);
				dev.deviceName_ = deviceName;
				dev.identityId_ = identityId;
				dev.maxSequenceNumber_ = c.getLong(1);
				return dev;
			}
			return null;
		} finally {
			c.close();
		}
		
	}
	
	public void insertDevice(MDevice dev) {
		SQLiteDatabase db = initializeDatabase();
		if(sqlInsertDevice_ == null) {
			synchronized (this) {
				if(sqlInsertDevice_ == null) {
					sqlInsertDevice_ = db.compileStatement(
						"INSERT INTO " + MDevice.TABLE + 
						" (" +
						MDevice.COL_DEVICE_NAME + "," +
						MDevice.COL_IDENTITY_ID + "," +
						MDevice.COL_MAX_SEQUENCE_NUMBER +
						") " +
						"VALUES (?,?,?)"
					);
				}
			}
		}
		synchronized (sqlInsertDevice_) {
			sqlInsertDevice_.bindLong(1, dev.deviceName_);
			sqlInsertDevice_.bindLong(2, dev.identityId_);
			sqlInsertDevice_.bindLong(3, dev.maxSequenceNumber_);
			dev.id_ = sqlInsertDevice_.executeInsert();
		}
	}

	@Override
	public synchronized void close() {
		if (sqlInsertDevice_ != null) {
			sqlInsertDevice_.close();
			sqlInsertDevice_ = null;
    	}
		if (sqlGetDeviceId_ != null) {
			sqlGetDeviceId_.close();
			sqlGetDeviceId_ = null;
    	}
	}
}
