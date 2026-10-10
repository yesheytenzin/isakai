// SPDX-License-Identifier: GPL-3.0-or-later
package android.text.method;

import android.text.Spannable;

/** Meta-key state tracking: no-ops, desktop keys carry their own state. */
public class MetaKeyKeyListener {
    public static final int META_SHIFT_ON = 1;
    public static final int META_ALT_ON = 2;
    public static final int META_SYM_ON = 4;
    public static final int META_SELECTING = 0x100;

    public static void adjustMetaAfterKeypress(Spannable text) {}

    public static void resetMetaState(Spannable text) {}

    public static boolean isMetaTracker(CharSequence text, Object what) {
        return false;
    }

    public static boolean isSelectingMetaTracker(CharSequence text, Object what) {
        return false;
    }

    public static int getMetaState(CharSequence text) {
        return 0;
    }
}
