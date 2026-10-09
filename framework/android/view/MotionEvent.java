// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

/**
 * Minimal MotionEvent: DOWN/MOVE/UP/CANCEL, single pointer, button state.
 * Coordinates are surface-absolute floats; the single pointer is index 0.
 */
public class MotionEvent {
    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP = 1;
    public static final int ACTION_MOVE = 2;
    public static final int ACTION_CANCEL = 3;

    public static final int BUTTON_PRIMARY = 1 << 0;
    public static final int BUTTON_SECONDARY = 1 << 1;
    public static final int BUTTON_TERTIARY = 1 << 2;

    private int action;
    private float x;
    private float y;
    private int buttonState;
    private long eventTime;
    private long downTime;

    public MotionEvent(int action, float x, float y, int buttonState, long eventTime) {
        this.action = action;
        this.x = x;
        this.y = y;
        this.buttonState = buttonState;
        this.eventTime = eventTime;
        this.downTime = eventTime;
    }

    /** Pooled-style factory (no actual pool in M3; always allocates). */
    public static MotionEvent obtain(int action, float x, float y, int buttonState, long eventTime) {
        return new MotionEvent(action, x, y, buttonState, eventTime);
    }

    /** Copy factory. */
    public static MotionEvent obtain(MotionEvent other) {
        MotionEvent e = new MotionEvent(other.action, other.x, other.y, other.buttonState, other.eventTime);
        e.downTime = other.downTime;
        return e;
    }

    public final int getAction() {
        return action;
    }

    /** M3 has one pointer, so masked == raw action. */
    public final int getActionMasked() {
        return action;
    }

    public final int getPointerCount() {
        return 1;
    }

    public final float getX() {
        return x;
    }

    public final float getY() {
        return y;
    }

    public final float getX(int pointerIndex) {
        return x;
    }

    public final float getY(int pointerIndex) {
        return y;
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
        this.x += dx;
        this.y += dy;
    }
}
