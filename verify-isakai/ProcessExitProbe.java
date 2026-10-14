package org.isakai.verify;

public final class ProcessExitProbe {
    public static final class Idle extends android.app.Application {
        public void onCreate() {
            Thread worker=new Thread(()-> {
                try { new java.util.concurrent.CountDownLatch(1).await(); }
                catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            },"probe-idle-executor");
            worker.setDaemon(false);worker.start();
        }
    }
    public static final class Crash extends android.app.Application {
        public void onCreate() {
            new Thread(()->{throw new IllegalStateException("SEEDED_WORKER_CRASH");},"probe-crash").start();
            try { Thread.sleep(10000); }catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        }
    }
}
