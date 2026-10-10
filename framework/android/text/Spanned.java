// SPDX-License-Identifier: GPL-3.0-or-later
package android.text;

/** Read-only span queries over marked-up text. */
public interface Spanned extends CharSequence {
    int SPAN_POINT_POINT = 0;
    int SPAN_MARK_MARK = 1 << 8;
    int SPAN_MARK_POINT = (1 << 8) | 0;
    int SPAN_POINT_MARK = (1 << 8) | 1;
    int SPAN_POINT_POINT_MARK = (1 << 8) | 2;
    int SPAN_EXCLUSIVE_EXCLUSIVE = 33;
    int SPAN_EXCLUSIVE_INCLUSIVE = 34;
    int SPAN_INCLUSIVE_EXCLUSIVE = 17;
    int SPAN_INCLUSIVE_INCLUSIVE = 18;
    int SPAN_COMPOSING = 256;
    int SPAN_INTERMEDIATE = 512;
    int SPAN_USER_SHIFT = 24;
    int SPAN_USER = 0xFF000000;
    int SPAN_PRIORITY_SHIFT = 16;
    int SPAN_PRIORITY = 0xFF0000;

    <T> T[] getSpans(int start, int end, Class<T> type);
    int getSpanStart(Object tag);
    int getSpanEnd(Object tag);
    int getSpanFlags(Object tag);
    int nextSpanTransition(int start, int limit, Class<?> type);
}
