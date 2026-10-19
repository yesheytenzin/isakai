// SPDX-License-Identifier: GPL-3.0-or-later
package android.database;

/** Read contract for cursors exposing a row window. */
public abstract class AbstractWindowedCursor extends AbstractCursor {
    public abstract CursorWindow getWindow();

    public boolean hasWindow() {
        return getWindow() != null;
    }
}
