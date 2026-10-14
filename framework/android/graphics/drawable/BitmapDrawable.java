// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics.drawable;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;

/** Bitmap drawable: draws the bitmap scaled to its bounds. */
public class BitmapDrawable extends Drawable {
    private Bitmap bitmap;
    private final Paint paint = new Paint();

    public BitmapDrawable() {}

    public BitmapDrawable(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    public BitmapDrawable(android.content.res.Resources res, Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    public Bitmap getBitmap() { return bitmap; }

    public Paint getPaint() { return paint; }

    public void setTileModeX(Shader.TileMode mode) {}
    public void setTileModeY(Shader.TileMode mode) {}
    public void setTileModeXY(Shader.TileMode x, Shader.TileMode y) {}

    @Override
    public void draw(Canvas canvas) {
        if (canvas == null || bitmap == null || bitmap.isRecycled()) return;
        Rect b = getBounds();
        canvas.drawBitmapFiltered(bitmap, b.left, b.top, b.width(), b.height(), this::tintedColor);
    }

    @Override
    public int getIntrinsicWidth() {
        return bitmap == null ? -1 : bitmap.getWidth();
    }

    @Override
    public int getIntrinsicHeight() {
        return bitmap == null ? -1 : bitmap.getHeight();
    }

    @Override
    public int getOpacity() {
        return android.graphics.PixelFormat.TRANSLUCENT;
    }
}
