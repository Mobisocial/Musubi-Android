package mobisocial.musubi.service;

import mobisocial.musubi.encoding.ObjEncoder;
import mobisocial.musubi.model.MApp;
import mobisocial.musubi.model.MDevice;
import mobisocial.musubi.model.MEncodedMessage;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MObject;
import mobisocial.musubi.model.helpers.DeviceManager;
import mobisocial.musubi.objects.StatusObj;
import mobisocial.musubi.service.MessageEncodeProcessor.ProcessorThread;
import mobisocial.musubi.util.Util;
import mobisocial.socialkit.Obj;
import mobisocial.socialkit.obj.MemObj;
import mobisocial.test.MockMusubiAppContext;
import mobisocial.test.TestDatabase;

import org.json.JSONObject;

import android.content.Intent;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Message;
import android.test.ServiceTestCase;

public class EncodeDecodeTest extends ServiceTestCase<MusubiService> {
    static final String TAG = "EncodeDecodeTest";
    MIdentity me;
    MDevice meDevice;
    MIdentity claimedFriend;
    MIdentity unclaimedFriend;
    MDevice amigoDevice;
    SQLiteOpenHelper dbh;

    TestDatabase database;
    MessageEncodeProcessor encoder;
    MessageDecodeProcessor decoder;
    ObjPipelineProcessor pipeline;
    MockMusubiAppContext context;

    public EncodeDecodeTest() {
        super(MusubiService.class);
    }

    @Override
    protected void setUp() throws Exception {
        context = new MockMusubiAppContext(getContext());
        setApplication(context);
        setContext(context);
        super.setUp();
        dbh = context.getDatabaseSource();
        database = new TestDatabase(getContext(), dbh);

        me = database.insertIdentity(database.randomIBIdentity(), true, true);
        DeviceManager dm = new DeviceManager(dbh);
        meDevice = new MDevice();
        meDevice.identityId_ = me.id_;
        meDevice.deviceName_ = dm.getLocalDeviceName();
        dm.insertDevice(meDevice);
        claimedFriend = database.insertIdentity(database.randomIBIdentity(), false, true);
        unclaimedFriend = database.insertIdentity(database.randomIBIdentity(), false, false);

        encoder = MessageEncodeProcessor.newInstance(getContext(), dbh, null, context.getSettings().mAlternateIdentityProvider);
        decoder = MessageDecodeProcessor.newInstance(getContext(), dbh, null, context.getSettings().mAlternateIdentityProvider);
        pipeline = ObjPipelineProcessor.newInstance(getContext());
    }
    public void tearDown() {
    	if(getService() != null)
    		getService().shutdownThreads();
    	dbh.close();
    }

    public void testEncodeBasic() throws Exception {
        // Prepare a bit of data
        MessageEncodeProcessor encoder = MessageEncodeProcessor.newInstance(getContext(), dbh, null, context.getSettings().mAlternateIdentityProvider);
        MApp app = database.getAppManager().ensureApp(getContext().getPackageName());
        MFeed feed = database.createFixedFeed(me, claimedFriend, unclaimedFriend);

        Obj obj1 = new MemObj("anon", null, null, null, "funny business");
        MObject send1 = new MObject();
        send1.appId_ = app.id_;
        send1.feedId_ = feed.id_;
        send1.identityId_ = me.id_;
        send1.deviceId_ = meDevice.id_;
        ObjEncoder.populate(send1, obj1);
        database.getObjectManager().insertObject(send1);

        Obj obj2 = new MemObj("status", new JSONObject("{\"text\":\"woaw\"}"), null, null, null);
        MObject send2 = new MObject();
        send2.appId_ = app.id_;
        send2.feedId_ = feed.id_;
        send2.identityId_ = me.id_;
        send2.deviceId_ = meDevice.id_;
        ObjEncoder.populate(send2, obj2);
        database.getObjectManager().insertObject(send2);

        Obj obj3 = new MemObj("status", new JSONObject("{\"text\":\"seven\"}"), null, 7, null);
        MObject send3 = new MObject();
        send3.appId_ = app.id_;
        send3.feedId_ = feed.id_;
        send3.identityId_ = me.id_;
        send3.deviceId_ = meDevice.id_;
        ObjEncoder.populate(send3, obj3);
        database.getObjectManager().insertObject(send3);

        // Pre-checks
        long[] idsToEncode = encoder.objectsToEncode();
        assertEquals(3, idsToEncode.length);
        assertNull(send1.encodedId_);
        assertNull(send1.universalHash_);
        assertNull(send1.shortUniversalHash_);
        assertNull(send2.encodedId_);
        assertNull(send2.universalHash_);
        assertNull(send2.shortUniversalHash_);
        assertNotSame(send1.id_, send2.id_);
        long[] idsToProcess = pipeline.getUnprocessedObjs();
        assertEquals(0, idsToProcess.length);

        // Run the actual encoder
        ProcessorThread proc = encoder.mProcessorThreads.get(0);
        for (long id : idsToEncode) {
            Message msg = proc.mHandler.obtainMessage();
            msg.what = ProcessorThread.ENCODE_MESSAGE;
            msg.obj = id;
            proc.mHandler.handleMessage(msg);
        }

        idsToEncode = encoder.objectsToEncode();
        assertEquals(0, idsToEncode.length);

        // Make sure things look good
        MObject encoded1 = database.getObjectManager().getObjectForId(send1.id_);
        MObject encoded2 = database.getObjectManager().getObjectForId(send2.id_);
        MObject encoded3 = database.getObjectManager().getObjectForId(send3.id_);
        MEncodedMessage msg3 = database.getEncodedMessageManager().lookupById(encoded3.encodedId_);

        assertEquals(send1.id_, encoded1.id_);
        assertEquals(send1.type_, encoded1.type_);
        assertEquals(encoded1.shortUniversalHash_, (Long)Util.shortHash(encoded1.universalHash_));
        assertEquals(send2.id_, encoded2.id_);
        assertEquals(send2.type_, encoded2.type_);
        assertEquals(encoded2.shortUniversalHash_, (Long)Util.shortHash(encoded2.universalHash_));
        assertNotNull(encoded1.encodedId_);
        assertNotNull(encoded2.encodedId_);
        assertTrue(msg3.outbound_);
        assertFalse(msg3.processed_);
        assertEquals(msg3.fromIdentityId_, (Long)me.id_);
        assertEquals(msg3.fromDevice_, (Long)meDevice.id_);

        long[] hopefullyEmpty = encoder.objectsToEncode();
        assertEquals(0, hopefullyEmpty.length);
        hopefullyEmpty = decoder.objsToDecode().toArray();
        assertEquals(0, hopefullyEmpty.length);

        // Make sure all objects will be processed
        idsToProcess = pipeline.getUnprocessedObjs();
        assertEquals(3, idsToProcess.length);
        pipeline.onChange(false);
        idsToProcess = pipeline.getUnprocessedObjs();
        assertEquals(0, idsToProcess.length);
    }

    public void testEncodeService() throws Exception {
        Intent startIntent = new Intent();
        startIntent.setClass(getContext(), MusubiService.class);
        startService(startIntent);

        MApp app = database.getAppManager().ensureApp(getContext().getPackageName());
        MFeed feed = database.createFixedFeed(me, claimedFriend, unclaimedFriend);

        // Send a renderable obj
        Obj obj1 = StatusObj.from("show me the renderable_");
        MObject send1 = new MObject();
        send1.appId_ = app.id_;
        send1.feedId_ = feed.id_;
        send1.identityId_ = me.id_;
        send1.deviceId_ = meDevice.id_;
        ObjEncoder.populate(send1, obj1);
        database.getObjectManager().insertObject(send1);
        MObject db1 = database.getObjectManager().getObjectForId(send1.id_);
        assertFalse(db1.renderable_);
        long[] idsToEncode = encoder.objectsToEncode();
        assertEquals(idsToEncode.length, 1);
        long[] idsToProcess = pipeline.getUnprocessedObjs();
        assertEquals(0, idsToProcess.length);

        // Trigger encoder and pipeline processors
        getContext().getContentResolver().notifyChange(MusubiService.PLAIN_OBJ_READY, null);
        try {
        	for(int i = 0; i < 300; ++i) {
        		if(encoder.objectsToEncode().length == 0 && pipeline.getUnprocessedObjs().length == 0) {
        			break;
        		}
        		Thread.sleep(100);
        	}
        } catch (InterruptedException e) {}

        // Verify
        idsToEncode = encoder.objectsToEncode();
        assertEquals(0, idsToEncode.length);
        MObject proc1 = database.getObjectManager().getObjectForId(send1.id_);
        idsToProcess = pipeline.getUnprocessedObjs();
        assertEquals(0, idsToProcess.length);
        assertTrue(proc1.renderable_);
        assertEquals((Long)Util.shortHash(proc1.universalHash_), proc1.shortUniversalHash_);
    }
}
