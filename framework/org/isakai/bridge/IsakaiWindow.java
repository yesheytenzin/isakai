// SPDX-License-Identifier: GPL-3.0-or-later
package org.isakai.bridge;

import java.nio.ByteBuffer;

/**
 * JNI doorway to the Zig Wayland client ({@code libisakai_jni.so}).
 *
 * <p>Two present paths: the copy path ({@link #nPost} from an ARGB
 * {@code int[]}; native rewrites to XRGB) and the zero-copy path
 * ({@link #nMapBuffer} hands out a direct {@link ByteBuffer} over SHM,
 * Java draws into it, {@link #nCommit} presents it). The mapped buffer is
 * only valid until commit/post; a resize remakes the native pool and any
 * previously mapped buffer is stale and must be re-mapped. Set
 * {@code ISAKAI_ZEROCOPY=0} to force the copy path.
 *
 * <p>Native errors surface as {@code 0}/{@code -1}/null returns,
 * translated to {@link IllegalStateException} by the wrapper below.
 */
public final class IsakaiWindow {
    static {
        System.loadLibrary("isakai_jni");
    }

    private IsakaiWindow() {}

    public static native long nOpen(String title, String appId, int w, int h);

    /** Copy path. Returns 0 on success, -1 on failure. */
    public static native int nPost(long handle, int[] pixels, int w, int height, int stride);

    /**
     * Zero-copy map: reserves a free SHM slot and wraps it in a direct
     * ByteBuffer of w*h*4 bytes. {@code outSlot[0]} receives the slot
     * index. Returns null when zero-copy is disabled, no slot is free,
     * or the pool remake fails.
     */
    public static native ByteBuffer nMapBuffer(long handle, int[] outSlot, int w, int h);

    /**
     * Commit the buffer from {@link #nMapBuffer}. Returns 1 when a mapped
     * buffer was committed, 0 when stale (nothing outstanding).
     */
    public static native int nCommit(long handle, int w, int h);

    /** Poll the compositor and report a pending resize, packed (w&lt;&lt;32)|h; 0 = none. */
    public static native long nPollSize(long handle);

    /** Current window size, packed (w&lt;&lt;32)|h; 0 on bad handle. */
    public static native long nSize(long handle);

    /** Returns 0 when the window closed, 1 on timeout, negative on error. */
    public static native int nRun(long h, long timeoutMs);

    /**
     * Drain queued input events into {@code batch} as groups of 8 ints,
     * kind first (see the M3 contract). Returns the event count, 0 for
     * none, negative on error (bad handle, short batch). The wrapper
     * turns negatives into {@link IllegalStateException}.
     */
    public static native int nPollInput(long handle, int[] batch);

    /**
     * Poll the compositor and report a pending DisplayMetrics snapshot
     * into {@code out} (int[8]: {@code [w, h, densityDpi, density*1000,
     * scaled*1000, xdpi*1000, ydpi*1000, scale]}). Returns 1 when fresh
     * (the first poll after open is always fresh: scale=1 fallback when
     * no wl_output is bound), 0 when nothing new, negative on error.
     * The compositor scale event feeds {@code densityDpi = 160*scale};
     * apply with {@code DisplayMetrics.setFromSnapshot}. Touch rides
     * nPollInput kinds 7/8.
     */
    public static native int nPollMetrics(long handle, int[] out);

    /**
     * Copy {@code text} into the Wayland selection (empty clears).
     * Returns 0 on success, negative on error. Silent local-only when no
     * data device is bound; the ClipboardManager keeps its own copy.
     */
    public static native int nClipboardSet(long handle, String text);

    /**
     * Paste the current selection, or null when no text is offered or
     * the transfer fails.
     */
    public static native String nClipboardGet(long handle);

    /** 1 when the remote offer carries text, 0 otherwise. No I/O. */
    public static native int nClipboardHasText(long handle);

    public static native void nClose(long h);

    /** Unpack width from a packed size long. */
    public static int unpackW(long packed) {
        return (int) (packed >>> 32);
    }

    /** Unpack height from a packed size long. */
    public static int unpackH(long packed) {
        return (int) (packed & 0xFFFFFFFFL);
    }

    /** AutoCloseable handle wrapper around the native window pointer. */
    public static final class Window implements AutoCloseable {
        private long handle;

        private Window(long handle) {
            this.handle = handle;
        }

        private long h() {
            if (handle == 0) throw new IllegalStateException("isakai: window closed");
            return handle;
        }

        private static int checked(int r, String what) {
            if (r < 0) throw new IllegalStateException("isakai: " + what + " failed");
            return r;
        }

        public static Window open(String title, String appId, int w, int h) {
            long ptr = nOpen(title, appId, w, h);
            if (ptr == 0) throw new IllegalStateException("isakai: nOpen failed");
            return new Window(ptr);
        }
        public void post(int[] pixels, int w, int height, int stride) {
            checked(nPost(h(), pixels, w, height, stride), "nPost");
        }

        public ByteBuffer mapBuffer(int[] outSlot, int w, int h) {
            return nMapBuffer(h(), outSlot, w, h);
        }

        public int commit(int w, int h) {
            return nCommit(h(), w, h);
        }

        /** Current size, packed (w&lt;&lt;32)|h. */
        public long size() {
            return nSize(h());
        }

        /** Pending resize, packed (w&lt;&lt;32)|h, or 0 when none. */
        public long pollSize() {
            return nPollSize(h());
        }

        /**
         * Drain queued input events into {@code batch} as groups of 8
         * ints, kind first. Returns the event count, 0 when empty.
         */
        public int pollInput(int[] batch) {
            return checked(nPollInput(h(), batch), "nPollInput");
        }

        /**
         * Pending DisplayMetrics snapshot into {@code out} (int[8], layout
         * as {@link #nPollMetrics}): 1 = fresh and written, 0 = nothing
         * new ({@code out} untouched). Requires length >= 8; throws
         * IllegalStateException on native failure.
         */
        public int pollMetrics(int[] out) {
            if (out == null || out.length < 8) throw new IllegalStateException("isakai: nPollMetrics needs int[8]");
            int r = nPollMetrics(h(), out);
            if (r < 0) throw new IllegalStateException("isakai: nPollMetrics failed");
            return r;
        }
        /**
         * Copy {@code text} into the Wayland selection (null/empty
         * clears). The manager keeps its own copy regardless.
         */
        public void clipboardSet(String text) {
            checked(nClipboardSet(h(), text == null ? "" : text), "nClipboardSet");
        }

        /** Paste the current selection, or null when unavailable. */
        public String clipboardGet() {
            if (handle == 0) throw new IllegalStateException("isakai: window closed");
            return nClipboardGet(handle);
        }

        /** True when the remote offer carries text. No I/O. */
        public boolean clipboardHasText() {
            if (handle == 0) throw new IllegalStateException("isakai: window closed");
            return nClipboardHasText(handle) != 0;
        }

        /** Dispatch events until close or timeout; 0 = closed, 1 = timeout. */
        public int run(long timeoutMs) {
            return checked(nRun(h(), timeoutMs), "nRun");
        }

        @Override
        public void close() {
            if (handle != 0) {
                nClose(handle);
                handle = 0;
            }
        }
    }
}
