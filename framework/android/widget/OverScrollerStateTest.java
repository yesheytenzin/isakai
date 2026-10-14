// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

/** Regression coverage for springBack replacing an existing animation. */
public final class OverScrollerStateTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        OverScroller scroller = new OverScroller(new android.app.Application());
        scroller.startScroll(0, 0, 900, 800, 10000);
        check(!scroller.springBack(25, 30, 0, 100, 0, 100),
                "in-range coordinates must not spring");
        check(scroller.isFinished(), "in-range springBack must cancel previous animation");
        check(scroller.getStartX() == 25 && scroller.getStartY() == 30,
                "springBack must replace stale starting coordinates");
        check(scroller.getCurrX() == 25 && scroller.getCurrY() == 30,
                "springBack must replace stale current coordinates");
        check(scroller.getFinalX() == 25 && scroller.getFinalY() == 30,
                "springBack must replace stale destinations");
        check(!scroller.computeScrollOffset(), "cancelled animation must stay stopped");
        check(scroller.springBack(-20, 150, 0, 100, 0, 100),
                "out-of-range coordinates must spring");
        check(!scroller.isFinished(), "spring animation must be active");
        check(scroller.getCurrX() == -20 && scroller.getCurrY() == 150,
                "spring animation must start at supplied coordinates");
        check(scroller.getFinalX() == 0 && scroller.getFinalY() == 100,
                "both axes must clamp to their own bounds");
        scroller.abortAnimation();
        check(scroller.getCurrX() == 0 && scroller.getCurrY() == 100,
                "abort must finish at clamped destinations");
        check(!scroller.springBack(0, 100, 0, 100, 0, 100),
                "inclusive boundary coordinates must not spring");
        System.out.println("PASS: OverScroller springBack resets animation state");
    }
}
