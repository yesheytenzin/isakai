// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.view.View;

/**
 * Pins {@link ImageView} measure semantics to AOSP: a view with no drawable has
 * intrinsic size 0 (not the AT_MOST bound), so WRAP_CONTENT ImageViews without
 * loaded drawables collapse instead of filling the parent and painting over
 * siblings (OpenCalc blank window).
 */
public final class ImageViewMeasureTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        android.app.Application app = new android.app.Application();
        ImageView bare = new ImageView(app);
        check(bare.getDrawable() == null, "test view must start drawable-free");
        int at = View.MeasureSpec.makeMeasureSpec(949, View.MeasureSpec.AT_MOST);
        bare.measure(at, View.MeasureSpec.makeMeasureSpec(1036, View.MeasureSpec.AT_MOST));
        check(bare.getMeasuredWidth() == 0, "bare ImageView width filled AT_MOST: " + bare.getMeasuredWidth());
        check(bare.getMeasuredHeight() == 0, "bare ImageView height filled AT_MOST: " + bare.getMeasuredHeight());
        ImageButton bareBtn = new ImageButton(app);
        bareBtn.measure(at, at);
        check(bareBtn.getMeasuredWidth() == 0 && bareBtn.getMeasuredHeight() == 0,
                "bare ImageButton did not collapse: " + bareBtn.getMeasuredWidth() + "x" + bareBtn.getMeasuredHeight());
        System.out.println("PASS: ImageView without drawable collapses under AT_MOST");
    }
}
