// SPDX-License-Identifier: GPL-3.0-or-later
package android.text;

import java.util.ArrayList;
import java.util.List;

/** Mutable text with mutable span marks. */
public class SpannableStringBuilder implements Editable {
    private final StringBuilder buffer;
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

    public SpannableStringBuilder() {
        this.buffer = new StringBuilder();
    }

    public SpannableStringBuilder(CharSequence text) {
        this.buffer = new StringBuilder(text != null ? text.toString() : "");
    }

    public SpannableStringBuilder(CharSequence text, int start, int end) {
        this.buffer = new StringBuilder();
        if (text != null) {
            int s = Math.max(0, Math.min(start, text.length()));
            int e = Math.max(0, Math.min(end, text.length()));
            if (s < e) buffer.append(text, s, e);
        }
    }

    public static SpannableStringBuilder valueOf(CharSequence source) {
        return source instanceof SpannableStringBuilder
                ? (SpannableStringBuilder) source
                : new SpannableStringBuilder(source);
    }

    @Override
    public int length() { return buffer.length(); }

    @Override
    public char charAt(int index) { return buffer.charAt(index); }

    @Override
    public CharSequence subSequence(int start, int end) { return buffer.subSequence(start, end); }

    @Override
    public String toString() { return buffer.toString(); }

    private InputFilter[] filters=new InputFilter[0];
    public void setFilters(InputFilter[] filters) { if(filters==null)throw new IllegalArgumentException("filters");this.filters=filters.clone(); }
    public InputFilter[] getFilters() { return filters.clone(); }
    public void getChars(int start,int end,char[] dest,int destoff) { buffer.getChars(start,end,dest,destoff); }
    public void clear() { delete(0,length()); }
    public void clearSpans() { spans.clear(); }

    private int clamp(int v) { return Math.max(0, Math.min(v, buffer.length())); }

    private void shiftForInsert(int at, int count) {
        for (Span s : spans) {
            if (s.start >= at) s.start += count;
            if (s.end >= at) s.end += count;
        }
    }

    private void shiftForDelete(int start, int end) {
        int count = end - start;
        for (Span s : spans) {
            if (s.start > start) s.start = Math.max(start, s.start - count);
            if (s.end > start) s.end = Math.max(start, s.end - count);
        }
    }

    public SpannableStringBuilder insert(int where, CharSequence text) {
        return insert(where, text, 0, text == null ? 0 : text.length());
    }

    public SpannableStringBuilder insert(int where, CharSequence text, int start, int end) {
        return replace(where,where,text,start,end);
    }
    public SpannableStringBuilder delete(int start,int end) { return replace(start,end,"",0,0); }
    public SpannableStringBuilder replace(int start,int end,CharSequence text) {
        return replace(start,end,text,0,text==null?0:text.length());
    }
    public SpannableStringBuilder replace(int start,int end,CharSequence text,int textStart,int textEnd) {
        if(start<0 || end<start || end>length())throw new IndexOutOfBoundsException("replace range");
        if(text==null)text="";
        if(textStart<0 || textEnd<textStart || textEnd>text.length())throw new IndexOutOfBoundsException("source range");
        CharSequence replacement=text.subSequence(textStart,textEnd);
        for(InputFilter filter:filters) {
            CharSequence filtered=filter.filter(replacement,0,replacement.length(),this,start,end);
            if(filtered!=null)replacement=filtered;
        }
        String value=replacement.toString();
        buffer.replace(start,end,value);shiftForDelete(start,end);shiftForInsert(start,value.length());
        return this;
    }

    @Override
    public SpannableStringBuilder append(CharSequence text) {
        insert(buffer.length(), text);
        return this;
    }

    @Override
    public SpannableStringBuilder append(CharSequence text, int start, int end) {
        insert(buffer.length(), text, start, end);
        return this;
    }

    @Override
    public SpannableStringBuilder append(char c) {
        insert(buffer.length(), String.valueOf(c));
        return this;
    }

    public SpannableStringBuilder append(CharSequence text, Object what, int flags) {
        int start = buffer.length();
        append(text);
        setSpan(what, start, buffer.length(), flags);
        return this;
    }

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
