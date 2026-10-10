// SPDX-License-Identifier: GPL-3.0-or-later
package android.app;

import android.content.Context;
import android.content.Intent;

/** Background service: lifecycle hooks only. */
public abstract class Service extends Context {
    public static final int START_STICKY = 1;
    public static final int START_NOT_STICKY = 2;
    public static final int START_REDELIVER_INTENT = 3;

    public void onCreate() {}

    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    public android.os.IBinder onBind(Intent intent) {
        return null;
    }

    public void onDestroy() {}

    public final void stopSelf() {}

    @Override
    public android.content.res.Resources getResources() {
        return android.content.res.Resources.getSystem();
    }

    @Override
    public Object getSystemService(String name) {
        return super.getSystemService(name);
    }
}
