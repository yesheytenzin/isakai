// SPDX-License-Identifier: GPL-3.0-or-later
package android.database;

/** Observer registry with Android's erased Observable superclass ABI. */
public class DataSetObservable extends Observable<DataSetObserver> {
    public void notifyChanged() {
        synchronized(mObservers) {
            for(int i=mObservers.size()-1;i>=0;i--)mObservers.get(i).onChanged();
        }
    }
    public void notifyInvalidated() {
        synchronized(mObservers) {
            for(int i=mObservers.size()-1;i>=0;i--)mObservers.get(i).onInvalidated();
        }
    }
}
