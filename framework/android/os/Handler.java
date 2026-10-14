// SPDX-License-Identifier: GPL-3.0-or-later
package android.os;

/** Enqueues callbacks/messages on its Looper's owning thread. */
public class Handler {
    public interface Callback { boolean handleMessage(Message msg); }
    private final Looper looper;
    private final Callback callback;
    public Handler() { this(Looper.myLooper(), null); }
    public Handler(Callback callback) { this(Looper.myLooper(), callback); }
    public Handler(Looper looper) { this(looper, null); }
    public Handler(Looper looper, Callback callback) {
        if (looper == null) throw new RuntimeException("Handler requires a prepared Looper");
        this.looper = looper; this.callback = callback;
    }
    public static Handler createAsync(Looper looper) { return new Handler(looper); }
    public Looper getLooper() { return looper; }
    public void handleMessage(Message msg) {}
    public void dispatchMessage(Message msg) {
        if (msg.callback != null) msg.callback.run();
        else if (callback == null || !callback.handleMessage(msg)) handleMessage(msg);
    }
    public boolean sendMessage(Message msg) { return sendMessageDelayed(msg, 0); }
    public boolean sendMessageDelayed(Message msg, long delay) {
        long now = SystemClock.uptimeMillis();
        long positive = Math.max(0, delay);
        return sendMessageAtTime(msg, positive > Long.MAX_VALUE - now ? Long.MAX_VALUE : now + positive);
    }
    public boolean sendMessageAtTime(Message msg, long time) {
        if (msg == null) return false;
        msg.target = this;
        return looper.getQueue().enqueue(msg, time, false);
    }
    public boolean sendMessageAtFrontOfQueue(Message msg) {
        if (msg == null) return false;
        msg.target = this;
        return looper.getQueue().enqueue(msg, 0, true);
    }
    public boolean sendEmptyMessage(int what) { return sendMessage(obtainMessage(what)); }
    public boolean sendEmptyMessageDelayed(int what, long delay) { return sendMessageDelayed(obtainMessage(what), delay); }
    public boolean sendEmptyMessageAtTime(int what, long time) { return sendMessageAtTime(obtainMessage(what), time); }
    public boolean post(Runnable r) { return sendMessage(Message.obtain(this, r)); }
    public boolean postDelayed(Runnable r, long delay) { return sendMessageDelayed(Message.obtain(this, r), delay); }
    public boolean postDelayed(Runnable r, Object token, long delay) {
        Message m = Message.obtain(this, r); m.obj = token; return sendMessageDelayed(m, delay);
    }
    public boolean postAtTime(Runnable r, long time) { return postAtTime(r, null, time); }
    public boolean postAtTime(Runnable r, Object token, long time) {
        Message m = Message.obtain(this, r); m.obj = token; return sendMessageAtTime(m, time);
    }
    public boolean postAtFrontOfQueue(Runnable r) { return sendMessageAtFrontOfQueue(Message.obtain(this, r)); }
    public Message obtainMessage() { return Message.obtain(this); }
    public Message obtainMessage(int what) { return Message.obtain(this, what); }
    public Message obtainMessage(int what, Object obj) { return Message.obtain(this, what, obj); }
    public Message obtainMessage(int what, int a, int b) { return obtainMessage(what, a, b, null); }
    public Message obtainMessage(int what, int a, int b, Object obj) {
        Message m = obtainMessage(what, obj); m.arg1 = a; m.arg2 = b; return m;
    }
    public boolean hasMessages(int what) { return looper.getQueue().matches(this, null, what, null, false); }
    public boolean hasCallbacks(Runnable r) { return looper.getQueue().matches(this, r, null, null, false); }
    public void removeCallbacks(Runnable r) { removeCallbacks(r, null); }
    public void removeCallbacks(Runnable r, Object token) { if (r != null) looper.getQueue().matches(this, r, null, token, true); }
    public void removeMessages(int what) { removeMessages(what, null); }
    public void removeMessages(int what, Object token) { looper.getQueue().matches(this, null, what, token, true); }
    public void removeCallbacksAndMessages(Object token) { looper.getQueue().matches(this, null, null, token, true); }
}
