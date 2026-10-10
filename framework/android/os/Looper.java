// SPDX-License-Identifier: GPL-3.0-or-later
package android.os;

/** Thread message queue: single main looper, direct dispatch. */
public class Looper {
    private static final Looper MAIN = new Looper();

    public static Looper getMainLooper() {
        return MAIN;
    }

    public static Looper myLooper() {
        return MAIN;
    }

    public static void prepare() {}
    public static void prepareMainLooper() {}
    public static void loop() {}

    public Thread getThread() {
        return Thread.currentThread();
    }

    public MessageQueue getQueue() {
        return new MessageQueue();
    }

    public static class MessageQueue {
        public boolean isIdle() { return true; }
    }
}
