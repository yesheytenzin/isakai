// SPDX-License-Identifier: GPL-3.0-or-later
package android.view.animation;

import android.content.Context;
import android.graphics.Path;
import android.util.AttributeSet;

/** Cubic-Bezier easing through the given control points. */
public class PathInterpolator implements Interpolator {
    private static final float PRECISION = 0.002f;

    private final float controlX1;
    private final float controlY1;
    private final float controlX2;
    private final float controlY2;

    public PathInterpolator(float controlX1, float controlY1, float controlX2, float controlY2) {
        this.controlX1 = controlX1;
        this.controlY1 = controlY1;
        this.controlX2 = controlX2;
        this.controlY2 = controlY2;
    }

    public PathInterpolator(Path path) {
        this(0.33f, 0.0f, 0.67f, 1.0f);
    }

    public PathInterpolator(Context context, AttributeSet attrs) {
        this(0.33f, 0.0f, 0.67f, 1.0f);
    }

    @Override
    public float getInterpolation(float input) {
        if (input <= 0.0f) return 0.0f;
        if (input >= 1.0f) return 1.0f;
        float t = input;
        for (int i = 0; i < 8; i++) {
            float x = cubic(t, 0.0f, controlX1, controlX2, 1.0f) - input;
            if (Math.abs(x) < PRECISION) break;
            float dx = cubicDerivative(t, 0.0f, controlX1, controlX2, 1.0f);
            if (Math.abs(dx) < 1e-6f) break;
            t -= x / dx;
            if (t < 0.0f) t = 0.0f;
            else if (t > 1.0f) t = 1.0f;
        }
        return cubic(t, 0.0f, controlY1, controlY2, 1.0f);
    }

    private static float cubic(float t, float p0, float p1, float p2, float p3) {
        float u = 1.0f - t;
        return u * u * u * p0 + 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t * p3;
    }

    private static float cubicDerivative(float t, float p0, float p1, float p2, float p3) {
        float u = 1.0f - t;
        return 3 * u * u * (p1 - p0) + 6 * u * t * (p2 - p1) + 3 * t * t * (p3 - p2);
    }
}
