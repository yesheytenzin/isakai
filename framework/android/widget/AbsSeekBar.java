// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;

/** Seek bar base: progress plus a thumb drawable. */
public abstract class AbsSeekBar extends ProgressBar {
    private Drawable thumb;
    private int thumbOffset;
    private int keyProgressIncrement = 1;

    public AbsSeekBar(Context context) {
        super(context);
    }

    public AbsSeekBar(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    public AbsSeekBar(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setThumb(Drawable thumb) {
        this.thumb = thumb;
    }

    public Drawable getThumb() {
        return thumb;
    }

    public void setThumbOffset(int thumbOffset) {
        this.thumbOffset = thumbOffset;
    }

    public int getThumbOffset() {
        return thumbOffset;
    }

    public void setKeyProgressIncrement(int increment) {
        this.keyProgressIncrement = increment;
    }

    public int getKeyProgressIncrement() {
        return keyProgressIncrement;
    }

    public void onStartTrackingTouch() {
    }

    public void onStopTrackingTouch() {
    }
}
