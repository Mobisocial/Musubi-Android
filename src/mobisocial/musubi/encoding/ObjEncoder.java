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

package mobisocial.musubi.encoding;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import mobisocial.musubi.encoding.DiscardMessage.BadObjFormat;
import mobisocial.musubi.model.MApp;
import mobisocial.musubi.model.MDevice;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MFeed.FeedType;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MObject;
import mobisocial.socialkit.Obj;
import mobisocial.socialkit.obj.MemObj;

import org.codehaus.jackson.map.DeserializationConfig.Feature;
import org.codehaus.jackson.map.ObjectMapper;
import org.json.JSONException;
import org.json.JSONObject;

import de.undercouch.bson4jackson.BsonParser;
import de.undercouch.bson4jackson.BsonFactory;

/**
 * <p>Encodes and decodes data available to Musubi for transmission across the network.
 *
 * <p>Data is encoded as follows. The first four bytes are the int value 30050081,
 * indicating the use of {@see ObjFormat}. The next four bytes are reserved.
 * The following n bytes are a bson encoded data structure.
 */
public class ObjEncoder {
    public static final int VERSION_HEADER = 30050081;
    public static final int DEFAULT_FLAGS = 0x0;
    private static final int HEADER_GUESSTIMATE = 200;
	private static ObjectMapper sMapper; // final but lazy

    public static byte[] encode(ObjFormat decoded) {
        int approx = decoded.raw == null ? HEADER_GUESSTIMATE :
            decoded.raw.length + HEADER_GUESSTIMATE;
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream(approx);
        DataOutputStream dataOut = new DataOutputStream(byteOut);
        try {
            dataOut.writeInt(VERSION_HEADER);
            dataOut.writeInt(DEFAULT_FLAGS);
            dataOut.write(getObjectMapper().writeValueAsBytes(decoded));
            return byteOut.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Bad encoding", e);
        } finally {
            try {
                byteOut.close();
                dataOut.close();
            } catch (IOException e) {}
        }
    }

    public static ObjFormat decode(byte[] encoded) throws BadObjFormat {
        ObjFormat obj = null;

        ByteArrayInputStream inputStream = new ByteArrayInputStream(encoded);
        DataInputStream dataStream = new DataInputStream(inputStream);
        try {
            int version = dataStream.readInt();
            if (version != VERSION_HEADER) {
                throw new BadObjFormat("Bad version header " + version);
            }
            dataStream.readInt(); // RFU flags
            obj = getObjectMapper().readValue(dataStream, ObjFormat.class);
        } catch (IOException e) {
            throw new BadObjFormat("Bad encoding", e);
        } finally {
            try {
                dataStream.close();
                inputStream.close();
            } catch (IOException e) {}
        }
        return obj;
    }

    public static void populate(MObject object, Obj with) {
        object.type_ = with.getType();
        if (with.getJson() != null) {
            object.json_ = with.getJson().toString();
        }
        object.raw_ = with.getRaw();
        object.intKey_ = with.getIntKey();
        object.stringKey_ = with.getStringKey();
    }

    public static byte[] computeUniversalHash(MIdentity from, MDevice device, byte[] hash) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
            md.update((byte)from.type_.ordinal());
            md.update(from.principalHash_);
            byte[] deviceName = new byte[8];
            ByteBuffer buf = ByteBuffer.wrap(deviceName);
            buf.putLong(device.deviceName_);
            md.update(deviceName);
            md.update(hash);
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("your platform does not support sha256", e);
        }
    }

    public static ObjFormat getPreparedObj(MApp app, MFeed feed, MObject object)
            throws DiscardMessage {
        JSONObject json = null;
        if (object.json_ != null) {
            try {
                json = new JSONObject(object.json_);
            } catch (JSONException e) {
                throw new DiscardMessage.Corrupted("Bad json", e);
            }
        }
        Obj data = new MemObj(object.type_, json, object.raw_, object.intKey_, object.stringKey_);
        FeedType feedType = feed.type_;
        byte[] feedCapability = feed.capability_;
        return new ObjFormat(feedType, feedCapability, app.appId_, object.timestamp_, data);
    }

    private static ObjectMapper getObjectMapper() {
        if (sMapper == null) {
            sMapper = new ObjectMapper(new BsonFactory().enable(BsonParser.Feature.HONOR_DOCUMENT_LENGTH)).
                    configure(Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            sMapper.configure(Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        }
        return sMapper;
    }
}