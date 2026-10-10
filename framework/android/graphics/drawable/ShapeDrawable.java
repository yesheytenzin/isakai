// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.shapes.Shape;

/** Drawable delegating geometry to a Shape with its own Paint. */
public class ShapeDrawable extends Drawable {
    private Shape shape;
    private final Paint paint = new Paint();

    public ShapeDrawable() {}

    public ShapeDrawable(Shape shape) {
        this.shape = shape;
    }

    public Shape getShape() { return shape; }
    public void setShape(Shape shape) { this.shape = shape; }

    public Paint getPaint() { return paint; }

    public void setIntrinsicWidth(int width) {}
    public void setIntrinsicHeight(int height) {}

    @Override
    public void draw(Canvas canvas) {
        if (canvas == null || shape == null) return;
        Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0) return;
        canvas.save();
        try {
            shape.resize(b.width(), b.height());
            shape.draw(canvas, paint);
        } finally {
            canvas.restore();
        }
    }

    @Override
    public int getOpacity() {
        return android.graphics.PixelFormat.TRANSLUCENT;
    }
}
