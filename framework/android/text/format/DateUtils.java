// SPDX-License-Identifier: GPL-3.0-or-later
package android.text.format;

/**
 * Date utilities needed by translated apps (OpenCalc history list calls
 * getRelativeTimeSpanString; Material datepicker calls formatDateTime).
 * Formats via java.time in the default locale; flag coverage matches what
 * the pinned apps exercise (show date/time/year/weekday, numeric, abbrev).
 */
public class DateUtils {
    private DateUtils() {}

    public static final long SECOND_IN_MILLIS = 1000;
    public static final long MINUTE_IN_MILLIS = SECOND_IN_MILLIS * 60;
    public static final long HOUR_IN_MILLIS = MINUTE_IN_MILLIS * 60;
    public static final long DAY_IN_MILLIS = HOUR_IN_MILLIS * 24;
    public static final long WEEK_IN_MILLIS = DAY_IN_MILLIS * 7;
    @Deprecated
    public static final long YEAR_IN_MILLIS = WEEK_IN_MILLIS * 52;

    public static final int FORMAT_SHOW_TIME = 0x00001;
    public static final int FORMAT_SHOW_WEEKDAY = 0x00002;
    public static final int FORMAT_SHOW_YEAR = 0x00004;
    public static final int FORMAT_NO_YEAR = 0x00008;
    public static final int FORMAT_SHOW_DATE = 0x00010;
    public static final int FORMAT_NO_MONTH_DAY = 0x00020;
    @Deprecated
    public static final int FORMAT_12HOUR = 0x00040;
    @Deprecated
    public static final int FORMAT_24HOUR = 0x00080;
    @Deprecated
    public static final int FORMAT_CAP_AMPM = 0x00100;
    public static final int FORMAT_NO_NOON = 0x00200;
    @Deprecated
    public static final int FORMAT_CAP_NOON = 0x00400;
    public static final int FORMAT_NO_MIDNIGHT = 0x00800;
    @Deprecated
    public static final int FORMAT_CAP_MIDNIGHT = 0x01000;
    @Deprecated
    public static final int FORMAT_UTC = 0x02000;
    public static final int FORMAT_ABBREV_TIME = 0x04000;
    public static final int FORMAT_ABBREV_WEEKDAY = 0x08000;
    public static final int FORMAT_ABBREV_MONTH = 0x10000;
    public static final int FORMAT_NUMERIC_DATE = 0x20000;
    public static final int FORMAT_ABBREV_RELATIVE = 0x40000;
    public static final int FORMAT_ABBREV_ALL = 0x80000;

    public static final int LENGTH_LONG = 10;
    public static final int LENGTH_MEDIUM = 20;
    public static final int LENGTH_SHORT = 30;
    public static final int LENGTH_SHORTER = 40;
    public static final int LENGTH_SHORTEST = 50;

    public static CharSequence getRelativeTimeSpanString(long time) {
        return getRelativeTimeSpanString(time, System.currentTimeMillis(), MINUTE_IN_MILLIS, 0);
    }

    public static CharSequence getRelativeTimeSpanString(long time, long now, long minResolution) {
        return getRelativeTimeSpanString(time, now, minResolution, 0);
    }

    public static CharSequence getRelativeTimeSpanString(long time, long now, long minResolution, int flags) {
        long span = Math.max(0, now - time);
        boolean abbrev = (flags & (FORMAT_ABBREV_RELATIVE | FORMAT_ABBREV_ALL)) != 0;
        if (span < MINUTE_IN_MILLIS && minResolution < MINUTE_IN_MILLIS) {
            long s = Math.max(1, span / SECOND_IN_MILLIS);
            if (minResolution <= SECOND_IN_MILLIS) return count(s, "second", "sec", abbrev) + " ago";
            return abbrev ? "0m ago" : "0 minutes ago";
        }
        if (span < HOUR_IN_MILLIS) {
            long m = Math.max(1, span / MINUTE_IN_MILLIS);
            return count(m, "minute", "min", abbrev) + " ago";
        }
        if (span < DAY_IN_MILLIS) {
            long h = span / HOUR_IN_MILLIS;
            return count(h, "hour", "hr", abbrev) + " ago";
        }
        if (span < WEEK_IN_MILLIS) {
            long d = span / DAY_IN_MILLIS;
            if (d <= 1) return abbrev ? "1d ago" : "Yesterday";
            return count(d, "day", "d", abbrev) + " ago";
        }
        long w = span / WEEK_IN_MILLIS;
        if (w < 5) return count(w, "week", "wk", abbrev) + " ago";
        java.time.LocalDate date = java.time.Instant.ofEpochMilli(time)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        return date.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy"));
    }

    private static String count(long n, String unit, String shortUnit, boolean abbrev) {
        if (abbrev) return n + shortUnit;
        return n + " " + unit + (n == 1 ? "" : "s");
    }

    public static String formatDateTime(android.content.Context context, long millis, int flags) {
        java.time.ZonedDateTime dt = java.time.Instant.ofEpochMilli(millis)
                .atZone(java.time.ZoneId.systemDefault());
        boolean abbrevAll = (flags & FORMAT_ABBREV_ALL) != 0;
        boolean abbrevWeekday = abbrevAll || (flags & (FORMAT_ABBREV_WEEKDAY | FORMAT_ABBREV_RELATIVE)) != 0;
        boolean abbrevMonth = abbrevAll || (flags & FORMAT_ABBREV_MONTH) != 0;
        boolean numeric = (flags & FORMAT_NUMERIC_DATE) != 0;
        StringBuilder pattern = new StringBuilder();
        if ((flags & FORMAT_SHOW_WEEKDAY) != 0) pattern.append(abbrevWeekday ? "EEE, " : "EEEE, ");
        if ((flags & FORMAT_SHOW_DATE) != 0 && (flags & FORMAT_NO_MONTH_DAY) == 0) {
            if (numeric) pattern.append("M/d/yyyy");
            else if (abbrevMonth) pattern.append("MMM d");
            else pattern.append("MMMM d");
            if ((flags & FORMAT_SHOW_YEAR) != 0 && (flags & FORMAT_NO_YEAR) == 0) pattern.append(", yyyy");
            else if ((flags & FORMAT_NO_YEAR) == 0
                    && dt.getYear() != java.time.LocalDate.now().getYear()) pattern.append(", yyyy");
        } else if ((flags & FORMAT_SHOW_YEAR) != 0 && (flags & FORMAT_NO_YEAR) == 0) {
            pattern.append("yyyy");
        }
        if ((flags & FORMAT_SHOW_TIME) != 0) {
            if (pattern.length() > 0) pattern.append(' ');
            pattern.append((flags & FORMAT_ABBREV_TIME) != 0 || abbrevAll ? "h:mm a" : "h:mm:ss a");
        }
        if (pattern.length() == 0) pattern.append("MMM d, yyyy");
        return dt.format(java.time.format.DateTimeFormatter.ofPattern(pattern.toString()));
    }

    public static String formatElapsedTime(long elapsedSeconds) {
        long h = elapsedSeconds / 3600, m = (elapsedSeconds % 3600) / 60, s = elapsedSeconds % 60;
        if (h > 0) return h + ":" + String.format("%02d:%02d", m, s);
        return m + ":" + String.format("%02d", s);
    }

    public static boolean isToday(long millis) {
        java.time.LocalDate date = java.time.Instant.ofEpochMilli(millis)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        return date.equals(java.time.LocalDate.now());
    }
}
