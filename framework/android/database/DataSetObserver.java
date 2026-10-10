// SPDX-License-Identifier: GPL-3.0-or-later
package android.database;

/** Data-change observer contract. */
public abstract class DataSetObserver {
    public void onChanged() {}
    public void onInvalidated() {}
}
