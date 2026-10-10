// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

import java.io.IOException;

/** Image decode shell: always fails closed. */
public final class ImageDecoder {
    public interface OnHeaderDecodedListener { void onHeaderDecoded(ImageDecoder decoder, ImageInfo info, Source source); }
    public static abstract class Source { protected Source() { } }
    public static final class ImageInfo {
        private final Size size = new Size(0, 0);
        public Size getSize() { return size; }
        public String getMimeType() { return ""; }
    }
    public static final class Size { private final int w; private final int h; public Size(int w, int h) { this.w = w; this.h = h; } public int getWidth() { return w; } public int getHeight() { return h; } }
    private ImageDecoder() { }
    public static Source createSource(byte[] data) { return new Source() { }; }
    public static Source createSource(byte[] data, int offset, int length) { return new Source() { }; }
    public int getWidth() { return 0; }
    public int getHeight() { return 0; }
    public boolean isAnimated() { return false; }
    public void setTargetSize(int w, int h) { }
    public void setCrop(Rect crop) { }
    public static Bitmap decodeBitmap(Source src) throws IOException { throw new IOException("unsupported"); }
    public static Bitmap decodeBitmap(Source src, OnHeaderDecodedListener listener) throws IOException { throw new IOException("unsupported"); }
}
