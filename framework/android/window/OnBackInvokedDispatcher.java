// SPDX-License-Identifier: GPL-3.0-or-later
package android.window;

/** Priority-ordered back callbacks; host Back/Escape invokes the active activity dispatcher. */
public interface OnBackInvokedDispatcher {
    int PRIORITY_DEFAULT = 0;
    int PRIORITY_OVERLAY = 1000000;

    void registerOnBackInvokedCallback(int priority, OnBackInvokedCallback callback);
    void unregisterOnBackInvokedCallback(OnBackInvokedCallback callback);
}
