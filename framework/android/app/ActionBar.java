// SPDX-License-Identifier: GPL-3.0-or-later
package android.app;

/** Action bar: title/subtitle record, display flags only. */
public abstract class ActionBar {
    public static final int DISPLAY_USE_LOGO = 1;
    public static final int DISPLAY_SHOW_HOME = 2;
    public static final int DISPLAY_HOME_AS_UP = 4;
    public static final int DISPLAY_SHOW_TITLE = 8;
    public static final int DISPLAY_SHOW_CUSTOM = 16;

    public static final int NAVIGATION_MODE_STANDARD = 0;
    public static final int NAVIGATION_MODE_LIST = 1;
    public static final int NAVIGATION_MODE_TABS = 2;

    public abstract void setTitle(CharSequence title);
    public abstract void setTitle(int resId);
    public abstract void setSubtitle(CharSequence subtitle);
    public abstract void show();
    public abstract void hide();
    public abstract boolean isShowing();
    public abstract void setDisplayOptions(int options);
    public abstract void setDisplayHomeAsUpEnabled(boolean showHomeAsUp);
    public abstract void setHomeButtonEnabled(boolean enabled);
    public abstract CharSequence getTitle();
}
