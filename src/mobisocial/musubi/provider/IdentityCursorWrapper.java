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

package mobisocial.musubi.provider;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;

import mobisocial.musubi.App;
import mobisocial.musubi.R;
import mobisocial.musubi.model.MIdentity;
import mobisocial.musubi.model.SKIdentities;
import mobisocial.musubi.model.helpers.IdentitiesManager;
import mobisocial.musubi.ui.util.UiUtil;
import android.content.Context;
import android.database.CrossProcessCursor;
import android.database.Cursor;
import android.database.CursorWindow;
import android.database.CursorWrapper;
import android.graphics.BitmapFactory;

/**
 * Provides "safe" access to certain fields from the identities table.
 * identity_id must be the last column in the cursor's projection.
 */
public class IdentityCursorWrapper extends CursorWrapper implements CrossProcessCursor {
    final Context mContext;

    int colThumb, colHash, colName;

    public IdentityCursorWrapper(Context context, Cursor cursor) {
        super(cursor);
        mContext = context;
        colThumb = getColumnIndex(SKIdentities.COL_THUMBNAIL);
        colHash = getColumnIndex(SKIdentities.COL_ID_HASH);
        colName = getColumnIndex(SKIdentities.COL_NAME);
    }

    // TODO: avoid extra DB call
    @Override
    public byte[] getBlob(int columnIndex) {
        if (columnIndex != colThumb) {
            return super.getBlob(columnIndex); 
        }
        long identityId = getLong(getColumnCount() - 1);
        IdentitiesManager im = new IdentitiesManager(App.getDatabaseSource(mContext));
        MIdentity ident = im.getIdentityWithThumbnailsForId(identityId);
        if (ident == null) {
            return null;
        }
        byte[] thumbnail = ident.musubiThumbnail_;
        if (thumbnail == null) {
            thumbnail = ident.thumbnail_;
        }
        if (thumbnail == null) {
            try {
                InputStream is = mContext.getResources().openRawResource(R.drawable.ic_contact_picture);
                thumbnail = IOUtils.toByteArray(is);
            } catch (IOException e) {}
        }
        return thumbnail;
    }

    // TODO: avoid extra DB call
    @Override
    public String getString(int columnIndex) {
        if (columnIndex != colName) {
            return super.getString(columnIndex); 
        }
        long identityId = getLong(getColumnCount() - 1);
        IdentitiesManager im = new IdentitiesManager(App.getDatabaseSource(mContext));
        MIdentity ident = im.getIdentityForId(identityId);
        return UiUtil.safeNameForIdentity(ident);
    }

    @Override
    public CursorWindow getWindow() {
        return null;
    }

    @Override
    public void fillWindow(int position, CursorWindow window) {
        if (position < 0 || position > getCount()) {
            return;
        }
        window.acquireReference();

        try {
            moveToPosition(position - 1);
            window.clear();
            window.setStartPosition(position);
            int columnNum = getColumnCount();
            window.setNumColumns(columnNum);
            while (moveToNext() && window.allocRow()) {
                for (int i = 0; i < columnNum; i++) {
                    if (i == colThumb || i == colHash) {
                        byte[] field = getBlob(i);
                        if (!window.putBlob(field, getPosition(), i)) {
                            window.freeLastRow();
                            break;
                        }
                    } else {
                        String field = getString(i);
                        if (field != null) {
                            if (!window.putString(field, getPosition(), i)) {
                                window.freeLastRow();
                                break;
                            }
                        } else {
                            if (!window.putNull(getPosition(), i)) {
                                window.freeLastRow();
                                break;
                            }
                        }
                    }
                }
            }
        } catch (IllegalStateException e) {
            // simply ignore it
        } finally {
            window.releaseReference();
        }
    }

    @Override
    public boolean onMove(int oldPosition, int newPosition) {
        return true;
    }
}