// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/**
 * RelativeLayout with rule-based placement.
 *
 * <p>Rules are recorded per child (AOSP verb values) and resolved during
 * measure/layout: parent alignment, sibling relations (toLeftOf/toRightOf/
 * above/below), alignments, centering and margins. Layout direction is
 * resolved before solving, so START/END verbs follow the parent's direction.
 *
 * <p>Deterministic fallbacks (documented deviations from AOSP's retry passes):
 * an anchor whose target has not been placed yet falls back to the parent
 * edge, and a rule cycle therefore degrades to parent alignment instead of
 * looping. `alignWithParentIfMissing` applies to relational rules whose
 * anchor id is absent from the hierarchy.
 */
public class RelativeLayout extends ViewGroup {
    public static final int TRUE = -1;

    // AOSP verb ids (android.widget.RelativeLayout).
    public static final int LEFT_OF = 0;
    public static final int RIGHT_OF = 1;
    public static final int ABOVE = 2;
    public static final int BELOW = 3;
    public static final int ALIGN_BASELINE = 4;
    public static final int ALIGN_LEFT = 5;
    public static final int ALIGN_TOP = 6;
    public static final int ALIGN_RIGHT = 7;
    public static final int ALIGN_BOTTOM = 8;
    public static final int ALIGN_PARENT_LEFT = 9;
    public static final int ALIGN_PARENT_TOP = 10;
    public static final int ALIGN_PARENT_RIGHT = 11;
    public static final int ALIGN_PARENT_BOTTOM = 12;
    public static final int CENTER_IN_PARENT = 13;
    public static final int CENTER_HORIZONTAL = 14;
    public static final int CENTER_VERTICAL = 15;
    public static final int START_OF = 16;
    public static final int END_OF = 17;
    public static final int ALIGN_START = 18;
    public static final int ALIGN_END = 19;
    public static final int ALIGN_PARENT_START = 20;
    public static final int ALIGN_PARENT_END = 21;

    private static final int VERB_COUNT = 22;

    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        private final int[] rules = new int[VERB_COUNT];
        /** AOSP: relational rules with a missing anchor align to the parent. */
        private boolean alignWithParentIfMissing;

        public LayoutParams(Context c, AttributeSet attrs) {
            super(WRAP_CONTENT, WRAP_CONTENT);
            parseRules(attrs);
        }

        public LayoutParams(int w, int h) {
            super(w, h);
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
            if (source instanceof LayoutParams) {
                System.arraycopy(((LayoutParams) source).rules, 0, rules, 0, VERB_COUNT);
                alignWithParentIfMissing = ((LayoutParams) source).alignWithParentIfMissing;
            }
        }

        public void addRule(int verb) {
            addRule(verb, TRUE);
        }

        public void addRule(int verb, int anchor) {
            if (verb >= 0 && verb < rules.length) rules[verb] = anchor;
        }

        public int[] getRules() {
            return rules.clone();
        }

        public boolean getAlignWithParentIfMissing() {
            return alignWithParentIfMissing;
        }

        public void setAlignWithParentIfMissing(boolean value) {
            alignWithParentIfMissing = value;
        }

        /** Ids referenced by relational rules (`0` = none). */
        int[] anchors() {
            return rules.clone();
        }

        private void parseRules(AttributeSet attrs) {
            if (attrs == null) return;
            addRule(LEFT_OF, idRule(attrs, "layout_toLeftOf"));
            addRule(RIGHT_OF, idRule(attrs, "layout_toRightOf"));
            addRule(START_OF, idRule(attrs, "layout_toStartOf"));
            addRule(END_OF, idRule(attrs, "layout_toEndOf"));
            addRule(ABOVE, idRule(attrs, "layout_above"));
            addRule(BELOW, idRule(attrs, "layout_below"));
            addRule(ALIGN_LEFT, idRule(attrs, "layout_alignLeft"));
            addRule(ALIGN_RIGHT, idRule(attrs, "layout_alignRight"));
            addRule(ALIGN_TOP, idRule(attrs, "layout_alignTop"));
            addRule(ALIGN_BOTTOM, idRule(attrs, "layout_alignBottom"));
            addRule(ALIGN_START, idRule(attrs, "layout_alignStart"));
            addRule(ALIGN_END, idRule(attrs, "layout_alignEnd"));
            addRule(ALIGN_BASELINE, idRule(attrs, "layout_alignBaseline"));
            addRule(ALIGN_PARENT_LEFT, boolRule(attrs, "layout_alignParentLeft"));
            addRule(ALIGN_PARENT_RIGHT, boolRule(attrs, "layout_alignParentRight"));
            addRule(ALIGN_PARENT_TOP, boolRule(attrs, "layout_alignParentTop"));
            addRule(ALIGN_PARENT_BOTTOM, boolRule(attrs, "layout_alignParentBottom"));
            addRule(ALIGN_PARENT_START, boolRule(attrs, "layout_alignParentStart"));
            addRule(ALIGN_PARENT_END, boolRule(attrs, "layout_alignParentEnd"));
            addRule(CENTER_IN_PARENT, boolRule(attrs, "layout_centerInParent"));
            addRule(CENTER_HORIZONTAL, boolRule(attrs, "layout_centerHorizontal"));
            addRule(CENTER_VERTICAL, boolRule(attrs, "layout_centerVertical"));
            addRule(ALIGN_PARENT_LEFT, idRule(attrs, "layout_alignParentLeft"));
            alignWithParentIfMissing = boolRule(attrs, "layout_alignWithParentIfMissing") == TRUE;
        }

        /** Parse a rule whose value is a view id (or `true` for "parent"). */
        private static int idRule(AttributeSet attrs, String name) {
            String raw = ruleValue(attrs, name);
            if (raw == null) return 0;
            return parseId(raw);
        }

        private static int boolRule(AttributeSet attrs, String name) {
            String raw = ruleValue(attrs, name);
            if (raw == null) return 0;
            if ("true".equalsIgnoreCase(raw) || "1".equals(raw)) return TRUE;
            if ("false".equalsIgnoreCase(raw) || "0".equals(raw)) return 0;
            int id = parseId(raw);
            return id == 0 ? 0 : TRUE;
        }

        private static String ruleValue(AttributeSet attrs, String name) {
            String value = null;
            try {
                value = attrs.getAttributeValue(RelativeLayout.ANDROID_NS, name);
            } catch (RuntimeException ignored) {
            }
            if (value != null) return value;
            try {
                value = attrs.getAttributeValue(null, name);
            } catch (RuntimeException ignored) {
            }
            if (value != null) return value;
            // Compiled XML carries per-attribute identity rows; fall back to a
            // positional scan so a reader without namespace support still works.
            try {
                int count = attrs.getAttributeCount();
                for (int i = 0; i < count; i++) {
                    String attrName = attrs.getAttributeName(i);
                    if (attrName != null && attrName.endsWith(name)) return attrs.getAttributeValue(i);
                }
            } catch (RuntimeException ignored) {
            }
            return null;
        }

        /** "@0x7f0b0001" / "@2131230721" / "2131230721" / "true" -> id. */
        static int parseId(String raw) {
            if (raw == null) return 0;
            String text = raw.trim();
            if (text.isEmpty()) return 0;
            if (text.charAt(0) == '@') text = text.substring(1);
            try {
                if (text.startsWith("0x") || text.startsWith("0X")) {
                    return (int) Long.parseLong(text.substring(2), 16);
                }
                return (int) Long.parseLong(text);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }

    static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    public RelativeLayout(Context context) {
        super(context);
    }

    public RelativeLayout(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    public RelativeLayout(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams source) {
        return new LayoutParams(source);
    }

    @Override protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override public boolean checkLayoutParams(ViewGroup.LayoutParams params) {
        return params instanceof LayoutParams;
    }

    private boolean rtl(int index) {
        return getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
    }

    /** True when a rule is a relational anchor (needs a sibling reference). */
    private static boolean isRelation(int verb) {
        return verb == LEFT_OF || verb == RIGHT_OF || verb == START_OF || verb == END_OF
                || verb == ABOVE || verb == BELOW
                || verb == ALIGN_LEFT || verb == ALIGN_RIGHT || verb == ALIGN_TOP || verb == ALIGN_BOTTOM
                || verb == ALIGN_START || verb == ALIGN_END || verb == ALIGN_BASELINE;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int widthMode = View.MeasureSpec.getMode(widthSpec);
        int heightMode = View.MeasureSpec.getMode(heightSpec);
        int maxWidth = View.MeasureSpec.getSize(widthSpec);
        int maxHeight = View.MeasureSpec.getSize(heightSpec);
        int innerWidth = Math.max(0, maxWidth - getPaddingLeft() - getPaddingRight());
        int innerHeight = Math.max(0, maxHeight - getPaddingTop() - getPaddingBottom());
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child == null || child.getVisibility() == GONE) continue;
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            int childWidth = lp.width;
            int childHeight = lp.height;
            int wSpec = widthMode == View.MeasureSpec.UNSPECIFIED
                    ? getChildMeasureSpec(widthSpec, getPaddingLeft() + getPaddingRight(), childWidth)
                    : View.MeasureSpec.makeMeasureSpec(innerWidth, childWidth == ViewGroup.LayoutParams.MATCH_PARENT
                            ? View.MeasureSpec.EXACTLY : View.MeasureSpec.AT_MOST);
            int hSpec = heightMode == View.MeasureSpec.UNSPECIFIED
                    ? getChildMeasureSpec(heightSpec, getPaddingTop() + getPaddingBottom(), childHeight)
                    : View.MeasureSpec.makeMeasureSpec(innerHeight, childHeight == ViewGroup.LayoutParams.MATCH_PARENT
                            ? View.MeasureSpec.EXACTLY : View.MeasureSpec.AT_MOST);
            child.measure(wSpec, hSpec);
        }
        // Positioned extents: BELOW/RIGHT_OF siblings stack, so the parent
        // needs the anchor offset plus the child, not the largest child.
        int innerLeft = getPaddingLeft();
        int innerTop = getPaddingTop();
        int innerRight = widthMode == View.MeasureSpec.UNSPECIFIED
                ? Integer.MAX_VALUE : innerLeft + innerWidth;
        int innerBottom = heightMode == View.MeasureSpec.UNSPECIFIED
                ? Integer.MAX_VALUE : innerTop + innerHeight;
        int[] xs = new int[count];
        int[] ys = new int[count];
        boolean[] placed = new boolean[count];
        int remaining = 0;
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child == null || child.getVisibility() == GONE) {
                placed[i] = true;
                continue;
            }
            remaining++;
        }
        int widest = 0;
        int tallest = 0;
        while (remaining > 0) {
            int beforeRemaining = remaining;
            for (int i = 0; i < count; i++) {
                if (placed[i]) continue;
                View child = getChildAt(i);
                LayoutParams lp = (LayoutParams) child.getLayoutParams();
                if (!anchorsReady(lp, placed)) continue;
                int[] pos = computePosition(i, xs, ys, placed, innerLeft, innerTop, innerRight, innerBottom);
                xs[i] = pos[0];
                ys[i] = pos[1];
                int[] extent = positionedExtent(child, lp, pos, innerLeft, innerTop, innerWidth, innerHeight, widthMode, heightMode);
                widest = Math.max(widest, extent[0]);
                tallest = Math.max(tallest, extent[1]);
                placed[i] = true;
                remaining--;
            }
            if (remaining == beforeRemaining) {
                for (int i = 0; i < count; i++) {
                    if (placed[i]) continue;
                    View child = getChildAt(i);
                    LayoutParams lp = (LayoutParams) child.getLayoutParams();
                    int[] pos = computePosition(i, xs, ys, placed, innerLeft, innerTop, innerRight, innerBottom);
                    xs[i] = pos[0];
                    ys[i] = pos[1];
                    int[] extent = positionedExtent(child, lp, pos, innerLeft, innerTop, innerWidth, innerHeight, widthMode, heightMode);
                    widest = Math.max(widest, extent[0]);
                    tallest = Math.max(tallest, extent[1]);
                    placed[i] = true;
                    remaining--;
                    break;
                }
            }
        }
        int width = resolveSize(widest + getPaddingLeft() + getPaddingRight(), widthSpec);
        int height = resolveSize(tallest + getPaddingTop() + getPaddingBottom(), heightSpec);
        setMeasuredDimension(width == 0 && widthMode != View.MeasureSpec.UNSPECIFIED ? maxWidth : width,
                height == 0 && heightMode != View.MeasureSpec.UNSPECIFIED ? maxHeight : height);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        // AOSP: child bounds are parent-relative.
        int innerLeft = getPaddingLeft();
        int innerTop = getPaddingTop();
        int innerRight = (r - l) - getPaddingRight();
        int innerBottom = (b - t) - getPaddingBottom();

        int count = getChildCount();
        boolean[] placed = new boolean[count];
        int remaining = 0;
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            if (child == null || child.getVisibility() == GONE) {
                placed[i] = true;
                continue;
            }
            remaining++;
        }
        int[] xs = new int[count];
        int[] ys = new int[count];
        // Place children whose anchors are already placed, then fall back for
        // cycles. Each child is placed at most once, so a cycle cannot loop.
        while (remaining > 0) {
            int beforeRemaining = remaining;
            for (int i = 0; i < count; i++) {
                if (placed[i]) continue;
                View child = getChildAt(i);
                LayoutParams lp = (LayoutParams) child.getLayoutParams();
                if (!anchorsReady(lp, placed)) continue;
                int[] pos = computePosition(i, xs, ys, placed, innerLeft, innerTop, innerRight, innerBottom);
                xs[i] = pos[0];
                ys[i] = pos[1];
                child.layout(pos[0], pos[1], pos[0] + child.getMeasuredWidth(), pos[1] + child.getMeasuredHeight());
                placed[i] = true;
                remaining--;
            }
            if (remaining == beforeRemaining) {
                // Cycle (or an anchor chain that never becomes ready): place
                // one child with parent-relative fallbacks and continue.
                for (int i = 0; i < count; i++) {
                    if (placed[i]) continue;
                    View child = getChildAt(i);
                    int[] pos = computePosition(i, xs, ys, placed, innerLeft, innerTop, innerRight, innerBottom);
                    xs[i] = pos[0];
                    ys[i] = pos[1];
                    child.layout(pos[0], pos[1], pos[0] + child.getMeasuredWidth(), pos[1] + child.getMeasuredHeight());
                    placed[i] = true;
                    remaining--;
                    break;
                }
            }
        }
    }

    /** True when every relational anchor this child uses is already placed. */
    private boolean anchorsReady(LayoutParams lp, boolean[] placed) {
        int[] rules = lp.rules;
        for (int verb = 0; verb < rules.length; verb++) {
            int anchor = rules[verb];
            if (anchor == 0 || anchor == TRUE || !isRelation(verb)) continue;
            int index = indexOfChildId(anchor);
            if (index >= 0 && !placed[index]) return false;
        }
        return true;
    }

    private int indexOfChildId(int id) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child != null && child.getId() == id) return i;
        }
        return -1;
    }

    /** Positioned extent of one child for the parent size: the anchor offset
     * plus the child, clamped only on bounded axes. Unbounded axes keep
     * intrinsic extents so scrolling lists do not collapse to zero. */
    private int[] positionedExtent(View child, LayoutParams lp, int[] pos,
            int innerLeft, int innerTop, int innerWidth, int innerHeight, int widthMode, int heightMode) {
        int right = pos[0] + child.getMeasuredWidth() + lp.rightMargin - innerLeft;
        int bottom = pos[1] + child.getMeasuredHeight() + lp.bottomMargin - innerTop;
        if (widthMode != View.MeasureSpec.UNSPECIFIED)
            right = Math.min(right, innerWidth);
        if (heightMode != View.MeasureSpec.UNSPECIFIED)
            bottom = Math.min(bottom, innerHeight);
        return new int[]{Math.max(0, right), Math.max(0, bottom)};
    }

    /** Shared child-position solver for measure and layout. Runs once per child
     * in anchor dependency order; callers store the result and either aggregate
     * extents (measure) or apply bounds (layout). Unbounded axes leave
     * parent-relative rules at the start edge instead of overflowing. */
    private int[] computePosition(int index, int[] xs, int[] ys, boolean[] placed,
            int innerLeft, int innerTop, int innerRight, int innerBottom) {
        View child = getChildAt(index);
        LayoutParams lp = (LayoutParams) child.getLayoutParams();
        int[] rules = lp.rules;
        boolean alignParentIfMissing = lp.alignWithParentIfMissing;
        int w = child.getMeasuredWidth();
        int h = child.getMeasuredHeight();
        boolean unboundedWidth = innerRight == Integer.MAX_VALUE;
        boolean unboundedHeight = innerBottom == Integer.MAX_VALUE;

        // --- horizontal ---
        int x = innerLeft + lp.leftMargin;
        Integer relationX = null;
        int leftOf = idOf(rules, rtl(0) ? RIGHT_OF : LEFT_OF);
        int rightOf = idOf(rules, rtl(0) ? LEFT_OF : RIGHT_OF);
        int startOf = idOf(rules, rtl(0) ? END_OF : START_OF);
        int endOf = idOf(rules, rtl(0) ? START_OF : END_OF);
        int relatedRightOf = rightOf != 0 ? rightOf : startOf;
        int relatedLeftOf = leftOf != 0 ? leftOf : endOf;
        if (relatedRightOf != 0) {
            int anchorIndex = indexOfChildId(relatedRightOf);
            if (anchorIndex >= 0 && placed[anchorIndex]) {
                View anchor = getChildAt(anchorIndex);
                x = xs[anchorIndex] + anchor.getMeasuredWidth() + lp.leftMargin;
            } else if (relatedRightOf != TRUE) {
                relationX = 0; // anchor missing: keep parent fallback
            }
        } else if (relatedLeftOf != 0) {
            int anchorIndex = indexOfChildId(relatedLeftOf);
            if (anchorIndex >= 0 && placed[anchorIndex]) {
                x = xs[anchorIndex] - w - lp.rightMargin;
            }
        } else if (ruleSet(rules, rtl(0) ? ALIGN_RIGHT : ALIGN_LEFT, ALIGN_PARENT_LEFT, ALIGN_PARENT_START)) {
            x = innerLeft + lp.leftMargin;
        } else if (ruleSet(rules, rtl(0) ? ALIGN_LEFT : ALIGN_RIGHT, ALIGN_PARENT_RIGHT, ALIGN_PARENT_END)) {
            if (!unboundedWidth) x = innerRight - w - lp.rightMargin;
        } else if (ruleSet(rules, CENTER_IN_PARENT, CENTER_HORIZONTAL)) {
            if (!unboundedWidth) x = innerLeft + (innerRight - innerLeft - w) / 2;
        } else {
            int alignId = idOf(rules, rtl(0) ? ALIGN_END : ALIGN_START);
            if (alignId == 0) alignId = idOf(rules, rtl(0) ? ALIGN_START : ALIGN_END);
            int anchorIndex = alignId != 0 ? indexOfChildId(alignId) : -1;
            if (anchorIndex >= 0 && placed[anchorIndex]) {
                x = xs[anchorIndex] + lp.leftMargin;
            } else if (alignId != 0 && alignParentIfMissing) {
                x = innerLeft + lp.leftMargin;
            }
        }
        if (relationX != null) {
            boolean alignsRight = ruleSet(rules, ALIGN_RIGHT, ALIGN_PARENT_RIGHT, ALIGN_PARENT_END);
            if (alignsRight && !unboundedWidth && anchorsPresent(rules, placed)) x = innerRight - w;
        }

        // --- vertical ---
        int y = innerTop + lp.topMargin;
        int belowId = idOf(rules, BELOW);
        int aboveId = idOf(rules, ABOVE);
        int alignTopId = idOf(rules, ALIGN_TOP);
        int alignBottomId = idOf(rules, ALIGN_BOTTOM);
        int baselineId = idOf(rules, ALIGN_BASELINE);
        if (belowId != 0) {
            int anchorIndex = indexOfChildId(belowId);
            if (anchorIndex >= 0 && placed[anchorIndex]) {
                View anchor = getChildAt(anchorIndex);
                y = ys[anchorIndex] + anchor.getMeasuredHeight() + lp.topMargin;
            }
        } else if (aboveId != 0) {
            int anchorIndex = indexOfChildId(aboveId);
            if (anchorIndex >= 0 && placed[anchorIndex]) {
                y = ys[anchorIndex] - h - lp.bottomMargin;
            }
        } else if (alignTopId != 0) {
            int anchorIndex = indexOfChildId(alignTopId);
            if (anchorIndex >= 0 && placed[anchorIndex]) {
                y = ys[anchorIndex] + lp.topMargin;
            }
        } else if (alignBottomId != 0) {
            int anchorIndex = indexOfChildId(alignBottomId);
            if (anchorIndex >= 0 && placed[anchorIndex]) {
                View anchor = getChildAt(anchorIndex);
                y = ys[anchorIndex] + anchor.getMeasuredHeight() - h - lp.bottomMargin;
            }
        } else if (baselineId != 0) {
            int anchorIndex = indexOfChildId(baselineId);
            if (anchorIndex >= 0 && placed[anchorIndex]) {
                y = ys[anchorIndex] + lp.topMargin;
            }
        } else if (ruleSet(rules, ALIGN_PARENT_BOTTOM)) {
            if (!unboundedHeight) y = innerBottom - h - lp.bottomMargin;
        } else if (ruleSet(rules, ALIGN_PARENT_TOP)) {
            y = innerTop + lp.topMargin;
        } else if (ruleSet(rules, CENTER_IN_PARENT, CENTER_VERTICAL)) {
            if (!unboundedHeight) y = innerTop + (innerBottom - innerTop - h) / 2;
        }
        return new int[]{x, y};
    }

    /** Rule value for a verb; 0 when unset. */
    private static int idOf(int[] rules, int verb) {
        if (verb < 0 || verb >= rules.length) return 0;
        int value = rules[verb];
        return value > 0 ? value : 0;
    }

    /** True when at least one of the boolean verbs is set to TRUE. */
    private static boolean ruleSet(int[] rules, int... verbs) {
        for (int verb : verbs) {
            if (verb >= 0 && verb < rules.length && rules[verb] == TRUE) return true;
        }
        return false;
    }

    private boolean anchorsPresent(int[] rules, boolean[] placed) {
        for (int verb = 0; verb < rules.length; verb++) {
            int anchor = rules[verb];
            if (anchor <= 0 || !isRelation(verb)) continue;
            int index = indexOfChildId(anchor);
            if (index >= 0 && placed[index]) return true;
        }
        return false;
    }
}
