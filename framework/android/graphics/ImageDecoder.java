// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

import java.io.IOException;

/** Image decode shell: always fails closed. */
public final class ImageDecoder {
    public interface OnHeaderDecodedListener { void onHeaderDecoded(ImageDecoder decoder, ImageInfo info, Source source); }

    /**
     * Incomplete-image listener; decode fails closed here, so callers that
     * accept partial images never receive a callback.
     */
    public interface OnPartialImageListener { boolean onPartialImage(DecodeException e); }

    /** Decode failure carrying the AOSP error codes. */
    public static class DecodeException extends Exception {
        public static final int SOURCE_EXCEPTION = 1;
        public static final int SOURCE_INCOMPLETE = 2;
        public static final int SOURCE_MALFORMED_DATA = 3;

        private final int error;

        public DecodeException(int error, Throwable cause) {
            super(cause);
            this.error = error;
        }

        public int getError() { return error; }
    }

    public static abstract class Source { protected Source() { } }

    public static final int ALLOCATOR_DEFAULT = 0;
    public static final int ALLOCATOR_SOFTWARE = 1;
    public static final int ALLOCATOR_SHARED_MEMORY = 2;
    public static final int ALLOCATOR_HARDWARE = 3;

    public static final int MEMORY_POLICY_DEFAULT = 1;
    public static final int MEMORY_POLICY_LOW_RAM = 0;

    /** Header metadata for a decoded image. */
    public static final class ImageInfo {
        private final android.util.Size size = new android.util.Size(0, 0);

        public android.util.Size getSize() { return size; }
        public String getMimeType() { return ""; }
        /** Host decode has no color-managed pipeline; the honest default is sRGB. */
        public ColorSpace getColorSpace() { return ColorSpace.get(ColorSpace.Named.SRGB); }
    }

    private ImageDecoder() { }

    public static Source createSource(byte[] data) { return new Source() { }; }
    public static Source createSource(byte[] data, int offset, int length) { return new Source() { }; }
    public static Source createSource(java.nio.ByteBuffer buffer) { return new Source() { }; }

    public int getWidth() { return 0; }
    public int getHeight() { return 0; }
    public boolean isAnimated() { return false; }
    /** Allocator preferences have no host decode backend; accepted as state. */
    public void setAllocator(int allocator) { }
    public void setMemorySizePolicy(int policy) { }
    public void setTargetSize(int w, int h) { }
    public void setCrop(Rect crop) { }
    public void setTargetColorSpace(ColorSpace colorSpace) { }
    public void setOnPartialImageListener(OnPartialImageListener listener) { }
    public static Bitmap decodeBitmap(Source src) throws IOException { throw new IOException("unsupported"); }
    public static Bitmap decodeBitmap(Source src, OnHeaderDecodedListener listener) throws IOException { throw new IOException("unsupported"); }
    public static android.graphics.drawable.Drawable decodeDrawable(Source src) throws IOException { throw new IOException("unsupported"); }
    public static android.graphics.drawable.Drawable decodeDrawable(Source src, OnHeaderDecodedListener listener) throws IOException { throw new IOException("unsupported"); }
}
