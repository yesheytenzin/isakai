// SPDX-License-Identifier: GPL-3.0-or-later
package org.isakai.bridge;

/**
 * JNI doorway to the Zig PulseAudio client ({@code libisakai_jni.so}).
 *
 * <p>Format: S16LE, rate/channels chosen per stream (see
 * {@code nAudioOpenFormat}); {@code nAudioOpen} is the 44100/stereo
 * default. {@code nAudioOpen*} return a nonzero handle (0 on
 * failure/unsupported format); {@code nAudioWrite} returns 0 on
 * success, -1 on failure; {@code nAudioDrain} returns 0 on success, -1
 * on failure. The Java wrapper turns failures into
 * {@link IllegalStateException}.
 */
public final class IsakaiAudio {
    static {
        System.loadLibrary("isakai_jni");
    }

    private IsakaiAudio() {}

    public static native long nAudioOpen();

    /** Open a stream at {@code rate} Hz / {@code channels}; 0 when unsupported. */
    public static native long nAudioOpenFormat(int rate, int channels);

    public static native int nAudioWrite(long handle, byte[] data, int len);

    public static native int nAudioDrain(long handle);

    public static native void nAudioClose(long handle);
}
