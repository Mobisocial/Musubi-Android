package mobisocial.test;

import java.lang.reflect.Method;
import java.util.Random;

import android.app.Application;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;


public abstract class NServicesTestCase extends TestBase {
	Class<Service>[] mServiceClass;

    private Context mSystemContext;
    private Application mApplication;

    public NServicesTestCase(Class<Service>... serviceClass) {
        mServiceClass = serviceClass;
    }

    private Service[] mService;
    private boolean mServiceAttached = false;
    private boolean mServiceCreated = false;
    private boolean mServiceStarted = false;
    private boolean mServiceBound = false;
    private Intent[] mServiceIntent = null;
    private int mServiceId;

    public Service getService(int n) {
        return mService[n];
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // get the real context, before the individual tests have a chance to muck with it
        mSystemContext = getContext();

    }
    
    protected abstract Context getContextForService(int index, Service service);
    protected abstract Context getApplicationForService(int index, Service service);

    protected void setupService() {
    	mService = new Service[mServiceClass.length];
    	for(int i = 0; i < mService.length; ++i) {
	        try {
	            mService[i] = mServiceClass[i].newInstance();
	        } catch (Exception e) {
	            assertNotNull(mService[i]);
	        }
	        Method ms[] = mServiceClass[i].getMethods();
	        for(Method m : ms) {
	        	if(!m.getName().equals("attach"))
	        		continue;
	        	try {
					m.invoke(mService[i],
							getContextForService(i, mService[i]),
					        null,               // ActivityThread not actually used in Service
					        mServiceClass[i].getName(),
					        null,               // token not needed when not talking with the activity manager
					        getApplicationForService(i, mService[i]),
					        null                // mocked services don't talk with the activity manager
					        );
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
	        }	        
	        assertNotNull(mService[i]);
    	}
        
        mServiceId = new Random().nextInt();
        mServiceAttached = true;
    }
    
    /**
     * Start the service under test, in the same way as if it was started by
     * {@link android.content.Context#startService Context.startService()}, providing the 
     * arguments it supplied.  If you use this method to start the service, it will automatically
     * be stopped by {@link #tearDown}.
     *  
     * @param intent The Intent as if supplied to {@link android.content.Context#startService}.
     */
    protected void startService(Intent[] intent) {
        assertFalse(mServiceStarted);
        assertFalse(mServiceBound);
        
        if (!mServiceAttached) {
            setupService();
        }
        assertNotNull(mService);
        
        if (!mServiceCreated) {
        	for(Service s : mService)
        		s.onCreate();
            mServiceCreated = true;
        }
        for(int i = 0; i < mService.length; ++i)
    		mService[i].onStart(intent[i], mServiceId);
        
        mServiceStarted = true;
    }
    
    /**
     * Start the service under test, in the same way as if it was started by
     * {@link android.content.Context#bindService Context.bindService()}, providing the 
     * arguments it supplied.
     *  
     * Return the communication channel to the service.  May return null if 
     * clients can not bind to the service.  The returned
     * {@link android.os.IBinder} is usually for a complex interface
     * that has been <a href="{@docRoot}reference/aidl.html">described using
     * aidl</a>. 
     * 
     * Note:  In order to test with this interface, your service must implement a getService()
     * method, as shown in samples.ApiDemos.app.LocalService.

     * @param intent The Intent as if supplied to {@link android.content.Context#bindService}.
     * 
     * @return Return an IBinder for making further calls into the Service.
     */
    protected IBinder[] bindService(Intent[] intent) {
        assertFalse(mServiceStarted);
        assertFalse(mServiceBound);
        
        if (!mServiceAttached) {
            setupService();
        }
        assertNotNull(mService);
        
        if (!mServiceCreated) {
        	for(Service s : mService)
        		s.onCreate();
            mServiceCreated = true;
        }
        // no extras are expected by unbind
        IBinder[] result = new IBinder[mServiceClass.length];
        mServiceIntent = new Intent[mServiceClass.length];
        for(int i = 0; i < mService.length; ++i) {
            mServiceIntent[i] = intent[i].cloneFilter();
    		result[i] = mService[i].onBind(intent[i]);
        }
        
        mServiceBound = true;
        return result;
    }
    
    /**
     * This will make the necessary calls to stop (or unbind) the Service under test, and
     * call onDestroy().  Ordinarily this will be called automatically (by {@link #tearDown}, but
     * you can call it directly from your test in order to check for proper shutdown behaviors.
     */
    protected void shutdownService() {
        if (mServiceStarted) {
            for(Service s : mService)
            	s.stopSelf();
            mServiceStarted = false;
        } else if (mServiceBound) {
            for(int i = 0; i < mService.length; ++i) {
            	mService[i].onUnbind(mServiceIntent[i]);
            }
            mServiceBound = false;
        }
        if (mServiceCreated) {
        	for(Service s : mService)
            	s.onDestroy();
        }
    }
    
    /**
     * Shuts down the Service under test.  Also makes sure all resources are cleaned up and 
     * garbage collected before moving on to the next
     * test.  Subclasses that override this method should make sure they call super.tearDown()
     * at the end of the overriding method.
     * 
     * @throws Exception
     */
    @Override
    protected void tearDown() throws Exception {
        shutdownService();
        mService = null;

        // Scrub out members - protects against memory leaks in the case where someone 
        // creates a non-static inner class (thus referencing the test case) and gives it to
        // someone else to hold onto
        scrubClass(NServicesTestCase.class);

        super.tearDown();
    }
    
    /**
     * Set the application for use during the test.  If your test does not call this function,
     * a new {@link android.test.mock.MockApplication MockApplication} object will be generated.
     * 
     * @param application The Application object that will be injected into the Service under test.
     */
    public void setApplication(Application application) {
        mApplication = application;
    }

    /**
     * Return the Application object being used by the Service under test.
     * 
     * @return Returns the application object.
     * 
     * @see #setApplication
     */
    public Application getApplication() {
        return mApplication;
    }
    
    /**
     * Return a real (not mocked or instrumented) system Context that can be used when generating
     * Mock or other Context objects for your Service under test.
     * 
     * @return Returns a reference to a normal Context.
     */
    public Context getSystemContext() {
        return mSystemContext;
    }

    public void testServiceTestCaseSetUpProperly() throws Exception {
        setupService();
        assertNotNull("service should be launched successfully", mService);
    }
}
