// SPDX-License-Identifier: GPL-3.0-or-later
package android.app;

import android.content.Context;

/** Minimal Application: lifecycle anchor over the Context storage surface. */
public class Application extends Context {
    private NotificationManager notificationManager;
    /** Called by IsakaiMain before any component runs. */
    @Override
    public void attach(String dataDir) {
        super.attach(dataDir);
    }

    public void onCreate() {}

    @Override
    public android.content.res.Resources getResources() {
        return android.content.res.Resources.getSystem();
    }
    /**
     * Service lookup: {@code NOTIFICATION_SERVICE} lazily creates a
     * desktop manager; misses fall through to the Context default
     * (layout inflater and future services).
     */
    @Override
    public Object getSystemService(String name) {
        if (Context.NOTIFICATION_SERVICE.equals(name)) {
            if (notificationManager == null) {
                notificationManager = new NotificationManager(getDataDirString());
            }
            return notificationManager;
        }
        return super.getSystemService(name);
    }

    /** Framework hook: drain queued disk writes (prefs apply()). No-op unless overridden. */
    @Override
    public void drainQueuedWork() {
        org.isakai.prefs.IsakaiPrefsWork.waitToFinish();
    }

    public interface ActivityLifecycleCallbacks {
        void onActivityCreated(Activity activity, android.os.Bundle savedInstanceState);
        void onActivityStarted(Activity activity);
        void onActivityResumed(Activity activity);
        void onActivityPaused(Activity activity);
        void onActivityStopped(Activity activity);
        void onActivitySaveInstanceState(Activity activity, android.os.Bundle outState);
        void onActivityDestroyed(Activity activity);
    }

    private final java.util.ArrayList<ActivityLifecycleCallbacks> lifecycleCallbacks =
            new java.util.ArrayList<>();

    public void registerActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback) {
        if (callback != null && !lifecycleCallbacks.contains(callback)) lifecycleCallbacks.add(callback);
    }

    public void unregisterActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback) {
        lifecycleCallbacks.remove(callback);
    }
}
