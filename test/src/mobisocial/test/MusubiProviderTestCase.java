package mobisocial.test;

import android.content.ContentProvider;
import android.os.Build;
import android.test.AndroidTestCase;
import android.test.mock.MockContentResolver;


public abstract class MusubiProviderTestCase<T extends ContentProvider> extends AndroidTestCase {
    Class<T> mProviderClass;
    String mProviderAuthority;

    private MockMusubiAppContext mProviderContext;
    private MockContentResolver mResolver;

    public MusubiProviderTestCase(Class<T> providerClass, String providerAuthority) {
        mProviderClass = providerClass;
        mProviderAuthority = providerAuthority;
    }

    private T mProvider;
    public T getProvider() {
        return mProvider;
    }
    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mResolver = new MockContentResolver();
        mProviderContext = new MockMusubiAppContext(getContext());

        mProvider = mProviderClass.newInstance();
        mProvider.attachInfo(mProviderContext, null);
        assertNotNull(mProvider);
        mResolver.addProvider(mProviderAuthority, getProvider());
    }
    @Override
    protected void tearDown() throws Exception {
    	if(Build.VERSION.SDK_INT >= 11) {
    		//mProvider.shutdown(); 
    	}
        super.tearDown();
    }
    public MockContentResolver getMockContentResolver() {
        return mResolver;
    }
    public MockMusubiAppContext getMockContext() {
        return mProviderContext;
    }
}
  