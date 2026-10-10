// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

import java.util.ArrayList;
import java.util.List;

/** Host-side unit checks for {@link GestureDetector}. Run on the host JVM. */
public final class GestureTest {
    private GestureTest() {}

    static void check(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }

    static class Log extends GestureDetector.SimpleOnGestureListener {
        final List<String> events = new ArrayList<>();
        float lastVx;
        float lastVy;

        @Override
        public boolean onDown(MotionEvent e) {
            events.add("down");
            return true;
        }

        @Override
        public void onShowPress(MotionEvent e) {
            events.add("show");
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            events.add("tap");
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
            events.add("scroll");
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            events.add("long");
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
            events.add("fling");
            lastVx = vx;
            lastVy = vy;
            return true;
        }
    }

    static MotionEvent ev(int action, float x, float y, long t) {
        return MotionEvent.obtain(action, x, y, 0, t);
    }

    public static void main(String[] args) {
        // Tap: down + up inside slop fires down/tap.
        Log tap = new Log();
        GestureDetector g = new GestureDetector(null, tap);
        check(g.onTouchEvent(ev(MotionEvent.ACTION_DOWN, 100, 100, 1000)), "tap down handled");
        check(g.onTouchEvent(ev(MotionEvent.ACTION_UP, 101, 99, 1050)), "tap up handled");
        check(tap.events.equals(java.util.Arrays.asList("down", "tap")), "tap sequence: " + tap.events);

        // Scroll: down + moves past slop + up fires down/scroll (slow: no fling).
        Log sc = new Log();
        GestureDetector gs = new GestureDetector(null, sc);
        gs.onTouchEvent(ev(MotionEvent.ACTION_DOWN, 50, 50, 2000));
        gs.onTouchEvent(ev(MotionEvent.ACTION_MOVE, 52, 52, 2100));
        check(gs.onTouchEvent(ev(MotionEvent.ACTION_MOVE, 100, 50, 2600)), "scroll handled");
        gs.onTouchEvent(ev(MotionEvent.ACTION_UP, 100, 50, 4000));
        check(sc.events.contains("down") && sc.events.contains("scroll"), "scroll fired: " + sc.events);
        check(!sc.events.contains("tap") && !sc.events.contains("fling"), "no tap/fling: " + sc.events);

        // Fling: fast move then up fires fling with positive X velocity.
        Log fl = new Log();
        GestureDetector gf = new GestureDetector(null, fl);
        gf.onTouchEvent(ev(MotionEvent.ACTION_DOWN, 0, 0, 5000));
        gf.onTouchEvent(ev(MotionEvent.ACTION_MOVE, 200, 0, 5050));
        gf.onTouchEvent(ev(MotionEvent.ACTION_UP, 300, 0, 5060));
        check(fl.events.contains("fling"), "fling fired: " + fl.events);
        check(fl.lastVx > 50, "fling velocity: " + fl.lastVx);

        // Long-press: down, no move, up past the deadline fires long (no tap).
        Log lp = new Log();
        GestureDetector gl = new GestureDetector(null, lp);
        gl.onTouchEvent(ev(MotionEvent.ACTION_DOWN, 10, 10, 7000));
        check(gl.onTouchEvent(ev(MotionEvent.ACTION_UP, 10, 10, 7000 + 900)), "long up handled");
        check(lp.events.contains("long"), "long fired: " + lp.events);
        check(!lp.events.contains("tap"), "no tap after long: " + lp.events);

        // Time-driven long-press without any UP yet.
        Log lp2 = new Log();
        GestureDetector gl2 = new GestureDetector(null, lp2);
        gl2.onTouchEvent(ev(MotionEvent.ACTION_DOWN, 10, 10, 9000));
        check(gl2.checkLongPress(9000 + 900), "checkLongPress fired");
        check(lp2.events.contains("long"), "timed long fired: " + lp2.events);

        // Cancel ends tracking: later UP is unhandled.
        Log cx = new Log();
        GestureDetector gc = new GestureDetector(null, cx);
        gc.onTouchEvent(ev(MotionEvent.ACTION_DOWN, 10, 10, 11000));
        gc.onTouchEvent(ev(MotionEvent.ACTION_CANCEL, 10, 10, 11100));
        check(!gc.onTouchEvent(ev(MotionEvent.ACTION_UP, 10, 10, 11200)), "up after cancel unhandled");

        // View dispatch: detector + click both fire through dispatchTouchEvent.
        final int[] clicks = {0};
        final int[] longs = {0};
        View v = new View();
        v.layout(0, 0, 200, 200);
        v.setClickable(true);
        v.setLongClickable(true);
        v.setOnClickListener(view -> clicks[0]++);
        v.setOnLongClickListener(view -> {
            longs[0]++;
            return true;
        });
        Log vl = new Log() {
            @Override
            public void onLongPress(MotionEvent e) {
                super.onLongPress(e);
                v.onLongPressFallback(e);
            }
        };
        GestureDetector gv = new GestureDetector(null, vl);
        v.setGestureDetector(gv);
        // Tap path.
        v.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, 50, 50, 20000));
        v.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, 50, 50, 20050));
        check(clicks[0] == 1, "view click fired");
        check(vl.events.contains("tap"), "view detector tap: " + vl.events);
        // Long-press path.
        v.dispatchTouchEvent(ev(MotionEvent.ACTION_DOWN, 50, 50, 21000));
        v.dispatchTouchEvent(ev(MotionEvent.ACTION_UP, 50, 50, 21000 + 900));
        check(longs[0] == 1, "view long-click fired");

        System.out.println("GestureTest: all PASS");
    }
}
