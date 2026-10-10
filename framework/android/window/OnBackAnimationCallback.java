// SPDX-License-Identifier: GPL-3.0-or-later
package android.window;

/** Back-gesture animation callback (extends the invoked hook). */
public interface OnBackAnimationCallback extends OnBackInvokedCallback {
    default void onBackStarted(Object backEvent) {}
    default void onBackProgressed(Object backEvent) {}
    default void onBackCancelled() {}
}
