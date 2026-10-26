// SPDX-License-Identifier: GPL-3.0-or-later
package android.content.res;

import java.util.Locale;

/** Device configuration snapshot with diff support. */
public class Configuration implements Comparable<Configuration> {
    public static final int ORIENTATION_UNDEFINED = 0;
    public static final int ORIENTATION_PORTRAIT = 1;
    public static final int ORIENTATION_LANDSCAPE = 2;
    public static final int ORIENTATION_SQUARE = 3;

    public static final int SCREENLAYOUT_SIZE_UNDEFINED = 0;
    public static final int SCREENLAYOUT_SIZE_SMALL = 1;
    public static final int SCREENLAYOUT_SIZE_NORMAL = 2;
    public static final int SCREENLAYOUT_SIZE_LARGE = 3;
    public static final int SCREENLAYOUT_SIZE_XLARGE = 4;
    public static final int SCREENLAYOUT_SIZE_MASK = 0x0f;

    public static final int UI_MODE_TYPE_UNDEFINED = 0;
    public static final int UI_MODE_TYPE_NORMAL = 1;
    public static final int UI_MODE_TYPE_DESK = 2;
    public static final int UI_MODE_TYPE_CAR = 3;

    public static final int UI_MODE_NIGHT_UNDEFINED = 0;
    public static final int UI_MODE_NIGHT_NO = 0x10;
    public static final int UI_MODE_NIGHT_YES = 0x20;
    public static final int UI_MODE_NIGHT_MASK = 0x30;

    public static final int HARDKEYBOARDHIDDEN_UNDEFINED = 0;
    public static final int HARDKEYBOARDHIDDEN_NO = 1;
    public static final int HARDKEYBOARDHIDDEN_YES = 2;

    public float fontScale = 1.0f;
    public int mcc;
    public int mnc;
    public Locale locale;
    public int touchscreen;
    public int keyboard;
    public int keyboardHidden;
    public int hardKeyboardHidden;
    public int navigation;
    public int navigationHidden;
    public int orientation;
    public int screenLayout;
    public int uiMode;
    public int screenWidthDp;
    public int screenHeightDp;
    public int smallestScreenWidthDp;
    public int densityDpi;
    public int colorMode;
    public int fontWeightAdjustment;

    public Configuration() {}

    public Configuration(Configuration o) {
        setTo(o);
    }

    public void setTo(Configuration o) {
        if (o == null) return;
        fontScale = o.fontScale;
        mcc = o.mcc;
        mnc = o.mnc;
        locale = o.locale == null ? null : (Locale) o.locale.clone();
        touchscreen = o.touchscreen;
        keyboard = o.keyboard;
        keyboardHidden = o.keyboardHidden;
        hardKeyboardHidden = o.hardKeyboardHidden;
        navigation = o.navigation;
        navigationHidden = o.navigationHidden;
        orientation = o.orientation;
        screenLayout = o.screenLayout;
        uiMode = o.uiMode;
        screenWidthDp = o.screenWidthDp;
        screenHeightDp = o.screenHeightDp;
        smallestScreenWidthDp = o.smallestScreenWidthDp;
        densityDpi = o.densityDpi;
        colorMode = o.colorMode;
        fontWeightAdjustment = o.fontWeightAdjustment;
    }

    public void setLocale(Locale loc) {
        locale = loc;
    }

    public android.os.LocaleList getLocales() {
        return locale == null
                ? android.os.LocaleList.getEmptyLocaleList()
                : new android.os.LocaleList(locale);
    }

    public void setLocales(android.os.LocaleList locales) {
        locale = locales == null || locales.isEmpty() ? null : locales.get(0);
    }


    public void setLayoutDirection(Locale loc) {}

    public int getLayoutDirection() {
        return 0;
    }

    /**
     * Copy every field {@code delta} defines (AOSP updateFrom: zero, null
     * and the undefined uiMode halves leave this configuration's value).
     * Returns the changed fields as {@link #diff} bits.
     */
    public int updateFrom(Configuration delta) {
        if (delta == null) return 0;
        Configuration before = new Configuration(this);
        if (delta.fontScale > 0) fontScale = delta.fontScale;
        if (delta.mcc != 0) mcc = delta.mcc;
        if (delta.mnc != 0) mnc = delta.mnc;
        if (delta.locale != null) locale = (Locale) delta.locale.clone();
        if (delta.touchscreen != 0) touchscreen = delta.touchscreen;
        if (delta.keyboard != 0) keyboard = delta.keyboard;
        if (delta.keyboardHidden != 0) keyboardHidden = delta.keyboardHidden;
        if (delta.hardKeyboardHidden != HARDKEYBOARDHIDDEN_UNDEFINED) {
            hardKeyboardHidden = delta.hardKeyboardHidden;
        }
        if (delta.navigation != 0) navigation = delta.navigation;
        if (delta.navigationHidden != 0) navigationHidden = delta.navigationHidden;
        if (delta.orientation != ORIENTATION_UNDEFINED) orientation = delta.orientation;
        if (delta.screenLayout != 0) screenLayout = delta.screenLayout;
        if ((delta.uiMode & 0x0f) != UI_MODE_TYPE_UNDEFINED) uiMode = (uiMode & ~0x0f) | (delta.uiMode & 0x0f);
        if ((delta.uiMode & UI_MODE_NIGHT_MASK) != UI_MODE_NIGHT_UNDEFINED) {
            uiMode = (uiMode & ~UI_MODE_NIGHT_MASK) | (delta.uiMode & UI_MODE_NIGHT_MASK);
        }
        if (delta.screenWidthDp != 0) screenWidthDp = delta.screenWidthDp;
        if (delta.screenHeightDp != 0) screenHeightDp = delta.screenHeightDp;
        if (delta.smallestScreenWidthDp != 0) smallestScreenWidthDp = delta.smallestScreenWidthDp;
        if (delta.densityDpi != 0) densityDpi = delta.densityDpi;
        if (delta.colorMode != 0) colorMode = delta.colorMode;
        return before.diff(this);
    }

    public int diff(Configuration other) {
        if (other == null) return -1;
        int changed = 0;
        if (fontScale != other.fontScale) changed |= 0x40000000;
        if (mcc != other.mcc) changed |= 0x0001;
        if (mnc != other.mnc) changed |= 0x0002;
        if (locale == null ? other.locale != null : !locale.equals(other.locale)) changed |= 0x0004;
        if (touchscreen != other.touchscreen) changed |= 0x0008;
        if (keyboard != other.keyboard) changed |= 0x0010;
        if (keyboardHidden != other.keyboardHidden || hardKeyboardHidden != other.hardKeyboardHidden) changed |= 0x0020;
        if (navigation != other.navigation) changed |= 0x0040;
        if (navigationHidden != other.navigationHidden) changed |= 0x0080;
        if (orientation != other.orientation) changed |= 0x0100;
        if (screenLayout != other.screenLayout) changed |= 0x0200;
        if (uiMode != other.uiMode) changed |= 0x0400;
        if (screenWidthDp != other.screenWidthDp || screenHeightDp != other.screenHeightDp) changed |= 0x0800;
        if (smallestScreenWidthDp != other.smallestScreenWidthDp) changed |= 0x1000;
        if (densityDpi != other.densityDpi) changed |= 0x2000;
        return changed;
    }

    public boolean equals(Configuration other) {
        return other != null && diff(other) == 0;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Configuration && equals((Configuration) o);
    }

    @Override
    public int hashCode() {
        return orientation * 31 + screenLayout;
    }

    @Override
    public int compareTo(Configuration o) {
        return Integer.compare(hashCode(), o.hashCode());
    }

    @Override
    public String toString() {
        return "Configuration{orientation=" + orientation + " density=" + densityDpi + "}";
    }
}
