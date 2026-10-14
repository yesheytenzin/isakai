// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/** Looper-owned fallback frame scheduler. Host presentation is not a vsync clock. */
public final class Choreographer {
    public interface FrameCallback { void doFrame(long frameTimeNanos); }
    private static final ThreadLocal<Choreographer> LOCAL = new ThreadLocal<>();
    private final Handler handler;
    private Choreographer(Looper looper) { handler = new Handler(looper); }
    public static Choreographer getInstance() {
        Looper looper = Looper.myLooper();
        if (looper == null) throw new IllegalStateException("No Looper for this thread");
        Choreographer instance = LOCAL.get();
        if (instance == null) { instance = new Choreographer(looper); LOCAL.set(instance); }
        return instance;
    }
    public void postFrameCallback(FrameCallback callback) { postFrameCallbackDelayed(callback,0); }
    public void postFrameCallbackDelayed(FrameCallback callback,long delayMillis) {
        if (callback == null) throw new IllegalArgumentException("callback must not be null");
        long delay = Math.max(0,delayMillis);
        long now = SystemClock.uptimeMillis();
        long deadline = delay > Long.MAX_VALUE - now - 16 ? Long.MAX_VALUE : now + delay + 16;
        handler.postAtTime(() -> callback.doFrame(System.nanoTime()),callback,deadline);
    }
    public void removeFrameCallback(FrameCallback callback) {
        if (callback == null) throw new IllegalArgumentException("callback must not be null");
        handler.removeCallbacksAndMessages(callback);
    }
}
