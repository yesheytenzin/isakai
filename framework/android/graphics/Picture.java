// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

/** Recorded picture: begin/end link, rendering ignores the content. */
public class Picture {
    private int width;
    private int height;

    public Picture() {}

    public Picture(Picture src) {
        if (src != null) {
            this.width = src.width;
            this.height = src.height;
        }
    }

    public Canvas beginRecording(int width, int height) {
        this.width = width;
        this.height = height;
        return new Canvas(Math.max(1, width), Math.max(1, height));
    }

    public void endRecording() {}

    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public void draw(Canvas canvas) {}

    public void writeToStream(java.io.OutputStream stream) {}
}
