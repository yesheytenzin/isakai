// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

/**
 * Pins layout-direction resolution timing: MarginLayoutParams must resolve at
 * attach (addView) so ConstraintLayout-style params copy their raw fields into
 * resolved slots before the solver runs, and getLayoutDirection must exist on
 * params (translated ConstraintLayout calls it). Without this, guidelines stay
 * at 0 and constrained children collapse (OpenCalc display band blank).
 */
public final class LayoutDirectionTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    static final class RecordingParams extends ViewGroup.MarginLayoutParams {
        boolean resolved;
        RecordingParams() { super(0, 0); }
        @Override public void resolveLayoutDirection(int direction) {
            super.resolveLayoutDirection(direction);
            resolved = true;
        }
    }

    public static void main(String[] args) {
        android.app.Application app = new android.app.Application();
        ViewGroup root = new ViewGroup(app) {};
        View child = new View(app);
        RecordingParams p = new RecordingParams();
        child.setLayoutParams(p);
        check(!p.resolved, "resolved before attach");
        root.addView(child);
        check(p.resolved, "params not resolved at addView");
        check(p.getLayoutDirection() == View.LAYOUT_DIRECTION_LTR, "direction getter wrong");
        p.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        check(p.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL, "direction setter wrong");
        // View.layout also resolves (covers setLayoutParams-after-add).
        RecordingParams p2 = new RecordingParams();
        child.setLayoutParams(p2);
        child.layout(0, 0, 10, 10);
        check(p2.resolved, "params not resolved at layout");
        RecordingParams p3 = new RecordingParams();
        child.setLayoutParams(p3);
        child.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        child.measure(View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY));
        check(p3.resolved, "replacement params not resolved before measurement");
        check(p3.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL, "resolved RTL not stored");
        System.out.println("PASS: layout direction resolves at attach and layout");
    }
}
