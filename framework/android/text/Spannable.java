// SPDX-License-Identifier: GPL-3.0-or-later
package android.text;

/** Mutable span attachment over Spanned. */
public interface Spannable extends Spanned {
    int SPAN_COMPOSING = Spanned.SPAN_COMPOSING;
    int SPAN_INTERMEDIATE = Spanned.SPAN_INTERMEDIATE;
    int SPAN_USER_SHIFT = Spanned.SPAN_USER_SHIFT;
    int SPAN_USER = Spanned.SPAN_USER;
    int SPAN_PRIORITY_SHIFT = Spanned.SPAN_PRIORITY_SHIFT;
    int SPAN_PRIORITY = Spanned.SPAN_PRIORITY;

    void setSpan(Object what, int start, int end, int flags);
    void removeSpan(Object what);

    interface Factory {
        Spannable newSpannable(CharSequence source);
    }
}
