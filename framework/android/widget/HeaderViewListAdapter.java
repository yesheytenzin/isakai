// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;

/** ListAdapter wrapper accounting for header/footer rows. */
public class HeaderViewListAdapter implements ListAdapter {
    /** Fixed header/footer row descriptor. */
    public static class FixedViewInfo {
        public View view;
        public Object data;
        public boolean isSelectable;
    }

    private final java.util.ArrayList<FixedViewInfo> headers;
    private final java.util.ArrayList<FixedViewInfo> footers;
    private final ListAdapter wrapped;

    public HeaderViewListAdapter(java.util.ArrayList<FixedViewInfo> headerViewInfos,
            java.util.ArrayList<FixedViewInfo> footerViewInfos, ListAdapter adapter) {
        this.headers = headerViewInfos != null ? headerViewInfos : new java.util.ArrayList<>();
        this.footers = footerViewInfos != null ? footerViewInfos : new java.util.ArrayList<>();
        this.wrapped = adapter;
    }

    public ListAdapter getWrappedAdapter() {
        return wrapped;
    }

    public int getHeadersCount() {
        return headers.size();
    }

    public int getFootersCount() {
        return footers.size();
    }

    public boolean removeHeader(View v) {
        for (int i = 0; i < headers.size(); i++) {
            if (headers.get(i).view == v) {
                headers.remove(i);
                return true;
            }
        }
        return false;
    }

    public boolean removeFooter(View v) {
        for (int i = 0; i < footers.size(); i++) {
            if (footers.get(i).view == v) {
                footers.remove(i);
                return true;
            }
        }
        return false;
    }

    @Override
    public int getCount() {
        return getHeadersCount() + (wrapped != null ? wrapped.getCount() : 0) + getFootersCount();
    }

    @Override
    public Object getItem(int position) {
        int headersCount = getHeadersCount();
        if (position < headersCount) return headers.get(position).data;
        int innerCount = wrapped != null ? wrapped.getCount() : 0;
        if (position - headersCount < innerCount) return wrapped.getItem(position - headersCount);
        return footers.get(position - headersCount - innerCount).data;
    }

    @Override
    public long getItemId(int position) {
        int headersCount = getHeadersCount();
        int innerCount = wrapped != null ? wrapped.getCount() : 0;
        if (position >= headersCount && position - headersCount < innerCount && wrapped != null) {
            return wrapped.getItemId(position - headersCount);
        }
        return -1;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        int headersCount = getHeadersCount();
        if (position < headersCount) return headers.get(position).view;
        int innerCount = wrapped != null ? wrapped.getCount() : 0;
        if (position - headersCount < innerCount && wrapped != null) {
            return wrapped.getView(position - headersCount, convertView, parent);
        }
        return footers.get(position - headersCount - innerCount).view;
    }

    @Override
    public int getViewTypeCount() {
        return wrapped != null ? wrapped.getViewTypeCount() : 1;
    }

    @Override
    public int getItemViewType(int position) {
        int headersCount = getHeadersCount();
        int innerCount = wrapped != null ? wrapped.getCount() : 0;
        if (position >= headersCount && position - headersCount < innerCount && wrapped != null) {
            return wrapped.getItemViewType(position - headersCount);
        }
        return Adapter.IGNORE_ITEM_VIEW_TYPE;
    }

    @Override
    public boolean isEmpty() {
        return getCount() == 0;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return wrapped == null || wrapped.areAllItemsEnabled();
    }

    @Override
    public boolean isEnabled(int position) {
        int headersCount = getHeadersCount();
        if (position < headersCount) return headers.get(position).isSelectable;
        int innerCount = wrapped != null ? wrapped.getCount() : 0;
        if (position - headersCount < innerCount && wrapped != null) {
            return wrapped.isEnabled(position - headersCount);
        }
        return footers.get(position - headersCount - innerCount).isSelectable;
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
        if (wrapped != null) wrapped.registerDataSetObserver(observer);
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        if (wrapped != null) wrapped.unregisterDataSetObserver(observer);
    }
}
