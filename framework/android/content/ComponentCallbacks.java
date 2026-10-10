// SPDX-License-Identifier: GPL-3.0-or-later
package android.content;

/** Configuration-change and memory callbacks. */
public interface ComponentCallbacks {
    void onConfigurationChanged(android.content.res.Configuration newConfig);
    void onLowMemory();
}
