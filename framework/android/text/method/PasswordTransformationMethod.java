// SPDX-License-Identifier: GPL-3.0-or-later
package android.text.method;

import android.graphics.Rect;
import android.text.Spannable;
import android.text.Spanned;
import android.view.View;

/** Masks every character as a dot. */
public class PasswordTransformationMethod implements TransformationMethod {
    private static final PasswordTransformationMethod INSTANCE = new PasswordTransformationMethod();

    public static PasswordTransformationMethod getInstance() {
        return INSTANCE;
    }

    @Override
    public CharSequence getTransformation(CharSequence source, View view) {
        if (source == null) return "";
        return new PasswordCharSequence(source);
    }

    @Override
    public void onFocusChanged(View view, CharSequence sourceText, boolean focused, int direction,
            Rect previouslyFocusedRect) {
    }

    private static final class PasswordCharSequence implements CharSequence, Spanned {
        private final CharSequence source;

        PasswordCharSequence(CharSequence source) {
            this.source = source;
        }

        @Override
        public int length() { return source.length(); }

        @Override
        public char charAt(int index) { return '\u2022'; }

        @Override
        public CharSequence subSequence(int start, int end) {
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < end; i++) sb.append('\u2022');
            return sb.toString();
        }

        @Override
        public String toString() { return subSequence(0, length()).toString(); }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T[] getSpans(int start, int end, Class<T> type) {
            if (source instanceof Spanned) return ((Spanned) source).getSpans(start, end, type);
            @SuppressWarnings("unchecked")
            T[] arr = (T[]) java.lang.reflect.Array.newInstance(
                    type == null ? Object.class : type, 0);
            return arr;
        }

        @Override
        public int getSpanStart(Object tag) {
            return source instanceof Spanned ? ((Spanned) source).getSpanStart(tag) : -1;
        }

        @Override
        public int getSpanEnd(Object tag) {
            return source instanceof Spanned ? ((Spanned) source).getSpanEnd(tag) : -1;
        }

        @Override
        public int getSpanFlags(Object tag) {
            return source instanceof Spanned ? ((Spanned) source).getSpanFlags(tag) : 0;
        }

        @Override
        public int nextSpanTransition(int start, int limit, Class<?> type) {
            return source instanceof Spanned
                    ? ((Spanned) source).nextSpanTransition(start, limit, type) : limit;
        }
    }

    public static void adjustMetaAfterKeypress(Spannable text) {
        MetaKeyKeyListener.adjustMetaAfterKeypress(text);
    }
}
