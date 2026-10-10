// SPDX-License-Identifier: GPL-3.0-or-later
package android.app;

import android.os.Handler;

/** Alarm scheduling; no-op shell. */
public class AlarmManager {
    public static final int RTC = 1;
    public static final int RTC_WAKEUP = 0;
    public static final int ELAPSED_REALTIME = 3;
    public static final int ELAPSED_REALTIME_WAKEUP = 2;
    public static final long INTERVAL_FIFTEEN_MINUTES = 900000L;
    public static final long INTERVAL_HALF_HOUR = 1800000L;
    public static final long INTERVAL_HOUR = 3600000L;
    public static final long INTERVAL_HALF_DAY = 43200000L;
    public static final long INTERVAL_DAY = 86400000L;
    public interface OnAlarmListener { void onAlarm(); }
    public void set(int type, long triggerAtMillis, PendingIntent operation) { }
    public void set(int type, long triggerAtMillis, String tag, OnAlarmListener listener, Handler targetHandler) { }
    public void setExact(int type, long triggerAtMillis, PendingIntent operation) { }
    public void setExactAndAllowWhileIdle(int type, long triggerAtMillis, PendingIntent operation) { }
    public void setAndAllowWhileIdle(int type, long triggerAtMillis, PendingIntent operation) { }
    public void setRepeating(int type, long triggerAtMillis, long intervalMillis, PendingIntent operation) { }
    public void setInexactRepeating(int type, long triggerAtMillis, long intervalMillis, PendingIntent operation) { }
    public void setWindow(int type, long windowStartMillis, long windowLengthMillis, PendingIntent operation) { }
    public void cancel(PendingIntent operation) { }
    public void cancel(OnAlarmListener listener) { }
    public boolean canScheduleExactAlarms() { return false; }
    public long getNextAlarmClock() { return 0; }
}
