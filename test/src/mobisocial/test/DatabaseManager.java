package mobisocial.test;

import java.util.Date;
import java.util.List;

import mobisocial.musubi.encoding.ObjEncoder;
import mobisocial.musubi.encoding.ObjFormat;
import mobisocial.musubi.model.MApp;
import mobisocial.musubi.model.MDevice;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MFeed.FeedType;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MObject;
import mobisocial.musubi.model.helpers.AppManager;
import mobisocial.musubi.model.helpers.DeviceManager;
import mobisocial.musubi.model.helpers.EncodedMessageManager;
import mobisocial.musubi.model.helpers.FactManager;
import mobisocial.musubi.model.helpers.FeedManager;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.model.helpers.MyAccountManager;
import mobisocial.musubi.model.helpers.ObjectManager;
import mobisocial.musubi.util.Util;
import mobisocial.socialkit.Obj;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

class DatabaseManager {
    private Context ctx;
    private SQLiteOpenHelper dbh;
    private IdentitiesManager idm;
    private DeviceManager dm;
    private ObjectManager om;
    private AppManager am;
    private MyAccountManager mam;
    private FeedManager fm;
    private EncodedMessageManager em;
    private FactManager factm;

    DatabaseManager(Context context, SQLiteOpenHelper dbh) {
        this.ctx = context;
        this.dbh = dbh;

    }

    public IdentitiesManager getIdentityManager() {
        if (idm == null) idm = new IdentitiesManager(dbh);
        return idm;
    }

    public DeviceManager getDeviceManager() {
        if (dm == null) dm = new DeviceManager(dbh);
        return dm;
    }

    public ObjectManager getObjectManager() {
        if (om == null) om = new ObjectManager(dbh);
        return om;
    }

    public AppManager getAppManager() {
        if (am == null) am = new AppManager(dbh);
        return am;
    }

    public MyAccountManager getMyAccountManager() {
        if (mam == null) mam = new MyAccountManager(dbh);
        return mam;
    }

    public FeedManager getFeedManager() {
        if (fm == null) fm = new FeedManager(dbh);
        return fm;
    }

    public FactManager getFactManager() {
        if (factm == null) factm = new FactManager(dbh);
        return factm;
    }

    public EncodedMessageManager getEncodedMessageManager() {
        if (em == null) em = new EncodedMessageManager(dbh);
        return em;
    }

    /**
     * Inserts an object with this app's identifier and the current timestamp,
     * sent from the local user's primary identity.
     */
    public MObject insert(MFeed feed, Obj obj) {
        String appId = ctx.getPackageName();
        long timestamp = new Date().getTime();

        List<MIdentity> owned = getIdentityManager().getOwnedIdentities();
        if (owned.size() == 0) {
            throw new RuntimeException("No owned identities on this device");
        }
        MIdentity sender = owned.get(0);
        long deviceName = getDeviceManager().getLocalDeviceName();
        MDevice device = getDeviceManager().getDeviceForName(sender.id_, deviceName);
        if (device == null) {
            throw new RuntimeException("No device entry for identity _id=" +
                    sender.id_ + ", deviceName=" + deviceName);
        }

        MObject object = new MObject();
        MApp appRow = getAppManager().ensureApp(appId);
        object.appId_ = appRow.id_;

        object.id_ = -1;
        object.feedId_ = feed.id_;
        object.identityId_ = sender.id_;
        object.deviceId_ = device.id_;
        ObjEncoder.populate(object, obj);

        FeedType feedType = feed.type_;
        ObjFormat f = new ObjFormat(feedType, feed.capability_, appId, timestamp, obj);
        byte[] innerHash = ObjEncoder.encode(f);
        object.universalHash_ = ObjEncoder.computeUniversalHash(sender, device, innerHash);
        object.shortUniversalHash_ = Util.shortHash(object.universalHash_);

        getObjectManager().insertObject(object);
        return object;
    }

    public SQLiteDatabase getReadableDatabase() {
        return dbh.getReadableDatabase();
    }

    public SQLiteDatabase getWritableDatabase() {
        return dbh.getWritableDatabase();
    }

    protected Context getContext() {
        return ctx;
    }
}
