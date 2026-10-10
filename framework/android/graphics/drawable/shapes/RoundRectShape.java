// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics.drawable.shapes;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

/** Rounded rectangle: radii recorded, renders as a fill. */
public class RoundRectShape extends RectShape {
    private final float[] outerRadii;
    private final RectF inset;
    private final float[] innerRadii;

    public RoundRectShape(float[] outerRadii, RectF inset, float[] innerRadii) {
        this.outerRadii = outerRadii == null ? null : outerRadii.clone();
        this.inset = inset == null ? null : new RectF(inset);
        this.innerRadii = innerRadii == null ? null : innerRadii.clone();
    }

    @Override
    public void draw(Canvas canvas, Paint paint) {
        super.draw(canvas, paint);
    }

    @Override
    public RoundRectShape clone() throws CloneNotSupportedException {
        return (RoundRectShape) super.clone();
    }
}
