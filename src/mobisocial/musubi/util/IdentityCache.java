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

package mobisocial.musubi.util;
import mobisocial.musubi.App;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.ui.util.UiUtil;
import android.content.Context;
import android.graphics.Bitmap;
import android.support.v4.util.LruCache;

//TODO: Old one supported a default that wouldn't count against the cache size
//TODO: is background loading it even helpful?
public class IdentityCache extends LruCache<Long, IdentityCache.CachedIdentity>{
	
	final Context mContext;
	final IdentitiesManager mIdentitiesManager;
	final int mCapacity;
	static final int DEFAULT_CAPACITY = 30;

	public IdentityCache(Context context) {
		super(DEFAULT_CAPACITY);
		mCapacity = DEFAULT_CAPACITY;
		mContext = context.getApplicationContext();
		mIdentitiesManager = new IdentitiesManager(App.getDatabaseSource(mContext));
	}

    public synchronized void invalidate(long id){
    	remove(id);
    }
    public synchronized void invalidateAll(){
    	evictAll();
    }

    /**
     * Gets a cached identity
     * @param id the identity's id
     */
    @Override
	public CachedIdentity create(Long id) {
		MIdentity mident = mIdentitiesManager.getIdentityWithThumbnailsForId(id);
		if (mident == null) {
			return null;
		}
		String name = UiUtil.safeNameForIdentity(mident);
		Bitmap thumbnail = UiUtil.safeGetContactThumbnailWithoutCache(mIdentitiesManager, id);
		boolean hasThumb = (thumbnail != null);
		if (!hasThumb) {
			thumbnail = UiUtil.getDefaultContactThumbnail(mContext);
		}
		// these blob fields have been processed and cached.
		mident.thumbnail_ = null;
		mident.musubiThumbnail_ = null;
		return new CachedIdentity(name, hasThumb, thumbnail, mident);
	}

    @Override
    protected void entryRemoved(boolean evicted, Long key,
    		CachedIdentity oldValue, CachedIdentity newValue) {
    	if (oldValue.thumbnail != null) {
    		//oldValue.thumbnail.recycle();
    	}
    }

	public static final class CachedIdentity {
		public final String name;
		public boolean hasThumbnail;
		public final Bitmap thumbnail;
		public final MIdentity midentity;

		public CachedIdentity(String name, boolean hasThumbnail, Bitmap thumbnail, MIdentity ident) {
			this.name = name;
			this.hasThumbnail = hasThumbnail;
			this.thumbnail = thumbnail;
			this.midentity = ident;
		}

		@Override
		public String toString() {
			return name;
		}
	}
}
