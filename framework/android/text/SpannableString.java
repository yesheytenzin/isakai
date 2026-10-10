// SPDX-License-Identifier: GPL-3.0-or-later
package android.text;

import java.util.ArrayList;
import java.util.List;

/** Immutable text with mutable span marks. */
public class SpannableString implements Spannable {
    private final String text;
    private final List<Span> spans = new ArrayList<>();

    private static final class Span {
        final Object what;
        int start;
        int end;
        int flags;

        Span(Object what, int start, int end, int flags) {
            this.what = what;
            this.start = start;
            this.end = end;
            this.flags = flags;
        }
    }

    public SpannableString(CharSequence source) {
        this.text = source == null ? "" : source.toString();
    }

    public static SpannableString valueOf(CharSequence source) {
        return source instanceof SpannableString ? (SpannableString) source : new SpannableString(source);
    }

    @Override
    public int length() { return text.length(); }

    @Override
    public char charAt(int index) { return text.charAt(index); }

    @Override
    public CharSequence subSequence(int start, int end) { return text.subSequence(start, end); }

    @Override
    public String toString() { return text; }

    private int clamp(int v) { return Math.max(0, Math.min(v, text.length())); }

    @Override
    public void setSpan(Object what, int start, int end, int flags) {
        if (what == null) return;
        removeSpan(what);
        spans.add(new Span(what, clamp(start), clamp(end), flags));
    }

    @Override
    public void removeSpan(Object what) {
        spans.removeIf(s -> s.what == what);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T[] getSpans(int start, int end, Class<T> type) {
        List<T> out = new ArrayList<>();
        for (Span s : spans) {
            if (s.end >= start && s.start <= end && (type == null || type.isInstance(s.what))) {
                out.add((T) s.what);
            }
        }
        @SuppressWarnings("unchecked")
        T[] arr = (T[]) java.lang.reflect.Array.newInstance(
                type == null ? Object.class : type, out.size());
        return out.toArray(arr);
    }

    @Override
    public int getSpanStart(Object tag) {
        for (Span s : spans) if (s.what == tag) return s.start;
        return -1;
    }

    @Override
    public int getSpanEnd(Object tag) {
        for (Span s : spans) if (s.what == tag) return s.end;
        return -1;
    }

    @Override
    public int getSpanFlags(Object tag) {
        for (Span s : spans) if (s.what == tag) return s.flags;
        return 0;
    }

    @Override
    public int nextSpanTransition(int start, int limit, Class<?> type) {
        int next = limit;
        for (Span s : spans) {
            if (type != null && !type.isInstance(s.what)) continue;
            if (s.start > start && s.start < next) next = s.start;
            if (s.end > start && s.end < next) next = s.end;
        }
        return next;
    }
}
