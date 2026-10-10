// SPDX-License-Identifier: GPL-3.0-or-later
package android.view.inputmethod;

import android.os.IBinder;
import android.view.View;

/** Input-method manager: show/hide are no-ops. */
public class InputMethodManager {
    public static final int SHOW_IMPLICIT = 1;
    public static final int HIDE_IMPLICIT_ONLY = 1;
    public static final int HIDE_NOT_ALWAYS = 2;

    public boolean showSoftInput(View view, int flags) {
        return false;
    }

    public boolean hideSoftInputFromWindow(IBinder windowToken, int flags) {
        return false;
    }

    public boolean isActive() {
        return false;
    }

    public boolean isActive(View view) {
        return false;
    }

    public void toggleSoftInput(int showFlags, int hideFlags) {}
    public void restartInput(View view) {}
}
