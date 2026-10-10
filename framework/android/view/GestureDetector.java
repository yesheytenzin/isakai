// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

/**
 * Tap/long-press/scroll/fling detection over touch events. Single
 * pointer only: the first pointer drives the gesture; extra pointers
 * cancel tap/long-press tracking (scroll/fling keep the first
 * pointer's stream).
 *
 * <p>Timing comes from MotionEvent timestamps (not wall clock), so
 * headless tests can drive gestures deterministically. Long-press is
 * time-driven: it fires on the first MOVE/UP at or past
 * downTime + LONGPRESS_TIMEOUT, or when {@link #checkLongPress} is
 * called (the window loop calls it per slice).
 */
public class GestureDetector {
    public interface OnGestureListener {
        boolean onDown(MotionEvent e);
        void onShowPress(MotionEvent e);
        boolean onSingleTapUp(MotionEvent e);
        boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY);
        void onLongPress(MotionEvent e);
        boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY);
    }

    public interface OnDoubleTapListener {
        boolean onSingleTapConfirmed(MotionEvent e);
        boolean onDoubleTap(MotionEvent e);
        boolean onDoubleTapEvent(MotionEvent e);
    }

    public interface OnContextClickListener {
        boolean onContextClick(MotionEvent e);
    }

    public static class SimpleOnGestureListener implements OnGestureListener, OnDoubleTapListener {
        @Override
        public boolean onDown(MotionEvent e) { return false; }
        @Override
        public void onShowPress(MotionEvent e) {}
        @Override
        public boolean onSingleTapUp(MotionEvent e) { return false; }
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) { return false; }
        @Override
        public void onLongPress(MotionEvent e) {}
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) { return false; }
        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) { return false; }
        @Override
        public boolean onDoubleTap(MotionEvent e) { return false; }
        @Override
        public boolean onDoubleTapEvent(MotionEvent e) { return false; }
    }

    private final OnGestureListener listener;
    private OnDoubleTapListener doubleTapListener;
    private boolean longpressEnabled = true;

    private MotionEvent downEvent;
    private MotionEvent lastMove;
    private boolean inTap;
    private boolean longPressed;
    private boolean showPressSent;
    private boolean scrolled;
    private float lastX;
    private float lastY;
    private long lastMoveTime;
    private float velocityX;
    private float velocityY;

    // Double-tap: pending first-tap UP awaiting a second DOWN.
    private MotionEvent pendingSingleTap;
    private long pendingTapTime;

    public GestureDetector(android.content.Context context, OnGestureListener listener) {
        this.listener = listener;
    }

    public GestureDetector(android.content.Context context, OnGestureListener listener,
            android.os.Handler handler) {
        this.listener = listener;
    }

    private static int touchSlop() {
        return 8;
    }

    private static int minFlingVelocity() {
        return 50;
    }

    private static int maxFlingVelocity() {
        return 8000;
    }

    private static long tapTimeout() {
        return ViewConfiguration.getTapTimeout();
    }

    private static long longPressTimeout() {
        return ViewConfiguration.getLongPressTimeout();
    }

    private static long doubleTapTimeout() {
        return ViewConfiguration.getDoubleTapTimeout();
    }

    public boolean onTouchEvent(MotionEvent event) {
        if (event == null || listener == null) return false;
        int masked = event.getActionMasked();
        switch (masked) {
            case MotionEvent.ACTION_DOWN:
                return handleDown(event);
            case MotionEvent.ACTION_MOVE:
                return handleMove(event);
            case MotionEvent.ACTION_UP:
                return handleUp(event);
            case MotionEvent.ACTION_CANCEL:
                cancel();
                return false;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_POINTER_UP:
                // Extra pointers end tap/long-press candidacy.
                inTap = false;
                return false;
            default:
                return false;
        }
    }

    private boolean handleDown(MotionEvent e) {
        // Second tap of a double-tap?
        if (pendingSingleTap != null
                && e.getEventTime() - pendingTapTime <= doubleTapTimeout()
                && distance(e.getX(), e.getY(), pendingSingleTap.getX(), pendingSingleTap.getY())
                        <= touchSlop() * 2) {
            pendingSingleTap = null;
            if (doubleTapListener != null) {
                doubleTapListener.onDoubleTap(e);
                doubleTapListener.onDoubleTapEvent(e);
            }
        }
        downEvent = MotionEvent.obtain(e);
        lastMove = MotionEvent.obtain(e);
        inTap = true;
        longPressed = false;
        showPressSent = false;
        scrolled = false;
        lastX = e.getX();
        lastY = e.getY();
        lastMoveTime = e.getEventTime();
        velocityX = 0;
        velocityY = 0;
        return listener.onDown(e);
    }

    private boolean handleMove(MotionEvent e) {
        if (downEvent == null) return false;
        float x = e.getX();
        float y = e.getY();
        long dt = e.getEventTime() - lastMoveTime;
        if (dt > 0) {
            float vx = (x - lastX) / dt * 1000f;
            float vy = (y - lastY) / dt * 1000f;
            // Clamp + smooth: keeps one noisy slice from exploding fling.
            vx = Math.max(-maxFlingVelocity(), Math.min(maxFlingVelocity(), vx));
            vy = Math.max(-maxFlingVelocity(), Math.min(maxFlingVelocity(), vy));
            velocityX = velocityX * 0.7f + vx * 0.3f;
            velocityY = velocityY * 0.7f + vy * 0.3f;
        }
        lastX = x;
        lastY = y;
        lastMoveTime = e.getEventTime();
        lastMove = MotionEvent.obtain(e);

        float totalDx = x - downEvent.getX();
        float totalDy = y - downEvent.getY();
        if (!scrolled && Math.hypot(totalDx, totalDy) > touchSlop()) {
            scrolled = true;
            inTap = false;
        }
        if (inTap && !longPressed && longpressEnabled
                && e.getEventTime() - downEvent.getEventTime() >= longPressTimeout()) {
            longPressed = true;
            inTap = false;
            listener.onLongPress(e);
            return true;
        }
        if (inTap && !showPressSent
                && e.getEventTime() - downEvent.getEventTime() >= tapTimeout()) {
            showPressSent = true;
            listener.onShowPress(e);
        }
        if (scrolled) {
            float dx = downEvent.getX() - x;
            float dy = downEvent.getY() - y;
            return listener.onScroll(downEvent, e, dx, dy);
        }
        return false;
    }

    private boolean handleUp(MotionEvent e) {
        if (downEvent == null) return false;
        try {
            // Late long-press (no MOVE past the deadline before UP).
            if (inTap && !longPressed && longpressEnabled
                    && e.getEventTime() - downEvent.getEventTime() >= longPressTimeout()) {
                longPressed = true;
                inTap = false;
                listener.onLongPress(e);
                return true;
            }
            if (scrolled) {
                float vx = velocityX;
                float vy = velocityY;
                if (Math.hypot(vx, vy) >= minFlingVelocity()) {
                    return listener.onFling(downEvent, e, vx, vy);
                }
                return false;
            }
            if (inTap && !longPressed) {
                boolean handled = listener.onSingleTapUp(e);
                if (doubleTapListener != null) {
                    if (doubleTapListener.onSingleTapConfirmed(e)) handled = true;
                    // Arm double-tap window only when no listener
                    // consumed it as confirmed (kept simple: always arm).
                    pendingSingleTap = MotionEvent.obtain(e);
                    pendingTapTime = e.getEventTime();
                }
                return handled;
            }
            return false;
        } finally {
            if (!inTap || longPressed || scrolled) {
                pendingSingleTap = null;
            }
            downEvent = null;
            inTap = false;
        }
    }

    /**
     * Time-driven long-press check for loops that slice without MOVE
     * events: fires onLongPress when the deadline passed with no
     * movement. Returns true when it fired.
     */
    public boolean checkLongPress(long nowMs) {
        if (downEvent == null || !inTap || longPressed || !longpressEnabled) return false;
        if (scrolled) return false;
        if (nowMs - downEvent.getEventTime() < longPressTimeout()) return false;
        longPressed = true;
        inTap = false;
        listener.onLongPress(lastMove != null ? lastMove : downEvent);
        return true;
    }

    /** True while a tap/long-press candidate is being tracked. */
    public boolean isInTap() {
        return inTap;
    }

    private void cancel() {
        downEvent = null;
        lastMove = null;
        inTap = false;
        longPressed = false;
        showPressSent = false;
        scrolled = false;
        pendingSingleTap = null;
    }

    private static float distance(float x0, float y0, float x1, float y1) {
        return (float) Math.hypot(x0 - x1, y0 - y1);
    }

    public void setOnDoubleTapListener(OnDoubleTapListener listener) {
        this.doubleTapListener = listener;
    }

    public void setIsLongpressEnabled(boolean enabled) {
        this.longpressEnabled = enabled;
    }

    public boolean isLongpressEnabled() {
        return longpressEnabled;
    }

    public void setOnContextClickListener(OnContextClickListener listener) {}
}
