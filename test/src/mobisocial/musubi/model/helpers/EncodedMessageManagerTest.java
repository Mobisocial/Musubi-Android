package mobisocial.musubi.model.helpers;

import gnu.trove.list.linked.TLongLinkedList;
import gnu.trove.procedure.TLongProcedure;

import java.util.Arrays;
import java.util.Random;

import mobisocial.musubi.model.MEncodedMessage;
import mobisocial.musubi.util.Util;
import android.database.sqlite.SQLiteOpenHelper;
import android.test.AndroidTestCase;

public class EncodedMessageManagerTest extends AndroidTestCase {
	SQLiteOpenHelper mDbh;
	public void setUp() {
		 mDbh = new DatabaseFile(getContext(), null);
	}
	public void tearDown() {
		mDbh.close();
	}
	void assertEncodedMetadataEqual(MEncodedMessage a, MEncodedMessage b) {
		if(a == b)
			return;
		
		assertEquals(a.id_, b.id_);
		assertEquals(a.outbound_, b.outbound_);
		assertEquals(a.processed_, b.processed_);
		assertEquals(a.processedTime_, b.processedTime_);
		assertTrue(a.hash_ == b.hash_ || Arrays.equals(a.hash_, b.hash_));
		assertTrue(a.fromDevice_ == null && b.fromDevice_ == null || a.fromDevice_.equals(b.fromDevice_));
		assertTrue(a.fromIdentityId_ == null && b.fromIdentityId_ == null || a.fromIdentityId_.equals(b.fromIdentityId_));
		assertTrue(a.shortHash_ == null && b.shortHash_ == null || a.shortHash_.equals(b.shortHash_));
	}
	void assertEncodedEqual(MEncodedMessage a, MEncodedMessage b) {
		if(a == b)
			return;
		assertEncodedMetadataEqual(a, b);
	}	
	public void testBasicInsertAndGets() {
		EncodedMessageManager emm = new EncodedMessageManager(mDbh);

		MEncodedMessage a = new MEncodedMessage();
		a.encoded_ = new byte[8];
		new Random().nextBytes(a.encoded_);
		emm.insertEncoded(a);
		
		MEncodedMessage b = emm.lookupById(a.id_);
		assertEncodedEqual(a, b);

		b = emm.lookupMetadataById(a.id_);
		assertEncodedEqual(a, b);
	}
	public void testFullInsertAndGet() {
		EncodedMessageManager emm = new EncodedMessageManager(mDbh);
		Random r = new Random();
		MEncodedMessage a = new MEncodedMessage();
		a.encoded_ = new byte[8];
		r.nextBytes(a.encoded_);
		a.fromDevice_ = 10L;
		a.fromIdentityId_ = 3L;
		a.hash_ = Util.sha256(a.encoded_);
		a.outbound_ = true;
		a.processed_ = true;
		a.shortHash_ = Util.shortHash(a.hash_);
		a.processedTime_ = 11111111;
		emm.insertEncoded(a);
		
		MEncodedMessage b = emm.lookupById(a.id_);
		assertEncodedEqual(a, b);
	}
	public void testUpdateMetadata() {
		EncodedMessageManager emm = new EncodedMessageManager(mDbh);
		Random r = new Random();
		MEncodedMessage a = new MEncodedMessage();
		a.encoded_ = new byte[8];
		r.nextBytes(a.encoded_);
		a.fromDevice_ = 10L;
		a.fromIdentityId_ = 3L;
		a.hash_ = Util.sha256(a.encoded_);
		a.outbound_ = true;
		a.processed_ = true;
		a.processedTime_ = 11111111;
		a.shortHash_ = Util.shortHash(a.hash_);
		emm.insertEncoded(a);
		
		r.nextBytes(a.encoded_);
		a.fromDevice_ = null;
		a.fromIdentityId_ = null;
		a.hash_ = Util.sha256(a.encoded_);
		a.outbound_ = false;
		a.processed_ = false;
		a.processedTime_ = 2222222;
		a.shortHash_ = Util.shortHash(a.hash_);
		emm.updateEncodedMetadata(a);
		
		MEncodedMessage b = emm.lookupById(a.id_);
		assertEncodedMetadataEqual(a, b);
	}
	
	public void testOutboundIDs() {
		final EncodedMessageManager emm = new EncodedMessageManager(mDbh);
		Random r = new Random();
		final int TOTAL = 16;
		for(int i = 1; i < TOTAL; ++i) {
			MEncodedMessage a = new MEncodedMessage();
			a.encoded_ = new byte[8];
			r.nextBytes(a.encoded_);
			a.fromDevice_ = (long)i;
			a.fromIdentityId_ = (long)i;
			a.hash_ = Util.sha256(a.encoded_);
			a.outbound_ = (i & 1) != 0;
			a.processed_ = (i & 2) != 0;
			a.shortHash_ = Util.shortHash(a.hash_);
			emm.insertEncoded(a);
		}
		
		TLongLinkedList unsent = emm.getUnsentOutboundIdsNotPending();
		assertEquals(unsent.size(), TOTAL / 4);
		unsent.forEach(new TLongProcedure() {
			public boolean execute(long id) {
				MEncodedMessage b = emm.lookupById(id);
				assertNotNull(b);
				assertTrue(b.outbound_);
				assertFalse(b.processed_);
				return true;
			}
		});
	}
}
