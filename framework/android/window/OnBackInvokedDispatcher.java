// SPDX-License-Identifier: GPL-3.0-or-later
package android.window;

/** Back-gesture callback registration: recorded, never invoked. */
public interface OnBackInvokedDispatcher {
    int PRIORITY_DEFAULT = 0;
    int PRIORITY_OVERLAY = 1000000;

    void registerOnBackInvokedCallback(int priority, OnBackInvokedCallback callback);
    void unregisterOnBackInvokedCallback(OnBackInvokedCallback callback);
}
