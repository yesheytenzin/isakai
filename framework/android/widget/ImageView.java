// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.View;

/** Image view: draws its drawable fitted by scale type. */
public class ImageView extends View {
    public enum ScaleType {
        MATRIX, FIT_XY, FIT_START, FIT_CENTER, FIT_END, CENTER, CENTER_CROP, CENTER_INSIDE
    }

    private Drawable drawable;
    private ScaleType scaleType = ScaleType.FIT_CENTER;
    private final Matrix matrix = new Matrix();
    private boolean adjustViewBounds;
    private int maxWidth = Integer.MAX_VALUE;
    private int maxHeight = Integer.MAX_VALUE;
    private int imageLevel;

    public ImageView(Context context) {
        super(context);
    }

    public ImageView(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    public ImageView(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setImageDrawable(Drawable drawable) {
        this.drawable = drawable;
        invalidate();
    }

    public Drawable getDrawable() {
        return drawable;
    }

    public void setImageBitmap(Bitmap bitmap) {
        setImageDrawable(bitmap == null ? null : new BitmapDrawable(bitmap));
    }

    public void setImageResource(int resId) {
        setImageDrawable(new android.graphics.drawable.ColorDrawable(0));
    }

    public void setImageURI(Uri uri) {
        setImageDrawable(null);
    }

    public void setImageLevel(int level) {
        this.imageLevel = level;
        if (drawable != null) drawable.setLevel(level);
    }

    public void setScaleType(ScaleType scaleType) {
        if (scaleType != null) this.scaleType = scaleType;
    }

    public ScaleType getScaleType() {
        return scaleType;
    }

    public void setImageMatrix(Matrix matrix) {
        if (matrix != null) this.matrix.set(matrix);
    }

    public void setAdjustViewBounds(boolean adjust) {
        this.adjustViewBounds = adjust;
    }

    public void setMaxWidth(int maxWidth) {
        this.maxWidth = maxWidth;
    }

    public void setMaxHeight(int maxHeight) {
        this.maxHeight = maxHeight;
    }

    public void setImageTintList(android.content.res.ColorStateList tint) {}
    public android.content.res.ColorStateList getImageTintList() { return null; }
    public void setImageTintMode(android.graphics.PorterDuff.Mode mode) {}
    public android.graphics.PorterDuff.Mode getImageTintMode() { return null; }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (drawable == null || canvas == null) return;
        drawable.setBounds(getLeft(), getTop(), getRight(), getBottom());
        drawable.draw(canvas);
    }
}
