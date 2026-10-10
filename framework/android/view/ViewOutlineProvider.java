// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

/** Shadow outline supplier: default rect. */
public class ViewOutlineProvider {
    public static final ViewOutlineProvider BACKGROUND = new ViewOutlineProvider();
    public static final ViewOutlineProvider BOUNDS = new ViewOutlineProvider();
    public static final ViewOutlineProvider PADDED_BOUNDS = new ViewOutlineProvider();

    public void getOutline(View view, android.graphics.Outline outline) {
        if (view != null && outline != null) {
            outline.setRect(0, 0, view.getWidth(), view.getHeight());
        }
    }
}
