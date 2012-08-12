package mobisocial.musubi.service;

import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MObject;
import mobisocial.musubi.objects.StatusObj;
import mobisocial.socialkit.Obj;
import mobisocial.socialkit.obj.MemObj;
import mobisocial.test.MockMusubiAppContext;
import mobisocial.test.TestBase;
import mobisocial.test.TestDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class ObjPipelineHandlerTest extends TestBase {
    MIdentity me;
    MIdentity claimedFriend;
    MIdentity unclaimedFriend;
    SQLiteOpenHelper dbh;
    MockMusubiAppContext mockContext;
    
    TestDatabase database;

    public void setUp() throws Exception {
    	mockContext = new MockMusubiAppContext(getContext());
        dbh = mockContext.getDatabaseSource();
        database = new TestDatabase(mockContext, dbh);

        me = database.insertIdentity(randomIBIdentity(), true, true);
        claimedFriend = database.insertIdentity(randomIBIdentity(), false, true);
        unclaimedFriend = database.insertIdentity(randomIBIdentity(), false, false);
    }
    public void tearDown() {
    	dbh.close();
    }

    public void testRenderablePipeline() throws Exception {
        ObjPipelineProcessor pipeline = ObjPipelineProcessor.newInstance(mockContext);
        MessageEncodeProcessor encoder = MessageEncodeProcessor.newInstance(mockContext, dbh, null, mockContext.getSettings().mAlternateIdentityProvider);

        MFeed feed = database.createFixedFeed(me, claimedFriend, unclaimedFriend);
        MObject send1 = database.insertObject(feed, me, StatusObj.from("monkey business"));
        Obj junk = new MemObj("junk", null, null, 2414455, "yea yeaaa");
        MObject send2 = database.insertObject(feed, claimedFriend, StatusObj.from("monkey 2 business"));
        MObject send3 = database.insertObject(feed, me, junk);

        MObject tmp = database.getObjectManager().getObjectForId(send1.id_);
        assertEquals(tmp.type_, "status");
        tmp = database.getObjectManager().getObjectForId(send2.id_);
        assertEquals(tmp.type_, "status");
        tmp = database.getObjectManager().getObjectForId(send3.id_);
        assertEquals(tmp.type_, "junk");
        
        //nothing is renderable until it has been processed
        assertFalse(send1.renderable_);
        assertFalse(send2.renderable_);
        assertFalse(send3.renderable_);

        int assertedLength = 3;
        long[] ids;
        encoder.onChange(false);
        for (int i = 0; i < 150; i++) {
            ids = pipeline.getUnprocessedObjs();
            if (ids.length == assertedLength) {
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {}
        }
        ids = pipeline.getUnprocessedObjs();
        assertEquals("Timing issue likely detected.", assertedLength, ids.length);
        pipeline.onChange(false);

        send1 = database.getObjectManager().getObjectForId(send1.id_);
        send2 = database.getObjectManager().getObjectForId(send2.id_);
        send3 = database.getObjectManager().getObjectForId(send3.id_);

        assertTrue(send1.processed_);
        assertTrue(send2.processed_);
        assertTrue(send3.processed_);

        assertEquals("status", send1.type_);
        assertEquals("status", send2.type_);
        assertEquals("junk", send3.type_);

        // First object was renderable, not the second.
        assertTrue(send1.renderable_);
        assertTrue(send2.renderable_);
        assertFalse(send3.renderable_);

        // Make sure the parent feed knows about this renderable.
        feed = database.getFeedManager().lookupFeed(feed.id_);
        assertEquals((Long)send2.id_, feed.latestRenderableObjId_);
        //must have a timestamp
        assertTrue(send2.timestamp_ > 0);
        //timestamp for feed must be greater than or equal to sending time
        assertTrue(send2.timestamp_ <= feed.latestRenderableObjTime_);
        //normally we record the receive time in last modified timestamp
        //but we havent run the network obj processors...
        assertTrue(send2.lastModifiedTimestamp_ == 0);
        
        assertEquals(1L, feed.numUnread_);

        // All objects should be processed.
        ids = pipeline.getUnprocessedObjs();
        assertEquals(ids.length, 0);
    }
}
