// SPDX-License-Identifier: GPL-3.0-or-later
package android.webkit;

/** Minimal ValueCallback: single-value async delivery. */
public interface ValueCallback<T> {
    void onReceiveValue(T value);
}
