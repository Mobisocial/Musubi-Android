/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.contacts.widget;

//import com.android.common.widget.CompositeCursorAdapter;

import mobisocial.socialkit.SocialKit.VERSIONS;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

/**
 * A subclass of {@link CompositeCursorAdapter} that pins a single partition header.
 */
public abstract class SingleTopPinnedHeaderListAdapter extends CompositeCursorAdapter
        implements PinnedHeaderListView.PinnedHeaderAdapter {

    public static final int PARTITION_HEADER_TYPE = 0;

    private boolean mPinnedPartitionHeadersEnabled;
    private boolean mHeaderVisibility[];

    public SingleTopPinnedHeaderListAdapter(Context context) {
        super(context);
    }

    public SingleTopPinnedHeaderListAdapter(Context context, int initialCapacity) {
        super(context, initialCapacity);
    }

    public boolean getPinnedPartitionHeadersEnabled() {
        return mPinnedPartitionHeadersEnabled;
    }

    public void setPinnedPartitionHeadersEnabled(boolean flag) {
        this.mPinnedPartitionHeadersEnabled = flag;
    }

    public int getPinnedHeaderCount() {
        if (mPinnedPartitionHeadersEnabled) {
            return getPartitionCount();
        } else {
            return 0;
        }
    }

    protected boolean isPinnedPartitionHeaderVisible(int partition) {
        return mPinnedPartitionHeadersEnabled && hasHeader(partition)
                && !isPartitionEmpty(partition);
    }

    /**
     * The default implementation creates the same type of view as a normal
     * partition header.
     */
    public View getPinnedHeaderView(int partition, View convertView, ViewGroup parent) {
        if (hasHeader(partition)) {
            View view = null;
            if (convertView != null) {
                Integer headerType = (Integer)convertView.getTag();
                if (headerType != null && headerType == PARTITION_HEADER_TYPE) {
                    view = convertView;
                }
            }
            if (view == null) {
                view = newHeaderView(getContext(), partition, null, parent);
                view.setTag(PARTITION_HEADER_TYPE);
                view.setFocusable(false);
                //view.setEnabled(true);
            }
            bindHeaderView(view, partition, getCursor(partition));
            return view;
        } else {
            return null;
        }
    }

    public void configurePinnedHeaders(PinnedHeaderListView listView) {
        if (!mPinnedPartitionHeadersEnabled) {
            return;
        }

        int size = getPartitionCount();

        // Cache visibility bits, because we will need them several times later on
        if (mHeaderVisibility == null || mHeaderVisibility.length != size) {
            mHeaderVisibility = new boolean[size];
        }
        for (int i = 0; i < size; i++) {
            boolean visible = isPinnedPartitionHeaderVisible(i);
            mHeaderVisibility[i] = visible;
            if (!visible) {
                listView.setHeaderInvisible(i, true);
            }
        }

        int headerViewsCount = listView.getHeaderViewsCount();

        // Starting at the top, find and pin headers for partitions preceding the visible one(s)

        int inboundHeader = -1;
        int topHeaderHeight = listView.getTotalTopPinnedHeaderHeight();
        int topPositionInView = listView.getPositionAt(topHeaderHeight);
        int position = topPositionInView - headerViewsCount;
        int partition = getPartitionForPosition(position);
        if (getOffsetInPartition(position) == -1 && listView.getChildAt(0) != null) { // header
        	//inboundHeader = listView.getChildAt(0).getTop();
        }

        if (partition < 0 || partition > size) {
        	return;
        }
        for (int i = 0; i < partition; i++) {
        	listView.setHeaderInvisible(i, false);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
        	listView.setHeaderPinnedAtTop(partition, 0, false);
        } else {
        	listView.setHeaderInvisible(partition, false);
        }

        for (int i = partition + 1; i < size; i++) {
        	listView.setHeaderInvisible(i, false);
        }
    }

    public int getScrollPositionForHeader(int viewIndex) {
        return getPositionForPartition(viewIndex);
    }

    @Override
    public int getViewTypeCount() {
    	return 2; // 1 header 1 rest.
    }

    @Override
    public int getItemViewType(int position) {
    	return (getOffsetInPartition(position) == -1) ? 0 : 1;
    }
}
