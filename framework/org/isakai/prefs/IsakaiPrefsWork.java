// SPDX-License-Identifier: GPL-3.0-or-later
package org.isakai.prefs;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

/**
 * Single-thread prefs writer: apply() batches serialize here in call order.
 * {@link #waitToFinish()} blocks until all queued writes complete; the
 * Activity calls it from onPause/onStop/onDestroy so no write is lost.
 */
public final class IsakaiPrefsWork {
    private static final ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "IsakaiPrefsWork");
                    t.setDaemon(true);
                    return t;
                }
            });

    private IsakaiPrefsWork() {}

    public static void enqueue(Runnable work) {
        EXECUTOR.execute(work);
    }

    /** Block until every queued write has completed. */
    public static void waitToFinish() {
        try {
            Future<?> done = EXECUTOR.submit(() -> {});
            done.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            throw new RuntimeException("prefs drain failed: " + e.getMessage(), e);
        }
    }
}
