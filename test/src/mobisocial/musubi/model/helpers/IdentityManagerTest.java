package mobisocial.musubi.model.helpers;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import mobisocial.crypto.IBHashedIdentity.Authority;
import mobisocial.crypto.IBIdentity;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.util.Util;
import mobisocial.test.TestBase;
import mobisocial.test.TestDatabase;

public class IdentityManagerTest extends TestBase {
	private DatabaseFile mDbh;
	public void setUp() {
		 mDbh = new DatabaseFile(getContext(), null);
	}
	public void tearDown() {
		mDbh.close();
	}
	void assertIdentitiesEqual(MIdentity a, MIdentity b) {
		if(a == b)
			return;
		
		assertEquals(a.id_, b.id_);
		assertEquals(a.blocked_, b.blocked_);
		assertEquals(a.claimed_, b.claimed_);
		assertEquals(a.receivedProfileVersion_, b.receivedProfileVersion_);
		assertEquals(a.sentProfileVersion_, b.sentProfileVersion_);
		assertEquals(a.name_, b.name_);
		assertEquals(a.nextSequenceNumber_, b.nextSequenceNumber_);
		assertEquals(a.owned_, b.owned_);
		assertEquals(a.type_, b.type_);
		assertEquals(a.principal_, b.principal_);
		assertTrue(a.principalHash_ == b.principalHash_ || Arrays.equals(a.principalHash_, b.principalHash_));
		assertEquals(a.principalShortHash_, b.principalShortHash_);
		assertTrue(a.contactId_ == null && b.contactId_ == null || a.contactId_.equals(b.contactId_));
		assertTrue(a.androidAggregatedContactId_ == null && b.androidAggregatedContactId_ == null || a.androidAggregatedContactId_.equals(b.androidAggregatedContactId_));
		assertTrue(a.updatedAt_ > 0 && a.createdAt_ > 0);
		assertTrue(a.createdAt_ <= a.updatedAt_);
		assertTrue(b.updatedAt_ > 0 && b.createdAt_ > 0);
		assertTrue(b.createdAt_ <= b.updatedAt_);
		assertEquals(a.whitelisted_, b.whitelisted_);
	}
	void assertIdentitiesEqualWithThumbnail(MIdentity a, MIdentity b) {
		assertIdentitiesEqual(a, b);
		assertTrue(a.thumbnail_ == b.thumbnail_ || Arrays.equals(a.thumbnail_, b.thumbnail_));
		assertTrue(a.musubiThumbnail_ == b.musubiThumbnail_ || Arrays.equals(a.musubiThumbnail_, b.musubiThumbnail_));
	}	
	public void testBasicInsertAndGet() throws Exception {
		IdentitiesManager idm = new IdentitiesManager(mDbh);

		IBIdentity ibid0 = new IBIdentity(Authority.Email, "a", 0);
		MIdentity id0 = new MIdentity();
		id0.name_ = "Test";
		id0.type_ = ibid0.authority_;
		id0.principal_ = ibid0.principal_;
		id0.principalHash_ = ibid0.hashed_;
		id0.principalShortHash_ = ByteBuffer.wrap(ibid0.hashed_).getLong();
		id0.owned_ = true;
		id0.claimed_ = true;
		id0.whitelisted_ = true;
		idm.insertIdentity(id0);
		
		assertEquals(idm.getIBIdentityForIBHashedIdentity(ibid0), ibid0);
		assertIdentitiesEqual(id0, idm.getIdentityForIBHashedIdentity(ibid0));		
		assertIdentitiesEqual(id0, idm.getIdentityForId(id0.id_));
		assertIdentitiesEqualWithThumbnail(id0, idm.getIdentityWithThumbnailsForId(id0.id_));
	}
	public void testBasicHashedInsertAndGet() throws Exception {
		IdentitiesManager idm = new IdentitiesManager(mDbh);

		IBIdentity ibid0 = new IBIdentity(Authority.Email, "a", 0);
		MIdentity id0 = new MIdentity();
		id0.name_ = "Test";
		id0.type_ = ibid0.authority_;
		id0.principalHash_ = ibid0.hashed_;
		id0.principalShortHash_ = ByteBuffer.wrap(ibid0.hashed_).getLong();
		id0.owned_ = false;
		id0.claimed_ = true;
		id0.whitelisted_ = true;
		idm.insertIdentity(id0);
				
		assertNull(idm.getIBIdentityForIBHashedIdentity(ibid0));
		assertIdentitiesEqual(id0, idm.getIdentityForIBHashedIdentity(ibid0));		
		assertIdentitiesEqual(id0, idm.getIdentityForId(id0.id_));
		assertIdentitiesEqualWithThumbnail(id0, idm.getIdentityWithThumbnailsForId(id0.id_));
	}
	public void testUpdate() throws Exception {
		IdentitiesManager idm = new IdentitiesManager(mDbh);
		
		IBIdentity ibid0 = new IBIdentity(Authority.Email, "a", 0);
		MIdentity id0 = new MIdentity();
		id0.name_ = "Test";
		id0.musubiName_ = "Trist";
		id0.type_ = ibid0.authority_;
		id0.principal_ = ibid0.principal_;
		id0.principalHash_ = ibid0.hashed_;
		id0.principalShortHash_ = ByteBuffer.wrap(ibid0.hashed_).getLong();
		id0.owned_ = true;
		id0.claimed_ = true;
		id0.whitelisted_ = true;
		idm.insertIdentity(id0);
		
		Thread.sleep(1000);
		
		//The authority and principalHash and createdTime should never change, but still...
		//i am testing the update function.
		IBIdentity ibid1 = new IBIdentity(Authority.Facebook, "b", 0);
		id0.name_ = null;
		id0.musubiName_ = null;
		id0.type_ = ibid1.authority_;
		id0.principal_ = ibid1.principal_;
		id0.principalHash_ = ibid1.hashed_;
		id0.principalShortHash_ = ByteBuffer.wrap(ibid1.hashed_).getLong();
		id0.owned_ = false;
		id0.claimed_ = false;
		id0.blocked_ = true;
		id0.androidAggregatedContactId_ = 7L;
		id0.contactId_ = 25L;
		id0.receivedProfileVersion_ = 82L;
		id0.sentProfileVersion_ = 91L;
		id0.nextSequenceNumber_ = 765L;
		id0.thumbnail_ = new byte[1];
		id0.hasSentEmail_ = true;
		id0.whitelisted_ = false;
		idm.updateIdentity(id0);
		
		MIdentity tmp = idm.getIdentityForIBHashedIdentity(ibid1);
		assertTrue(tmp.updatedAt_ > tmp.createdAt_);

		assertFalse(idm.getIBIdentityForIBHashedIdentity(ibid1).equals(ibid0));
		assertTrue(idm.getIBIdentityForIBHashedIdentity(ibid1).equals(ibid1));
		assertIdentitiesEqual(id0, idm.getIdentityForIBHashedIdentity(ibid1));		
		assertIdentitiesEqual(id0, idm.getIdentityForId(id0.id_));
		assertFalse(id0.equals(idm.getIdentityWithThumbnailsForId(id0.id_)));
		
		idm.updateThumbnail(id0);
		MIdentity test =  idm.getIdentityWithThumbnailsForId(id0.id_);
		idm.getThumbnail(test);
		assertIdentitiesEqualWithThumbnail(id0, test);

		id0.musubiThumbnail_ = new byte[2];
		idm.updateMusubiThumbnail(id0);
		test =  idm.getIdentityWithThumbnailsForId(id0.id_);
		idm.getMusubiThumbnail(test);
		assertIdentitiesEqualWithThumbnail(id0, test);
	}
	public void testNullableAndroidDataId() {
        TestDatabase database = new TestDatabase(getContext(), mDbh);

        MIdentity friend = database.insertIdentity(randomIBIdentity(), false, true);
        long id = friend.id_;

        friend.androidAggregatedContactId_ = 1L;
        database.getIdentityManager().updateIdentity(friend);
        MIdentity friend2 = database.getIdentityManager().getIdentityForId(id);
        assertEquals((Long)1L, friend2.androidAggregatedContactId_);

        friend2.androidAggregatedContactId_ = null;
        database.getIdentityManager().updateIdentity(friend2);
        friend = database.getIdentityManager().getIdentityForId(id);
        assertNull(friend.androidAggregatedContactId_);
        
	}
	public void testAuthorityPrincipalLookup() throws Exception {
		IdentitiesManager idm = new IdentitiesManager(mDbh);
		
		IBIdentity ibid0 = new IBIdentity(Authority.Email, "a", 0);
		MIdentity id0 = new MIdentity();
		id0.type_ = ibid0.authority_;
		id0.principal_ = ibid0.principal_;
		id0.principalHash_ = ibid0.hashed_;
		id0.principalShortHash_ = ByteBuffer.wrap(ibid0.hashed_).getLong();
		idm.insertIdentity(id0);
		
		IBIdentity ibid1 = new IBIdentity(Authority.Facebook, "a", 0);
		MIdentity id1 = new MIdentity();
		id1.type_ = ibid1.authority_;
		id1.principal_ = ibid1.principal_;
		id1.principalHash_ = ibid1.hashed_;
		id1.principalShortHash_ = ByteBuffer.wrap(ibid1.hashed_).getLong();
		idm.insertIdentity(id1);

		assertFalse(idm.getIBIdentityForIBHashedIdentity(ibid0).equals(ibid1));
		assertTrue(idm.getIBIdentityForIBHashedIdentity(ibid0).equals(ibid0));
		assertFalse(idm.getIBIdentityForIBHashedIdentity(ibid1).equals(ibid0));
		assertTrue(idm.getIBIdentityForIBHashedIdentity(ibid1).equals(ibid1));
	}
	public void testOwnedIdentities() throws Exception {
		IdentitiesManager idm = new IdentitiesManager(mDbh);

		IBIdentity ibid0 = new IBIdentity(Authority.Email, "a", 0);
		MIdentity id0 = new MIdentity();
		id0.name_ = "Test";
		id0.type_ = ibid0.authority_;
		id0.principal_ = ibid0.principal_;
		id0.principalHash_ = ibid0.hashed_;
		id0.principalShortHash_ = Util.shortHash(id0.principalHash_);
		id0.owned_ = false;
		id0.claimed_ = true;
		idm.insertIdentity(id0);
		assertIdentitiesEqual(id0, idm.getIdentityForIBHashedIdentity(ibid0));		
		
		List<MIdentity> owned = idm.getOwnedIdentities();
		assertEquals(1, owned.size());

		IBIdentity ibid1 = new IBIdentity(Authority.Email, "b", 0);
		MIdentity id1 = new MIdentity();
		id1.name_ = "Test";
		id1.type_ = ibid1.authority_;
		id1.principal_ = ibid1.principal_;
		id1.principalHash_ = ibid1.hashed_;
		id1.principalShortHash_ = Util.shortHash(ibid1.hashed_);
		id1.owned_ = true;
		id1.claimed_ = true;
		idm.insertIdentity(id1);
		assertIdentitiesEqual(id1, idm.getIdentityForIBHashedIdentity(ibid1));	
		
		owned = idm.getOwnedIdentities();
		assertEquals(2, owned.size());
		assertIdentitiesEqual(idm.getIdentityForIBHashedIdentity(ibid1), owned.get(1));
	}

	public void testDeviceNameExists() {
	    DeviceManager ddm = new DeviceManager(mDbh);
	    long deviceName = ddm.getLocalDeviceName();
	    assertTrue(deviceName != 0);
	}
	public void testTemporalFrame() {
		String principal = randomUniquePrincipal();
		int ok = 0;
		for(int i = 0; i < 3; ++i) {
			long a = IdentitiesManager.computeTemporalFrameFromPrincipal(principal);
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {}
			long b = IdentitiesManager.computeTemporalFrameFromPrincipal(principal);
			if(a == b)
				++ok;
		}
		//best two out of three in case test runs at the month boundary
		assertTrue(ok >= 2);
	}
}
