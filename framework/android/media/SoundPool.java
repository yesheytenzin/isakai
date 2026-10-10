// SPDX-License-Identifier: GPL-3.0-or-later
package android.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.isakai.bridge.IsakaiAudio;

/**
 * Minimal SoundPool: load a 16-bit PCM WAV file (mono or stereo, any
 * supported rate) by path and play it once through PulseAudio at its
 * native rate. Only {@code loop == 0} (play once) is supported.
 */
public class SoundPool {
    /** Supported rates (must match audio.zig supportsFormat). */
    public static final int[] SUPPORTED_RATES = {8000, 11025, 16000, 22050, 32000, 44100, 48000};

    /** Interleaved S16LE PCM at its native rate/channels. */
    public static final class Pcm {
        public final byte[] bytes;
        public final int sampleRate;
        public final int channels;

        Pcm(byte[] bytes, int sampleRate, int channels) {
            this.bytes = bytes;
            this.sampleRate = sampleRate;
            this.channels = channels;
        }
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Map<Integer, Pcm> sounds = new HashMap<>();
    private int nextId = 1;
    private volatile boolean released = false;

    public SoundPool() {}

    /**
     * Parse a WAV file into S16LE PCM at its native rate. Requires
     * RIFF/WAVE, fmt == 1 (PCM), 16-bit mono or stereo at a supported
     * rate (see {@link #SUPPORTED_RATES}). Mono is duplicated to
     * stereo; anything else throws {@link IllegalArgumentException}.
     */
    public static Pcm parseWav(byte[] wav) {
        if (wav == null || wav.length < 44) {
            throw new IllegalArgumentException("wav too short");
        }
        if (wav[0] != 'R' || wav[1] != 'I' || wav[2] != 'F' || wav[3] != 'F') {
            throw new IllegalArgumentException("not RIFF");
        }
        if (wav[8] != 'W' || wav[9] != 'A' || wav[10] != 'V' || wav[11] != 'E') {
            throw new IllegalArgumentException("not WAVE");
        }
        int pos = 12;
        int channels = -1;
        int sampleRate = -1;
        int bits = -1;
        int audioFormat = -1;
        byte[] data = null;
        while (pos + 8 <= wav.length) {
            int id0 = wav[pos] & 0xFF;
            int id1 = wav[pos + 1] & 0xFF;
            int id2 = wav[pos + 2] & 0xFF;
            int id3 = wav[pos + 3] & 0xFF;
            int size = (wav[pos + 4] & 0xFF)
                    | ((wav[pos + 5] & 0xFF) << 8)
                    | ((wav[pos + 6] & 0xFF) << 16)
                    | ((wav[pos + 7] & 0xFF) << 24);
            if (size < 0 || pos + 8 + size > wav.length) {
                throw new IllegalArgumentException("bad chunk size");
            }
            if (id0 == 'f' && id1 == 'm' && id2 == 't' && id3 == ' ') {
                if (size < 16) {
                    throw new IllegalArgumentException("bad fmt chunk");
                }
                audioFormat = (wav[pos + 8] & 0xFF) | ((wav[pos + 9] & 0xFF) << 8);
                channels = (wav[pos + 10] & 0xFF) | ((wav[pos + 11] & 0xFF) << 8);
                sampleRate = (wav[pos + 12] & 0xFF)
                        | ((wav[pos + 13] & 0xFF) << 8)
                        | ((wav[pos + 14] & 0xFF) << 16)
                        | ((wav[pos + 15] & 0xFF) << 24);
                bits = (wav[pos + 22] & 0xFF) | ((wav[pos + 23] & 0xFF) << 8);
            } else if (id0 == 'd' && id1 == 'a' && id2 == 't' && id3 == 'a') {
                data = new byte[size];
                System.arraycopy(wav, pos + 8, data, 0, size);
            }
            pos += 8 + size + (size & 1);
        }
        if (audioFormat != 1) {
            throw new IllegalArgumentException("only PCM (fmt==1) supported, got " + audioFormat);
        }
        if (bits != 16) {
            throw new IllegalArgumentException("only 16-bit supported, got " + bits);
        }
        boolean rateOk = false;
        for (int r : SUPPORTED_RATES) {
            if (r == sampleRate) {
                rateOk = true;
                break;
            }
        }
        if (!rateOk) {
            throw new IllegalArgumentException("unsupported rate " + sampleRate);
        }
        if (data == null) {
            throw new IllegalArgumentException("no data chunk");
        }
        if (channels == 1) {
            if ((data.length & 1) != 0) {
                throw new IllegalArgumentException("odd mono data length");
            }
            byte[] stereo = new byte[data.length * 2];
            for (int i = 0, j = 0; i < data.length; i += 2, j += 4) {
                stereo[j] = data[i];
                stereo[j + 1] = data[i + 1];
                stereo[j + 2] = data[i];
                stereo[j + 3] = data[i + 1];
            }
            return new Pcm(stereo, sampleRate, 2);
        } else if (channels == 2) {
            if ((data.length & 3) != 0) {
                throw new IllegalArgumentException("odd stereo data length");
            }
            return new Pcm(data, sampleRate, 2);
        }
        throw new IllegalArgumentException("only mono/stereo supported, got " + channels);
    }

    /** Load a WAV file from {@code path}; returns a sound ID. */
    public int load(String path) {
        if (released) {
            throw new IllegalStateException("released");
        }
        try {
            Path p = Paths.get(path);
            byte[] wav = Files.readAllBytes(p);
            Pcm pcm = parseWav(wav);
            synchronized (sounds) {
                int id = nextId++;
                sounds.put(id, pcm);
                return id;
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("cannot read " + path + ": " + e.getMessage(), e);
        }
    }

    /**
     * Play a loaded sound once, blocking until it finishes. Only
     * {@code loop == 0} is supported; any other value throws
     * {@link IllegalArgumentException}.
     */
    public void play(int soundId, int loop) {
        if (loop != 0) {
            throw new IllegalArgumentException("only loop==0 supported, got " + loop);
        }
        final Pcm pcm;
        synchronized (sounds) {
            pcm = sounds.get(soundId);
        }
        if (pcm == null) {
            throw new IllegalArgumentException("unknown sound " + soundId);
        }
        if (released) {
            throw new IllegalStateException("released");
        }
        // Synchronous on the calling thread: the fixture calls this from
        // onCreate (a background-safe test path); real apps SHOULD call
        // from a worker thread. The executor serializes concurrent plays.
        try {
            executor.submit(() -> {
                // AOSP parity: missing audio hardware degrades to a
                // logged no-op (play must not throw; sandboxed runs
                // have no PulseAudio socket).
                long handle;
                try {
                    handle = IsakaiAudio.nAudioOpenFormat(pcm.sampleRate, pcm.channels);
                } catch (UnsatisfiedLinkError | Exception e) {
                    android.util.Log.w("SoundPool",
                            "audio unavailable, dropping play: " + e.getMessage());
                    return;
                }
                if (handle == 0) {
                    android.util.Log.w("SoundPool", "audio open failed for "
                            + pcm.sampleRate + "Hz/" + pcm.channels
                            + "ch, dropping play");
                    return;
                }
                try {
                    if (IsakaiAudio.nAudioWrite(handle, pcm.bytes, pcm.bytes.length) != 0) {
                        android.util.Log.w("SoundPool", "audio write failed, dropping play");
                    } else if (IsakaiAudio.nAudioDrain(handle) != 0) {
                        android.util.Log.w("SoundPool", "audio drain failed");
                    }
                } finally {
                    try {
                        IsakaiAudio.nAudioClose(handle);
                    } catch (Exception ignored) {
                    }
                }
            }).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted", e);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException) throw (RuntimeException) c;
            throw new RuntimeException("play failed", c);
        }
    }

    /** Stop accepting work; in-flight playback runs to completion. */
    public void release() {
        released = true;
        executor.shutdown();
        synchronized (sounds) {
            sounds.clear();
        }
    }
}
