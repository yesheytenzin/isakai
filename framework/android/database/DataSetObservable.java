// SPDX-License-Identifier: GPL-3.0-or-later
package android.database;

import java.util.ArrayList;

/** Observer registry with change broadcast. */
public class DataSetObservable {
    private final ArrayList<DataSetObserver> observers = new ArrayList<>();

    public void registerObserver(DataSetObserver observer) {
        if (observer != null && !observers.contains(observer)) observers.add(observer);
    }

    public void unregisterObserver(DataSetObserver observer) {
        observers.remove(observer);
    }

    public void notifyChanged() {
        for (DataSetObserver o : observers.toArray(new DataSetObserver[0])) o.onChanged();
    }

    public void notifyInvalidated() {
        for (DataSetObserver o : observers.toArray(new DataSetObserver[0])) o.onInvalidated();
    }
}
