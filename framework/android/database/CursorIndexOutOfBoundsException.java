// SPDX-License-Identifier: GPL-3.0-or-later
package android.database;

/** Row index out of range (mirrors the AOSP cursor exception). */
public class CursorIndexOutOfBoundsException extends IndexOutOfBoundsException {
    public CursorIndexOutOfBoundsException(int index, int size) {
        super("Index " + index + " requested, with a size of " + size);
    }

    public CursorIndexOutOfBoundsException(String message) {
        super(message);
    }
}
