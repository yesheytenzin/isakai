// SPDX-License-Identifier: GPL-3.0-or-later
package android.os;

/** Handler: posts run immediately on the calling thread. */
public class Handler {
    public interface Callback {
        boolean handleMessage(Message msg);
    }

    private final Looper looper;
    private final Callback callback;

    public Handler() {
        this(Looper.getMainLooper(), null);
    }

    public Handler(Looper looper) {
        this(looper, null);
    }

    public Handler(Looper looper, Callback callback) {
        this.looper = looper == null ? Looper.getMainLooper() : looper;
        this.callback = callback;
    }

    public static Handler createAsync(Looper looper) {
        return new Handler(looper);
    }

    public Looper getLooper() {
        return looper;
    }

    public void handleMessage(Message msg) {}

    public boolean sendMessage(Message msg) {
        if (msg == null) return false;
        msg.target = this;
        dispatchMessage(msg);
        return true;
    }

    public boolean sendEmptyMessage(int what) {
        return sendMessage(obtainMessage(what));
    }

    public boolean sendEmptyMessageDelayed(int what, long delayMillis) {
        return sendEmptyMessage(what);
    }

    public boolean post(Runnable r) {
        if (r != null) r.run();
        return true;
    }

    public boolean postDelayed(Runnable r, long delayMillis) {
        return post(r);
    }

    public boolean postAtFrontOfQueue(Runnable r) {
        return post(r);
    }

    public void dispatchMessage(Message msg) {
        if (callback != null && callback.handleMessage(msg)) return;
        handleMessage(msg);
    }

    public Message obtainMessage(int what) {
        return Message.obtain(this, what);
    }

    public Message obtainMessage(int what, Object obj) {
        return Message.obtain(this, what, obj);
    }

    public boolean hasMessages(int what) {
        return false;
    }

    public void removeCallbacks(Runnable r) {}
    public void removeMessages(int what) {}
    public void removeCallbacksAndMessages(Object token) {}
}
