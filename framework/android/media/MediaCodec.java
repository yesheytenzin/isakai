// SPDX-License-Identifier: GPL-3.0-or-later
package android.media;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.isakai.bridge.IsakaiMedia;

/**
 * Synchronous decoder fronting host ffmpeg (software decode).
 *
 * <p>Spike, not product: queued input is accumulated and decoded by one
 * supervised ffmpeg run when end-of-stream arrives; the resulting whole
 * frames are then served FIFO from {@link #dequeueOutputBuffer}. Per-NAL
 * latency, Surface rendering, async callbacks and DRM are out of scope.
 * Decodes {@code video/avc} to yuv420p frames and {@code audio/mp4a-latm}
 * to S16LE stereo 44100 PCM. Any other MIME throws
 * {@link IllegalArgumentException} at creation.
 *
 * <p>Fail-closed: ffmpeg absent or decode failure surfaces as
 * {@link IllegalStateException} from the dequeue call that would have
 * produced the frames, never as silent empty output.
 */
public final class MediaCodec {
    public static final int INFO_TRY_AGAIN_LATER = -1;
    public static final int INFO_OUTPUT_FORMAT_CHANGED = -2;
    public static final int INFO_OUTPUT_BUFFERS_CHANGED = -3;

    public static final int BUFFER_FLAG_SYNC_FRAME = 1;
    public static final int BUFFER_FLAG_CODEC_CONFIG = 2;
    public static final int BUFFER_FLAG_END_OF_STREAM = 4;

    public static final int CONFIGURE_FLAG_ENCODE = 1;

    public static final class BufferInfo {
        public int offset;
        public int size;
        public long presentationTimeUs;
        public int flags;
    }

    private enum State { UNINITIALIZED, CONFIGURED, RUNNING, EOS_DECODED, END }

    private static final int INPUT_SLOTS = 4;
    private static final int INPUT_SLOT_BYTES = 256 * 1024;

    private final String mime;
    private final boolean video;
    private State state = State.UNINITIALIZED;
    private MediaFormat configured;
    private int width;
    private int height;

    private final ByteBuffer[] inputs = new ByteBuffer[INPUT_SLOTS];
    private int nextInput;
    // Accumulated elementary-stream bytes plus the queued pts range, so
    // output frames get byte-proportional timestamps (exact for CBR).
    private final List<byte[]> pending = new ArrayList<>();
    private long firstPtsUs;
    private long lastPtsUs;
    private boolean sawInputEos;
    private long pendingBytes;

    private final Deque<Frame> decoded = new ArrayDeque<>();
    private final List<ByteBuffer> outputs = new ArrayList<>();
    private boolean formatChangePending;
    private boolean outputEosDelivered;
    private IOExceptionHolder failure;

    private static final class Frame {
        final byte[] bytes;
        final long ptsUs;
        Frame(byte[] bytes, long ptsUs) { this.bytes = bytes; this.ptsUs = ptsUs; }
    }

    private static final class IOExceptionHolder {
        final java.io.IOException cause;
        IOExceptionHolder(java.io.IOException cause) { this.cause = cause; }
    }

    private MediaCodec(String mime, boolean video) {
        this.mime = mime;
        this.video = video;
        for (int i = 0; i < INPUT_SLOTS; i++) inputs[i] = ByteBuffer.allocate(INPUT_SLOT_BYTES);
    }

    public static MediaCodec createDecoderByType(String mime) {
        if ("video/avc".equals(mime)) return new MediaCodec(mime, true);
        if ("audio/mp4a-latm".equals(mime)) return new MediaCodec(mime, false);
        throw new IllegalArgumentException("no spike decoder for " + mime);
    }

    public void configure(MediaFormat format, android.view.Surface surface, MediaCrypto crypto, int flags) {
        if (state != State.UNINITIALIZED) throw new IllegalStateException("configure in " + state);
        if (format == null) throw new IllegalArgumentException("format == null");
        if ((flags & CONFIGURE_FLAG_ENCODE) != 0) throw new UnsupportedOperationException("spike is decode-only");
        if (surface != null) throw new UnsupportedOperationException("surface render not in spike: dump output buffers");
        if (crypto != null) throw new UnsupportedOperationException("drm-backed decode unavailable");
        this.configured = format;
        if (video) {
            this.width = format.getInteger(MediaFormat.KEY_WIDTH);
            this.height = format.getInteger(MediaFormat.KEY_HEIGHT);
        }
        state = State.CONFIGURED;
    }

    public void start() {
        if (state != State.CONFIGURED) throw new IllegalStateException("start in " + state);
        state = State.RUNNING;
        formatChangePending = true;
    }

    public void flush() {
        if (state != State.RUNNING && state != State.EOS_DECODED) throw new IllegalStateException("flush in " + state);
        pending.clear();
        pendingBytes = 0;
        sawInputEos = false;
        decoded.clear();
        outputs.clear();
        outputEosDelivered = false;
        failure = null;
        state = State.RUNNING;
    }

    public void stop() {
        if (state == State.END) throw new IllegalStateException("released");
        pending.clear();
        decoded.clear();
        outputs.clear();
        state = State.CONFIGURED;
    }

    public void release() {
        pending.clear();
        decoded.clear();
        outputs.clear();
        state = State.END;
    }

    public int dequeueInputBuffer(long timeoutUs) {
        if (state != State.RUNNING) throw new IllegalStateException("dequeueInputBuffer in " + state);
        if (sawInputEos) return INFO_TRY_AGAIN_LATER;
        int idx = nextInput;
        nextInput = (nextInput + 1) % INPUT_SLOTS;
        inputs[idx].clear();
        return idx;
    }

    public ByteBuffer getInputBuffer(int idx) {
        if (idx < 0 || idx >= INPUT_SLOTS) throw new IllegalArgumentException("input " + idx);
        return inputs[idx];
    }

    public void queueInputBuffer(int idx, int offset, int size, long presentationTimeUs, int flags) {
        if (state != State.RUNNING) throw new IllegalStateException("queueInputBuffer in " + state);
        if (idx < 0 || idx >= INPUT_SLOTS) throw new IllegalArgumentException("input " + idx);
        if (pending.isEmpty()) firstPtsUs = presentationTimeUs;
        if (size > 0) {
            byte[] chunk = new byte[size];
            ByteBuffer src = inputs[idx];
            src.position(offset);
            src.get(chunk, 0, size);
            pending.add(chunk);
            pendingBytes += size;
        }
        lastPtsUs = presentationTimeUs;
        if ((flags & BUFFER_FLAG_END_OF_STREAM) != 0) sawInputEos = true;
    }

    public int dequeueOutputBuffer(BufferInfo info, long timeoutUs) {
        if (state != State.RUNNING && state != State.EOS_DECODED)
            throw new IllegalStateException("dequeueOutputBuffer in " + state);
        if (formatChangePending) {
            formatChangePending = false;
            return INFO_OUTPUT_FORMAT_CHANGED;
        }
        if (failure != null) throw new IllegalStateException("decode failed: " + failure.cause.getMessage(), failure.cause);
        if (!decoded.isEmpty()) return emit(info);
        if (state == State.EOS_DECODED) {
            if (outputEosDelivered) return INFO_TRY_AGAIN_LATER;
            outputEosDelivered = true;
            info.offset = 0;
            info.size = 0;
            info.presentationTimeUs = lastPtsUs;
            info.flags = BUFFER_FLAG_END_OF_STREAM;
            return outputs.size();
        }
        if (!sawInputEos) return INFO_TRY_AGAIN_LATER;
        decodeAll();
        if (failure != null) throw new IllegalStateException("decode failed: " + failure.cause.getMessage(), failure.cause);
        state = State.EOS_DECODED;
        if (!decoded.isEmpty()) return emit(info);
        outputEosDelivered = true;
        info.offset = 0;
        info.size = 0;
        info.presentationTimeUs = lastPtsUs;
        info.flags = BUFFER_FLAG_END_OF_STREAM;
        return outputs.size();
    }

    private int emit(BufferInfo info) {
        Frame f = decoded.removeFirst();
        outputs.add(ByteBuffer.wrap(f.bytes));
        info.offset = 0;
        info.size = f.bytes.length;
        info.presentationTimeUs = f.ptsUs;
        info.flags = 0;
        return outputs.size() - 1;
    }

    private void decodeAll() {
        byte[] es = new byte[(int) pendingBytes];
        int pos = 0;
        for (byte[] chunk : pending) {
            System.arraycopy(chunk, 0, es, pos, chunk.length);
            pos += chunk.length;
        }
        try {
            if (video) {
                List<byte[]> frames = IsakaiMedia.decodeVideoEs(es, width, height);
                long span = lastPtsUs - firstPtsUs;
                for (int i = 0; i < frames.size(); i++) {
                    long pts = frames.size() == 1 ? firstPtsUs
                            : firstPtsUs + span * i / (frames.size() - 1);
                    decoded.add(new Frame(frames.get(i), pts));
                }
            } else {
                byte[] pcm = IsakaiMedia.decodeAudioEs(es);
                decoded.add(new Frame(pcm, firstPtsUs));
            }
        } catch (java.io.IOException e) {
            failure = new IOExceptionHolder(e);
        }
    }

    public ByteBuffer getOutputBuffer(int idx) {
        if (idx < 0 || idx >= outputs.size()) throw new IllegalArgumentException("output " + idx);
        return outputs.get(idx).duplicate();
    }

    public MediaFormat getOutputFormat() {
        if (state == State.UNINITIALIZED || state == State.END) throw new IllegalStateException("getOutputFormat in " + state);
        if (video) {
            MediaFormat f = MediaFormat.createVideoFormat(mime, width, height);
            f.setString("color-format", "yuv420p");
            return f;
        }
        MediaFormat f = MediaFormat.createAudioFormat(mime, 44100, 2);
        f.setString("pcm-encoding", "s16le");
        return f;
    }

    public void releaseOutputBuffer(int idx, boolean render) {
        if (render) throw new UnsupportedOperationException("surface render not in spike");
    }

    public String getName() {
        return "isakai-ffmpeg-" + (video ? "avc" : "aac");
    }
}
