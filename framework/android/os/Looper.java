// SPDX-License-Identifier: GPL-3.0-or-later
package android.os;

/** A thread-owned message loop. The host window loop pumps the main queue. */
public final class Looper {
    private static final ThreadLocal<Looper> LOCAL = new ThreadLocal<>();
    private static volatile Looper main;
    private final Thread thread = Thread.currentThread();
    private final MessageQueue queue = new MessageQueue();
    private Looper() {}
    public static void prepare() {
        if (LOCAL.get() != null) throw new RuntimeException("Only one Looper per thread");
        LOCAL.set(new Looper());
    }
    public static synchronized void prepareMainLooper() {
        if (main != null) {
            if (main.thread != Thread.currentThread()) throw new IllegalStateException("Main looper already owned");
            return;
        }
        if (LOCAL.get() == null) prepare();
        main = LOCAL.get();
    }
    public static Looper getMainLooper() { return main; }
    public static Looper myLooper() { return LOCAL.get(); }
    public static MessageQueue myQueue() {
        Looper l = myLooper();
        if (l == null) throw new IllegalStateException("Thread has no Looper");
        return l.queue;
    }
    public Thread getThread() { return thread; }
    public MessageQueue getQueue() { return queue; }
    public boolean isCurrentThread() { return thread == Thread.currentThread(); }
    public void quit() { queue.quit(false); }
    public void quitSafely() { queue.quit(true); }
    public static void loop() {
        Looper l = myLooper();
        if (l == null) throw new IllegalStateException("Call Looper.prepare first");
        for (;;) {
            Message m = l.queue.next(true);
            if (m == null) return;
            l.dispatch(m);
        }
    }
    private void dispatch(Message m) {
        try { m.target.dispatchMessage(m); }
        finally { synchronized (queue) { m.inUse = false; } }
    }
    /** Bounded host integration: never lets a self-posting callback starve window events. */
    public int dispatchPending() {
        if (!isCurrentThread()) throw new IllegalStateException("Wrong Looper thread");
        int count = 0;
        while (count < 256) {
            Message m = queue.next(false);
            if (m == null) break;
            dispatch(m);
            count++;
        }
        return count;
    }
    public static final class MessageQueue {
        private final java.util.ArrayList<Message> messages = new java.util.ArrayList<>();
        private boolean quitting;
        synchronized boolean enqueue(Message m, long when, boolean front) {
            if (m == null) return false;
            if (m.target == null) throw new IllegalArgumentException("Message target must not be null");
            if (m.inUse) throw new IllegalStateException("Message already in use");
            if (quitting) return false;
            m.inUse = true;
            m.when = when;
            int i = 0;
            if (!front) while (i < messages.size() && messages.get(i).when <= when) i++;
            messages.add(i, m);
            notifyAll();
            return true;
        }
        synchronized Message next(boolean wait) {
            for (;;) {
                long now = SystemClock.uptimeMillis();
                if (!messages.isEmpty() && messages.get(0).when <= now) return messages.remove(0);
                if (!wait || quitting) return null;
                try {
                    if (messages.isEmpty()) wait();
                    else wait(Math.max(1, messages.get(0).when - now));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    continue;
                }
            }
        }
        public synchronized boolean isIdle() {
            return messages.isEmpty() || messages.get(0).when > SystemClock.uptimeMillis();
        }
        synchronized void quit(boolean safe) {
            quitting = true;
            long now = SystemClock.uptimeMillis();
            messages.removeIf(m -> {
                if (!safe || m.when > now) { m.inUse = false; return true; }
                return false;
            });
            notifyAll();
        }
        synchronized boolean matches(Handler h, Runnable r, Integer what, Object token, boolean remove) {
            boolean found = false;
            for (int i = messages.size() - 1; i >= 0; i--) {
                Message m = messages.get(i);
                if (m.target == h && (r == null || m.callback == r)
                        && (what == null || m.what == what)
                        && (token == null || m.obj == token)) {
                    found = true;
                    if (remove) { messages.remove(i); m.inUse = false; }
                }
            }
            return found;
        }
    }
}
