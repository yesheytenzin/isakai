// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics.drawable;

import android.content.res.ColorStateList;
import android.graphics.Canvas;

/** Ripple drawable: renders the content layer, ripple ignored. */
public class RippleDrawable extends LayerDrawable {
    private ColorStateList color;

    public RippleDrawable(ColorStateList color, Drawable content, Drawable mask) {
        super(content == null ? new Drawable[0] : new Drawable[]{content});
        this.color = color;
    }

    public void setColor(ColorStateList color) {
        this.color = color;
        invalidateSelf();
    }

    public ColorStateList getColor() { return color; }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
    }
}
