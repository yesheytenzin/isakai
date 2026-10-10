// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.Color;

/** Solid-color drawable filling its bounds. */
public class ColorDrawable extends Drawable {
    private int color;

    public ColorDrawable() {
        this(0);
    }

    public ColorDrawable(int color) {
        this.color = color;
    }

    public int getColor() { return color; }

    public void setColor(int color) {
        this.color = color;
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        if (canvas != null && Color.alpha(color) != 0) canvas.drawColor(color);
    }

    @Override
    public int getOpacity() {
        switch (Color.alpha(color)) {
            case 0xFF: return android.graphics.PixelFormat.OPAQUE;
            case 0: return android.graphics.PixelFormat.TRANSPARENT;
            default: return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }

    @Override
    public ConstantState getConstantState() {
        return new ColorState(color);
    }

    private static final class ColorState extends ConstantState {
        final int color;

        ColorState(int color) { this.color = color; }

        @Override
        public Drawable newDrawable() { return new ColorDrawable(color); }

        @Override
        public int getChangingConfigurations() { return 0; }
    }
}
