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

import gnu.trove.map.hash.TLongLongHashMap;
import gnu.trove.procedure.TLongLongProcedure;

import java.util.Arrays;

import mobisocial.crypto.IBEncryptionScheme;
import mobisocial.crypto.IBHashedIdentity;
import mobisocial.crypto.IBSignatureScheme;
import mobisocial.musubi.encoding.NeedsKey;
import mobisocial.musubi.encoding.OutgoingMessage;
import mobisocial.musubi.encoding.TransportDataProvider;
import mobisocial.musubi.model.MDevice;
import mobisocial.musubi.model.MEncodedMessage;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MIncomingSecret;
import mobisocial.musubi.model.MMissingMessage;
import mobisocial.musubi.model.MOutgoingSecret;
import mobisocial.musubi.model.MSequenceNumber;
import mobisocial.musubi.util.Util;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.os.Build;

/**
 * This class provides the database binding for the api that allows
 * identity based message encoding/decoding to be done using a 
 * persistent store of time-varying IBE user keys and dynamic.
 * 
 * Aside from things it does using other managers, it manipulates
 * the incoming and outgoing channel secrets tables and the missing
 * message tracking table
 */
public class MessageTransportManager extends ManagerBase implements TransportDataProvider {
	final IBEncryptionScheme encryptionScheme_;
	final IBSignatureScheme signatureScheme_;
	final long myDeviceName_;
	final DatabaseManager mDbManager;
	final UserKeyManager userKeyManager_;
	public MessageTransportManager(SQLiteOpenHelper databaseSource, IBEncryptionScheme encryptionScheme, 
			IBSignatureScheme signatureScheme, long deviceName) 
	{
        super(databaseSource);
		encryptionScheme_ = encryptionScheme;
		signatureScheme_ = signatureScheme;
		myDeviceName_ = deviceName;
		mDbManager = new DatabaseManager(databaseSource);
		userKeyManager_ = new UserKeyManager(encryptionScheme, signatureScheme, databaseSource);
	}

	SQLiteStatement sqlInsertIncomingSecret_;
	SQLiteStatement sqlInsertOutgoingSecret_;
	SQLiteStatement sqlDeleteMissingSequenceNumber_;
	SQLiteStatement sqlAddSequenceNumber_;
	
	@Override
	public IBEncryptionScheme getEncryptionScheme() {
		return encryptionScheme_;
	}

	@Override
	public IBSignatureScheme getSignatureScheme() {
		return signatureScheme_;
	}
	@Override
	public IBSignatureScheme.UserKey getSignatureKey(MIdentity from, IBHashedIdentity me) throws NeedsKey.Signature {
		return userKeyManager_.getSignatureKey(from, me);
	}

	@Override
	public IBEncryptionScheme.UserKey getEncryptionKey(MIdentity to, IBHashedIdentity me) throws NeedsKey.Encryption {
		return userKeyManager_.getEncryptionKey(to, me);
	}

	// CALL THESE to get the time for the Identity you use for signature
	@Override
	public long getSignatureTime(MIdentity from) {
		//TODO: consider revocation/online offline status, etc
		return IdentitiesManager.computeTemporalFrameFromHash(from.principalHash_);
	}

	@Override
	public long getEncryptionTime(MIdentity to) {
		//TODO: consider revocation/online offline status, etc
		return IdentitiesManager.computeTemporalFrameFromHash(to.principalHash_);
	}

	@Override
	public long getDeviceName() {
		return myDeviceName_;
	}

	@Override
	public MOutgoingSecret lookupOutgoingSecret(MIdentity from, MIdentity to, IBHashedIdentity me, IBHashedIdentity you) {
		SQLiteDatabase db = initializeDatabase();
		Cursor c = db.query(
				MOutgoingSecret.TABLE, 
			new String[] { MOutgoingSecret.COL_ID, MOutgoingSecret.COL_OUTGOING_ENCRYPTED_KEY, MOutgoingSecret.COL_OUTGOING_KEY,
						MOutgoingSecret.COL_OUTGOING_SIGNATURE}, 
			MOutgoingSecret.COL_MY_IDENTITY_ID + "=? AND " + MOutgoingSecret.COL_OTHER_IDENTITY_ID + "=? AND " + 
					MOutgoingSecret.COL_OUTGOING_ENCRYPTION_WHEN + "=? AND " + MOutgoingSecret.COL_OUTGOING_SIGNATURE_WHEN + "=?",
			new String[] { String.valueOf(from.id_), String.valueOf(to.id_), String.valueOf(you.temporalFrame_), String.valueOf(me.temporalFrame_)}, 
			null, null, null
		); 
		try {
			while(c.moveToNext()) {
				MOutgoingSecret os = new MOutgoingSecret();
				os.id_ = c.getLong(0);
				os.encryptedKey_ = c.getBlob(1);
				os.key_ = c.getBlob(2);
				os.encryptionWhen_ = you.temporalFrame_;
				os.signatureWhen_ = me.temporalFrame_;
				os.myIdentityId_ = from.id_;
				os.otherIdentityId_ = to.id_;
				os.signature_ = c.getBlob(3);
				return os;
			}
			return null;
		} finally {
			c.close();
		}
	}

	@Override
	public void insertOutgoingSecret(IBHashedIdentity me, IBHashedIdentity you, MOutgoingSecret os) {
		SQLiteDatabase db = initializeDatabase();
		if(sqlInsertOutgoingSecret_ == null) {
			synchronized(this) {
				if(sqlInsertOutgoingSecret_ == null) {
					sqlInsertOutgoingSecret_ = db.compileStatement(
						"INSERT INTO " + MOutgoingSecret.TABLE + 
						" (" + 
							MOutgoingSecret.COL_MY_IDENTITY_ID + ", " +
							MOutgoingSecret.COL_OTHER_IDENTITY_ID + ", " +
							MOutgoingSecret.COL_OUTGOING_SIGNATURE_WHEN + ", " +
							MOutgoingSecret.COL_OUTGOING_ENCRYPTION_WHEN + ", " +
							MOutgoingSecret.COL_OUTGOING_ENCRYPTED_KEY + ", " +
							MOutgoingSecret.COL_OUTGOING_SIGNATURE + ", " +
							MOutgoingSecret.COL_OUTGOING_KEY +
						") " + 
						"VALUES (?,?,?,?,?,?,?)");
				}
			}
		}
		synchronized (sqlInsertOutgoingSecret_) {
			sqlInsertOutgoingSecret_.bindLong(1, os.myIdentityId_);
			sqlInsertOutgoingSecret_.bindLong(2, os.otherIdentityId_);
			sqlInsertOutgoingSecret_.bindLong(3, os.signatureWhen_);
			sqlInsertOutgoingSecret_.bindLong(4, os.encryptionWhen_);
			sqlInsertOutgoingSecret_.bindBlob(5, os.encryptedKey_);
			sqlInsertOutgoingSecret_.bindBlob(6, os.signature_);
			sqlInsertOutgoingSecret_.bindBlob(7, os.key_);
			os.id_ = sqlInsertOutgoingSecret_.executeInsert();
		}
	}

	@Override
	public MIncomingSecret lookupIncomingSecret(MIdentity from, MDevice fromDevice, MIdentity to, byte[] signature, IBHashedIdentity you, IBHashedIdentity me) {
		SQLiteDatabase db = initializeDatabase();
		Cursor c = db.query(
				MIncomingSecret.TABLE, 
			new String[] { MIncomingSecret.COL_ID, MIncomingSecret.COL_INCOMING_ENCRYPTED_KEY,
						MIncomingSecret.COL_INCOMING_KEY, MIncomingSecret.COL_INCOMING_SIGNATURE }, 
			MIncomingSecret.COL_MY_IDENTITY_ID + "=? AND " + MIncomingSecret.COL_OTHER_IDENTITY_ID + "=? AND " + 
				MIncomingSecret.COL_INCOMING_ENCRYPTION_WHEN + "=? AND " + MIncomingSecret.COL_INCOMING_SIGNATURE_WHEN + "=? AND " +
				MIncomingSecret.COL_INCOMING_DEVICE_ID + "=?",
			new String[] { String.valueOf(to.id_), String.valueOf(from.id_), String.valueOf(me.temporalFrame_), String.valueOf(you.temporalFrame_), String.valueOf(fromDevice.id_)}, 
			null, null, null
		); 
		try {
			while(c.moveToNext()) {
				byte[] cached_signature = c.getBlob(3);
				//its possible to have different signatures on the same set of parameters
				if(!Arrays.equals(signature, cached_signature))
					continue;
				MIncomingSecret is = new MIncomingSecret();
				is.id_ = c.getLong(0);
				is.deviceId_ = fromDevice.id_;
				is.encryptedKey_ = c.getBlob(1);
				is.key_ = c.getBlob(2);
				is.encryptionWhen_ = me.temporalFrame_;
				is.signatureWhen_ = you.temporalFrame_;
				is.myIdentityId_ = to.id_;
				is.otherIdentityId_ = from.id_;
				is.signature_ = cached_signature;
				return is;
			}
			return null;
		} finally {
			c.close();
		}
	}

	@Override
	public void insertIncomingSecret(IBHashedIdentity you, IBHashedIdentity me, MIncomingSecret is) {
		SQLiteDatabase db = initializeDatabase();
		if(sqlInsertIncomingSecret_ == null) {
			synchronized(this) {
				if(sqlInsertIncomingSecret_ == null) {
					sqlInsertIncomingSecret_ = db.compileStatement(
						"INSERT INTO " + MIncomingSecret.TABLE + 
						" (" + 
							MIncomingSecret.COL_MY_IDENTITY_ID + ", " +
							MIncomingSecret.COL_OTHER_IDENTITY_ID + ", " +
							MIncomingSecret.COL_INCOMING_SIGNATURE_WHEN + ", " +
							MIncomingSecret.COL_INCOMING_ENCRYPTION_WHEN + ", " +
							MIncomingSecret.COL_INCOMING_ENCRYPTED_KEY + ", " +
							MIncomingSecret.COL_INCOMING_DEVICE_ID + ", " +
							MIncomingSecret.COL_INCOMING_SIGNATURE + ", " +
							MIncomingSecret.COL_INCOMING_KEY +
						") " + 
						"VALUES (?,?,?,?,?,?,?,?)");
				}
			}
		}
		synchronized (sqlInsertIncomingSecret_) {
			sqlInsertIncomingSecret_.bindLong(1, is.myIdentityId_);
			sqlInsertIncomingSecret_.bindLong(2, is.otherIdentityId_);
			sqlInsertIncomingSecret_.bindLong(3, is.signatureWhen_);
			sqlInsertIncomingSecret_.bindLong(4, is.encryptionWhen_);
			sqlInsertIncomingSecret_.bindBlob(5, is.encryptedKey_);
			sqlInsertIncomingSecret_.bindLong(6, is.deviceId_);
			sqlInsertIncomingSecret_.bindBlob(7, is.signature_);
			sqlInsertIncomingSecret_.bindBlob(8, is.key_);
			is.id_ = sqlInsertIncomingSecret_.executeInsert();
		}
	}

	@Override
	public void incrementSequenceNumber(MIdentity to) {
		mDbManager.getIdentitiesManager().incrementSequenceNumber(to);
	}

	@Override
	public void receivedSequenceNumber(MDevice from, long sequenceNumber) {
		SQLiteDatabase db = initializeDatabase();
		//TODO:this needs to add sequence numbers based on if message appear to have been missing
		if(sqlDeleteMissingSequenceNumber_ == null) {
			synchronized(this) {
				if(sqlDeleteMissingSequenceNumber_ == null) {
					sqlDeleteMissingSequenceNumber_ = db.compileStatement(
						"DELETE FROM " + MMissingMessage.TABLE + " WHERE " + MMissingMessage.COL_DEVICE_ID + "=? AND " +
						MMissingMessage.COL_SEQUENCE_NUMBER + "=? "
					);
				}
			}
		}
		
		synchronized (sqlDeleteMissingSequenceNumber_) {
			sqlDeleteMissingSequenceNumber_.bindLong(1, from.id_);
			sqlDeleteMissingSequenceNumber_.execute();
		}
	}

	@Override
	public boolean haveHash(byte[] hash) {
		return mDbManager.getEncodedMessageManager().getEncodedIdForHash(hash) != -1;
	}

	@Override
	public void storeSequenceNumbers(final MEncodedMessage encoded, TLongLongHashMap sequence_numbers) {
		SQLiteDatabase db = initializeDatabase();
		if(sqlAddSequenceNumber_ == null) {
			synchronized (this) {
				if(sqlAddSequenceNumber_ == null) {
					sqlAddSequenceNumber_ = db.compileStatement(
						"INSERT INTO " + MSequenceNumber.TABLE + 
						" (" +
						MSequenceNumber.COL_RECIPIENT + "," +
						MSequenceNumber.COL_ENCODED_ID + "," +
						MSequenceNumber.COL_SEQUENCE_NUMBER +
						") " +
						"VALUES (?,?,?)"
					);
				}
			}
		}
		synchronized (sqlAddSequenceNumber_) {
			sequence_numbers.forEachEntry(new TLongLongProcedure() {
				@Override
				public boolean execute(long identityId, long sequenceNumber) {
					sqlAddSequenceNumber_.bindLong(1, identityId);
					sqlAddSequenceNumber_.bindLong(2, encoded.id_);
					sqlAddSequenceNumber_.bindLong(3, sequenceNumber);
					sqlAddSequenceNumber_.executeInsert();
					return true;
				}
			});
		}
	}

	@Override
	public boolean isBlacklisted(MIdentity from) {
		return mDbManager.getIdentitiesManager().isBlacklisted(from);
	}

	@Override
	public boolean isMe(IBHashedIdentity ibHashedIdentity) {
		return mDbManager.getIdentitiesManager().isMe(ibHashedIdentity);
	}

	@Override
	public MIdentity addClaimedIdentity(IBHashedIdentity hid) {
		return mDbManager.getIdentitiesManager().ensureClaimedIdentity(hid);
	}

	@Override
	public MIdentity addUnclaimedIdentity(IBHashedIdentity hid) {
		MIdentity id = mDbManager.getIdentitiesManager().getIdentityForIBHashedIdentity(hid);
		if(id != null) {
			return id;
		}
		id = new MIdentity();
		id.claimed_ = false;
		id.principalHash_ = hid.hashed_;
		id.principalShortHash_ = Util.shortHash(hid.hashed_);
		id.type_ = hid.authority_;
		id.hasSentEmail_ = false;
		mDbManager.getIdentitiesManager().insertIdentity(id);
		return id;
	}

	@Override
	public MDevice addDevice(MIdentity ident, long deviceId) {
		MDevice dev = mDbManager.getDeviceManager().getDeviceForName(ident.id_, deviceId);
		if(dev != null)
			return dev;
		dev = new MDevice();
		dev.deviceName_ = deviceId;
		dev.identityId_ = ident.id_;
		dev.maxSequenceNumber_ = 0;
		mDbManager.getDeviceManager().insertDevice(dev);
		return dev;
	}

	@Override
	public void updateEncodedMetadata(MEncodedMessage encoded) {
		mDbManager.getEncodedMessageManager().updateEncodedMetadata(encoded);
	}

	@Override
	public void insertEncodedMessage(OutgoingMessage om, MEncodedMessage encoded) {
		mDbManager.getEncodedMessageManager().insertEncoded(encoded);
	}

	@Override
	public void setTransactionSuccessful() {
		SQLiteDatabase db = initializeDatabase();
		db.setTransactionSuccessful();
	}

	@Override
	public void beginTransaction() {
		SQLiteDatabase db = initializeDatabase();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			db.beginTransactionNonExclusive();
		} else {
			db.beginTransaction();
		}
	}

	@Override
	public void endTransaction() {
		SQLiteDatabase db = initializeDatabase();
		db.endTransaction();
	}

	@Override
	public synchronized void close() {
		if (sqlInsertIncomingSecret_ != null) {
			sqlInsertIncomingSecret_.close();
			sqlInsertIncomingSecret_ = null;
		}
		if (sqlInsertOutgoingSecret_ != null) {
			sqlInsertOutgoingSecret_.close();
			sqlInsertOutgoingSecret_ = null;
		}
		if (sqlDeleteMissingSequenceNumber_ != null) {
			sqlDeleteMissingSequenceNumber_.close();
			sqlDeleteMissingSequenceNumber_ = null;
		}
		if (sqlAddSequenceNumber_ != null) {
			sqlAddSequenceNumber_.close();
			sqlAddSequenceNumber_ = null;
		}
		if (mDbManager != null) {
			mDbManager.close();
		}
	}
}
