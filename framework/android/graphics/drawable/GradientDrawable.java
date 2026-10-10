// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;

/**
 * Shape drawable: fills bounds with a solid color; stroke, gradient,
 * corners, and ring/oval shapes are recorded but render as a fill.
 */
public class GradientDrawable extends Drawable {
    public static final int RECTANGLE = 0;
    public static final int OVAL = 1;
    public static final int LINE = 2;
    public static final int RING = 3;

    public enum Orientation {
        TOP_BOTTOM, TR_BL, RIGHT_LEFT, BR_TL, BOTTOM_TOP, BL_TR, LEFT_RIGHT, TL_BR
    }

    private int shape = RECTANGLE;
    private int color = 0;
    private float cornerRadius;
    private float[] cornerRadii;
    private int strokeWidth;
    private int strokeColor;

    public GradientDrawable() {}

    public GradientDrawable(Orientation orientation, int[] colors) {
        if (colors != null && colors.length > 0) this.color = colors[0];
    }

    public void setShape(int shape) { this.shape = shape; }
    public int getShape() { return shape; }

    public void setColor(int color) {
        this.color = color;
        invalidateSelf();
    }

    public void setColors(int[] colors) {
        if (colors != null && colors.length > 0) setColor(colors[0]);
    }

    public void setCornerRadius(float radius) { this.cornerRadius = radius; }
    public float getCornerRadius() { return cornerRadius; }
    public void setCornerRadii(float[] radii) { this.cornerRadii = radii == null ? null : radii.clone(); }
    public float[] getCornerRadii() { return cornerRadii == null ? null : cornerRadii.clone(); }

    public void setStroke(int width, int color) {
        this.strokeWidth = width;
        this.strokeColor = color;
        invalidateSelf();
    }

    public void setSize(int width, int height) {}

    @Override
    public void draw(Canvas canvas) {
        if (canvas == null || Color.alpha(color) == 0) return;
        Rect b = getBounds();
        canvas.fillRect(b.left, b.top, b.right, b.bottom, color);
    }

    @Override
    public int getOpacity() {
        switch (Color.alpha(color)) {
            case 0xFF: return android.graphics.PixelFormat.OPAQUE;
            case 0: return android.graphics.PixelFormat.TRANSPARENT;
            default: return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }
}
