// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

import android.content.Context;

/** Touch dispatch reaches parent-offset children with correct coordinates. */
public class TouchOffsetTest {
    static int failures = 0;

    static void check(boolean ok, String message) {
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + message);
        }
    }

    public static void main(String[] args) {
        // Views accept a null context for pure layout/dispatch tests.
        Context ctx = null;
        // Parent group at (100,50) size 200x200; child button at (10,10)-(60,40) parent-relative.
        ViewGroup root = new ViewGroup(ctx) {};
        root.layout(0, 0, 640, 800);
        ViewGroup parent = new ViewGroup(ctx) {};
        root.addView(parent);
        parent.layout(100, 50, 300, 250);
        final boolean[] clicked = {false};
        View child = new View(ctx);
        child.setOnClickListener(v -> clicked[0] = true);
        parent.addView(child);
        child.layout(10, 10, 60, 40);
        // Tap the child's center in root coords: (100+35, 50+25) = (135,75).
        long now = System.currentTimeMillis();
        boolean down = root.dispatchTouchEvent(MotionEvent.obtain(MotionEvent.ACTION_DOWN, 135, 75, 0, now));
        boolean up = root.dispatchTouchEvent(MotionEvent.obtain(MotionEvent.ACTION_UP, 135, 75, 0, now + 10));
        check(down, "DOWN at child center should be handled");
        check(up, "UP at child center should be handled");
        check(clicked[0], "child should receive performClick via offset dispatch");

        // Tap outside the child but inside the parent: no click.
        clicked[0] = false;
        root.dispatchTouchEvent(MotionEvent.obtain(MotionEvent.ACTION_DOWN, 180, 200, 0, now + 20));
        root.dispatchTouchEvent(MotionEvent.obtain(MotionEvent.ACTION_UP, 180, 200, 0, now + 30));
        check(!clicked[0], "tap outside child must not click it");

        // Nested groups: grandchild at (5,5)-(25,20) inside mid at (20,30) inside parent.
        ViewGroup mid = new ViewGroup(ctx) {};
        parent.addView(mid);
        mid.layout(20, 30, 120, 130);
        final boolean[] deepClicked = {false};
        View deep = new View(ctx);
        deep.setOnClickListener(v -> deepClicked[0] = true);
        mid.addView(deep);
        deep.layout(5, 5, 25, 20);
        // root coords: 100+20+15, 50+30+12 = (135,92)
        root.dispatchTouchEvent(MotionEvent.obtain(MotionEvent.ACTION_DOWN, 135, 92, 0, now + 40));
        root.dispatchTouchEvent(MotionEvent.obtain(MotionEvent.ACTION_UP, 135, 92, 0, now + 50));
        check(deepClicked[0], "nested grandchild should receive the tap");

        // Sibling overlap: last child is on top.
        final boolean[] bottomClicked = {false};
        final boolean[] topClicked = {false};
        View bottom = new View(ctx);
        bottom.setOnClickListener(v -> bottomClicked[0] = true);
        View top = new View(ctx);
        top.setOnClickListener(v -> topClicked[0] = true);
        ViewGroup overlap = new ViewGroup(ctx) {};
        root.addView(overlap);
        overlap.layout(400, 400, 500, 500);
        overlap.addView(bottom);
        bottom.layout(0, 0, 100, 100);
        overlap.addView(top);
        top.layout(0, 0, 100, 100);
        root.dispatchTouchEvent(MotionEvent.obtain(MotionEvent.ACTION_DOWN, 450, 450, 0, now + 60));
        root.dispatchTouchEvent(MotionEvent.obtain(MotionEvent.ACTION_UP, 450, 450, 0, now + 70));
        check(topClicked[0] && !bottomClicked[0], "top sibling should win the tap");

        // Elevation must take precedence over insertion order. This is the
        // AppBarLayout/scroll-content overlap used by the calculator.
        final boolean[] elevatedClicked = {false};
        final boolean[] contentClicked = {false};
        View content = new View(ctx);
        content.setOnClickListener(v -> contentClicked[0] = true);
        View appBar = new View(ctx);
        appBar.setOnClickListener(v -> elevatedClicked[0] = true);
        ViewGroup elevatedOverlap = new ViewGroup(ctx) {};
        root.addView(elevatedOverlap);
        elevatedOverlap.layout(520, 400, 620, 500);
        elevatedOverlap.addView(appBar);
        appBar.layout(0, 0, 100, 40);
        elevatedOverlap.addView(content);
        content.layout(0, 0, 100, 100);
        appBar.setElevation(4f);
        root.dispatchTouchEvent(MotionEvent.obtain(MotionEvent.ACTION_DOWN, 550, 410, 0, now + 80));
        root.dispatchTouchEvent(MotionEvent.obtain(MotionEvent.ACTION_UP, 550, 410, 0, now + 90));
        check(elevatedClicked[0] && !contentClicked[0],
                "elevated toolbar should win over overlapping scroll content");

        if (failures > 0) {
            System.out.println("FAILURES: " + failures);
            System.exit(1);
        }
        System.out.println("PASS: offset touch dispatch reaches nested children");
    }
}
