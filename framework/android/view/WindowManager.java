// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

/** Window manager: view add/remove plus default display. */
public interface WindowManager extends ViewManager {
    Display getDefaultDisplay();

    class LayoutParams extends ViewGroup.LayoutParams {
        public static final int FLAG_FULLSCREEN = 0x400;
        public static final int FLAG_KEEP_SCREEN_ON = 0x80;
        public static final int FLAG_DIM_BEHIND = 0x2;

        public int flags;
        public int gravity;
        public int windowAnimations;
        public float dimAmount;
        public int type;

        public LayoutParams() {
            super(WRAP_CONTENT, WRAP_CONTENT);
        }

        public LayoutParams(int w, int h, int type, int flags, int format) {
            super(w, h);
            this.type = type;
            this.flags = flags;
        }

        public int copyFrom(LayoutParams o) {
            if (o == null) return 0;
            this.flags = o.flags;
            this.gravity = o.gravity;
            this.windowAnimations = o.windowAnimations;
            this.dimAmount = o.dimAmount;
            this.type = o.type;
            return 0;
        }
    }

    /** Thrown when a window is added against a token that is no longer valid. */
    class BadTokenException extends RuntimeException {
        public BadTokenException() {}

        public BadTokenException(String name) {
            super(name);
        }
    }
}
