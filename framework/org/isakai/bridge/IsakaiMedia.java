// SPDX-License-Identifier: GPL-3.0-or-later
package org.isakai.bridge;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

/**
 * Media helpers backed by the host {@code ffmpeg}/{@code ffprobe} CLIs.
 * No native deps; fails closed when ffmpeg is absent.
 *
 * <p>Used by {@link android.media.MediaPlayer} (audio decode → S16LE PCM
 * at 44.1k stereo, fed to {@link IsakaiAudio}) and
 * {@link android.media.MediaMetadataRetriever} (frame extract → PNG →
 * {@link Bitmap}).
 *
 * <p>Every host process goes through {@link #run}: the deadline starts
 * before the process is awaited, both pipes are drained concurrently, and
 * the child is killed and reaped on timeout, interrupt or overflow. The
 * earlier shape (read stdout to EOF, then stderr, then waitFor) could not
 * time out a decoder that never wrote: a hung ffmpeg blocked the caller
 * forever.
 */
public final class IsakaiMedia {
    private static final String TAG = "IsakaiMedia";
    private static final long PROC_TIMEOUT_MS = 15_000;
    private static final int MAX_PCM_BYTES = 64 * 1024 * 1024;
    private static final int MAX_STDERR_BYTES = 64 * 1024;

    private IsakaiMedia() {}

    public static final class Decoded {
        public final byte[] bytes;
        public final int rate;
        public final int channels;
        public final int durationMs;

        Decoded(byte[] bytes, int rate, int channels, int durationMs) {
            this.bytes = bytes;
            this.rate = rate;
            this.channels = channels;
            this.durationMs = durationMs;
        }
    }

    /** Result of one supervised host process (package-private for tests). */
    static final class Proc {
        int exit;
        byte[] out = new byte[0];
        byte[] err = new byte[0];

        String errorText(String fallback) {
            return err.length > 0 ? new String(err).trim() : fallback;
        }
    }

    /**
     * Run a host process with concurrent pipe draining and a hard deadline.
     * {@code maxOut} is a hard cap (stdout is binary payload; exceeding it
     * kills the child and throws); stderr keeps the last {@code maxErr}
     * bytes because ffmpeg reports the failure at the end.
     */
    static Proc run(ProcessBuilder pb, int maxOut, int maxErr, long timeoutMs) throws IOException {
        return supervise(pb, null, maxOut, maxErr, timeoutMs);
    }

    /** Same supervisor, but {@code input} is piped to the child stdin first. */
    static Proc runWithInput(ProcessBuilder pb, byte[] input, int maxOut, int maxErr, long timeoutMs) throws IOException {
        return supervise(pb, input, maxOut, maxErr, timeoutMs);
    }

    private static Proc supervise(ProcessBuilder pb, byte[] input, int maxOut, int maxErr, long timeoutMs) throws IOException {
        Process proc;
        try {
            proc = pb.start();
        } catch (IOException e) {
            throw new IOException(pb.command().get(0) + " start failed: " + e.getMessage(), e);
        }
        OutputStream stdin = proc.getOutputStream();
        try {
            // Decoders must never block on a stdin that will not arrive.
            if (input != null) stdin.write(input);
            stdin.close();
        } catch (IOException ignored) {
        }
        Drain out = new Drain(proc.getInputStream(), maxOut, false);
        Drain err = new Drain(proc.getErrorStream(), maxErr, true);
        out.start();
        err.start();
        boolean finished;
        try {
            finished = proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            kill(proc);
            out.joinQuietly();
            err.joinQuietly();
            throw new IOException("interrupted", e);
        }
        if (!finished) {
            kill(proc);
            out.joinQuietly();
            err.joinQuietly();
            throw new IOException(pb.command().get(0) + " timed out after " + timeoutMs + " ms");
        }
        out.joinQuietly();
        err.joinQuietly();
        Proc result = new Proc();
        result.exit = proc.exitValue();
        if (out.overflow) {
            throw new IOException(pb.command().get(0) + " output exceeded " + maxOut + " bytes");
        }
        result.out = out.bytes();
        result.err = err.bytes();
        return result;
    }

    private static void kill(Process proc) {
        proc.destroyForcibly();
        try {
            proc.waitFor(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Drains one pipe. stdout is capped (overflowing is an error, so the
     * reader stops and the supervisor kills the child); stderr keeps a
     * rolling tail of the last {@code max} bytes.
     */
    private static final class Drain extends Thread {
        private final InputStream in;
        private final int max;
        private final boolean tail;
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        private final byte[] ring;
        private int ringSize;
        private int ringStart;
        volatile boolean overflow;

        Drain(InputStream in, int max, boolean tail) {
            super("isakai-media-drain");
            setDaemon(true);
            this.in = in;
            this.max = max;
            this.tail = tail;
            this.ring = tail ? new byte[max] : null;
        }

        @Override
        public void run() {
            byte[] chunk = new byte[8192];
            try {
                int n;
                while (!overflow && (n = in.read(chunk)) != -1) {
                    if (tail) {
                        appendTail(chunk, n);
                    } else {
                        if (buf.size() + n > max) {
                            overflow = true;
                            return;
                        }
                        buf.write(chunk, 0, n);
                    }
                }
            } catch (IOException ignored) {
            } finally {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
        }

        private void appendTail(byte[] chunk, int n) {
            for (int i = 0; i < n; i++) {
                if (ringSize < max) {
                    ring[(ringStart + ringSize) % max] = chunk[i];
                    ringSize++;
                } else {
                    ring[ringStart] = chunk[i];
                    ringStart = (ringStart + 1) % max;
                }
            }
        }

        byte[] bytes() {
            if (!tail) return buf.toByteArray();
            byte[] out = new byte[ringSize];
            for (int i = 0; i < ringSize; i++) out[i] = ring[(ringStart + i) % max];
            return out;
        }

        void joinQuietly() {
            try {
                join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static boolean hasFfmpeg() {
        return canExecute("/usr/bin/ffmpeg") || canExecute("/usr/local/bin/ffmpeg") || canExecute("ffmpeg");
    }

    public static boolean hasFfprobe() {
        return canExecute("/usr/bin/ffprobe") || canExecute("/usr/local/bin/ffprobe") || canExecute("ffprobe");
    }

    private static boolean canExecute(String path) {
        if (path.contains("/")) {
            File f = new File(path);
            return f.isFile() && f.canExecute();
        }
        // bare name: try PATH lookup via `which` (also supervised: a stuck
        // PATH helper must not hold the caller).
        try {
            Proc p = run(new ProcessBuilder("which", path), 4096, 1024, 2000);
            return p.exit == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String ffmpegBin() {
        if (canExecute("/usr/bin/ffmpeg")) return "/usr/bin/ffmpeg";
        if (canExecute("/usr/local/bin/ffmpeg")) return "/usr/local/bin/ffmpeg";
        return "ffmpeg";
    }

    private static String ffprobeBin() {
        if (canExecute("/usr/bin/ffprobe")) return "/usr/bin/ffprobe";
        if (canExecute("/usr/local/bin/ffprobe")) return "/usr/local/bin/ffprobe";
        return "ffprobe";
    }

    /**
     * Decode audio at {@code path} to S16LE PCM (stereo 44100) via ffmpeg.
     * Returns null when ffmpeg is absent (caller must throw
     * UnsupportedOperationException) or throws IOException on decode failure.
     */
    public static Decoded decode(String path) throws IOException {
        if (!hasFfmpeg()) return null;
        File f = new File(path);
        if (!f.isFile()) throw new IOException("not found: " + path);
        // ffmpeg -v error -i <path> -f s16le -acodec pcm_s16le -ac 2 -ar 44100 pipe:1
        ProcessBuilder pb = new ProcessBuilder(
                ffmpegBin(), "-v", "error", "-i", path,
                "-f", "s16le", "-acodec", "pcm_s16le", "-ac", "2", "-ar", "44100", "pipe:1");
        pb.redirectErrorStream(false);
        Proc proc = run(pb, MAX_PCM_BYTES, MAX_STDERR_BYTES, PROC_TIMEOUT_MS);
        if (proc.exit != 0) {
            throw new IOException(proc.errorText("ffmpeg exit " + proc.exit));
        }
        byte[] pcm = proc.out;
        if (pcm.length == 0) throw new IOException("ffmpeg produced no output for " + path);
        // durationMs = bytes / (rate * channels * bytesPerSample) * 1000
        int durationMs = (int) ((pcm.length * 1000L) / (44100 * 2 * 2));
        return new Decoded(pcm, 44100, 2, durationMs);
    }

    /**
     * Extract a single frame at {@code timeUs} (microseconds) as a Bitmap.
     * Null when ffmpeg absent or no frame.
     */
    public static Bitmap getFrameAtTime(String path, long timeUs) throws IOException {
        if (!hasFfmpeg()) return null;
        File f = new File(path);
        if (!f.isFile()) throw new IOException("not found: " + path);
        double sec = timeUs / 1_000_000.0;
        if (sec < 0) sec = 0;
        String ss = String.format(java.util.Locale.US, "%.3f", sec);
        ProcessBuilder pb = new ProcessBuilder(
                ffmpegBin(), "-v", "error", "-ss", ss, "-i", path,
                "-vframes", "1", "-f", "image2pipe", "-vcodec", "png", "pipe:1");
        pb.redirectErrorStream(false);
        Proc proc = run(pb, 8 * 1024 * 1024, MAX_STDERR_BYTES, PROC_TIMEOUT_MS);
        if (proc.exit != 0) {
            // No frame is not necessarily an error for some files; return null
            Log.w(TAG, "getFrameAtTime failed: " + proc.errorText("ffmpeg exit " + proc.exit));
            return null;
        }
        if (proc.out.length == 0) return null;
        return BitmapFactory.decodeByteArray(proc.out, 0, proc.out.length);
    }

    /**
     * Probe duration via ffprobe. Returns durationUs or -1 when unavailable.
     */
    public static long probeDurationUs(String path) {
        if (!hasFfprobe()) return -1;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    ffprobeBin(), "-v", "error", "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1", path);
            pb.redirectErrorStream(false);
            Proc proc = run(pb, 1024, MAX_STDERR_BYTES, 5000);
            if (proc.exit != 0) return -1;
            String s = new String(proc.out).trim();
            if (s.isEmpty() || s.equals("N/A")) return -1;
            double sec = Double.parseDouble(s);
            return (long) (sec * 1_000_000);
        } catch (Exception e) {
            return -1;
        }
    }

    /** One ffprobe stream row. Duration is microseconds, or -1 when unknown. */
    public static final class Track {
        public final String mime;
        public final int width;
        public final int height;
        public final int sampleRate;
        public final int channels;
        public final long durationUs;

        Track(String mime, int width, int height, int sampleRate, int channels, long durationUs) {
            this.mime = mime;
            this.width = width;
            this.height = height;
            this.sampleRate = sampleRate;
            this.channels = channels;
            this.durationUs = durationUs;
        }
    }

    /**
     * List every stream via ffprobe csv. Empty when ffprobe is absent;
     * throws on unreadable input so discovery never silently reports zero.
     */
    public static java.util.List<Track> probeTracks(String path) throws IOException {
        java.util.List<Track> out = new java.util.ArrayList<>();
        if (!hasFfprobe()) return out;
        File f = new File(path);
        if (!f.isFile()) throw new IOException("not found: " + path);
        ProcessBuilder pb = new ProcessBuilder(
                ffprobeBin(), "-v", "error",
                "-show_entries", "stream=index,codec_name,codec_type,width,height,sample_rate,channels,duration",
                "-of", "flat", path);
        pb.redirectErrorStream(false);
        Proc proc = run(pb, 64 * 1024, MAX_STDERR_BYTES, 5000);
        if (proc.exit != 0) throw new IOException(proc.errorText("ffprobe exit " + proc.exit));
        // flat emits streams.stream.<n>.<key>=<value>; csv is unusable
        // here because ffprobe drops empty columns instead of aligning them.
        java.util.Map<Integer, java.util.Map<String, String>> rows = new java.util.TreeMap<>();
        for (String line : new String(proc.out).split("\n")) {
            line = line.trim();
            if (!line.startsWith("streams.stream.")) continue;
            String rest = line.substring("streams.stream.".length());
            int dot = rest.indexOf('.');
            int eq = rest.indexOf('=');
            if (dot < 0 || eq < 0 || dot > eq) continue;
            int idx;
            try {
                idx = Integer.parseInt(rest.substring(0, dot));
            } catch (NumberFormatException e) {
                continue;
            }
            String key = rest.substring(dot + 1, eq);
            String val = rest.substring(eq + 1).replace("\"", "");
            java.util.Map<String, String> row = rows.get(idx);
            if (row == null) {
                row = new java.util.HashMap<>();
                rows.put(idx, row);
            }
            row.put(key, val);
        }
        for (java.util.Map<String, String> row : rows.values()) {
            String type = row.containsKey("codec_type") ? row.get("codec_type") : "";
            String codec = row.containsKey("codec_name") ? row.get("codec_name") : "";
            String mime = toMime(type, codec);
            if (mime == null) continue;
            out.add(new Track(mime,
                    num(row.get("width")), num(row.get("height")),
                    num(row.get("sample_rate")), num(row.get("channels")),
                    durUs(row.get("duration"))));
        }
        return out;
    }

    private static String toMime(String type, String codec) {
        if (type.equals("video")) {
            switch (codec) {
                case "h264": return "video/avc";
                case "hevc": return "video/hevc";
                case "mpeg4": return "video/mp4v-es";
                case "vp9": return "video/x-vnd.on2.vp9";
                case "av1": return "video/av01";
                default: return "video/" + codec;
            }
        }
        if (type.equals("audio")) {
            switch (codec) {
                case "aac": return "audio/mp4a-latm";
                case "mp3": return "audio/mpeg";
                case "opus": return "audio/opus";
                case "vorbis": return "audio/vorbis";
                case "flac": return "audio/flac";
                default: return "audio/" + codec;
            }
        }
        return null;
    }

    private static int num(String s) {
        if (s == null) return 0;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long durUs(String s) {
        if (s == null) return -1;
        try {
            return (long) (Double.parseDouble(s) * 1_000_000);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Decode one elementary video stream to whole yuv420p frames.
     * The caller trusts the configured size; a mismatch throws.
     */
    public static java.util.List<byte[]> decodeVideoEs(byte[] es, int width, int height) throws IOException {
        if (!hasFfmpeg()) throw new IOException("ffmpeg not available");
        if (es.length == 0) throw new IOException("empty elementary stream");
        ProcessBuilder pb = new ProcessBuilder(
                ffmpegBin(), "-v", "error", "-i", "pipe:0",
                "-f", "rawvideo", "-pix_fmt", "yuv420p", "pipe:1");
        pb.redirectErrorStream(false);
        Proc proc = runWithInput(pb, es, 256 * 1024 * 1024, MAX_STDERR_BYTES, PROC_TIMEOUT_MS);
        if (proc.exit != 0) throw new IOException(proc.errorText("ffmpeg exit " + proc.exit));
        int frameSize = width * height * 3 / 2;
        if (frameSize <= 0) throw new IOException("bad size " + width + "x" + height);
        // A trailing partial frame means the configured size was wrong.
        if (proc.out.length == 0 || proc.out.length % frameSize != 0) {
            throw new IOException("rawvideo " + proc.out.length + " bytes not a multiple of " + frameSize);
        }
        java.util.List<byte[]> frames = new java.util.ArrayList<>(proc.out.length / frameSize);
        for (int off = 0; off < proc.out.length; off += frameSize) {
            byte[] frame = new byte[frameSize];
            System.arraycopy(proc.out, off, frame, 0, frameSize);
            frames.add(frame);
        }
        return frames;
    }

    /** Decode one elementary audio stream to S16LE stereo 44100 PCM. */
    public static byte[] decodeAudioEs(byte[] es) throws IOException {
        if (!hasFfmpeg()) throw new IOException("ffmpeg not available");
        if (es.length == 0) throw new IOException("empty elementary stream");
        ProcessBuilder pb = new ProcessBuilder(
                ffmpegBin(), "-v", "error", "-i", "pipe:0",
                "-f", "s16le", "-acodec", "pcm_s16le", "-ac", "2", "-ar", "44100", "pipe:1");
        pb.redirectErrorStream(false);
        Proc proc = runWithInput(pb, es, MAX_PCM_BYTES, MAX_STDERR_BYTES, PROC_TIMEOUT_MS);
        if (proc.exit != 0) throw new IOException(proc.errorText("ffmpeg exit " + proc.exit));
        if (proc.out.length == 0) throw new IOException("ffmpeg produced no audio");
        return proc.out;
    }

    public static String probeMime(String path) {
        if (!hasFfprobe()) return null;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    ffprobeBin(), "-v", "error", "-select_streams", "a:0",
                    "-show_entries", "stream=codec_name", "-of", "default=noprint_wrappers=1:nokey=1", path);
            Proc proc = run(pb, 1024, MAX_STDERR_BYTES, 5000);
            if (proc.exit != 0) return null;
            String codec = new String(proc.out).trim();
            if (codec.isEmpty() || codec.equals("N/A")) return null;
            // map codec to mime
            switch (codec) {
                case "mp3": return "audio/mpeg";
                case "aac": return "audio/mp4";
                case "vorbis": return "audio/ogg";
                case "opus": return "audio/opus";
                case "flac": return "audio/flac";
                case "pcm_s16le": return "audio/wav";
                default: return "audio/" + codec;
            }
        } catch (Exception e) {
            return null;
        }
    }
}
