// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

import android.content.Context;
import android.content.ContextWrapper;

/** Context with an attached theme override. */
public class ContextThemeWrapper extends ContextWrapper {
    private android.content.res.Resources.Theme theme;
    private int themeResId;

    public ContextThemeWrapper() {
        super(null);
    }

    public ContextThemeWrapper(Context base, int themeResId) {
        super(base);
        this.themeResId = themeResId;
    }

    public ContextThemeWrapper(Context base, android.content.res.Resources.Theme theme) {
        super(base);
        this.theme = theme;
    }

    public void applyOverrideConfiguration(android.content.res.Configuration overrideConfiguration) {}

    @Override
    public android.content.res.Resources.Theme getTheme() {
        if (theme == null) {
            theme = getResources().newTheme();
        }
        return theme;
    }

    public void setTheme(int resId) {
        this.themeResId = resId;
        this.theme = null;
    }

    public int getThemeResId() {
        return themeResId;
    }

    @Override
    public Object getSystemService(String name) {
        return getBaseContext() == null ? null : getBaseContext().getSystemService(name);
    }
}
