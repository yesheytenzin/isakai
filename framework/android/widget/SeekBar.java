// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.Context;
import android.view.MotionEvent;

/** Seek bar: draggable progress with change listener. */
public class SeekBar extends AbsSeekBar {
    public interface OnSeekBarChangeListener {
        void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser);
        void onStartTrackingTouch(SeekBar seekBar);
        void onStopTrackingTouch(SeekBar seekBar);
    }

    private OnSeekBarChangeListener listener;
    private boolean tracking;

    public SeekBar(Context context) {
        super(context);
        setClickable(true);
    }

    public SeekBar(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        setClickable(true);
    }

    public SeekBar(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setClickable(true);
    }

    public void setOnSeekBarChangeListener(OnSeekBarChangeListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            tracking = true;
            if (listener != null) listener.onStartTrackingTouch(this);
            updateFromTouch(event.getX());
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE && tracking) {
            updateFromTouch(event.getX());
            return true;
        }
        if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && tracking) {
            tracking = false;
            if (listener != null) listener.onStopTrackingTouch(this);
            return true;
        }
        return super.onTouchEvent(event);
    }

    private void updateFromTouch(float x) {
        int w = getWidth();
        if (w <= 0) return;
        int p = (int) ((x - getLeft()) / w * getMax());
        setProgress(Math.max(0, Math.min(p, getMax())));
        if (listener != null) listener.onProgressChanged(this, getProgress(), true);
    }
}
