// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

/**
 * Minimal MotionEvent: DOWN/MOVE/UP/CANCEL plus multi-pointer
 * POINTER_DOWN/POINTER_UP, full pointer arrays, index bits.
 * Coordinates are surface-absolute floats.
 */
public class MotionEvent {
    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP = 1;
    public static final int ACTION_MOVE = 2;
    public static final int ACTION_CANCEL = 3;
    public static final int ACTION_OUTSIDE = 4;
    public static final int ACTION_POINTER_DOWN = 5;
    public static final int ACTION_POINTER_UP = 6;

    public static final int ACTION_MASK = 0xff;
    public static final int ACTION_POINTER_INDEX_MASK = 0xff00;
    public static final int ACTION_POINTER_INDEX_SHIFT = 8;

    public static final int BUTTON_PRIMARY = 1 << 0;
    public static final int BUTTON_SECONDARY = 1 << 1;
    public static final int BUTTON_TERTIARY = 1 << 2;

    private int action;
    private final float[] xs;
    private final float[] ys;
    private final int[] ids;
    private int buttonState;
    private long eventTime;
    private long downTime;

    public MotionEvent(int action, float[] xs, float[] ys, int[] ids, int buttonState, long eventTime, long downTime) {
        this.action = action;
        this.xs = xs.clone();
        this.ys = ys.clone();
        this.ids = ids.clone();
        this.buttonState = buttonState;
        this.eventTime = eventTime;
        this.downTime = downTime;
    }

    public MotionEvent(int action, float x, float y, int buttonState, long eventTime) {
        this(action, new float[]{x}, new float[]{y}, new int[]{0}, buttonState, eventTime, eventTime);
    }

    /** Pooled-style factory (no actual pool; always allocates). */
    public static MotionEvent obtain(int action, float x, float y, int buttonState, long eventTime) {
        return new MotionEvent(action, x, y, buttonState, eventTime);
    }

    /** Multi-pointer factory: parallel xs/ys/ids arrays. */
    public static MotionEvent obtain(int action, float[] xs, float[] ys, int[] ids, int buttonState, long eventTime, long downTime) {
        return new MotionEvent(action, xs, ys, ids, buttonState, eventTime, downTime);
    }

    /** Copy factory. */
    public static MotionEvent obtain(MotionEvent other) {
        MotionEvent e = new MotionEvent(other.action, other.xs, other.ys, other.ids, other.buttonState, other.eventTime, other.downTime);
        return e;
    }

    public final int getAction() {
        return action;
    }

    public final int getActionMasked() {
        return action & ACTION_MASK;
    }

    public final int getActionIndex() {
        return (action & ACTION_POINTER_INDEX_MASK) >> ACTION_POINTER_INDEX_SHIFT;
    }

    public final int getPointerCount() {
        return xs.length;
    }

    public final int getPointerId(int pointerIndex) {
        return ids[pointerIndex];
    }

    public final int findPointerIndex(int pointerId) {
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] == pointerId) return i;
        }
        return -1;
    }

    public final float getX() {
        return xs[0];
    }

    public final float getY() {
        return ys[0];
    }

    public final float getX(int pointerIndex) {
        return xs[pointerIndex];
    }

    public final float getY(int pointerIndex) {
        return ys[pointerIndex];
    }

    public final float getPressure(int pointerIndex) {
        return 1.0f;
    }

    public final float getSize(int pointerIndex) {
        return 1.0f;
    }

    public final int getButtonState() {
        return buttonState;
    }

    public final long getEventTime() {
        return eventTime;
    }

    public final long getDownTime() {
        return downTime;
    }

    public final void setAction(int action) {
        this.action = action;
    }

    public final void offsetLocation(float dx, float dy) {
        for (int i = 0; i < xs.length; i++) {
            xs[i] += dx;
            ys[i] += dy;
        }
    }
}
