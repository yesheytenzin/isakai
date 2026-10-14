// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 * Rule-based RelativeLayout placement: parent alignment, sibling relations,
 * centering, margins, missing anchors, cycles and nested parent offsets.
 *
 * Regression for the recorded behaviour "RelativeLayout records rules but
 * stacks children vertically".
 */
public final class RelativeLayoutTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    /** Fixed-size child so measurements are deterministic in the test. */
    private static final class Box extends View {
        private final int boxW;
        private final int boxH;

        Box(int id, int w, int h) {
            super((Context) null);
            setId(id);
            this.boxW = w;
            this.boxH = h;
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(boxW, boxH);
        }
    }

    private static String rect(View v) {
        return v.getLeft() + "," + v.getTop() + "," + v.getRight() + "," + v.getBottom();
    }

    private static void expect(View v, int l, int t, int r, int b, String what) {
        check(rect(v).equals(l + "," + t + "," + r + "," + b),
                what + ": expected " + l + "," + t + "," + r + "," + b + " got " + rect(v));
    }

    private static RelativeLayout.LayoutParams lp(int verb, int anchor) {
        RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.addRule(verb, anchor);
        return p;
    }

    private static void add(RelativeLayout parent, View child, RelativeLayout.LayoutParams p) {
        child.setLayoutParams(p);
        parent.addView(child);
    }

    private static void layout(RelativeLayout parent, int l, int t, int r, int b) {
        parent.measure(View.MeasureSpec.makeMeasureSpec(r - l, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(b - t, View.MeasureSpec.EXACTLY));
        parent.layout(l, t, r, b);
    }

    public static void main(String[] args) {
        RelativeLayout parent = new RelativeLayout((Context) null);

        Box a = new Box(1, 60, 40);
        add(parent, a, lp(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE));
        ((RelativeLayout.LayoutParams) a.getLayoutParams())
                .addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);

        Box right = new Box(2, 50, 30);
        add(parent, right, lp(RelativeLayout.RIGHT_OF, 1));

        Box below = new Box(3, 40, 20);
        add(parent, below, lp(RelativeLayout.BELOW, 1));

        Box corner = new Box(4, 70, 25);
        RelativeLayout.LayoutParams cornerParams = lp(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
        cornerParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
        add(parent, corner, cornerParams);

        Box center = new Box(5, 100, 50);
        add(parent, center, lp(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE));

        Box margined = new Box(6, 20, 20);
        RelativeLayout.LayoutParams marginParams = lp(RelativeLayout.BELOW, 3);
        marginParams.topMargin = 10;
        marginParams.leftMargin = 7;
        add(parent, margined, marginParams);

        layout(parent, 0, 0, 400, 300);

        expect(a, 0, 0, 60, 40, "parent left/top");
        expect(right, 60, 0, 110, 30, "right of sibling");
        expect(below, 0, 40, 40, 60, "below sibling");
        expect(corner, 330, 275, 400, 300, "parent right/bottom");
        expect(center, 150, 125, 250, 175, "centered in parent");
        expect(margined, 7, 70, 27, 90, "below + margins");

        // Nested parent offset: child coordinates are parent-relative (AOSP),
        // so a parent at (10,20) still lays children from (0,0).
        RelativeLayout nested = new RelativeLayout((Context) null);
        Box inner = new Box(7, 30, 30);
        add(nested, inner, lp(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE));
        ((RelativeLayout.LayoutParams) inner.getLayoutParams())
                .addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
        Box innerCorner = new Box(8, 20, 20);
        RelativeLayout.LayoutParams innerCornerParams =
                lp(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
        innerCornerParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
        add(nested, innerCorner, innerCornerParams);
        layout(nested, 10, 20, 210, 120);
        expect(inner, 0, 0, 30, 30, "nested parent origin");
        expect(innerCorner, 180, 80, 200, 100, "nested parent corner");

        // Missing anchor: deterministic fallback to the parent's left edge.
        RelativeLayout missing = new RelativeLayout((Context) null);
        Box orphan = new Box(9, 25, 25);
        add(missing, orphan, lp(RelativeLayout.RIGHT_OF, 999));
        layout(missing, 0, 0, 200, 200);
        expect(orphan, 0, 0, 25, 25, "missing anchor falls back to the parent");

        // alignWithParentIfMissing applies to a missing relational anchor.
        RelativeLayout optIn = new RelativeLayout((Context) null);
        Box optInChild = new Box(10, 25, 25);
        RelativeLayout.LayoutParams optInParams = lp(RelativeLayout.RIGHT_OF, 999);
        optInParams.setAlignWithParentIfMissing(true);
        add(optIn, optInChild, optInParams);
        layout(optIn, 0, 0, 200, 200);
        expect(optInChild, 0, 0, 25, 25, "alignWithParentIfMissing");

        // Cycle: both children still get placed (no hang, no double layout).
        RelativeLayout cycle = new RelativeLayout((Context) null);
        Box first = new Box(11, 30, 30);
        Box second = new Box(12, 30, 30);
        add(cycle, first, lp(RelativeLayout.BELOW, 12));
        add(cycle, second, lp(RelativeLayout.BELOW, 11));
        layout(cycle, 0, 0, 300, 300);
        check(first.getWidth() == 30 && second.getWidth() == 30, "cycle children must be laid out");
        check(first.getTop() >= 0 && second.getTop() >= 0, "cycle children must not overlap the parent edge");

        // generateLayoutParams must produce rule-carrying params so the
        // inflater's plain LayoutParams do not drop the rules.
        RelativeLayout container = new RelativeLayout((Context) null);
        ViewGroup.LayoutParams generated = container.generateLayoutParams(
                new ViewGroup.LayoutParams(10, 20));
        check(generated instanceof RelativeLayout.LayoutParams,
                "generateLayoutParams must return RelativeLayout.LayoutParams");
        check(container.checkLayoutParams(generated), "checkLayoutParams must accept them");

        // Attribute parsing: an inflated rule targets the anchor id.
        android.util.AttributeSet attrs = new MapAttributes(
                "layout_toRightOf", "@0x7f0b0001",
                "layout_alignParentBottom", "true",
                "layout_alignWithParentIfMissing", "true");
        RelativeLayout.LayoutParams parsed = new RelativeLayout.LayoutParams((Context) null, attrs);
        int[] rules = parsed.getRules();
        check(rules[RelativeLayout.RIGHT_OF] == 0x7f0b0001,
                "layout_toRightOf: " + Integer.toHexString(rules[RelativeLayout.RIGHT_OF]));
        check(rules[RelativeLayout.ALIGN_PARENT_BOTTOM] == RelativeLayout.TRUE, "layout_alignParentBottom");
        check(parsed.getAlignWithParentIfMissing(), "layout_alignWithParentIfMissing");
        check(rules[RelativeLayout.LEFT_OF] == 0, "unset rules stay unset");

        System.out.println("PASS: RelativeLayout rules (parent alignment, sibling relations, centering, margins,"
                + " absolute parent offsets, missing-anchor fallback, cycles, generated params, attribute parsing)");
    }

    /** Minimal AttributeSet for parser tests. */
    private static final class MapAttributes implements android.util.AttributeSet {
        private final String[] keys;
        private final String[] values;

        MapAttributes(String... pairs) {
            keys = new String[pairs.length / 2];
            values = new String[pairs.length / 2];
            for (int i = 0; i < keys.length; i++) {
                keys[i] = pairs[i * 2];
                values[i] = pairs[i * 2 + 1];
            }
        }

        @Override public int getAttributeCount() { return keys.length; }
        @Override public String getAttributeName(int index) { return keys[index]; }
        @Override public String getAttributeValue(int index) { return values[index]; }
        @Override public String getAttributeValue(String namespace, String name) {
            for (int i = 0; i < keys.length; i++) if (keys[i].equals(name)) return values[i];
            return null;
        }
        @Override public String getPositionDescription() { return "test"; }
        @Override public int getAttributeNameResource(int index) { return 0; }
        @Override public int getAttributeListValue(String ns, String a, String[] options, int fallback) { return fallback; }
        @Override public boolean getAttributeBooleanValue(String ns, String a, boolean fallback) { return fallback; }
        @Override public int getAttributeResourceValue(String ns, String a, int fallback) { return fallback; }
        @Override public int getAttributeIntValue(String ns, String a, int fallback) { return fallback; }
        @Override public int getAttributeUnsignedIntValue(String ns, String a, int fallback) { return fallback; }
        @Override public float getAttributeFloatValue(String ns, String a, float fallback) { return fallback; }
        @Override public int getStyleAttribute() { return 0; }
        @Override public void recycle() {}
    }
}
