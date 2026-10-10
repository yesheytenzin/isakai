// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics.drawable.shapes;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/** Inscribed ellipse fill. */
public class OvalShape extends Shape {
    public OvalShape() {}

    @Override
    public void draw(Canvas canvas, Paint paint) {
        if (canvas == null || paint == null) return;
        int color = paint.getColor();
        if (Color.alpha(color) == 0) return;
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        float cx = w / 2.0f;
        float cy = h / 2.0f;
        for (int y = 0; y < (int) h; y++) {
            float ny = (y + 0.5f - cy) / (h / 2.0f);
            float span = (float) Math.sqrt(Math.max(0, 1 - ny * ny)) * w / 2.0f;
            canvas.fillRect((int) (cx - span), y, (int) (cx + span + 0.5f), y + 1, color);
        }
    }
}
