package mobisocial.musubi.service;

import mobisocial.musubi.App;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.test.MockMusubiAppContext;
import mobisocial.test.TestBase;
import mobisocial.test.TestDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class WizardServiceTest extends TestBase {
    SQLiteOpenHelper dbh;
    TestDatabase database;
    WizardStepHandler wizard;

    public void setUp() throws Exception {
        super.setUp();
        setContext(new MockMusubiAppContext(getContext()));
        dbh = App.getDatabaseSource(getContext());
        database = new TestDatabase(getContext(), dbh);
        wizard = WizardStepHandler.newInstance(getContext(), dbh);
    }

    public void tearDown() {
    	dbh.close();
    }
    public void testOpenFeedTask() {
        IdentitiesManager im = database.getIdentityManager();
        MIdentity preinstall;
        MIdentity musippi = im.getIdentityForIBHashedIdentity(IdentitiesManager.getPreInstallMusubiIdentity());
        assertNull(musippi);

        wizard.doTaskOpenFeed();
        musippi = im.getIdentityForIBHashedIdentity(IdentitiesManager.getPreInstallMusubiIdentity());
        preinstall = im.getIdentityForIBHashedIdentity(IdentitiesManager.getPreInstallIdentity());
        assertNotNull(preinstall);
        assertNotNull(musippi);
        assertEquals(musippi.musubiName_, "Musubi");
        assertEquals(musippi.name_, "Musubi");
        assertNotNull(im.getThumbnail(musippi));
        assertNotNull(im.getMusubiThumbnail(musippi));
    }
}
