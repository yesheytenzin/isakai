// SPDX-License-Identifier: GPL-3.0-or-later
package android.util;

public final class LogPrinter implements Printer {
    private final int priority;
    private final String tag;

    public LogPrinter(int priority, String tag) {
        this.priority = priority;
        this.tag = tag;
    }

    @Override
    public void println(String value) {
        switch (priority) {
            case Log.VERBOSE -> Log.v(tag, value);
            case Log.DEBUG -> Log.d(tag, value);
            case Log.INFO -> Log.i(tag, value);
            case Log.WARN -> Log.w(tag, value);
            case Log.ERROR, Log.ASSERT -> Log.e(tag, value);
            default -> Log.d(tag, value);
        }
    }
}
