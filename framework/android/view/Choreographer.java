// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

/** Frame scheduler: callbacks run immediately. */
public class Choreographer {
    public interface FrameCallback {
        void doFrame(long frameTimeNanos);
    }

    private static final Choreographer INSTANCE = new Choreographer();

    public static Choreographer getInstance() {
        return INSTANCE;
    }

    public void postFrameCallback(FrameCallback callback) {
        if (callback != null) callback.doFrame(System.nanoTime());
    }

    public void removeFrameCallback(FrameCallback callback) {}
}
