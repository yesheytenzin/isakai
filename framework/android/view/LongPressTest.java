// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

import android.os.Looper;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Long-press delivery from pointer input. Regression for the OpenCalc copy
 * flow: no code path armed a long press from real compositor input, so a
 * long-click listener could never fire; View.onTouchEvent now arms a
 * delayed check on the main queue and cancels it on movement or up.
 */
public final class LongPressTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static View view(AtomicBoolean clicked, AtomicBoolean longClicked) {
        View v = new View();
        v.layout(0, 0, 200, 200);
        v.setClickable(true);
        v.setLongClickable(true);
        v.setOnClickListener(x -> clicked.set(true));
        v.setOnLongClickListener(x -> {
            longClicked.set(true);
            return true;
        });
        return v;
    }

    private static void pump() {
        Looper.getMainLooper().dispatchPending();
    }

    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        long now = System.currentTimeMillis();

        AtomicBoolean clicked = new AtomicBoolean();
        AtomicBoolean longClicked = new AtomicBoolean();
        View v = view(clicked, longClicked);
        v.dispatchTouchEvent(MotionEvent.obtain(MotionEvent.ACTION_DOWN, 50, 50, 0, now));
        check(!longClicked.get(), "long press must not fire on down");
        Thread.sleep(700);
        pump();
        check(longClicked.get(), "held pointer must fire the long-click listener");
        check(!clicked.get(), "long press must not also click");
        v.dispatchTouchEvent(MotionEvent.obtain(MotionEvent.ACTION_UP, 50, 50, 0, now + 700));
        check(!clicked.get(), "release after a long press must not click again");

        // Quick tap: click, no long press.
        clicked.set(false);
        longClicked.set(false);
        v = view(clicked, longClicked);
        v.dispatchTouchEvent(MotionEvent.obtain(MotionEvent.ACTION_DOWN, 50, 50, 0, now));
        v.dispatchTouchEvent(MotionEvent.obtain(MotionEvent.ACTION_UP, 50, 50, 0, now + 20));
        Thread.sleep(700);
        pump();
        check(clicked.get(), "quick tap must click");
        check(!longClicked.get(), "quick tap must not long-press");

        // Movement past the slop before the timeout cancels the long press.
        clicked.set(false);
        longClicked.set(false);
        v = view(clicked, longClicked);
        v.dispatchTouchEvent(MotionEvent.obtain(MotionEvent.ACTION_DOWN, 50, 50, 0, now));
        v.dispatchTouchEvent(MotionEvent.obtain(MotionEvent.ACTION_MOVE, 50, 50, 0, now + 50));
        v.dispatchTouchEvent(MotionEvent.obtain(MotionEvent.ACTION_MOVE, 50, 90, 0, now + 51));
        Thread.sleep(700);
        pump();
        check(!longClicked.get(), "movement past the slop must cancel the long press");
        v.dispatchTouchEvent(MotionEvent.obtain(MotionEvent.ACTION_CANCEL, 50, 90, 0, now + 700));
        check(!clicked.get(), "cancelled gesture must not click");

        // Attaching only a long-click listener must arm the press: OpenCalc's
        // result display never calls setLongClickable itself.
        clicked.set(false);
        longClicked.set(false);
        View onlyLong = new View();
        onlyLong.layout(0, 0, 200, 200);
        check(!onlyLong.isLongClickable(), "fresh view must not be long-clickable");
        onlyLong.setOnLongClickListener(x -> {
            longClicked.set(true);
            return true;
        });
        check(onlyLong.isLongClickable(), "setOnLongClickListener must make the view long-clickable");
        onlyLong.dispatchTouchEvent(MotionEvent.obtain(MotionEvent.ACTION_DOWN, 50, 50, 0, now));
        Thread.sleep(700);
        pump();
        check(longClicked.get(), "view with only a long-click listener must fire it");
        onlyLong.dispatchTouchEvent(MotionEvent.obtain(MotionEvent.ACTION_UP, 50, 50, 0, now + 700));

        // Unregistering the listener before the timeout fires nothing.
        longClicked.set(false);
        v = view(new AtomicBoolean(), longClicked);
        v.dispatchTouchEvent(MotionEvent.obtain(MotionEvent.ACTION_DOWN, 50, 50, 0, now));
        check(v.removeCallbacks(null) == false, "null callback removal must report nothing removed");
        v.setOnLongClickListener(null);
        Thread.sleep(700);
        pump();
        check(!longClicked.get(), "removed listener must not fire");
        System.out.println("PASS: long-press delivery (delayed arm on the main queue, click suppression,"
                + " slop and cancel path)");
    }
}
