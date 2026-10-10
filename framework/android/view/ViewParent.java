// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

/** Parent contract: invalidation and layout requests climb here. */
public interface ViewParent {
    void requestLayout();
    void invalidateChild(View child, android.graphics.Rect r);
    ViewParent getParent();
    boolean isLayoutRequested();
}
