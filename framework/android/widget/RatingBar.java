// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.Context;
import android.graphics.Canvas;

/** Star rating bar: rating fraction fill. */
public class RatingBar extends AbsSeekBar {
    private int numStars = 5;
    private float rating;
    private float stepSize = 0.5f;

    public RatingBar(Context context) {
        super(context);
        setMax(100);
    }

    public RatingBar(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        setMax(100);
    }

    public RatingBar(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setMax(100);
    }

    public int getNumStars() { return numStars; }
    public void setNumStars(int numStars) { this.numStars = Math.max(1, numStars); }
    public float getRating() { return rating; }

    public void setRating(float rating) {
        this.rating = Math.max(0, Math.min(rating, numStars));
        setProgress(numStars == 0 ? 0 : (int) (this.rating / numStars * getMax()));
    }

    public float getStepSize() { return stepSize; }
    public void setStepSize(float stepSize) { this.stepSize = stepSize; }
    public boolean isIndicator() { return false; }
    public void setIsIndicator(boolean indicator) {}
}
