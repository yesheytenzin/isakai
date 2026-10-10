// SPDX-License-Identifier: GPL-3.0-or-later
package android.text;

import android.os.Parcel;
import android.os.Parcelable;

/** Text helpers: emptiness, equality, splitting, ellipsizing. */
public final class TextUtils {
    public enum TruncateAt {
        START, MIDDLE, END, MARQUEE
    }

    public static final Parcelable.Creator<CharSequence> CHAR_SEQUENCE_CREATOR =
            new Parcelable.Creator<CharSequence>() {
                @Override
                public CharSequence createFromParcel(Parcel source) {
                    return null;
                }

                @Override
                public CharSequence[] newArray(int size) {
                    return new CharSequence[size];
                }
            };

    private TextUtils() {}

    public static boolean isEmpty(CharSequence s) {
        return s == null || s.length() == 0;
    }

    public static boolean equals(CharSequence a, CharSequence b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        int len = a.length();
        if (len != b.length()) return false;
        for (int i = 0; i < len; i++) {
            if (a.charAt(i) != b.charAt(i)) return false;
        }
        return true;
    }

    public static String[] split(String text, String pattern) {
        if (text == null) return new String[0];
        return text.split(pattern == null ? "" : pattern);
    }

    public static String join(CharSequence delimiter, CharSequence[] tokens) {
        if (tokens == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            if (i > 0) sb.append(delimiter);
            if (tokens[i] != null) sb.append(tokens[i]);
        }
        return sb.toString();
    }

    public static String join(CharSequence delimiter, Iterable<?> tokens) {
        if (tokens == null) return "";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Object t : tokens) {
            if (!first) sb.append(delimiter);
            first = false;
            if (t != null) sb.append(t.toString());
        }
        return sb.toString();
    }

    public static CharSequence ellipsize(CharSequence text, TextPaint paint, float avail,
            TruncateAt where) {
        return ellipsize(text, paint, avail, where, false, null);
    }

    public static CharSequence ellipsize(CharSequence text, TextPaint paint, float avail,
            TruncateAt where, boolean preserveLength, EllipsizeCallback callback) {
        if (text == null) return "";
        if (paint == null || where == null || avail <= 0) return text;
        float full = paint.measureText(text, 0, text.length());
        if (full <= avail) return text;
        final String ellipsis = "\u2026";
        float ellW = paint.measureText(ellipsis);
        if (ellW >= avail) return preserveLength ? text.subSequence(0, 0) : ellipsis;
        int len = text.length();
        if (where == TruncateAt.START) {
            for (int i = 1; i < len; i++) {
                if (paint.measureText(text, i, len) + ellW <= avail) {
                    return ellipsis + text.subSequence(i, len);
                }
            }
            return ellipsis;
        }
        if (where == TruncateAt.MIDDLE || where == TruncateAt.MARQUEE) {
            int keep = len;
            while (keep > 0) {
                int head = (keep + 1) / 2;
                int tail = keep / 2;
                float w = paint.measureText(text, 0, head)
                        + paint.measureText(text, len - tail, len) + ellW;
                if (w <= avail) {
                    return text.subSequence(0, head) + ellipsis + text.subSequence(len - tail, len);
                }
                keep--;
            }
            return ellipsis;
        }
        for (int i = len - 1; i >= 0; i--) {
            if (paint.measureText(text, 0, i) + ellW <= avail) {
                return text.subSequence(0, i) + ellipsis;
            }
        }
        return ellipsis;
    }

    public static int getLayoutDirectionFromLocale(java.util.Locale locale) {
        return 0;
    }

    public static void writeToParcel(CharSequence text, Parcel dest, int flags) {
        if (dest != null) dest.writeString(text == null ? null : text.toString());
    }

    public static int getTrimmedLength(CharSequence s) {
        if (s == null) return 0;
        int len = s.length();
        while (len > 0 && s.charAt(len - 1) <= ' ') len--;
        return len;
    }

    public interface EllipsizeCallback {
        void ellipsized(int start, int end);
    }
}
