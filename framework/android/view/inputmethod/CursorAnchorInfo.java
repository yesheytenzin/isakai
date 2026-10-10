// SPDX-License-Identifier: GPL-3.0-or-later
package android.view.inputmethod;

/** Cursor/selection anchor snapshot with builder. */
public final class CursorAnchorInfo {
    private final int selectionStart;
    private final int selectionEnd;
    private final int composingStart;
    private final int composingEnd;
    private CursorAnchorInfo(Builder b) {
        this.selectionStart = b.selectionStart; this.selectionEnd = b.selectionEnd;
        this.composingStart = b.composingStart; this.composingEnd = b.composingEnd;
    }
    public int getSelectionStart() { return selectionStart; }
    public int getSelectionEnd() { return selectionEnd; }
    public int getComposingTextStart() { return composingStart; }
    public int getComposingTextEnd() { return composingEnd; }
    public static final class Builder {
        private int selectionStart = -1;
        private int selectionEnd = -1;
        private int composingStart = -1;
        private int composingEnd = -1;
        public Builder setSelectionRange(int start, int end) { this.selectionStart = start; this.selectionEnd = end; return this; }
        public Builder setComposingText(int start, CharSequence text) {
            this.composingStart = start; this.composingEnd = text == null ? start : start + text.length(); return this;
        }
        public Builder reset() { selectionStart = selectionEnd = composingStart = composingEnd = -1; return this; }
        public CursorAnchorInfo build() { return new CursorAnchorInfo(this); }
    }
}
