
package mobisocial.musubi.model.helpers;

import mobisocial.musubi.model.MMyAccount;
import mobisocial.test.TestBase;
import mobisocial.test.TestDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class MyAccountManagerTest extends TestBase {
    TestDatabase database;

    SQLiteOpenHelper dbh;

    @Override
    public void setUp() {
        dbh = new DatabaseFile(getContext(), null);
        database = new TestDatabase(getContext(), dbh);
    }

    public void testNullableFields() {
        String accountName = "bojangles";
        String accountType = "google";
        long identityId = 17, feedId = 148;
        MyAccountManager am = new MyAccountManager(dbh);

        MMyAccount account = new MMyAccount();
        account.accountName_ = accountName;
        account.accountType_ = accountType;
        account.id_ = -1;
        am.insertAccount(account);

        assertNotSame(-1, account.id_);
        assertEquals(accountName, account.accountName_);
        assertEquals(accountType, account.accountType_);
        assertNull(account.identityId_);
        assertNull(account.feedId_);

        MMyAccount[] accounts = am.getMyAccounts();
        assertEquals(2, accounts.length); // this one and a whitelist account in DatabaseFile
        assertNull(accounts[1].identityId_);
        assertNull(accounts[1].feedId_);

        MMyAccount lookup = am.lookupAccount(accountName, accountType);
        assertNotNull(lookup);
        assertEquals(accountName, lookup.accountName_);
        assertEquals(accountType, lookup.accountType_);
        assertNull(lookup.identityId_);
        assertNull(lookup.feedId_);

        lookup.identityId_ = identityId;
        lookup.feedId_ = feedId;
        am.updateAccount(lookup);

        MMyAccount lookup2 = am.lookupAccount(accountName, accountType);
        assertEquals((Long)identityId, lookup2.identityId_);
        assertEquals((Long)feedId, lookup2.feedId_);
    }
}
