// SPDX-License-Identifier: GPL-3.0-or-later
package android.app;

import android.content.Context;
import android.content.Intent;

/** Pending intent: wraps the intent, send is a no-op. */
public class PendingIntent {
    public static final int FLAG_ONE_SHOT = 1 << 30;
    public static final int FLAG_NO_CREATE = 1 << 29;
    public static final int FLAG_CANCEL_CURRENT = 1 << 28;
    public static final int FLAG_UPDATE_CURRENT = 1 << 27;
    public static final int FLAG_IMMUTABLE = 1 << 26;
    public static final int FLAG_MUTABLE = 1 << 25;

    private final Intent intent;

    private PendingIntent(Intent intent) {
        this.intent = intent;
    }

    public static PendingIntent getActivity(Context context, int requestCode, Intent intent, int flags) {
        return new PendingIntent(intent);
    }

    public static PendingIntent getService(Context context, int requestCode, Intent intent, int flags) {
        return new PendingIntent(intent);
    }

    public static PendingIntent getBroadcast(Context context, int requestCode, Intent intent, int flags) {
        return new PendingIntent(intent);
    }

    public void send() {}

    public Intent getIntent() {
        return intent;
    }

    public void cancel() {}
}
