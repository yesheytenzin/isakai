// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

/** Progress bar: level fill over the background. */
public class ProgressBar extends View {
    private int max = 100;
    private int progress;
    private int secondaryProgress;
    private boolean indeterminate;

    public ProgressBar(Context context) {
        super(context);
    }

    public ProgressBar(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    public ProgressBar(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public int getMax() { return max; }
    public void setMax(int max) { this.max = Math.max(1, max); }
    public int getProgress() { return progress; }

    public void setProgress(int progress) {
        this.progress = Math.max(0, Math.min(progress, max));
        invalidate();
    }

    public int getSecondaryProgress() { return secondaryProgress; }
    public void setSecondaryProgress(int progress) { this.secondaryProgress = progress; }
    public boolean isIndeterminate() { return indeterminate; }
    public void setIndeterminate(boolean indeterminate) { this.indeterminate = indeterminate; }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (canvas == null || max <= 0) return;
        int w = getWidth() * progress / max;
        canvas.fillRect(getLeft(), getTop(), getLeft() + w, getBottom(), 0xFF33B5E5);
    }

    @Override
    protected void onDrawAt(Canvas canvas, int offsetX, int offsetY) {
        super.onDraw(canvas);
        if (canvas == null || max <= 0) return;
        int w = getWidth() * progress / max;
        canvas.fillRect(offsetX, offsetY, offsetX + w, offsetY + getHeight(), 0xFF33B5E5);
    }
}
