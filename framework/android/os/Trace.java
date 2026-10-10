// SPDX-License-Identifier: GPL-3.0-or-later
package android.os;

/** Tracing helpers: no-op stub, tracing disabled. */
public final class Trace {
    public static final long TRACE_TAG_NEVER = 0L;
    public static final long TRACE_TAG_ALWAYS = 1L << 0;
    public static final long TRACE_TAG_APP = 1L << 12;

    private Trace() {
    }

    public static boolean isEnabled() {
        return false;
    }

    public static boolean isTagEnabled(long traceTag) {
        return false;
    }

    public static void beginSection(String sectionName) {
    }

    public static void endSection() {
    }

    public static void beginAsyncSection(String methodName, int cookie) {
    }

    public static void endAsyncSection(String methodName, int cookie) {
    }

    public static void setCounter(String counterName, long counterValue) {
    }
}
