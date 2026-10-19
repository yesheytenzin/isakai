// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

/**
 * Minimal MotionEvent: DOWN/MOVE/UP/CANCEL plus multi-pointer
 * POINTER_DOWN/POINTER_UP, full pointer arrays, index bits.
 * Coordinates are surface-absolute floats.
 */
public class MotionEvent extends InputEvent {
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
    public static final int TOOL_TYPE_FINGER = 1;

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
    private int metaState;
    private int deviceId;
    private int edgeFlags;
    private int source = InputDevice.SOURCE_TOUCHSCREEN;
    private int flags;
    private float xPrecision = 1.0f;
    private float yPrecision = 1.0f;

    /** Per-pointer coordinate record (AOSP-shaped public fields). */
    public static final class PointerCoords {
        public float x;
        public float y;
        public float pressure = 1.0f;
        public float size = 1.0f;
        public float touchMajor;
        public float touchMinor;
        public float toolMajor;
        public float toolMinor;
        public float orientation;

        public PointerCoords() {}

        public PointerCoords(PointerCoords other) {
            copyFrom(other);
        }

        public void clear() {
            x = 0; y = 0; pressure = 1.0f; size = 1.0f;
            touchMajor = 0; touchMinor = 0; toolMajor = 0; toolMinor = 0; orientation = 0;
        }

        public void copyFrom(PointerCoords other) {
            if (other == null) { clear(); return; }
            x = other.x; y = other.y; pressure = other.pressure; size = other.size;
            touchMajor = other.touchMajor; touchMinor = other.touchMinor;
            toolMajor = other.toolMajor; toolMinor = other.toolMinor;
            orientation = other.orientation;
        }

        /** Axis values other than X/Y are not tracked by host events. */
        public float getAxisValue(int axis) {
            if (axis == AXIS_X) return x;
            if (axis == AXIS_Y) return y;
            return 0f;
        }

        public void setAxisValue(int axis, float value) {
            if (axis == AXIS_X) x = value;
            else if (axis == AXIS_Y) y = value;
        }
    }

    /** Per-pointer identity record (AOSP-shaped public fields). */
    public static final class PointerProperties {
        public int id;
        public int toolType = TOOL_TYPE_FINGER;

        public PointerProperties() {}

        public PointerProperties(PointerProperties other) {
            copyFrom(other);
        }

        public void clear() {
            id = 0;
            toolType = TOOL_TYPE_FINGER;
        }

        public void copyFrom(PointerProperties other) {
            if (other == null) { clear(); return; }
            id = other.id;
            toolType = other.toolType;
        }
    }

    public static final int AXIS_X = 0;
    public static final int AXIS_Y = 1;

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

    /** AOSP classic single-pointer factory; times given oldest-first. */
    public static MotionEvent obtain(long downTime, long eventTime, int action, float x, float y, int metaState) {
        MotionEvent event = new MotionEvent(action, x, y, 0, eventTime);
        event.downTime = downTime;
        event.metaState = metaState;
        return event;
    }

    /** Multi-pointer factory: parallel xs/ys/ids arrays. */
    public static MotionEvent obtain(int action, float[] xs, float[] ys, int[] ids, int buttonState, long eventTime, long downTime) {
        return new MotionEvent(action, xs, ys, ids, buttonState, eventTime, downTime);
    }

    /**
     * AOSP-shaped multi-pointer factory: pointer identity and coordinates
     * arrive as parallel records; host events copy them into the flat
     * arrays the platform tracks.
     */
    public static MotionEvent obtain(long downTime, long eventTime, int action, int pointerCount,
            PointerProperties[] pointerProperties, PointerCoords[] pointerCoords,
            int metaState, int buttonState, float xPrecision, float yPrecision,
            int deviceId, int edgeFlags, int source, int flags) {
        int count = Math.max(1, pointerCount);
        float[] xs = new float[count];
        float[] ys = new float[count];
        int[] ids = new int[count];
        for (int i = 0; i < count; i++) {
            if (pointerCoords != null && i < pointerCoords.length && pointerCoords[i] != null) {
                xs[i] = pointerCoords[i].x;
                ys[i] = pointerCoords[i].y;
            }
            if (pointerProperties != null && i < pointerProperties.length && pointerProperties[i] != null) {
                ids[i] = pointerProperties[i].id;
            } else {
                ids[i] = i;
            }
        }
        MotionEvent event = new MotionEvent(action, xs, ys, ids, buttonState, eventTime, downTime);
        event.metaState = metaState;
        event.deviceId = deviceId;
        event.edgeFlags = edgeFlags;
        event.source = source;
        event.flags = flags;
        event.xPrecision = xPrecision;
        event.yPrecision = yPrecision;
        return event;
    }

    /** Copy factory. */
    public static MotionEvent obtain(MotionEvent other) {
        MotionEvent e = new MotionEvent(other.action, other.xs, other.ys, other.ids, other.buttonState, other.eventTime, other.downTime);
        return e;
    }

    public static MotionEvent obtainNoHistory(MotionEvent other) {
        return obtain(other);
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

    public final int getHistorySize() {
        return 0;
    }

    public final int getPointerId(int pointerIndex) {
        return ids[pointerIndex];
    }

    public final int getToolType(int pointerIndex) {
        return TOOL_TYPE_FINGER;
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

    public final float getRawX() {
        return getX();
    }

    public final float getRawY() {
        return getY();
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

    /** W3C-style axis values: X/Y are tracked; other axes read as zero. */
    public final float getAxisValue(int axis) {
        if (axis == AXIS_X) return getX();
        if (axis == AXIS_Y) return getY();
        return 0f;
    }

    /** Host events carry no hardware device identity; 0 is the virtual device. */
    public final int getDeviceId() {
        return deviceId;
    }

    public final int getFlags() {
        return flags;
    }

    public final int getMetaState() {
        return metaState;
    }

    /** Fill {@code out} with pointer {@code pointerIndex}'s coordinates. */
    public final void getPointerCoords(int pointerIndex, PointerCoords out) {
        if (out == null) return;
        out.clear();
        out.x = xs[pointerIndex];
        out.y = ys[pointerIndex];
    }

    /** Fill {@code out} with pointer {@code pointerIndex}'s identity. */
    public final void getPointerProperties(int pointerIndex, PointerProperties out) {
        if (out == null) return;
        out.clear();
        out.id = ids[pointerIndex];
        out.toolType = getToolType(pointerIndex);
    }

    /** Pointer identity matches the touchscreen the host forwards. */
    public final int getSource() {
        return source;
    }

    /** AOSP setter; Compose rewrites the source of synthesized hover/move events. */
    public final void setSource(int source) {
        this.source = source;
    }

    public final float getXPrecision() {
        return xPrecision;
    }

    public final float getYPrecision() {
        return yPrecision;
    }

    public final int getEdgeFlags() {
        return edgeFlags;
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

    /** No pool; present for API compatibility. */
    public final void recycle() {}
}
