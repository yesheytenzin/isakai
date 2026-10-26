// SPDX-License-Identifier: GPL-3.0-or-later
package android.app;

import android.content.Context;

/** Dialog subclass carrying progress state; rendered as a plain dialog. */
public class ProgressDialog extends AlertDialog {
    private boolean indeterminate;

    public ProgressDialog(Context context) {
        super(context);
    }

    public ProgressDialog(Context context, int themeResId) {
        super(context, themeResId);
    }

    public void setIndeterminate(boolean value) {
        indeterminate = value;
    }

    public boolean isIndeterminate() {
        return indeterminate;
    }
}
