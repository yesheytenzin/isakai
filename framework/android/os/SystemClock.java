// SPDX-License-Identifier: GPL-3.0-or-later
package android.os;

/** Monotonic and wall clocks over System.nanoTime. */
public final class SystemClock {
    private SystemClock() {}

    public static long uptimeMillis() {
        return System.nanoTime() / 1_000_000L;
    }

    public static long elapsedRealtime() {
        return uptimeMillis();
    }

    public static long elapsedRealtimeNanos() {
        return System.nanoTime();
    }

    public static long currentThreadTimeMillis() {
        return System.nanoTime() / 1_000_000L;
    }

    public static void sleep(long ms) {
        try {
            Thread.sleep(Math.max(0, ms));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
