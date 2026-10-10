// SPDX-License-Identifier: GPL-3.0-or-later
package android.os;

/** DropBox log stub: entries are discarded. */
public class DropBoxManager {
    public static final int IS_EMPTY = 1;
    public static final int IS_TEXT = 2;
    public static final int IS_BINARY = 4;

    public DropBoxManager() {
    }

    public void addText(String tag, String data) {
    }

    public void addData(String tag, byte[] data, int flags) {
    }

    public boolean isTagEnabled(String tag) {
        return false;
    }
}
