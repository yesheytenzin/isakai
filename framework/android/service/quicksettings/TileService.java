// SPDX-License-Identifier: GPL-3.0-or-later
package android.service.quicksettings;

import android.app.PendingIntent;
import android.content.Intent;

/** Quick-settings tile service: lifecycle hooks only. */
public class TileService extends android.app.Service {
    public void onTileAdded() {}
    public void onTileRemoved() {}
    public void onStartListening() {}
    public void onStopListening() {}
    public void onClick() {}

    public void startActivityAndCollapse(Intent intent) {}
    public void startActivityAndCollapse(PendingIntent pendingIntent) {}

    public void unlockAndRun(Runnable runnable) {
        if (runnable != null) runnable.run();
    }
}
