// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

/** System-bars visibility controller: no-op. */
public interface WindowInsetsController {
    int BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE = 0;
    int BEHAVIOR_SHOW_BARS_BY_TOUCH = 1;
    int BEHAVIOR_SHOW_BARS_BY_SWIPE = 1;

    int APPEARANCE_OPAQUE_STATUS_BARS = 1;
    int APPEARANCE_OPAQUE_NAVIGATION_BARS = 2;
    int APPEARANCE_LOW_PROFILE_BARS = 4;
    int APPEARANCE_LIGHT_STATUS_BARS = 8;
    int APPEARANCE_LIGHT_NAVIGATION_BARS = 16;

    void show(int types);
    void hide(int types);
    void setSystemBarsAppearance(int appearance, int mask);
    void setSystemBarsBehavior(int behavior);
}
