// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.database.DataSetObservable;
import android.database.DataSetObserver;

/** Adapter base with observer broadcast. */
public abstract class BaseAdapter implements ListAdapter {
    private final DataSetObservable observable = new DataSetObservable();

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
        observable.registerObserver(observer);
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        observable.unregisterObserver(observer);
    }

    public void notifyDataSetChanged() {
        observable.notifyChanged();
    }

    public void notifyDataSetInvalidated() {
        observable.notifyInvalidated();
    }

    @Override
    public boolean areAllItemsEnabled() {
        return true;
    }

    @Override
    public boolean isEnabled(int position) {
        return true;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public int getItemViewType(int position) {
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return getCount() == 0;
    }
}
