
package mobisocial.musubi.encoding;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Date;
import java.util.Random;

import mobisocial.musubi.encoding.DiscardMessage.BadObjFormat;
import mobisocial.musubi.model.MFeed.FeedType;
import mobisocial.musubi.util.Util;
import mobisocial.socialkit.Obj;
import mobisocial.socialkit.obj.MemObj;

import org.json.JSONObject;

import android.test.AndroidTestCase;

public class ObjEncodingTest extends AndroidTestCase {

    public void testObjEncoding() throws Exception {
        Random r = new Random();
        String appId = "my.app";
        String type = "TestObj";
        Integer intVal = r.nextInt();
        byte[] byteVal = new byte[] { 'w', '0', 'o', 'p' };
        String stringVal = "wo0p";
        JSONObject json = new JSONObject("{\"a\":\"b\"}");

        FeedType feedType = FeedType.FIXED;
        byte[] capability = new byte[32];
        r.nextBytes(capability);
        long timestamp = new Date().getTime();
        Obj data = new MemObj(type, json, byteVal, intVal, stringVal);

        ObjFormat format = new ObjFormat(feedType, capability, appId, timestamp, data);
        byte[] encoded = ObjEncoder.encode(format);

        ByteBuffer buf = ByteBuffer.wrap(encoded);
        int version = buf.getInt();
        int flags = buf.getInt();
        assertEquals(ObjEncoder.VERSION_HEADER, version);
        assertEquals(flags, ObjEncoder.DEFAULT_FLAGS);
        ObjFormat decoded = null;
        try {
            decoded = ObjEncoder.decode(encoded);
        } catch (BadObjFormat e) {
            throw e;
        }

        assertEquals(decoded.appId, appId);
        assertEquals(type, decoded.type);
        assertEquals(decoded.jsonSrc, json.toString());
        assertEquals(decoded.timestamp, timestamp);
        assertTrue(Arrays.equals(decoded.feedCapability, capability));
        assertEquals(decoded.feedType, feedType);
        assertEquals(stringVal, decoded.stringKey);
        assertEquals(intVal, decoded.intKey);
        assertTrue(Arrays.equals(decoded.raw, byteVal));
        assertEquals(json.toString(), decoded.jsonSrc);
    }

    public void testObjEncodingNulls() throws Exception {
        Random r = new Random();
        String appId = "my.app";
        Integer intVal = null;
        byte[] byteVal = null;
        String stringVal = null;
        JSONObject json = null;

        FeedType feedType = FeedType.FIXED;
        byte[] capability = new byte[32];
        r.nextBytes(capability);
        long timestamp = new Date().getTime();
        Obj data = new MemObj("TestObj", json, byteVal, intVal, stringVal);

        ObjFormat format = new ObjFormat(feedType, capability, appId, timestamp, data);
        byte[] encoded = ObjEncoder.encode(format);

        ByteBuffer buf = ByteBuffer.wrap(encoded);
        int version = buf.getInt();
        int flags = buf.getInt();
        assertEquals(ObjEncoder.VERSION_HEADER, version);
        assertEquals(flags, ObjEncoder.DEFAULT_FLAGS);
        ObjFormat decoded = null;
        try {
            decoded = ObjEncoder.decode(encoded);
        } catch (BadObjFormat e) {
            throw e;
        }

        assertEquals(decoded.appId, appId);
        assertNull(decoded.jsonSrc);
        assertNull(decoded.stringKey);
        assertNull(decoded.intKey);
        assertNull(decoded.raw);
    }

    public void testHexEncodeDecode() {
        byte[] hash = new byte[128];
        new Random().nextBytes(hash);
        String hex = Util.convertToHex(hash);
        byte[] converted = Util.convertToByteArray(hex);

        assertTrue(Arrays.equals(hash, converted));
    }
}
