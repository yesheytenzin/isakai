// SPDX-License-Identifier: GPL-3.0-or-later
package android.os;

/** Vibrator stub: device has no vibrator. */
public abstract class Vibrator {
    public Vibrator() {
    }

    public abstract boolean hasVibrator();

    public void vibrate(long milliseconds) {
    }

    public void vibrate(long[] pattern, int repeat) {
    }

    public void cancel() {
    }

    public boolean areAllPrimitivesSupported(int[] primitives) { return false; }
}
