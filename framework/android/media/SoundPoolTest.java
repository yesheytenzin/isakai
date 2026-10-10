// SPDX-License-Identifier: GPL-3.0-or-later
package android.media;

/** Host-side unit checks for {@link SoundPool#parseWav}. Run on the host JVM. */
public final class SoundPoolTest {
    private SoundPoolTest() {}

    static byte[] wav(int channels, int rate, int bits, byte[] pcm) {
        int fmtSize = 16;
        int dataSize = pcm.length;
        int total = 12 + 8 + fmtSize + 8 + dataSize;
        byte[] out = new byte[total];
        out[0] = 'R';
        out[1] = 'I';
        out[2] = 'F';
        out[3] = 'F';
        put32(out, 4, total - 8);
        out[8] = 'W';
        out[9] = 'A';
        out[10] = 'V';
        out[11] = 'E';
        out[12] = 'f';
        out[13] = 'm';
        out[14] = 't';
        out[15] = ' ';
        put32(out, 16, fmtSize);
        put16(out, 20, 1); // PCM
        put16(out, 22, channels);
        put32(out, 24, rate);
        put32(out, 28, rate * channels * bits / 8);
        put16(out, 32, channels * bits / 8);
        put16(out, 34, bits);
        out[36] = 'd';
        out[37] = 'a';
        out[38] = 't';
        out[39] = 'a';
        put32(out, 40, dataSize);
        System.arraycopy(pcm, 0, out, 44, dataSize);
        return out;
    }

    static void put16(byte[] b, int at, int v) {
        b[at] = (byte) (v & 0xFF);
        b[at + 1] = (byte) ((v >>> 8) & 0xFF);
    }

    static void put32(byte[] b, int at, int v) {
        b[at] = (byte) (v & 0xFF);
        b[at + 1] = (byte) ((v >>> 8) & 0xFF);
        b[at + 2] = (byte) ((v >>> 16) & 0xFF);
        b[at + 3] = (byte) ((v >>> 24) & 0xFF);
    }

    static void check(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }

    public static void main(String[] args) {
        // Mono duplicates to stereo.
        byte[] mono = new byte[] {0x01, 0x02, 0x03, 0x04};
        SoundPool.Pcm p = SoundPool.parseWav(wav(1, 44100, 16, mono));
        check(p.bytes.length == 8, "mono dup length");
        check(p.sampleRate == 44100 && p.channels == 2, "mono format");
        check(p.bytes[0] == 0x01 && p.bytes[1] == 0x02 && p.bytes[2] == 0x01 && p.bytes[3] == 0x02, "mono frame 0");
        check(p.bytes[4] == 0x03 && p.bytes[5] == 0x04 && p.bytes[6] == 0x03 && p.bytes[7] == 0x04, "mono frame 1");

        // Stereo passes through.
        byte[] stereo = new byte[] {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08};
        SoundPool.Pcm q = SoundPool.parseWav(wav(2, 44100, 16, stereo));
        check(q.bytes.length == 8, "stereo length");
        check(q.sampleRate == 44100 && q.channels == 2, "stereo format");
        for (int i = 0; i < 8; i++) {
            check(q.bytes[i] == stereo[i], "stereo byte " + i);
        }

        // Second format: 48000 Hz mono plays at its native rate.
        SoundPool.Pcm r48 = SoundPool.parseWav(wav(1, 48000, 16, mono));
        check(r48.sampleRate == 48000 && r48.channels == 2, "48000 format");
        check(r48.bytes.length == 8, "48000 length");

        // Rejections: bad rate, 8-bit, bad magic.
        try {
            SoundPool.parseWav(wav(1, 12345, 16, mono));
            throw new AssertionError("rate accepted");
        } catch (IllegalArgumentException expected) {
        }
        try {
            SoundPool.parseWav(wav(1, 44100, 8, new byte[] {1, 2}));
            throw new AssertionError("8-bit accepted");
        } catch (IllegalArgumentException expected) {
        }
        byte[] bad = wav(1, 44100, 16, mono);
        bad[0] = 'X';
        try {
            SoundPool.parseWav(bad);
            throw new AssertionError("bad magic accepted");
        } catch (IllegalArgumentException expected) {
        }
        SoundPool sp = new SoundPool();
        try {
            sp.play(1, 1);
            throw new AssertionError("loop accepted");
        } catch (IllegalArgumentException expected) {
        }
        try {
            sp.play(99, 0);
            throw new AssertionError("unknown id accepted");
        } catch (IllegalArgumentException expected) {
        }
        sp.release();
        System.out.println("SoundPoolTest: all PASS");
    }
}
