package mobisocial.musubi.service;

import mobisocial.musubi.model.MDevice;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MObject;
import mobisocial.musubi.objects.StatusObj;
import mobisocial.musubi.util.Util;
import mobisocial.socialkit.Obj;
import mobisocial.test.NMusubisTestBase;

public class TwoMusubisTest extends NMusubisTestBase {

	public TwoMusubisTest() {
		super(2);
	}
	@Override
	protected void setUp() throws Exception {
		super.setUp();
	}
	@Override
	protected void tearDown() throws Exception {
		super.tearDown();
	}
    public void testSendKnownPeopleKnownFeed() {
    	MFeed[] feeds = new MFeed[mContexts.length];
    	for(int i = 0; i < mContexts.length; ++i) {
    		for(int j = 0; j < mContexts.length; ++j) {
    			me[i][j].whitelisted_ = true;
    			me[i][j].claimed_ = true;
    		}
    		feeds[i] = fm[i].getOrCreateFixedFeed(me[i]);
        }        
        
        assertEquals(0, om[0].getTotalCountOfObjects());
        assertEquals(0, om[1].getTotalCountOfObjects());
        Obj obj = StatusObj.from("Hiya! Click me to open this feed!");
        insertObject(mContexts[0], im[0], dm[0], om[0], fm[0], am[0], feeds[0], me[0][0], obj);
        

        for(int i = 0; i < 150; ++i) {
			try {
				if(om[1].getTotalCountOfObjects() == 1)
					break;
				Thread.sleep(100);
			} catch (InterruptedException e) {}
		}
        assertEquals(1, om[0].getTotalCountOfObjects());
        assertEquals(1, om[1].getTotalCountOfObjects());

        MObject obj1 = om[0].getObjectForId(1);
        MObject obj2 = om[1].getObjectForId(1);
        assertNotNull(obj1);
        assertNotNull(obj2);
        assertEquals(obj1.type_, obj2.type_);

        MDevice d1 = dm[0].getDeviceForId(obj1.deviceId_);
        MDevice d2 = dm[1].getDeviceForId(obj2.deviceId_);
        assertEquals(d1.deviceName_, d2.deviceName_);
        MIdentity i1 = im[0].getIdentityForId(obj1.identityId_);
        MIdentity i2 = im[1].getIdentityForId(obj2.identityId_);
        assertNotSame(0, i1.principalShortHash_);
        assertEquals(i1.principalShortHash_, i2.principalShortHash_);
        assertEquals(i1.type_.ordinal(), i2.type_.ordinal());
        assertNotNull(obj1.universalHash_);
        assertNotNull(obj2.universalHash_);
        String hash1 = Util.convertToHex(obj1.universalHash_);
        String hash2 = Util.convertToHex(obj2.universalHash_);
        assertEquals(hash1, hash2);
    }


    public void testSendKnownPeopleUnknownFeed() {
    	MFeed[] feeds = new MFeed[mContexts.length];
    	for(int i = 0; i < mContexts.length; ++i) {
    		for(int j = 0; j < mContexts.length; ++j) {
    			me[i][j].whitelisted_ = true;
    			me[i][j].claimed_ = true;
    		}
        }        
		feeds[0] = fm[0].getOrCreateFixedFeed(me[0]);
        
        assertEquals(0, om[0].getTotalCountOfObjects());
        assertEquals(0, om[1].getTotalCountOfObjects());
        Obj obj = StatusObj.from("Hiya! Click me to open this feed!");
        insertObject(mContexts[0], im[0], dm[0], om[0], fm[0], am[0], feeds[0], me[0][0], obj);
        

        for(int i = 0; i < 150; ++i) {
			try {
				if(om[1].getTotalCountOfObjects() == 1)
					break;
				Thread.sleep(100);
			} catch (InterruptedException e) {}
		}
        assertEquals(1, om[0].getTotalCountOfObjects());
        assertEquals(1, om[1].getTotalCountOfObjects());
    }

    public void testSendUnknownPeopleUnknownFeed() {
    	MFeed[] feeds = new MFeed[mContexts.length];
    	for(int i = 0; i < mContexts.length; ++i) {
    		for(int j = 0; j < mContexts.length; ++j) {
    			me[i][j].whitelisted_ = false;
    			me[i][j].claimed_ = false;
    		}
        }        
		feeds[0] = fm[0].getOrCreateFixedFeed(me[0]);
        
        assertEquals(0, om[0].getTotalCountOfObjects());
        assertEquals(0, om[1].getTotalCountOfObjects());
        Obj obj = StatusObj.from("Hiya! Click me to open this feed!");
        insertObject(mContexts[0], im[0], dm[0], om[0], fm[0], am[0], feeds[0], me[0][0], obj);
        

        for(int i = 0; i < 150; ++i) {
			try {
				if(om[1].getTotalCountOfObjects() == 1)
					break;
				Thread.sleep(100);
			} catch (InterruptedException e) {}
		}
        assertEquals(1, om[0].getTotalCountOfObjects());
        assertEquals(1, om[1].getTotalCountOfObjects());
        //TODO: check that is isn't processed?
    }

    public void testSend10Feed() {
    	MFeed[] feeds = new MFeed[mContexts.length];
    	for(int i = 0; i < mContexts.length; ++i) {
    		for(int j = 0; j < mContexts.length; ++j) {
    			me[i][j].whitelisted_ = true;
    			me[i][j].claimed_ = true;
    		}
    		feeds[i] = fm[i].getOrCreateFixedFeed(me[i]);
        }        
        
        assertEquals(0, om[0].getTotalCountOfObjects());
        assertEquals(0, om[1].getTotalCountOfObjects());
        for(int i = 0; i < 10; ++i) {
	        Obj obj = StatusObj.from("Hiya!" + i);
	        insertObject(mContexts[0], im[0], dm[0], om[0], fm[0], am[0], feeds[0], me[0][0], obj);
        }
        assertTrue(om[1].getTotalCountOfObjects() < 10);

        for(int i = 0; i < 250; ++i) {
			try {
				if(om[1].getTotalCountOfObjects() == 10)
					break;
				Thread.sleep(100);
			} catch (InterruptedException e) {}
		}
        assertEquals(10, om[0].getTotalCountOfObjects());
        assertEquals(10, om[1].getTotalCountOfObjects());
    }
}
