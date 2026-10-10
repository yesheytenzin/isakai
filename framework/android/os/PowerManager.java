// SPDX-License-Identifier: GPL-3.0-or-later
package android.os;

/** Power state stub: device always interactive, never power-save. */
public class PowerManager {
    public static final int PARTIAL_WAKE_LOCK = 1;
    public static final int SCREEN_DIM_WAKE_LOCK = 6;
    public static final int SCREEN_BRIGHT_WAKE_LOCK = 10;
    public static final int FULL_WAKE_LOCK = 26;
    public static final int ACQUIRE_CAUSES_WAKEUP = 0x10000000;
    public static final int ON_AFTER_RELEASE = 0x20000000;

    public PowerManager() {
    }

    public WakeLock newWakeLock(int levelAndFlags, String tag) {
        return new WakeLock();
    }

    public boolean isInteractive() {
        return true;
    }

    public boolean isPowerSaveMode() {
        return false;
    }

    /** Wake lock stub: acquire/release tracked locally only. */
    public static class WakeLock {
        private boolean held;

        public void acquire() {
            held = true;
        }

        public void acquire(long timeout) {
            held = true;
        }

        public void release() {
            held = false;
        }

        public boolean isHeld() {
            return held;
        }

        public void setReferenceCounted(boolean value) {
        }
    }
}
