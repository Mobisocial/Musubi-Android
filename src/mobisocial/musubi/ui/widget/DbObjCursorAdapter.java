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

package mobisocial.musubi.ui.widget;

import java.io.FileDescriptor;
import java.util.HashMap;
import java.util.Map;

import mobisocial.musubi.App;
import mobisocial.musubi.R;
import mobisocial.musubi.feed.iface.FeedRenderer;
import mobisocial.musubi.model.DbLikeCache;
import mobisocial.musubi.model.MApp;
import mobisocial.musubi.model.MEncodedMessage;
import mobisocial.musubi.model.MObject;
import mobisocial.musubi.model.helpers.DatabaseManager;
import mobisocial.musubi.obj.ObjHelpers;
import mobisocial.musubi.provider.MusubiContentProvider;
import mobisocial.musubi.provider.MusubiContentProvider.Provided;
import mobisocial.socialkit.Obj;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class DbObjCursorAdapter extends CursorAdapter {
	//Map<String, RenderManager> mRenderManagers;
	final Map<String, Integer> mViewTypes;

	static final int VIEW_TYPE_UNKNOWN = 0;
	final int mColumnIndexType;

	final Context mContext;
	final DatabaseManager mDbManager;
	final int mViewTypeCount;

    public DbObjCursorAdapter (Context context, Cursor cursor) {
        super(context, cursor, false);
        mContext = context;
        mDbManager = new DatabaseManager(context);
        mColumnIndexType = cursor.getColumnIndexOrThrow(MObject.COL_TYPE);

        String[] renderables = ObjHelpers.getRenderableTypes();
    	mViewTypeCount = renderables.length + 1; // renderables + generic
    	int typeId = VIEW_TYPE_UNKNOWN;
    	mViewTypes = new HashMap<String, Integer>(mViewTypeCount);
    	mViewTypes.put("unknown", typeId++);
    	for (String type : renderables) {
    		mViewTypes.put(type, typeId++);
    	}
    }

    @Override
    public View newView(Context context, Cursor c, ViewGroup parent) {
        throw new IllegalStateException("newView() not used in this adapter");
    }

    @Override
    public void bindView(View v, Context context, Cursor c) {
        throw new IllegalStateException("bindView() not used in this adapter");
    }

    boolean moveCursorToPosition(Cursor cursor, int position) {
    	return cursor.moveToPosition(cursor.getCount() - position - 1);
    }

    @Override
    public int getItemViewType(int position) {
    	Cursor cursor = getCursor();
    	moveCursorToPosition(cursor, position);
    	String type = cursor.getString(mColumnIndexType);
    	Integer typeId = mViewTypes.get(type);
    	return (typeId == null) ? VIEW_TYPE_UNKNOWN : typeId;
    }

    @Override
    public int getViewTypeCount() {
    	return mViewTypeCount;
    }

    public static class ViewHolder {
    	public ViewGroup frame;
    	public View error;
    	public View objView;

    	public ImageView senderIcon;
    	public TextView senderName;
    	public TextView timeText;
    	public ImageView sendingIcon;
    	public ImageView attachmentsIcon;
    	public TextView attachmentsText;
    	public TextView addContact;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
    	Cursor cursor = getCursor();

    	if (!moveCursorToPosition(cursor, position)) {
            throw new IllegalStateException("couldn't move cursor to position " + position);
        }

    	DbObjCursor row;
    	if (convertView == null) {
    		row = DbObjCursor.getInstance(mDbManager, cursor);
    	} else {
    		row = DbObjCursor.getInstance(mDbManager, cursor,
    				(DbObjCursor)convertView.getTag(R.id.object_entry));
    	}

    	FeedRenderer renderer = ObjHelpers.getFeedRenderer(row.type);
    	ViewGroup objectMainView;
    	ViewHolder viewHolder;
    	if (convertView == null) {
    		viewHolder = new ViewHolder();
    		LayoutInflater inflater = LayoutInflater.from(mContext);
            objectMainView = (ViewGroup)inflater.inflate(R.layout.objects_item, parent, false);
            viewHolder.frame = (ViewGroup)objectMainView.findViewById(R.id.object_content);
            viewHolder.objView = renderer.createView(mContext, viewHolder.frame);
            viewHolder.frame.addView(viewHolder.objView);
            viewHolder.error = objectMainView.findViewById(R.id.error_text);
            viewHolder.senderIcon = (ImageView)objectMainView.findViewById(R.id.icon);
            viewHolder.senderName = (TextView)objectMainView.findViewById(R.id.name_text);
            viewHolder.timeText = (TextView)objectMainView.findViewById(R.id.time_text);
            viewHolder.sendingIcon = (ImageView)objectMainView.findViewById(R.id.sending_icon);
            viewHolder.attachmentsIcon = (ImageView)objectMainView.findViewById(R.id.obj_attachments_icon);
            viewHolder.attachmentsText = (TextView)objectMainView.findViewById(R.id.obj_attachments);
            viewHolder.addContact = (TextView)objectMainView.findViewById(R.id.add_contact);

            objectMainView.setTag(R.id.holder, viewHolder);
            objectMainView.setTag(R.id.object_entry, row);
    	} else {
    		objectMainView = (ViewGroup)convertView;
    		viewHolder = (ViewHolder)objectMainView.getTag(R.id.holder);
    	}

    	ObjHelpers.bindObjViewFrame(mContext, mDbManager, objectMainView, viewHolder, row);
    	boolean allowInteractions = true;

    	try {
        	renderer.render(mContext, viewHolder.objView, row, allowInteractions);
        	viewHolder.error.setVisibility(View.GONE);
        	viewHolder.frame.setVisibility(View.VISIBLE);
        } catch (Exception e) {
        	viewHolder.error.setVisibility(View.VISIBLE);
        	viewHolder.frame.setVisibility(View.GONE);
        	Log.e(getClass().getSimpleName(), "Error rendering type " + row.type, e);
        }
    	return objectMainView;
    }

    public static Loader<Cursor> getLoaderForFeed(Context context, long feedId, int maxCount) {
    	return new FeedObjectsCursorLoader(context, feedId, maxCount);
    }

    /**
     * A database-backed object with lazy-loaded accessors.
     */
    public static class DbObjCursor implements Obj {
    	public long objId;
    	public String type;
    	public long senderId;
    	public String json;
    	public boolean deleted;
    	public long timestamp;
    	public boolean sent;
    	public String appId;
    	public String appName;
    	public int likeCount;
    	public boolean localLike;

    	private JSONObject mJson;

    	/* lazy load extra fields */
    	private MObject mObject;
    	private byte[] mRaw;

    	private DatabaseManager mDbManager;

    	/**
    	 * Remove dependency and destroy.
    	 */
    	@Deprecated
    	public DbObjCursor(DatabaseManager db, long objId) {
    		this(db, db.getObjectManager().getObjectForId(objId));
    	}

		/**
    	 * Remove dependency and destroy.
    	 */
    	@Deprecated
    	public DbObjCursor(DatabaseManager db, MObject shim) {
    		mDbManager = db;
    		mObject = shim;
    		this.objId = mObject.id_;
    		this.type = mObject.type_;
    		this.senderId = mObject.identityId_;
    		this.json = mObject.json_;
    		this.deleted = mObject.deleted_;
    		this.timestamp = mObject.timestamp_;
    		this.sent = true; // eh?
    		MApp app = db.getAppManager().lookupApp(mObject.appId_);
    		this.appId = app == null ? null : app.appId_;
    		this.appName = app == null ? null : app.name_;
    		this.likeCount = 0; // yeh.
    		this.localLike = false; // guh.
    	}

    	static DbObjCursor getInstance(DatabaseManager dbManager,Cursor cursor) {
			DbObjCursor c = new DbObjCursor();
			c.populate(dbManager, cursor);
			return c;
		}

    	static DbObjCursor getInstance(DatabaseManager dbManager, Cursor cursor, DbObjCursor recycled) {
    		recycled.populate(dbManager, cursor);
    		return recycled;
    	}

    	private DbObjCursor() {
    		
    	}
    
    	private void populate(DatabaseManager dbManager, Cursor c) {
    		mDbManager = dbManager;
    		objId = c.getLong(0);
    		type = c.getString(1);
    		senderId = c.getLong(2);
    		json = (c.isNull(3)) ? null : c.getString(3);
    		deleted = c.getInt(4) == 1;
    		timestamp = c.getLong(5);
    		if (c.isNull(6)) {
    			sent = true;
    		} else {
    			sent = c.getInt(6) == 1;	
    		}
    		appId = c.isNull(7) ? null : c.getString(7);
    		appName = c.isNull(8) ? null : c.getString(8);
    		likeCount = c.isNull(9) ? 0 : c.getInt(9);
    		localLike = c.isNull(10) ? false : c.getInt(10) > 0;

    		mJson = null;
    		mObject = null;
    		mRaw = null;
    	}

    	public DatabaseManager getDatabaseManager() {
    		return mDbManager;
    	}

    	public JSONObject getJson() {
    		if (mJson == null && json != null) {
    			try {
    				mJson = new JSONObject(json);
    			} catch (JSONException e) {
    				Log.e("DbObjCursor", "bad json", e);
    			}
    		}
    		return mJson;
    	}

		@Override
		public Integer getIntKey() {
			return getObject().intKey_;
		}

		@Override
		public byte[] getRaw() {
			if (mRaw == null) {
				mRaw = mDbManager.getObjectManager().getRawForId(objId);
			}
			return getObject().raw_;
		}

		public FileDescriptor getFileDescriptorForRaw() {
			return mDbManager.getObjectManager().getFileDescriptorForRaw(objId);
		}

		@Override
		public String getStringKey() {
			return getObject().stringKey_;
		}

		@Override
		public String getType() {
			return type;
		}

		private MObject getObject() {
			if (mObject == null) {
				mObject = mDbManager.getObjectManager().getObjectForId(objId);
			}
			return mObject;
		}
    }

    /**
     * Static library support version of the framework's {@link android.content.CursorLoader}.
     * Used to write apps that run on platforms prior to Android 3.0.  When running
     * on Android 3.0 or above, this implementation is still used; it does not try
     * to switch to the framework's implementation.  See the framework SDK
     * documentation for a class overview.
     */
    static class FeedObjectsCursorLoader extends AsyncTaskLoader<Cursor> {
    	static final String TAG = "FeedObjectsCursorLoader";
        final ForceLoadContentObserver mObserver;

        final SQLiteDatabase mDb;
        long mFeedId;
        int mMaxCount;

        Cursor mCursor;

        /* Runs on a worker thread */
        @Override
        public Cursor loadInBackground() {
            Cursor cursor = initCursor();
            if (cursor != null) {
                // Ensure the cursor window is filled
                cursor.getCount();
                registerContentObserver(cursor, mObserver);
            }
            return cursor;
        }

        /**
         * Registers an observer to get notifications from the content provider
         * when the cursor needs to be refreshed.
         */
        void registerContentObserver(Cursor cursor, ContentObserver observer) {
            cursor.registerContentObserver(observer);
        }

        /* Runs on the UI thread */
        @Override
        public void deliverResult(Cursor cursor) {
            if (isReset()) {
                // An async query came in while the loader is stopped
                if (cursor != null) {
                    cursor.close();
                }
                return;
            }
            Cursor oldCursor = mCursor;
            mCursor = cursor;

            if (isStarted()) {
                super.deliverResult(cursor);
            }

            if (oldCursor != null && oldCursor != cursor && !oldCursor.isClosed()) {
                oldCursor.close();
            }
        }

        /**
         * Creates an empty unspecified CursorLoader.  You must follow this with
         * calls to {@link #setUri(Uri)}, {@link #setSelection(String)}, etc
         * to specify the query to perform.
         */
        public FeedObjectsCursorLoader(Context context, long feedId, int maxCount) {
            super(context);
            mDb = App.getDatabaseSource(context).getReadableDatabase();
            mFeedId = feedId;
            mMaxCount = maxCount;
            mObserver = new ForceLoadContentObserver();
        }

        /**
         * Starts an asynchronous load of the contacts list data. When the result is ready the callbacks
         * will be called on the UI thread. If a previous load has been completed and is still valid
         * the result may be passed to the callbacks immediately.
         *
         * Must be called from the UI thread
         */
        @Override
        protected void onStartLoading() {
            if (mCursor != null) {
                deliverResult(mCursor);
            }
            if (takeContentChanged() || mCursor == null) {
                forceLoad();
            }
        }

        /**
         * Must be called from the UI thread
         */
        @Override
        protected void onStopLoading() {
            // Attempt to cancel the current load task if possible.
            cancelLoad();
        }

        @Override
        public void onCanceled(Cursor cursor) {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }

        @Override
        protected void onReset() {
            super.onReset();

            // Ensure the loader is stopped
            onStopLoading();

            if (mCursor != null && !mCursor.isClosed()) {
                mCursor.close();
            }
            mCursor = null;
        }

        Cursor initCursor() {
	        String[] selectionArgs = new String[] { Long.toString(mFeedId), Integer.toString(mMaxCount) };
    		Cursor c = mDb.rawQuery(getFeedObjectsQuery(), selectionArgs);
    		c.setNotificationUri(getContext().getContentResolver(),
    				MusubiContentProvider.uriForItem(Provided.FEEDS_ID, mFeedId));
    		return c;
        }

        static String sFeedObjectsQuery;
        String getFeedObjectsQuery() {
        	if (sFeedObjectsQuery == null) {
        		sFeedObjectsQuery = new StringBuilder(100)
        		.append("SELECT ")
        			.append(MObject.TABLE).append(".").append(MObject.COL_ID).append(",")
        			.append(MObject.TABLE).append(".").append(MObject.COL_TYPE).append(",")
        			.append(MObject.TABLE).append(".").append(MObject.COL_IDENTITY_ID).append(",")
        			.append(MObject.TABLE).append(".").append(MObject.COL_JSON).append(",")
        			.append(MObject.TABLE).append(".").append(MObject.COL_DELETED).append(",")
        			.append(MObject.TABLE).append(".").append(MObject.COL_TIMESTAMP).append(",")
        			.append(MEncodedMessage.TABLE).append(".").append(MEncodedMessage.COL_PROCESSED).append(",")
        			.append(MApp.TABLE).append(".").append(MApp.COL_APP_ID).append(",")
        			.append(MApp.TABLE).append(".").append(MApp.COL_NAME).append(",")
        			.append(DbLikeCache.TABLE).append(".").append(DbLikeCache.COUNT).append(",")
        			.append(DbLikeCache.TABLE).append(".").append(DbLikeCache.LOCAL_LIKE)
        		.append(" FROM ")
        			.append(MObject.TABLE)
        			.append(" LEFT JOIN ").append(MEncodedMessage.TABLE).append(" ON ")
        				.append(MObject.TABLE).append(".").append(MObject.COL_ENCODED_ID).append("=")
        				.append(MEncodedMessage.TABLE).append(".").append(MEncodedMessage.COL_ID)
        			.append(" LEFT JOIN ").append(MApp.TABLE).append(" ON ")
        				.append(MObject.TABLE).append(".").append(MObject.COL_APP_ID).append("=")
        				.append(MApp.TABLE).append(".").append(MApp.COL_ID)
        			.append(" LEFT JOIN ").append(DbLikeCache.TABLE).append(" ON ")
        				.append(MObject.TABLE).append(".").append(MObject.COL_ID).append("=")
        				.append(DbLikeCache.TABLE).append(".").append(DbLikeCache.PARENT_OBJ)
    			.append(" WHERE ")
    				.append(MObject.TABLE).append(".").append(MObject.COL_RENDERABLE).append("=1 AND ")
	                .append(MObject.TABLE).append(".").append(MObject.COL_PARENT_ID).append(" is null AND ")
	                .append(MObject.TABLE).append(".").append(MObject.COL_FEED_ID).append(" =?")
				.append(" ORDER BY ").append(MObject.COL_LAST_MODIFIED_TIMESTAMP).append(" DESC LIMIT ?")
				.toString();
        	}
        	return sFeedObjectsQuery;
        }
    }
}
