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

package mobisocial.musubi.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import mobisocial.musubi.App;
import mobisocial.socialkit.User;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DbContactAttributes /* extends DbTable */ {
    public static final String TABLE = "contact_attributes";
    public static final String _ID = "_id";
    public static final String CONTACT_ID = "contact_id";
    public static final String ATTR_NAME = "attr_name";
    public static final String ATTR_VALUE = "attr_value";

    public static final String ATTR_PROTOCOL_VERSION = "vnd.mobisocial.device/protocol_version";
    public static final String ATTR_BT_CORRAL_UUID = "vnd.mobisocial.device/bt_corral";
    public static final String ATTR_BT_MAC = "vnd.mobisocial.device/bt_mac";
    public static final String ATTR_LAN_IP = "vnd.mobisocial.device/lan_ip";
    public static final String ATTR_WIFI_SSID = "vnd.mobisocial.device/wifi_ssid";
    public static final String ATTR_WIFI_BSSID = "vnd.mobisocial.device/wifi_bssid";
    public static final String ATTR_WIFI_FINGERPRINT = "vnd.mobisocial.device/wifi_fingerprint";

    /**
     * The time when this device was last known to be "nearby".
     * This attribute is not syncable from the network.
     */
    public static final String ATTR_NEARBY_TIMESTAMP = "vnd.mobisocial.device/nearby_timestamp";

    /**
     * Claimed device modalities.
     */
    public static final String ATTR_DEVICE_MODALITY = "vnd.mobisocial.device/modality";


    /**
     * A list of 'well known' attribute types, which are scanned on the network and
     * automatically pinned to a user.
     */
    private static final Set<String> sWellKnownAttrs = new LinkedHashSet<String>();

    static {
        sWellKnownAttrs.add(ATTR_WIFI_FINGERPRINT);
        sWellKnownAttrs.add(ATTR_WIFI_BSSID);
        sWellKnownAttrs.add(ATTR_WIFI_SSID);
        sWellKnownAttrs.add(ATTR_LAN_IP);
        sWellKnownAttrs.add(ATTR_BT_MAC);
        sWellKnownAttrs.add(ATTR_BT_CORRAL_UUID);
        sWellKnownAttrs.add(ATTR_PROTOCOL_VERSION);
        sWellKnownAttrs.add(ATTR_DEVICE_MODALITY);
    }

    public static boolean isWellKnownAttribute(String attr) {
        return sWellKnownAttrs.contains(attr);
    }

    /*
     * Table definitions:
     */

    public static final String[] getColumnNames() {
        return new String[] { _ID, CONTACT_ID, ATTR_NAME, ATTR_VALUE };
    }

    public static final String[] getTypeDefs() {
        return new String[] { "INTEGER PRIMARY KEY", "INTEGER", "TEXT", "TEXT" };
    }

    /*
     * Utilities:
     */

    public static void update(Context context, long contactId, String attr, String value) {
        ContentValues values = new ContentValues();
        values.put(CONTACT_ID, contactId);
        values.put(ATTR_NAME, attr);
        values.put(ATTR_VALUE, value);

        SQLiteOpenHelper helper = App.getDatabaseSource(context);
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            String[] columns = { _ID };
            String selection = CONTACT_ID + " = ? AND " + ATTR_NAME + " = ?";
            String[] selectionArgs = new String[] { Long.toString(contactId), attr };
            String groupBy = null;
            String having = null;
            String orderBy = null;
            Cursor c = db.query(TABLE, columns, selection, selectionArgs, groupBy, having, orderBy);
            if (c.moveToFirst()) {
                c.close();
                db.update(TABLE, values, selection, selectionArgs);
            } else {
                c.close();
                db.insert(TABLE, null, values);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public static String getDeviceAttribute(Context context, String attr) {
        throw new RuntimeException("Stub");
    }

    public static String getAttribute(Context context, long contactId, String attr) {
        SQLiteOpenHelper helper = App.getDatabaseSource(context);
        SQLiteDatabase db = helper.getWritableDatabase();

        String[] columns = { ATTR_VALUE };
        String selection = CONTACT_ID + " = ? AND " + ATTR_NAME + " = ?";
        String[] selectionArgs = new String[] { Long.toString(contactId), attr };
        String groupBy = null;
        String having = null;
        String orderBy = null;
        Cursor c = db.query(TABLE, columns, selection, selectionArgs, groupBy, having, orderBy);
        try {
            if (c.moveToFirst()) {
                return c.getString(0);
            }
            return null;
        } finally {
            c.close();
        }
    }

    public static List<User> getUsersWithAttribute(Context context, String attr) {
        throw new RuntimeException("Needs fixing");
        /*
        SQLiteOpenHelper helper = App.getDatabaseSource(context);
        SQLiteDatabase db = helper.getWritableDatabase();

        String sql = "SELECT c.*"
                + "   FROM contacts c, contact_attributes ca"
                + "   WHERE c._id = ca.contact_id"
                + "   AND   ca.attr_name = ?";
        String[] selectionArgs = new String[] { attr };
        Cursor c = db.rawQuery(sql, selectionArgs);
        try {
            if (!c.moveToFirst()) {
                return new ArrayList<CursorUser>(0);
            }
            List<CursorUser> users = new ArrayList<CursorUser>(c.getCount());
            while (true) {
                users.add(Contact.userFromCursor(context, c));
                if (!c.moveToNext()) {
                    break;
                }
            }
            return users;
        } finally {
            c.close();
            helper.close();
        }*/
    }
}