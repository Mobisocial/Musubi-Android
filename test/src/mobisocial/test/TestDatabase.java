package mobisocial.test;

import java.math.BigInteger;
import java.util.Date;
import java.util.HashSet;
import java.util.Random;

import mobisocial.crypto.IBHashedIdentity;
import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.crypto.IBIdentity;
import mobisocial.musubi.encoding.DiscardMessage;
import mobisocial.musubi.encoding.ObjEncoder;
import mobisocial.musubi.encoding.ObjEncodingException;
import mobisocial.musubi.encoding.ObjFormat;
import mobisocial.musubi.model.MApp;
import mobisocial.musubi.model.MDevice;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MFeed.FeedType;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MObject;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.util.Util;
import mobisocial.socialkit.Obj;
import mobisocial.socialkit.obj.MemObj;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class TestDatabase extends DatabaseManager {
    final Random random;
    final MDevice localDevice;

    public TestDatabase(Context context, SQLiteOpenHelper dbh) {
        super(context, dbh);
        random = new Random();

        IBIdentity generatedId = randomIBIdentity();
        MIdentity storedId = insertIdentity(generatedId, true, true);
        long deviceName = getDeviceManager().getLocalDeviceName();
        MDevice deviceClaim = new MDevice();
        deviceClaim.deviceName_ = deviceName;
        deviceClaim.identityId_ = storedId.id_;
        getDeviceManager().insertDevice(deviceClaim);
        localDevice = getDeviceManager().getDeviceForName(storedId.id_, deviceName);
    }

    // testing only.
    public MIdentity insertIdentity(IBHashedIdentity id, boolean owned, boolean claimed) {
        MIdentity row = new MIdentity();

        if (id instanceof IBIdentity) {
            row.type_ = Authority.Email;
            row.principal_ = ((IBIdentity)id).principal_;
            row.name_ = ((IBIdentity)id).principal_;
        } else {
            row.name_ = new BigInteger(80, random).toString(32);
        }
        row.principalHash_ = id.hashed_;
        row.principalShortHash_ = Util.shortHash(id.hashed_);
        row.owned_ = owned;
        row.claimed_ = claimed;
        getIdentityManager().insertIdentity(row);
        return row;
    }

    // testing only.
    public MIdentity insertIdentityAndDevice(IBHashedIdentity id, boolean owned, boolean claimed) {
        MIdentity row = new MIdentity();

        if (id instanceof IBIdentity) {
            row.type_ = Authority.Email;
            row.principal_ = ((IBIdentity)id).principal_;
            row.name_ = ((IBIdentity)id).principal_;
        } else {
            row.name_ = new BigInteger(80, random).toString(32);
        }
        row.principalHash_ = id.hashed_;
        row.principalShortHash_ = Util.shortHash(id.hashed_);
        row.owned_ = owned;
        row.claimed_ = claimed;
        getIdentityManager().insertIdentity(row);

        long deviceId;
        if (owned) {
            try {
                deviceId = getDeviceManager().getLocalDeviceName();
            } catch (RuntimeException e) {
                Log.w(getClass().getSimpleName(), "Auto-inserting local device id");
                deviceId = getDeviceManager().generateAndStoreLocalDeviceName();
            }
        } else {
            deviceId = random.nextLong();
        }
        MDevice device = new MDevice();
        device.deviceName_ = deviceId;
        device.identityId_ = row.id_;
        getDeviceManager().insertDevice(device);

        return row;
    }

    public MFeed createFixedFeed(MIdentity... participants) {
        return getFeedManager().getOrCreateFixedFeed(participants);
    }

    // testing only. TODO, add appId and timestamp fields.
    // TODO: fetch a real device, set object.deviceId and use name in univ. hash
    public MObject insertObject(MFeed feed, MIdentity sender, Obj obj) throws ObjEncodingException {
        String appId = MusubiContentProvider.SUPER_APP_ID;
        long timestamp = new Date().getTime();

        MObject object = new MObject();
        MApp appRow = getAppManager().ensureApp(appId);
        object.appId_ = appRow.id_;

        object.id_ = -1;
        object.feedId_ = feed.id_;
        object.identityId_ = sender.id_;
        object.deviceId_ = 1;

        object.type_ = obj.getType();
        if (obj.getJson() != null) {
            object.json_ = obj.getJson().toString();
        }
        object.raw_ = obj.getRaw();
        object.intKey_ = obj.getIntKey();
        object.stringKey_ = obj.getStringKey();
        object.timestamp_ = timestamp; //normally done by the guy who inserts the obj

        MDevice device = new MDevice();
        device.deviceName_ = getDeviceManager().getLocalDeviceName();
        FeedType feedType = feed.type_;
        ObjFormat f = new ObjFormat(feedType, feed.capability_, appId, timestamp, obj);
        byte[] innerHash = ObjEncoder.encode(f);
        object.universalHash_ = ObjEncoder.computeUniversalHash(sender, device, innerHash);
        object.shortUniversalHash_ = Util.shortHash(object.universalHash_);

        getObjectManager().insertObject(object);
        return object;
    }


    // testing only!
    public MObject insert(MFeed feed, MDevice sender, Obj obj) {
        MApp appRow = getAppManager().ensureApp(getContext().getPackageName());
        MObject object = new MObject();

        object.id_ = -1;
        object.appId_ = appRow.id_;
        object.feedId_ = feed.id_;
        object.identityId_ = sender.identityId_;
        object.deviceId_ = sender.id_;
        ObjEncoder.populate(object, obj);
        hash(object);
        getObjectManager().insertObject(object);
        return object;
    }

    private void hash(MObject object) {
        try {
            MDevice device = getDeviceManager().getDeviceForId(object.deviceId_);        
            MIdentity from = getIdentityManager().getIdentityForId(object.identityId_);
            MFeed feed = getFeedManager().lookupFeed(object.feedId_);
            MApp app = getAppManager().getAppBasics(object.appId_);
            ObjFormat outbound = ObjEncoder.getPreparedObj(app, feed, object);
            byte[] data = ObjEncoder.encode(outbound);
            byte[] hash = Util.sha256(data);
            object.universalHash_ = ObjEncoder.computeUniversalHash(from, device, hash);
            object.shortUniversalHash_ = Util.shortHash(object.universalHash_);
        } catch (DiscardMessage e) {
            throw new RuntimeException("Data error", e);
        }
    }

    private ObjFormat getPreparedObj(MApp app, MFeed feed, MObject object)
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

    private final HashSet<String> usedNames = new HashSet<String>();
    public String randomUniquePrincipal() {
        for(;;) {
            int length = random.nextInt(16);
            StringBuilder sb = new StringBuilder();
            for(int i = 0; i < length; ++i) {
                char c = (char) ('a' + random.nextInt('z' - 'a'));
                sb.append(c);
            }
            sb.append("@gmail.com");
            String result = sb.toString();
            if(usedNames.contains(result))
                continue;
            usedNames.add(result);
            return result;
        }       
    }
    public IBIdentity randomIBIdentity() {
        String principal = randomUniquePrincipal();
        long temporalFrame = IdentitiesManager.computeTemporalFrameFromPrincipal(principal);
        return new IBIdentity(Authority.Email, principal, temporalFrame);
    }

    public MDevice getLocalDevice() {
        return localDevice;
    }
}
