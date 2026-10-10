// SPDX-License-Identifier: GPL-3.0-or-later
package android.text;

/** Selection marks over a Spannable. */
public final class Selection {
    public static final Object SELECTION_START = new Object();
    public static final Object SELECTION_END = new Object();

    private Selection() {}

    public static int getSelectionStart(CharSequence text) {
        if (text instanceof Spanned) return ((Spanned) text).getSpanStart(SELECTION_START);
        return -1;
    }

    public static int getSelectionEnd(CharSequence text) {
        if (text instanceof Spanned) return ((Spanned) text).getSpanStart(SELECTION_END);
        return -1;
    }

    public static void setSelection(Spannable text, int index) {
        setSelection(text, index, index);
    }

    public static void setSelection(Spannable text, int start, int stop) {
        if (text == null) return;
        int len = text.length();
        int s = Math.max(0, Math.min(start, len));
        int e = Math.max(0, Math.min(stop, len));
        text.setSpan(SELECTION_START, s, s, Spanned.SPAN_POINT_POINT);
        text.setSpan(SELECTION_END, e, e, Spanned.SPAN_POINT_POINT);
    }

    public static void removeSelection(Spannable text) {
        if (text == null) return;
        text.removeSpan(SELECTION_START);
        text.removeSpan(SELECTION_END);
    }
}
