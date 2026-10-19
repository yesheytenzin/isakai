// SPDX-License-Identifier: GPL-3.0-or-later
package android.database;

/** Row-window read contract. Concrete window allocation is unsupported. */
public abstract class CursorWindow implements AutoCloseable {
    public abstract int getNumRows();

    @Override
    public abstract void close();
}
