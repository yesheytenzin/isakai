// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.Context;

/** Multi-token completion field with a tokenizer. */
public class MultiAutoCompleteTextView extends AutoCompleteTextView {
    public interface Tokenizer {
        int findTokenStart(CharSequence text, int cursor);
        int findTokenEnd(CharSequence text, int cursor);
        CharSequence terminateToken(CharSequence text);
    }

    public static class CommaTokenizer implements Tokenizer {
        @Override
        public int findTokenStart(CharSequence text, int cursor) {
            int i = cursor;
            while (i > 0 && text.charAt(i - 1) != ',') i--;
            while (i < cursor && text.charAt(i) == ' ') i++;
            return i;
        }

        @Override
        public int findTokenEnd(CharSequence text, int cursor) {
            int i = cursor;
            while (i < text.length() && text.charAt(i) != ',') i++;
            return i;
        }

        @Override
        public CharSequence terminateToken(CharSequence text) {
            int i = text.length();
            while (i > 0 && text.charAt(i - 1) == ' ') i--;
            if (i > 0 && text.charAt(i - 1) == ',') return text;
            return text + ", ";
        }
    }

    private Tokenizer tokenizer;

    public MultiAutoCompleteTextView(Context context) {
        super(context);
    }

    public MultiAutoCompleteTextView(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    public MultiAutoCompleteTextView(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setTokenizer(Tokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }
}
