// SPDX-License-Identifier: GPL-3.0-or-later
package android.util;

/** Log sink: stderr with level/tag prefix. */
public final class Log {
    public static final int VERBOSE = 2;
    public static final int DEBUG = 3;
    public static final int INFO = 4;
    public static final int WARN = 5;
    public static final int ERROR = 6;
    public static final int ASSERT = 7;

    private Log() {}

    private static int emit(char level, String tag, String msg, Throwable tr) {
        StringBuilder sb = new StringBuilder();
        sb.append(level).append('/').append(tag == null ? "?" : tag).append(": ");
        sb.append(msg == null ? "" : msg);
        if (tr != null) {
            sb.append('\n').append(tr.toString());
            for (StackTraceElement e : tr.getStackTrace()) sb.append("\n\tat ").append(e);
        }
        System.err.println(sb);
        return 0;
    }

    public static int v(String tag, String msg) { return emit('V', tag, msg, null); }
    public static int v(String tag, String msg, Throwable tr) { return emit('V', tag, msg, tr); }
    public static int d(String tag, String msg) { return emit('D', tag, msg, null); }
    public static int d(String tag, String msg, Throwable tr) { return emit('D', tag, msg, tr); }
    public static int i(String tag, String msg) { return emit('I', tag, msg, null); }
    public static int i(String tag, String msg, Throwable tr) { return emit('I', tag, msg, tr); }
    public static int w(String tag, String msg) { return emit('W', tag, msg, null); }
    public static int w(String tag, String msg, Throwable tr) { return emit('W', tag, msg, tr); }
    public static int w(String tag, Throwable tr) { return emit('W', tag, "", tr); }
    public static int e(String tag, String msg) { return emit('E', tag, msg, null); }
    public static int e(String tag, String msg, Throwable tr) { return emit('E', tag, msg, tr); }
    public static int wtf(String tag, String msg) { return emit('F', tag, msg, null); }
    public static int wtf(String tag, Throwable tr) { return emit('F', tag, "", tr); }
    public static int wtf(String tag, String msg, Throwable tr) { return emit('F', tag, msg, tr); }

    public static boolean isLoggable(String tag, int level) { return true; }
    public static String getStackTraceString(Throwable tr) {
        if (tr == null) return "";
        java.io.StringWriter sw = new java.io.StringWriter();
        tr.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }
}
