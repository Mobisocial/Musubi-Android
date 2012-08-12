/*
 * Copyright 2012 The Stanford MobiSocial Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mobisocial.musubi.service;

import java.io.ByteArrayOutputStream;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import mobisocial.crypto.IBIdentity;
import mobisocial.metrics.MusubiMetrics;
import mobisocial.musubi.App;
import mobisocial.musubi.R;
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
import mobisocial.musubi.objects.MusubiWizardObj;
import mobisocial.musubi.objects.PictureObj;
import mobisocial.musubi.objects.StatusObj;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.provider.MusubiContentProvider.Provided;
import mobisocial.musubi.ui.util.UiUtil;
import mobisocial.musubi.util.Util;
import mobisocial.socialkit.Obj;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HandlerThread;
import android.widget.Toast;

/**
 * Scans the list of identities for entries that need this user's latest
 * profile.
 */
public class WizardStepHandler extends ContentObserver {
    private final Context mContext;
    private final SQLiteOpenHelper mHelper;
	HandlerThread mThread;
    
	public final static String TABLE = "wizard_step";
	public final static String COL_ID = "_id";
	public final static String COL_CURRENT_STEP = "current_step";
	
    public final static String LAST_TASK_COMPLETED = "last_task_completed";
    public final static String CURRENT_TASK = "current_task";
    public final static int NO_TASK = -1;

    public final static String PROFILE_PICTURE_SET = "profile_picture_set";
    public final static String PROFILE_NAME_SET = "profile_name_set";
    public final static String WIZARD_PREFS_NAME = "wizard_prefs";
    
    public final static String TAG = "WizardStepHandler";
    
    public final static int TASK_OPEN_FEED = 0;
    public final static int TASK_TAKE_PICTURE = 1;
    public final static int TASK_EDIT_PICTURE = 2;
    public final static int TASK_SET_PROFILE = 3;
    public final static int TASK_LINK_ACCOUNT = 4;
    public final static int TASK_MESSAGE_FRIENDS = 5;
    
    public final static int TASK_SET_PROFILE_PICTURE = -10;
    public final static int TASK_SET_PROFILE_NAME = -11;


	public static final String DO_RESTORE = "do_restore";
	public static final boolean DO_RESTORE_DEFAULT = false;
    
    /**
     * Point at which we consider the Musubi wizard complete, allowing the user
     * normal interactivity.
     */
    public final static int TASK_WIZARD_COMPLETE = TASK_LINK_ACCOUNT + 1;

    public static WizardStepHandler newInstance(Context context, SQLiteOpenHelper dbh) {
        HandlerThread thread = new HandlerThread("WizardingThread");
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
        return new WizardStepHandler(context, dbh, thread);
    }

    private WizardStepHandler(Context context, SQLiteOpenHelper dbh, HandlerThread thread) {
        super(new Handler(thread.getLooper()));
        mThread = thread;
        mContext = context;
        mHelper = dbh;

        SharedPreferences p = context.getSharedPreferences(WIZARD_PREFS_NAME, 0);
        if (p.getInt(CURRENT_TASK, NO_TASK) > 0) {
            // lets the bootstrap activity know we've at least inserted the first wizard message.
            mContext.getContentResolver().notifyChange(MusubiService.WIZARD_READY, null);
        }
    }
    
    public static void restoreStepsFromDatabase(Context context) {
    	SQLiteDatabase db = App.getDatabaseSource(context).getWritableDatabase();
    	Cursor c = db.query(TABLE, new String[]{COL_CURRENT_STEP}, null, null, null, null, null);
    	c.moveToFirst();
    	int currentTask = c.getInt(0);
    	int lastTask = currentTask - 1;
    	c.close();
    	
    	SharedPreferences p = context.getSharedPreferences(WIZARD_PREFS_NAME, 0);
    	SharedPreferences.Editor editor = p.edit();
		editor = p.edit();
		editor.putInt(CURRENT_TASK, currentTask);
		editor.putInt(LAST_TASK_COMPLETED, lastTask);
    	editor.putBoolean(WizardStepHandler.DO_RESTORE, false);
        editor.commit();
    }

    public static boolean isWizardComplete(Context context) {
        SharedPreferences p = context.getSharedPreferences(WIZARD_PREFS_NAME, 0);
        return TASK_WIZARD_COMPLETE == p.getInt(CURRENT_TASK, NO_TASK);
    }

    public static boolean isCurrentTask(Context context, int task) {
    	SharedPreferences p = context.getSharedPreferences(WIZARD_PREFS_NAME, 0);
		return task == p.getInt(CURRENT_TASK, NO_TASK);
    }
    
    public static void accomplishTask(Context context, int task) {
    	if (WizardStepHandler.isCurrentTask(context, task)) {
        	WizardStepHandler.nextTask(context);
        	App.getUsageMetrics(context).report(MusubiMetrics.WIZARD_ACCOMPLISH_TASK, ""+task);
        } else if (task == TASK_SET_PROFILE_PICTURE) {	
    		if (WizardStepHandler.isCurrentTask(context, TASK_SET_PROFILE)) {
    			setProfilePicture(context);
    		} else {
    			SharedPreferences p = context.getSharedPreferences(WIZARD_PREFS_NAME, 0);
    	    	SharedPreferences.Editor editor = p.edit();
    	    	editor.putBoolean(PROFILE_PICTURE_SET, true);
    	    	editor.commit();
    		}
    	} else if (task == TASK_SET_PROFILE_NAME) {
    		if(WizardStepHandler.isCurrentTask(context, TASK_SET_PROFILE)) {
    			setProfileName(context);
    		} else {
    			SharedPreferences p = context.getSharedPreferences(WIZARD_PREFS_NAME, 0);
    	    	SharedPreferences.Editor editor = p.edit();
    	    	editor.putBoolean(PROFILE_NAME_SET, true);
    	    	editor.commit();
    		}
    	}
    }
    
    static void setProfilePicture(Context context) {
    	SharedPreferences p = context.getSharedPreferences(WIZARD_PREFS_NAME, 0);
    	SharedPreferences.Editor editor = p.edit();
    	editor.putBoolean(PROFILE_PICTURE_SET, true);
    	editor.commit();

		insertMusubiMessage(context, StatusObj.from("Looking good!"));    
		if (p.getBoolean(PROFILE_NAME_SET, false)) {
    		nextTask(context);
			Toast.makeText(context, "Alright, now that your profile has been set go back to the feed for more instructions!", Toast.LENGTH_LONG).show();
    	}
		else {
			Toast.makeText(context, "Becky: Make sure to set your name too!", Toast.LENGTH_LONG).show();
			insertMusubiMessage(context, 
                    StatusObj.from("Make sure to set your name too!"));
		}
    }
    
    static void setProfileName(Context context) {
    	SharedPreferences p = context.getSharedPreferences(WIZARD_PREFS_NAME, 0);
    	SharedPreferences.Editor editor = p.edit();
    	editor.putBoolean(PROFILE_NAME_SET, true);
    	editor.commit();
    	
    	SQLiteOpenHelper helper = App.getDatabaseSource(context);
		IdentitiesManager identitiesManager = new IdentitiesManager(helper);
		MIdentity self = identitiesManager.getOwnedIdentities().get(0);
		String name = UiUtil.safeNameForIdentity(self);
		insertMusubiMessage(context, StatusObj.from("Nice to meet you, " + name + "!"));
    	
    	if (p.getBoolean(PROFILE_PICTURE_SET, false)) {
    		nextTask(context);
			Toast.makeText(context, "Alright, now that your profile has been set go back to the feed for more instructions!", Toast.LENGTH_SHORT).show();
    	}
		else {
			Toast.makeText(context, "Becky: Make sure to set your picture too!", Toast.LENGTH_SHORT).show();
			insertMusubiMessage(context, 
                    StatusObj.from("Make sure to set your picture too!"));
		}
    }
    
    public static int getCurrentTask(Context context) {
    	SharedPreferences p = context.getSharedPreferences(WIZARD_PREFS_NAME, 0);
		return p.getInt(CURRENT_TASK, NO_TASK);
    }
    
    static int getLastTaskCompleted(Context context) {
    	SharedPreferences p = context.getSharedPreferences(WIZARD_PREFS_NAME, 0);
		return p.getInt(LAST_TASK_COMPLETED, NO_TASK);
    }
    
    static void nextTask(Context context) {
    	SharedPreferences p = context.getSharedPreferences(WIZARD_PREFS_NAME, 0);
    	int currentTask = p.getInt(CURRENT_TASK, NO_TASK);
		SharedPreferences.Editor editor = p.edit();
		editor = p.edit();
		editor.putInt(LAST_TASK_COMPLETED, currentTask);
        editor.commit();

        SQLiteDatabase db = App.getDatabaseSource(context).getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(WizardStepHandler.COL_CURRENT_STEP, currentTask+1);
        db.update(TABLE, cv, null, null);
        
		context.getContentResolver().notifyChange(MusubiService.WIZARD_STEP_TAKEN, null);
    }

    /**
     * Adds a message to the Wizard feed from the Musubi pseudo-user.
     */
    static void insertMusubiMessage(Context context, Obj obj) {
		SQLiteOpenHelper helper = App.getDatabaseSource(context);
		ObjectManager manager = new ObjectManager(helper);
		
		IdentitiesManager identitiesManager = new IdentitiesManager(helper);
		DeviceManager deviceManager = new DeviceManager(helper);

		MApp superApp = new AppManager(helper).ensureApp(MusubiContentProvider.SUPER_APP_ID);
		MObject o = new MObject();
        o.feedId_ = MFeed.WIZ_FEED_ID;
        o.identityId_ = identitiesManager.getIdForIBHashedIdentity(IdentitiesManager.getPreInstallMusubiIdentity());
        o.appId_ = superApp.id_;
        o.timestamp_ = new Date().getTime();
        ObjEncoder.populate(o, obj);
        
        MIdentity self = identitiesManager.getOwnedIdentities().get(0);

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
        
		manager.insertObject(o);
		context.getContentResolver().notifyChange(MusubiService.PLAIN_OBJ_READY, null);
    }
    
    @Override
    public void onChange(boolean selfChange) {
    	SharedPreferences p = mContext.getSharedPreferences(WIZARD_PREFS_NAME, 0);
		int lastTask = p.getInt(LAST_TASK_COMPLETED, NO_TASK);
		int currentTask = p.getInt(CURRENT_TASK, NO_TASK);

		if(lastTask == currentTask) {
			currentTask++;
			SharedPreferences.Editor editor = p.edit();
			editor.putInt(CURRENT_TASK, currentTask);
            editor.commit();
			switch(currentTask) {
				case TASK_OPEN_FEED:
				    doTaskOpenFeed();
					break;
				case TASK_TAKE_PICTURE:
				    doTaskTakePicture();
		            break;
				case TASK_EDIT_PICTURE:
					doTaskEditPicture();
					break;
				case TASK_SET_PROFILE:
					doTaskSetProfile();
		            break;
				case TASK_LINK_ACCOUNT:
					doTaskLinkAccount();
					break;
				case TASK_MESSAGE_FRIENDS:
					doTaskMessageFriends();
					break;
			}
		}
    }

    void doTaskOpenFeed() {
        IdentitiesManager identitiesManager = new IdentitiesManager(mHelper);
        FeedManager feedManager = new FeedManager(mHelper);
        assert(feedManager.lookupFeed(MFeed.WIZ_FEED_ID) != null);

        IBIdentity musubi_id = IdentitiesManager.getPreInstallMusubiIdentity();
        //don't try to insert a duplicate becky and first becky message even if the wizard
        //step information is lost, (e.g. in a clear data and restore)
        //TODO: move the wizard step state into the database so it is backed up and restored
        //as well.
        if(identitiesManager.getIdentityForIBHashedIdentity(musubi_id) != null)
        	return;

    	MIdentity musubiId = new MIdentity();
        musubiId.claimed_ = true;
        musubiId.owned_ = false;
        musubiId.principal_ = musubi_id.principal_;
        musubiId.principalHash_ = musubi_id.hashed_;
        musubiId.principalShortHash_ = Util.shortHash(musubi_id.hashed_);
        musubiId.type_ = musubi_id.authority_;
        musubiId.name_ = "Becky";
        musubiId.musubiName_ = musubiId.name_;
        musubiId.receivedProfileVersion_ = 1L;
        musubiId.sentProfileVersion_ = 1L;
        musubiId.hasSentEmail_ = true;

        Bitmap icon = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.becky);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        icon.compress(Bitmap.CompressFormat.PNG, 100, baos);
        byte[] thumbnail = baos.toByteArray();
        musubiId.thumbnail_ = thumbnail;
        musubiId.musubiThumbnail_ = thumbnail;
        identitiesManager.insertIdentity(musubiId);

        feedManager.ensureFeedMember(MFeed.WIZ_FEED_ID, musubiId.id_);
        
        
        MIdentity myId =  identitiesManager.getIdentityForIBHashedIdentity(IdentitiesManager.getPreInstallIdentity());
        //byte[] capability = FeedManager.computeFixedIdentifier(myId, musubiId);

        feedManager.ensureFeedMember(MFeed.WIZ_FEED_ID, myId.id_);

        insertMusubiMessage(mContext, StatusObj.from("Hi, I am Becky, a member on the Musubi team. I'm here to help you learn about Musubi!  Musubi lets you message and interact with groups of friends freely.  Click this message to open this feed!"));
        mContext.getContentResolver().notifyChange(MusubiContentProvider.uriForDir(Provided.FEEDS), null);
        mContext.getContentResolver().notifyChange(MusubiService.WIZARD_READY, null);
    }

    void doTaskTakePicture() {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            public void run() {
                insertMusubiMessage(mContext,
                        StatusObj.from("Musubi was developed by the MobiSocial Lab at Stanford University to help users connect with their friends while preserving their privacy. " +
                        		"You can share all kinds of things with your friends -- Here, let me share a picture of my cat with you!"));
            }
        }, 500);
        
        
        timer.schedule(new TimerTask() {
            public void run() {
                Bitmap icon = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.cat);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                icon.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                insertMusubiMessage(mContext,
                        PictureObj.from(baos.toByteArray()));
            }
        }, 6800);
        
        
        timer.schedule(new TimerTask() {
            public void run() {
                insertMusubiMessage(mContext, 
                        StatusObj.from("Why don't you take a picture of something too? To take a picture, tap the pin by the input box and click the camera button."));
            }
        }, 9500);
    }

	private boolean isAppInstalled(String uri) {
		PackageManager pm = mContext.getPackageManager();
		boolean installed = false;
		try {
			pm.getPackageInfo(uri, PackageManager.GET_ACTIVITIES);
			installed = true;
		} catch (PackageManager.NameNotFoundException e) {
			installed = false;
		}
		return installed;
	}
    
    void doTaskEditPicture() {
        Timer timer = new Timer();

        timer.schedule(new TimerTask() {
            public void run() {
                insertMusubiMessage(mContext,
                        StatusObj.from("I love touching up the photos I take, just like this..."));
            }
        }, 4000);
        
        
        timer.schedule(new TimerTask() {
            public void run() {
                Bitmap icon = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.sketch_cat);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                icon.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                insertMusubiMessage(mContext,
                        PictureObj.from(baos.toByteArray()));
            }
        }, 9000);

        
        
        timer.schedule(new TimerTask() {
            public void run() {
                insertMusubiMessage(mContext,
                        StatusObj.from("Let's edit your picture from before. -- just long press on the picture, click edit, and select Sketch. When you're done, all you have to do is press the post button!"));
            }
        }, 11000);
                
    }

    void doTaskSetProfile() {
        Timer timer = new Timer();
        

        timer.schedule(new TimerTask() {
            public void run() {
                insertMusubiMessage(mContext,
                        StatusObj.from("Neat! You can edit pictures with other apps such as PicSay too!"));
            }
        }, 3500);
        
        timer.schedule(new TimerTask() {
            public void run() {
                insertMusubiMessage(mContext,
                        MusubiWizardObj.from(MusubiWizardObj.WizardAction.PICSAY));
            }
        }, 8000);
        
        SharedPreferences p = mContext.getSharedPreferences(WIZARD_PREFS_NAME, 0);
    	if (p.getBoolean(PROFILE_NAME_SET, false) && p.getBoolean(PROFILE_PICTURE_SET, false)) {
  //  		timer.schedule(new TimerTask() {
  //              public void run() {
  //                  insertMusubiMessage(mContext,
  //                          StatusObj.from("Great! I see you've already set your profile!"));
  //
  //              }
  //         }, 3500);
    		nextTask(mContext);
    	}
    	else {
	        timer.schedule(new TimerTask() {
	            public void run() {
	                insertMusubiMessage(mContext,
	                        StatusObj.from("Moving on! To share with your friends, you first have to set your profile."));
	            }
	        }, 13500);
	        
	        timer.schedule(new TimerTask() {
	            public void run() {
	                insertMusubiMessage(mContext,
	                        MusubiWizardObj.from(MusubiWizardObj.WizardAction.PROFILE));
	            }
	        }, 15000);
    	}
    }

    void doTaskLinkAccount() {
        Timer timer = new Timer();
        IdentitiesManager manager = new IdentitiesManager(mHelper);
        if (manager.getOwnedIdentities().size() > 1) {
 //       	timer.schedule(new TimerTask() {
 //	            public void run() {
 //	                insertMusubiMessage(mContext,
 //	                        StatusObj.from("Awesome! Now you're ready to find your friends!"));
 //	            }
 //	        }, 1000);
 //       	
 //       	timer.schedule(new TimerTask() {
 //	            public void run() {
 //	                insertMusubiMessage(mContext,
 //	                        StatusObj.from("I see you've already linked an account, that's great!"));
 //	                nextTask(mContext);
 //	            }
 //	        }, 3000);
	        nextTask(mContext);
        }
        else {
	        timer.schedule(new TimerTask() {
	            public void run() {
	                insertMusubiMessage(mContext,
	                        StatusObj.from("Awesome! Now you're ready to find your friends!"));
	            }
	        }, 1000);
	        
	        timer.schedule(new TimerTask() {
	            public void run() {
	                insertMusubiMessage(mContext,
	                        MusubiWizardObj.from(MusubiWizardObj.WizardAction.ACCOUNT));
	            }
	        }, 2000);
        }
    }
    
    void doTaskMessageFriends() {
        Timer timer = new Timer();
        Toast.makeText(mContext, "Alright, now that you've linked your account, go back to the feed for more instructions!", Toast.LENGTH_LONG).show();
/*   	
        timer.schedule(new TimerTask() {
            public void run() {
                insertMusubiMessage(mContext, 
                        StatusObj.from("You can see a list of all of your friends on your Relationships page. You can get there by pressing on the button with 3 people on the home screen."));
            }
        }, 1000);

        timer.schedule(new TimerTask() {
            public void run() {
                insertMusubiMessage(mContext, 
                        StatusObj.from("People who show up grayed out are your friends that might not be on Musubi yet. You can still send the messages, but they might not immediately receive them."));
            }
        }, 6500);
*/
        timer.schedule(new TimerTask() {
            public void run() {
                insertMusubiMessage(mContext, 
                        StatusObj.from("To start a new feed, from the homescreen start typing the names of your friends into the input box.  That's all the advice I have for you, good luck and have fun!"));
            }
        }, 3000);
/*
        timer.schedule(new TimerTask() {
            public void run() {
                insertMusubiMessage(mContext, 
                        StatusObj.from("If your friends don't appear, you can add them to your address book and Musubi will automatically add them to your friends list."));
            }
        }, 16000);

        timer.schedule(new TimerTask() {
            public void run() {
                insertMusubiMessage(mContext, 
                        StatusObj.from("That's all the advice I have for you, good luck and have fun!"));
            }
        }, 21000);
 */
    }

    void doTaskDownloadWordplay() {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            public void run() {
                insertMusubiMessage(mContext, 
                        StatusObj.from("Alright! Now one last thing. You'll probably want to play games with your friends."));
            }
        }, 1000);
        
        timer.schedule(new TimerTask() {
            public void run() {
                insertMusubiMessage(mContext,
                        MusubiWizardObj.from(MusubiWizardObj.WizardAction.PICSAY));
            }
        }, 2000);
    }
}
