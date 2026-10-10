// SPDX-License-Identifier: GPL-3.0-or-later
package android.database;

import java.util.ArrayList;

/** Observer registry base. */
public abstract class Observable<T> {
    protected final ArrayList<T> mObservers = new ArrayList<>();

    public void registerObserver(T observer) {
        if (observer == null) throw new IllegalArgumentException("null observer");
        if (mObservers.contains(observer)) throw new IllegalStateException("already registered");
        mObservers.add(observer);
    }

    public void unregisterObserver(T observer) {
        mObservers.remove(observer);
    }

    public void unregisterAll() {
        mObservers.clear();
    }
}
