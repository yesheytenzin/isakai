// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

/** System window insets snapshot: all zero. */
public class WindowInsets {
    public static final WindowInsets CONSUMED = new WindowInsets();

    public WindowInsets() {}

    public WindowInsets(WindowInsets other) {}

    public int getSystemWindowInsetLeft() { return 0; }
    public int getSystemWindowInsetTop() { return 0; }
    public int getSystemWindowInsetRight() { return 0; }
    public int getSystemWindowInsetBottom() { return 0; }
    public int getStableInsetLeft() { return 0; }
    public int getStableInsetTop() { return 0; }
    public int getStableInsetRight() { return 0; }
    public int getStableInsetBottom() { return 0; }

    public android.graphics.Insets getInsets(int typeMask) {
        return android.graphics.Insets.NONE;
    }

    public android.graphics.Insets getInsetsIgnoringVisibility(int typeMask) {
        return android.graphics.Insets.NONE;
    }

    /**
     * System bars, IME and cutout do not exist on the host, so no inset type
     * is ever visible.
     */
    public boolean isVisible(int typeMask) {
        return false;
    }

    public android.graphics.Insets getSystemGestureInsets() {
        return android.graphics.Insets.NONE;
    }

    public android.graphics.Insets getMandatorySystemGestureInsets() {
        return android.graphics.Insets.NONE;
    }

    public android.graphics.Insets getTappableElementInsets() {
        return android.graphics.Insets.NONE;
    }

    public android.graphics.Insets getStableInsets() {
        return android.graphics.Insets.NONE;
    }

    public DisplayCutout getDisplayCutout() { return null; }
    public RoundedCorner getRoundedCorner(int position) { return null; }

    public WindowInsets consumeSystemWindowInsets() { return CONSUMED; }
    public WindowInsets consumeStableInsets() { return CONSUMED; }
    public WindowInsets consumeDisplayCutout() { return CONSUMED; }
    public WindowInsets inset(int left, int top, int right, int bottom) { return this; }

    @Override
    public boolean equals(Object o) {
        return o instanceof WindowInsets;
    }

    public static final class Builder {
        public Builder() {}
        public Builder(WindowInsets other) {}

        public Builder setSystemWindowInsets(android.graphics.Insets insets) { return this; }
        public Builder setStableInsets(android.graphics.Insets insets) { return this; }
        public Builder setSystemGestureInsets(android.graphics.Insets insets) { return this; }
        public Builder setMandatorySystemGestureInsets(android.graphics.Insets insets) { return this; }
        public Builder setTappableElementInsets(android.graphics.Insets insets) { return this; }
        public Builder setInsets(int typeMask, android.graphics.Insets insets) { return this; }

        public WindowInsets build() { return new WindowInsets(); }
    }

    public static final class Type {
        public static int statusBars() { return 1; }
        public static int navigationBars() { return 2; }
        public static int captionBar() { return 4; }
        public static int ime() { return 8; }
        public static int systemGestures() { return 16; }
        public static int mandatorySystemGestures() { return 32; }
        public static int tappableElement() { return 64; }
        public static int displayCutout() { return 128; }
    }
}
