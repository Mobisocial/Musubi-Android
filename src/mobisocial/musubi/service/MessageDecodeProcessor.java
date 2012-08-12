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

import gnu.trove.procedure.TLongProcedure;
import gnu.trove.set.hash.TLongHashSet;

import java.util.Arrays;
import java.util.Date;

import mobisocial.musubi.App;
import mobisocial.musubi.R;
import mobisocial.musubi.encoding.DiscardMessage;
import mobisocial.musubi.encoding.IncomingMessage;
import mobisocial.musubi.encoding.MessageDecoder;
import mobisocial.musubi.encoding.NeedsKey;
import mobisocial.musubi.encoding.ObjEncoder;
import mobisocial.musubi.encoding.ObjFormat;
import mobisocial.musubi.encoding.TransportDataProvider;
import mobisocial.musubi.identity.IdentityProvider;
import mobisocial.musubi.identity.IdentityProviderException;
import mobisocial.musubi.model.MApp;
import mobisocial.musubi.model.MDevice;
import mobisocial.musubi.model.MEncodedMessage;
import mobisocial.musubi.model.MEncryptionUserKey;
import mobisocial.musubi.model.MFeed;
import mobisocial.musubi.model.MFeed.FeedType;
import mobisocial.musubi.model.MFeedMember;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.MMyAccount;
import mobisocial.musubi.model.MObject;
import mobisocial.musubi.model.helpers.AppManager;
import mobisocial.musubi.model.helpers.DeviceManager;
import mobisocial.musubi.model.helpers.EncodedMessageManager;
import mobisocial.musubi.model.helpers.FeedManager;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.model.helpers.MessageTransportManager;
import mobisocial.musubi.model.helpers.MyAccountManager;
import mobisocial.musubi.model.helpers.ObjectManager;
import mobisocial.musubi.model.helpers.UserKeyManager;
import mobisocial.musubi.objects.ProfileObj;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.provider.MusubiContentProvider.Provided;
import mobisocial.musubi.provider.TestSettingsProvider;
import mobisocial.musubi.util.IdentityCache;
import mobisocial.musubi.util.Util;
import mobisocial.socialkit.Obj;

import org.javatuples.Pair;
import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.ContactsContract;
import android.util.Log;

/**
 * Scans for inbound encoded objects that need to be decoded.
 * 
 * @see MusubiService
 * @see MessageEncodeProcessor
 * @see ObjPipelineProcessor
 */
public class MessageDecodeProcessor extends ContentObserver {
    private static boolean DBG = true;
	private final String TAG = getClass().getSimpleName();
    private MessageDecoder mMessageDecoder;
    private final Context mContext;
    private final SQLiteOpenHelper mDatabaseSource;
    private final AppManager mAppManager;
    private final ObjectManager mObjectManager;
    private final IdentitiesManager mIdentityManager;
    private final EncodedMessageManager mEncodedMessageManager;
	private final MyAccountManager mAccountManager;
    private final FeedManager mFeedManager;
	private final DeviceManager mDeviceManager;
	private final KeyUpdateHandler mKeyUpdateHandler;
    private boolean mSynchronousKeyFetch = false;
	private IdentityCache mContactThumbnailCache;
    final IdentityProvider mIdentityProvider;
	HandlerThread mThread;

    public static MessageDecodeProcessor newInstance(Context context, SQLiteOpenHelper dbh, KeyUpdateHandler keyUpdateService, IdentityProvider identityProvider) {
        HandlerThread thread = new HandlerThread("MessageDecodeThread");
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
        return new MessageDecodeProcessor(context, dbh, thread, keyUpdateService, identityProvider);
    }

    private MessageDecodeProcessor(Context context, SQLiteOpenHelper dbh, HandlerThread thread, KeyUpdateHandler keyUpdateService, IdentityProvider identityProvider) {
        super(new Handler(thread.getLooper()));
        mThread = thread;
        mContext = context;
        mDatabaseSource = dbh;
        mAppManager = new AppManager(mDatabaseSource);
        mFeedManager = new FeedManager(mDatabaseSource);
        mAccountManager = new MyAccountManager(mDatabaseSource);
        mEncodedMessageManager = new EncodedMessageManager(mDatabaseSource);
        mIdentityManager = new IdentitiesManager(mDatabaseSource);
        mObjectManager = new ObjectManager(mDatabaseSource);
        mDeviceManager = new DeviceManager(mDatabaseSource);
    	mContactThumbnailCache = App.getContactCache(context);
    	mIdentityProvider = identityProvider;

        TestSettingsProvider.Settings settings = App.getTestSettings(context);
        if(settings != null) {
        	mSynchronousKeyFetch = settings.mSynchronousKeyFetchInMessageEncodeDecode;
        }
        mKeyUpdateHandler = keyUpdateService;
        
        //do initialization that hits the database in the background thread
        new Handler(mThread.getLooper()).post(new Runnable() {
			@Override
			public void run() {
		        long myDevice = mDeviceManager.getLocalDeviceName();
		        TransportDataProvider tdp = new MessageTransportManager(
		                mDatabaseSource, mIdentityProvider.getEncryptionScheme(), mIdentityProvider.getSignatureScheme(), myDevice);
		        mMessageDecoder = new MessageDecoder(tdp);
			}
		});
    }

    @Override
    public void onChange(boolean selfChange) {
        if (DBG) Log.d(TAG, "MessageDecodeProcessor noticed change");
        SQLiteDatabase db = mDatabaseSource.getWritableDatabase();
        long[] ids = objsToDecode();
        if (ids.length == 0) {
            return;
        }

        TLongHashSet dirtyFeeds = new TLongHashSet();
        MessageDecoderProcedure decoder = new MessageDecoderProcedure(db, dirtyFeeds);
        for (long id : ids) {
        	decoder.execute(id);
        }

		final ContentResolver resolver = mContext.getContentResolver();
        if (decoder.mSomethingChanged) {
            resolver.notifyChange(MusubiService.APP_OBJ_READY, this);
            requestAddressBookSync();
        }
        if (decoder.mRunProfilePush) {
            resolver.notifyChange(MusubiService.PROFILE_SYNC_REQUESTED, this);
        }

        if (dirtyFeeds.size() > 0) {
            resolver.notifyChange(MusubiContentProvider.uriForDir(Provided.FEEDS), null);
            dirtyFeeds.forEach(new TLongProcedure() {
                @Override
                public boolean execute(long id) {
                    resolver.notifyChange(MusubiContentProvider.uriForItem(Provided.FEEDS, id), null);
                    return true;
                }
            });
        }
    }

    private void requestAddressBookSync() {
    	String accountName = mContext.getString(R.string.account_name);
		String accountType = mContext.getString(R.string.account_type);
		Account account = new Account(accountName, accountType);
		ContentResolver.requestSync(account, ContactsContract.AUTHORITY, new Bundle());
	}

	boolean handleProfileUpdate(MIdentity owner, ObjFormat object) {
        if (!ProfileObj.TYPE.equals(object.type)) {
            return false;
        }
        if (object.jsonSrc == null) {
            Log.e(TAG, "received profile without content");
            return true;
        }

        try {
            boolean updateRequired = false;
            boolean syncRequested = false;
            boolean redrawRequired = false;
            JSONObject json = new JSONObject(object.jsonSrc);
            // a profile request may come from someone who we have messaged
            // we are not on their whitelist, so they won't send us a profile
            // but they will send us a message asking us to send a profile to them
            if(json.has(ProfileObj.VERSION)) {
	            long version = json.getLong(ProfileObj.VERSION);
	            if (owner.receivedProfileVersion_ < version) {
	                if (object.raw != null) {
	                    owner.musubiThumbnail_ = object.raw;
	                    mIdentityManager.updateMusubiThumbnail(owner);
	                    mContactThumbnailCache.invalidate(owner.id_);
	                	if(owner.owned_) {
	                		for(MIdentity me : mIdentityManager.getOwnedIdentities()) {
	                			//TODO: wasteful
	                			me.musubiThumbnail_ = owner.musubiThumbnail_;
	                			me.receivedProfileVersion_ = owner.receivedProfileVersion_;
	            				mIdentityManager.updateMusubiThumbnail(me);
	    	                    mContactThumbnailCache.invalidate(me.id_);
	                			mIdentityManager.updateIdentity(me);
	                		}
	                	}
	                }
	
	                if(json.has(ProfileObj.NAME)) {
	                	owner.musubiName_ = json.getString(ProfileObj.NAME);
	                	if(owner.owned_) {
	                		for(MIdentity me : mIdentityManager.getOwnedIdentities()) {
	                			//TODO: wasteful
	                			me.musubiName_ = owner.musubiName_;
	                			me.receivedProfileVersion_ = owner.receivedProfileVersion_;
	                			mIdentityManager.updateIdentity(me);
	                		}
	                	}
	                }
	                if(json.has(ProfileObj.PRINCIPAL)) {
	                	String principal = json.getString(ProfileObj.PRINCIPAL);
	                	//if they tell us their email address, etc. write it down.
	                	if(Arrays.equals(owner.principalHash_, Util.sha256(principal.getBytes()))) {
		                	owner.principal_ = principal;
	                	}
	                }
	                owner.receivedProfileVersion_ = version;
	                updateRequired = true;
	                redrawRequired = true;
	            }
            }
            if (json.has(ProfileObj.REPLY) && json.getBoolean(ProfileObj.REPLY)) {
                // If identity is in the whitelist, flag as needing profile sync.
                if (mIdentityManager.isWhitelisted(owner) || mFeedManager.isInAllowedFeed(owner)) {
                    owner.sentProfileVersion_ = 1; // Force an update without reply
                    // (assumes "my profile" has been set at least once.)
                    updateRequired = true;
                    syncRequested = true;
                }
            }

            if (updateRequired) {
        		mIdentityManager.updateIdentity(owner);
            } 
            if(redrawRequired) {
        	    mContext.getContentResolver().notifyChange(MusubiService.PRIMARY_CONTENT_CHANGED, this);
            }
            if (syncRequested) {
                mContext.getContentResolver().notifyChange(MusubiService.PROFILE_SYNC_REQUESTED,
                        this);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to decode profile", e);
            return true;
        }

        return true;
    }

    long[] objsToDecode() {
    	return mEncodedMessageManager.getNonDecodedInboundIds();
    }

    class ExpandMembersProcedure implements TLongProcedure {
    	boolean mRunProfilePush = false;
    	MFeed mFeed;
    	MIdentity[] mPersonas;
		MMyAccount[] mProvisional;
        MMyAccount[] mWhitelist;
        public ExpandMembersProcedure(MMyAccount[] provisional_account, MMyAccount[] whitelist_account, MFeed feed, MIdentity[] personas) {
        	mFeed = feed;
        	mPersonas = personas;
        	mProvisional = provisional_account;
        	mWhitelist = whitelist_account;
		}
		@Override
        public boolean execute(long id) {
            mFeedManager.ensureFeedMember(mFeed.id_, id);
			//send a profile request if we don't have one from them yet
        	MIdentity recipient = mIdentityManager.getIdentityForId(id);
			if(recipient.receivedProfileVersion_ == 0) {
				//we don't really want N profiles, but we may or may not be
				//friends, so its best to ask with any relevant identities to
				//maximize the chance we can know who the sender is
				for(MIdentity persona : mPersonas) {
					sendProfileRequest(persona, recipient);
				}
			}
            if(mFeed.accepted_) {
				for(int i = 0; i < mPersonas.length; ++i) {
					mRunProfilePush |= mFeedManager.addToWhitelistsIfNecessary(mProvisional[i], mWhitelist[i], mPersonas[i], recipient);
				}
            }
            return true;
        }
    }
    Pair<Boolean, Boolean> expandMembership(final SQLiteDatabase db, MIdentity[] personas, final MFeed feed, MIdentity[] recipients) {
        TLongHashSet participants = new TLongHashSet(recipients.length);
        for (MIdentity ident : recipients) {
            participants.add(ident.id_);
        }
        final String table = MFeedMember.TABLE;
        String[] columns = new String[] { MFeedMember.COL_IDENTITY_ID };
        String selection = MFeedMember.COL_FEED_ID + " = ?";
        String[] selectionArgs = new String[] { Long.toString(feed.id_) };
        String groupBy = null, having = null, orderBy = null;
        Cursor c = db.query(table, columns, selection, selectionArgs, groupBy, having, orderBy);
        while (c.moveToNext()) {
            Long dbid = c.getLong(0);
            participants.remove(dbid);
        }
		MMyAccount[] provisional_account = new MMyAccount[personas.length];
		MMyAccount[] whitelist_account = new MMyAccount[personas.length];
		for(int i = 0; i < personas.length; ++i) {
			provisional_account[i] = mAccountManager.getProvisionalWhitelistForIdentity(personas[i].id_);
			whitelist_account[i] = mAccountManager.getWhitelistForIdentity(personas[i].id_);
		}
    	ExpandMembersProcedure expand = new ExpandMembersProcedure(provisional_account, whitelist_account, feed, personas);
        if (participants.size() > 0) {
            participants.forEach(expand);
        }
        return Pair.with(participants.size() > 0, expand.mRunProfilePush);
    }

    private void sendProfileRequest(MIdentity from, MIdentity ident) {
    	Obj profile_request = ProfilePushProcessor.getProfileRequestObj();
    	MFeed feed = mFeedManager.createOneShotFeed(from, ident);
    	Uri feedUri = MusubiContentProvider.uriForItem(Provided.FEEDS, feed.id_);
        App.getMusubi(mContext).getFeed(feedUri).postObj(profile_request);
	}

	public class MessageDecoderProcedure implements TLongProcedure {
    	SQLiteDatabase mDB;
    	boolean mSomethingChanged = false;
    	TLongHashSet mDirtyFeeds;
		private boolean mRunProfilePush;

    	public MessageDecoderProcedure(SQLiteDatabase db, TLongHashSet dirtyFeeds) {
    		mDB = db;
    		mDirtyFeeds = dirtyFeeds;
    	}

		public boolean execute(long id) {
            // Get the encoded data for processing
            MEncodedMessage encoded = mEncodedMessageManager.lookupById(id);
            assert(encoded != null);

            // Decode the message
            IncomingMessage im = null;
            try {
                try {
                    im = mMessageDecoder.processMessage(encoded);
                } catch(NeedsKey.Encryption e) {
                	if(!mSynchronousKeyFetch) {
                		throw e;
                	}
                	try
                	{
                    	MIdentity to = mIdentityManager.getIdentityForIBHashedIdentity(e.identity_);
	                   	UserKeyManager ukm = new UserKeyManager(
	                   	        mIdentityProvider.getEncryptionScheme(),
	                   	        mIdentityProvider.getSignatureScheme(), mDatabaseSource);
	                   	MEncryptionUserKey suk = new MEncryptionUserKey();
	                   	suk.identityId_ = to.id_;
	                   	suk.when_ = e.identity_.temporalFrame_;
	                   	suk.userKey_ = mIdentityProvider.syncGetEncryptionKey(e.identity_).key_;
	                   	ukm.insertEncryptionUserKey(suk);
	                    im = mMessageDecoder.processMessage(encoded);
                	} catch (IdentityProviderException exn) {
                		Log.i(TAG, "Failed to get a user key to decode " + encoded.id_, exn);
                		return true;
                	}
                }
            } catch (NeedsKey e) {
                Log.i(TAG, "Failed to decode obj beause a user key was required. " + encoded.id_, e);
                if(mKeyUpdateHandler != null) {
                    if (DBG) Log.i(TAG, "Updating key for identity #" + e.identity_, e);
                	mKeyUpdateHandler.requestEncryptionKey(e.identity_);
                }
                return true;
            } catch (DiscardMessage.Duplicate e) {
            	//RabbitMQ does not support the "no desliver to self" routing policy.
        		//don't log self-routed device duplicates, everything else we want to know about
            	if(e.mFrom.deviceName_ != mDeviceManager.getLocalDeviceName()) {
                    Log.e(TAG, "Failed to decode message", e);
            	}
                mEncodedMessageManager.delete(encoded.id_);
                return true;
            } catch (DiscardMessage e) {
                Log.e(TAG, "Failed to decode message", e);
                mEncodedMessageManager.delete(encoded.id_);
                return true;
            }

            // Decode the app data
            MDevice device = im.fromDevice_;
            MIdentity sender = mIdentityManager.getIdentityForId(encoded.fromIdentityId_);
            boolean whitelisted = (sender.owned_ || sender.whitelisted_);
            ObjFormat obj;
            try {
                obj = ObjEncoder.decode(im.data_);
            } catch (DiscardMessage e) {
                Log.e(TAG, "Failed to decode " + im.sequenceNumber_ + " from " + im.fromDevice_, e);
                mEncodedMessageManager.delete(encoded.id_);
                return true;
            }

            // Look for profile updates, which don't require whitelisting
            if (handleProfileUpdate(sender, obj)) {
            	//TODO: this may be a lame way of handling this
                Log.d(TAG, "Found profile update from " + sender.musubiName_);
                mEncodedMessageManager.delete(encoded.id_);
                return true;
            }

            // Handle feed details
            if (obj.feedType == FeedType.FIXED) {
                // Fixed feeds have well-known capabilities.
                byte[] computedCap = FeedManager.computeFixedIdentifier(im.recipients_);
                if (!Arrays.equals(computedCap, obj.feedCapability)) {
                    Log.e(TAG, "Capability mismatch");
                    mEncodedMessageManager.delete(encoded.id_);
                    return true;
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            	mDB.beginTransactionNonExclusive();
            } else {
                mDB.beginTransaction();
            }

            try {
                MFeed feed;
                boolean asymmetric = false;
                if (obj.feedType == FeedType.ASYMMETRIC || obj.feedType == FeedType.ONE_TIME_USE) {
                	//never create well-known broadcast feeds
                    feed = mFeedManager.getGlobal();
                    asymmetric = true;
                } else {
                    feed = mFeedManager.lookupFeed(obj.feedType, obj.feedCapability);
                }

                if (feed == null) {	
                    MFeed created = new MFeed();
                    created.capability_ = obj.feedCapability;
                    if (created.capability_ != null) {
                        created.shortCapability_ = Util.shortHash(created.capability_);
                    }
                    created.type_ = obj.feedType;
                    created.accepted_ = whitelisted;
                    mFeedManager.insertFeed(created);

                    mFeedManager.ensureFeedMember(created.id_, sender.id_);
                    
                    for (MIdentity recipient : im.recipients_) {
                        mFeedManager.ensureFeedMember(created.id_, recipient.id_);
            			//send a profile request if we don't have one from them yet
            			if(recipient.receivedProfileVersion_ == 0) {
            				//we don't really want N profiles, but we may or may not be
            				//friends, so its best to ask with any relevant identities to
            				//maximize the chance we can know who the sender is
            				for(MIdentity persona : im.personas_) {
            					sendProfileRequest(persona, recipient);
            				}
            			}
                    }

                    //if this feed is accepted, then we should send a profile to
                    //all of the other people in it that we don't know
                    if(created.accepted_) {
        				for(MIdentity persona : im.personas_) {
	                		MMyAccount provisional_account = mAccountManager.getProvisionalWhitelistForIdentity(persona.id_);
	                        MMyAccount whitelist_account = mAccountManager.getWhitelistForIdentity(persona.id_);
                        
	                        for (MIdentity recipient : im.recipients_) {
            					mRunProfilePush |= mFeedManager.addToWhitelistsIfNecessary(provisional_account, whitelist_account, persona, recipient);
            				}
	                    }
                    }
                    feed = created;
                } else {
                    if (!feed.accepted_ && whitelisted && !asymmetric) {
                        feed.accepted_ = true;
                        mFeedManager.updateFeed(feed);
                        mDirtyFeeds.add(feed.id_);
                    }
                    if (feed.type_ == FeedType.EXPANDING) {
                        Pair<Boolean, Boolean> expanded_push = expandMembership(mDB, im.personas_, feed, im.recipients_);
                        if (expanded_push.getValue0()) {
                            mDirtyFeeds.add(feed.id_);
                        }
                        mRunProfilePush |= expanded_push.getValue1();
                    }
                }

                // Insert the object
                MObject object = new MObject();
                MApp app = mAppManager.ensureApp(obj.appId);
                byte[] uhash = ObjEncoder.computeUniversalHash(sender, device, im.hash_);
                long currentTime = new Date().getTime();

                object.id_ = -1;
                object.feedId_ = feed.id_;
                object.identityId_ = device.identityId_;
                object.deviceId_ = device.id_;
                object.parentId_ = null;
                object.appId_ = app.id_;
                object.timestamp_ = obj.timestamp;
                object.universalHash_ = uhash;
                object.shortUniversalHash_ = Util.shortHash(uhash);
                object.type_ = obj.type;
                object.json_ = obj.jsonSrc;
                object.raw_ = obj.raw;
                object.intKey_ = obj.intKey;
                object.stringKey_ = obj.stringKey;
                object.lastModifiedTimestamp_ = currentTime;
                object.encodedId_ = encoded.id_;
                object.deleted_ = false;
                object.renderable_ = false;
                object.processed_ = false;
                mObjectManager.insertObject(object);

                // Grant app access
                if (!MusubiContentProvider.isSuperApp(obj.appId)) {
                    mFeedManager.ensureFeedApp(feed.id_, app.id_);
                }

                // Finish up
                encoded.processed_ = true;
				encoded.processedTime_ = currentTime;
                mEncodedMessageManager.updateEncodedMetadata(encoded);
                mDB.setTransactionSuccessful();
                mSomethingChanged = true;
            } finally {
                mDB.endTransaction();
            }
            return true;
		}
	}
}
