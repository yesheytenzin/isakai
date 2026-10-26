// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

/** Pinch-scale detector shell: listener dispatch driven by tests. */
public class ScaleGestureDetector {
    public interface OnScaleGestureListener {
        boolean onScale(ScaleGestureDetector detector);
        boolean onScaleBegin(ScaleGestureDetector detector);
        void onScaleEnd(ScaleGestureDetector detector);
    }
    public static class SimpleOnScaleGestureListener implements OnScaleGestureListener {
        @Override public boolean onScale(ScaleGestureDetector detector) { return false; }
        @Override public boolean onScaleBegin(ScaleGestureDetector detector) { return true; }
        @Override public void onScaleEnd(ScaleGestureDetector detector) {}
    }
    private final OnScaleGestureListener listener;
    private boolean inProgress;
    private float scaleFactor = 1.0f;
    private float focusX;
    private float focusY;
    private boolean quickScaleEnabled = true;
    private boolean stylusScaleEnabled = true;
    public ScaleGestureDetector(android.content.Context context, OnScaleGestureListener listener) { this.listener = listener; }
    public boolean onTouchEvent(MotionEvent event) { return false; }
    public boolean isInProgress() { return inProgress; }
    public float getScaleFactor() { return scaleFactor; }
    public float getFocusX() { return focusX; }
    public float getFocusY() { return focusY; }
    public void setQuickScaleEnabled(boolean enabled) { quickScaleEnabled = enabled; }
    public boolean isQuickScaleEnabled() { return quickScaleEnabled; }
    public void setStylusScaleEnabled(boolean enabled) { stylusScaleEnabled = enabled; }
    public boolean isStylusScaleEnabled() { return stylusScaleEnabled; }
}
