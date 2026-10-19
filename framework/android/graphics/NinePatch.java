// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

/**
 * Nine-patch image: a bitmap plus stretch metadata. Isakai does not stretch
 * patch edges; the bitmap is drawn as one image into the destination rect.
 */
public class NinePatch {
    private final Bitmap bitmap;

    public NinePatch(Bitmap bitmap, byte[] chunk, String srcName) {
        this.bitmap = bitmap;
    }

    /** True when the chunk bytes carry the AOSP nine-patch marker. */
    public static boolean isNinePatchChunk(byte[] chunk) {
        return chunk != null && chunk.length >= 4 && chunk[0] == 1;
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    public int getWidth() {
        return bitmap == null ? 0 : bitmap.getWidth();
    }

    public int getHeight() {
        return bitmap == null ? 0 : bitmap.getHeight();
    }

    /** Draw into {@code location}, scaled to its bounds. */
    public void draw(Canvas canvas, Rect location) {
        if (canvas == null || location == null || bitmap == null) return;
        canvas.drawBitmap(bitmap, location.left, location.top,
                location.width(), location.height());
    }

    public void draw(Canvas canvas, Rect location, Paint paint) {
        draw(canvas, location);
    }
}
