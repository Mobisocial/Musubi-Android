package mobisocial.test;

import java.util.Date;

import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.musubi.App;
import mobisocial.musubi.encoding.ObjEncoder;
import mobisocial.musubi.feed.iface.DbEntryHandler;
import mobisocial.musubi.feed.iface.FeedRenderer;
import mobisocial.musubi.model.MApp;
import mobisocial.musubi.model.MDevice;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MObject;
import mobisocial.musubi.model.helpers.AppManager;
import mobisocial.musubi.model.helpers.DeviceManager;
import mobisocial.musubi.model.helpers.FeedManager;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.model.helpers.ObjectManager;
import mobisocial.musubi.obj.ObjHelpers;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.service.MusubiService;
import mobisocial.musubi.util.Util;
import mobisocial.socialkit.Obj;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.IBinder;
import android.util.Log;

public abstract class NMusubisTestBase extends NServicesTestCase {
	protected MockMusubiAppContext[] mContexts;
	protected Context mContext;
	protected SQLiteOpenHelper[] db;
	protected IdentitiesManager[] im;
	protected DeviceManager[] dm;
	protected ObjectManager[] om;
	protected FeedManager[] fm;
	protected AppManager[] am;
	protected MIdentity[][] me;
	protected MusubiService ms[];
	
	@SuppressWarnings({ "rawtypes" })
    private static Class[] makeClassList(int num) {
    	Class[] c = new Class[num];
    	for(int i = 0; i < num; ++i) 
    		c[i] = MusubiService.class;
    	return c;
    }
	@SuppressWarnings("unchecked")
	public NMusubisTestBase(int num) {
		super(makeClassList(num));
	}
	@Override
	protected Context getContextForService(int index, Service service) {
		return mContexts[index];
	}

	@Override
	protected Context getApplicationForService(int index, Service service) {
		return mContexts[index];
	}
	
	@Override
	protected void setUp() throws Exception {
		mContext = getContext();
		mContexts = new MockMusubiAppContext[2];
		for(int i = 0; i < mContexts.length; ++i) {
			mContexts[i] = new MockMusubiAppContext(mContext);
		}
		super.setUp();

	
        Intent startIntent[] = new Intent[] {new Intent(), new Intent()};
        assertEquals(mContexts.length, startIntent.length);
        for(int i = 0; i < mContexts.length; ++i)
        	startIntent[i].setClass(mContexts[i], MusubiService.class);
        IBinder[] b = bindService(startIntent);
        db = new SQLiteOpenHelper[mContexts.length];
        im = new IdentitiesManager[mContexts.length];
        dm = new DeviceManager[mContexts.length];
        om = new ObjectManager[mContexts.length];
        fm = new FeedManager[mContexts.length];
        am = new AppManager[mContexts.length];
        me = new MIdentity[mContexts.length][];
        ms = new MusubiService[mContexts.length];
        for(int i = 0; i < mContexts.length; ++i) {
    		MusubiService musubi_service = ((MusubiService.MusubiServiceBinder)b[i]).getService();
    		ms[i] = musubi_service;
    		db[i] = App.getDatabaseSource(musubi_service);        	
    		im[i] = new IdentitiesManager(db[i]);
    		dm[i] = new DeviceManager(db[i]);
    		om[i] = new ObjectManager(db[i]);
    		fm[i] = new FeedManager(db[i]);
    		am[i] = new AppManager(db[i]);

    		me[i] = new MIdentity[mContexts.length];
    		me[i][i] = new MIdentity();
    		me[i][i].type_ = Authority.Email;
    		me[i][i].principal_ = randomUniquePrincipal();
    		me[i][i].principalHash_ = Util.sha256(me[i][i].principal_.getBytes());
    		me[i][i].principalShortHash_ = Util.shortHash(me[i][i].principalHash_);
    		me[i][i].owned_ = true;
    		me[i][i].claimed_ = true;
    		im[i].insertIdentity(me[i][i]);

    		mContexts[i].getContentResolver().notifyChange(MusubiService.OWNED_IDENTITY_AVAILABLE, null);
        }
        for(int i = 0; i < mContexts.length; ++i) {
            for(int j = 0; j < mContexts.length; ++j) {
            	if(i == j) {
            		continue;
            	}
            	me[i][j] = new MIdentity();
        		me[i][j].type_ = me[j][j].type_;
            	me[i][j].principal_ = me[j][j].principal_;
	    		me[i][j].principalHash_ = me[j][j].principalHash_;
	    		me[i][j].principalShortHash_ = me[j][j].principalShortHash_;
	    		me[i][j].owned_ = false;
	    		im[i].insertIdentity(me[i][j]);
            }
        }
	}
	
	@Override
	protected void tearDown() throws Exception {
		if(ms != null)
			for(MusubiService s : ms) {
				s.shutdownThreads();
			}
		super.tearDown();
	}
	protected long insertObject(MockMusubiAppContext context, IdentitiesManager identitiesManager,
			DeviceManager deviceManager, ObjectManager objectManager,
			FeedManager feedManager, AppManager appManager, MFeed feed, MIdentity me, Obj obj) 
	{
		MApp superApp = appManager.ensureApp(MusubiContentProvider.SUPER_APP_ID);
		MObject o = new MObject();
        o.feedId_ = feed.id_;
        o.identityId_ = me.id_;
        o.appId_ = superApp.id_;
        o.timestamp_ = new Date().getTime();
        ObjEncoder.populate(o, obj);
        
        MIdentity self = identitiesManager.getOwnedIdentities().get(0);
        Log.w(getName(), "device id value: " + deviceManager.getLocalDeviceName());

        MDevice device = deviceManager.getDeviceForName(self.id_, deviceManager.getLocalDeviceName());
        o.deviceId_ = device.id_;

        byte[] hash = new byte[] { 'b', 'o', 'g', 'u', 's', '!', '!', '!' };
        o.universalHash_ = hash;
        o.shortUniversalHash_ = Util.shortHash(hash);
        o.lastModifiedTimestamp_ = o.timestamp_;
        o.processed_ = false;
        o.renderable_ = false;
		DbEntryHandler h = ObjHelpers.forType(o.type_);
        if (h instanceof FeedRenderer) {
            o.renderable_ = true;
        }
        
		objectManager.insertObject(o);
		context.getContentResolver().notifyChange(MusubiService.PLAIN_OBJ_READY, null);
		return o.id_;
	}
}
