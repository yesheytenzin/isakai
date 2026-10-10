// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

/** View add/update/remove contract. */
public interface ViewManager {
    void addView(View view, ViewGroup.LayoutParams params);
    void updateViewLayout(View view, ViewGroup.LayoutParams params);
    void removeView(View view);
}
