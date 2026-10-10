// SPDX-License-Identifier: GPL-3.0-or-later
package android.window;

/** Back gesture progress event. */
public class BackEvent {
    public static final int EDGE_LEFT = 0;
    public static final int EDGE_RIGHT = 1;

    private final float touchX;
    private final float touchY;
    private final float progress;
    private final int swipeEdge;

    public BackEvent(float touchX, float touchY, float progress, int swipeEdge) {
        this.touchX = touchX;
        this.touchY = touchY;
        this.progress = progress;
        this.swipeEdge = swipeEdge;
    }

    public float getTouchX() {
        return touchX;
    }

    public float getTouchY() {
        return touchY;
    }

    public float getProgress() {
        return progress;
    }

    public int getSwipeEdge() {
        return swipeEdge;
    }

    @Override
    public String toString() {
        return "BackEvent{touchX=" + touchX + " touchY=" + touchY
                + " progress=" + progress + " swipeEdge=" + swipeEdge + "}";
    }
}
