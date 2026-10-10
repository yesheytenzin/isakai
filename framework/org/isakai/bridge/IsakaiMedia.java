// SPDX-License-Identifier: GPL-3.0-or-later
package org.isakai.bridge;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * Media helpers backed by the host {@code ffmpeg}/{@code ffprobe} CLIs.
 * No native deps; fails closed when ffmpeg is absent.
 *
 * <p>Used by {@link android.media.MediaPlayer} (audio decode → S16LE PCM
 * at 44.1k stereo, fed to {@link IsakaiAudio}) and
 * {@link android.media.MediaMetadataRetriever} (frame extract → PNG →
 * {@link Bitmap}).
 */
public final class IsakaiMedia {
    private static final String TAG = "IsakaiMedia";
    private static final long PROC_TIMEOUT_S = 15;

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
        // bare name: try PATH lookup via `which`
        try {
            Process p = new ProcessBuilder("which", path).start();
            if (p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0) return true;
        } catch (Exception ignored) {}
        return false;
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
        Process proc;
        try {
            proc = pb.start();
        } catch (IOException e) {
            throw new IOException("ffmpeg start failed: " + e.getMessage(), e);
        }
        byte[] pcm;
        byte[] err;
        try (InputStream out = proc.getInputStream(); InputStream errS = proc.getErrorStream()) {
            pcm = readAll(out, 64 * 1024 * 1024);
            err = readAll(errS, 64 * 1024);
        }
        boolean done;
        try {
            done = proc.waitFor(PROC_TIMEOUT_S, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            proc.destroyForcibly();
            throw new IOException("interrupted", e);
        }
        if (!done) {
            proc.destroyForcibly();
            throw new IOException("ffmpeg timed out");
        }
        int code = proc.exitValue();
        if (code != 0) {
            String msg = err.length > 0 ? new String(err).trim() : "ffmpeg exit " + code;
            throw new IOException(msg);
        }
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
        Process proc = pb.start();
        byte[] png;
        byte[] err;
        try (InputStream out = proc.getInputStream(); InputStream errS = proc.getErrorStream()) {
            png = readAll(out, 8 * 1024 * 1024);
            err = readAll(errS, 64 * 1024);
        }
        boolean done;
        try {
            done = proc.waitFor(PROC_TIMEOUT_S, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            proc.destroyForcibly();
            throw new IOException("interrupted", e);
        }
        if (!done) {
            proc.destroyForcibly();
            throw new IOException("ffmpeg timed out");
        }
        int code = proc.exitValue();
        if (code != 0) {
            String msg = err.length > 0 ? new String(err).trim() : "ffmpeg exit " + code;
            // No frame is not necessarily an error for some files; return null
            Log.w(TAG, "getFrameAtTime failed: " + msg);
            return null;
        }
        if (png.length == 0) return null;
        Bitmap bm = BitmapFactory.decodeByteArray(png, 0, png.length);
        return bm;
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
            Process proc = pb.start();
            byte[] out;
            try (InputStream is = proc.getInputStream()) {
                out = readAll(is, 1024);
            }
            if (!proc.waitFor(5, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                return -1;
            }
            if (proc.exitValue() != 0) return -1;
            String s = new String(out).trim();
            if (s.isEmpty() || s.equals("N/A")) return -1;
            double sec = Double.parseDouble(s);
            return (long) (sec * 1_000_000);
        } catch (Exception e) {
            return -1;
        }
    }

    public static String probeMime(String path) {
        if (!hasFfprobe()) return null;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    ffprobeBin(), "-v", "error", "-select_streams", "a:0",
                    "-show_entries", "stream=codec_name", "-of", "default=noprint_wrappers=1:nokey=1", path);
            Process proc = pb.start();
            byte[] out;
            try (InputStream is = proc.getInputStream()) {
                out = readAll(is, 1024);
            }
            if (!proc.waitFor(5, TimeUnit.SECONDS) || proc.exitValue() != 0) return null;
            String codec = new String(out).trim();
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

    private static byte[] readAll(InputStream is, int max) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.min(max, 8192));
        byte[] buf = new byte[8192];
        int total = 0;
        int n;
        while ((n = is.read(buf)) != -1) {
            if (total + n > max) throw new IOException("output too large");
            baos.write(buf, 0, n);
            total += n;
        }
        return baos.toByteArray();
    }
}
