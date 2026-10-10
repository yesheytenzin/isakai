// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics.drawable;

import android.content.res.ColorStateList;
import android.graphics.Canvas;

/** Color drawable resolving its color from a state list. */
public class ColorStateListDrawable extends Drawable {
    private ColorStateList colors;

    public ColorStateListDrawable() {}

    public ColorStateListDrawable(ColorStateList colors) {
        this.colors = colors;
    }

    public ColorStateList getColorStateList() { return colors; }

    public void setColorStateList(ColorStateList colors) {
        this.colors = colors;
        invalidateSelf();
    }

    @Override
    public boolean isStateful() {
        return colors != null && colors.isStateful();
    }

    @Override
    protected boolean onStateChange(int[] state) {
        return colors != null && colors.isStateful();
    }

    @Override
    public void draw(Canvas canvas) {
        if (canvas == null) return;
        int color = colors == null ? 0 : colors.getColorForState(getState(), colors.getDefaultColor());
        if (android.graphics.Color.alpha(color) != 0) canvas.drawColor(color);
    }

    @Override
    public int getOpacity() {
        return android.graphics.PixelFormat.TRANSLUCENT;
    }
}
