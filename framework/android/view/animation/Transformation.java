// SPDX-License-Identifier: GPL-3.0-or-later
package android.view.animation;

/** Transformation record: matrix plus alpha. */
public class Transformation {
    public static final int TYPE_IDENTITY = 0;
    public static final int TYPE_ALPHA = 1;
    public static final int TYPE_MATRIX = 2;
    public static final int TYPE_BOTH = 3;

    private int transformationType = TYPE_IDENTITY;
    private float alpha = 1.0f;
    private final android.graphics.Matrix matrix = new android.graphics.Matrix();

    public void clear() {
        transformationType = TYPE_IDENTITY;
        alpha = 1.0f;
        matrix.reset();
    }

    public int getTransformationType() {
        return transformationType;
    }

    public void setTransformationType(int type) {
        this.transformationType = type;
    }

    public float getAlpha() {
        return alpha;
    }

    public void setAlpha(float alpha) {
        this.alpha = alpha;
    }

    public android.graphics.Matrix getMatrix() {
        return matrix;
    }
}
