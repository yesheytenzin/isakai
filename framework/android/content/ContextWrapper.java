// SPDX-License-Identifier: GPL-3.0-or-later
package android.content;

/**
 * Context delegating every call to a base context; attachBaseContext
 * re-targets the delegate.
 */
public class ContextWrapper extends Context {
    private Context base;

    public ContextWrapper(Context base) {
        this.base = base;
    }

    protected void attachBaseContext(Context base) {
        if (this.base != null) throw new IllegalStateException("already attached");
        this.base = base;
    }

    public Context getBaseContext() {
        return base;
    }

    @Override
    public android.content.res.Resources getResources() {
        return base.getResources();
    }

    @Override
    public android.content.res.Resources.Theme getTheme() {
        return base.getTheme();
    }

    @Override
    public Object getSystemService(String name) {
        return base.getSystemService(name);
    }

    @Override
    public String getPackageName() {
        return base.getPackageName();
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return base.getSharedPreferences(name, mode);
    }
}
