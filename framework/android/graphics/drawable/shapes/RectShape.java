// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics.drawable.shapes;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

/** Axis-aligned rectangle outline/fill. */
public class RectShape extends Shape {
    private RectF rect;

    public RectShape() {}

    @Override
    protected void onResize(float width, float height) {
        rect = new RectF(0, 0, width, height);
    }

    public RectF rect() {
        if (rect == null) rect = new RectF(0, 0, getWidth(), getHeight());
        return rect;
    }

    @Override
    public void draw(Canvas canvas, Paint paint) {
        if (canvas == null || paint == null) return;
        RectF r = rect();
        int color = paint.getColor();
        if (Color.alpha(color) == 0) return;
        canvas.fillRect((int) r.left, (int) r.top, (int) r.right, (int) r.bottom, color);
    }

    @Override
    public RectShape clone() throws CloneNotSupportedException {
        RectShape s = (RectShape) super.clone();
        s.rect = null;
        return s;
    }
}
