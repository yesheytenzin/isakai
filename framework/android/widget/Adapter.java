// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.database.DataSetObserver;

/** Adapter contract: count, items, views. */
public interface Adapter {
    int IGNORE_ITEM_VIEW_TYPE = -1;
    int NO_SELECTION = Integer.MIN_VALUE;

    int getCount();
    Object getItem(int position);
    long getItemId(int position);
    android.view.View getView(int position, android.view.View convertView,
            android.view.ViewGroup parent);
    int getViewTypeCount();
    int getItemViewType(int position);
    boolean isEmpty();
    void registerDataSetObserver(DataSetObserver observer);
    void unregisterDataSetObserver(DataSetObserver observer);
}
