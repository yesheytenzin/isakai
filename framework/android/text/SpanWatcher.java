// SPDX-License-Identifier: GPL-3.0-or-later
package android.text;

/** Span attach/detach/change callbacks. */
public interface SpanWatcher {
    void onSpanAdded(Spannable text, Object what, int start, int end);
    void onSpanRemoved(Spannable text, Object what, int start, int end);
    void onSpanChanged(Spannable text, Object what, int ostart, int oend, int nstart, int nend);
}
