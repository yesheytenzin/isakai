// SPDX-License-Identifier: GPL-3.0-or-later
package android.text.method;

import android.text.Editable;
import android.text.Spanned;
import android.view.KeyEvent;
import android.view.View;

/** Key listener admitting only a whitelisted set of characters. */
public abstract class NumberKeyListener implements KeyListener, android.text.InputFilter {
    protected abstract char[] getAcceptedChars();

    protected int lookup(KeyEvent event, CharSequence text) {
        char c = (char) event.getUnicodeChar();
        return ok(getAcceptedChars(), c) ? c : 0;
    }

    public static boolean ok(char[] accept, char c) {
        for (char a : accept) {
            if (a == c) return true;
        }
        return false;
    }

    @Override
    public CharSequence filter(CharSequence source, int start, int end, Spanned dest,
            int dstart, int dend) {
        char[] accept = getAcceptedChars();
        boolean keep = true;
        for (int i = start; i < end; i++) {
            if (!ok(accept, source.charAt(i))) {
                keep = false;
                break;
            }
        }
        if (keep) return null;
        StringBuilder kept = new StringBuilder();
        for (int i = start; i < end; i++) {
            char c = source.charAt(i);
            if (ok(accept, c)) kept.append(c);
        }
        if (kept.length() == end - start) return null;
        return kept.toString();
    }

    @Override
    public int getInputType() {
        return 2;
    }

    @Override
    public boolean onKeyDown(View view, Editable text, int keyCode, KeyEvent event) {
        int c = lookup(event, text);
        if (c == 0) return false;
        text.insert(text.length(), String.valueOf((char) c));
        return true;
    }

    @Override
    public boolean onKeyUp(View view, Editable text, int keyCode, KeyEvent event) {
        return false;
    }

    @Override
    public boolean onKeyOther(View view, Editable text, KeyEvent event) {
        return false;
    }

    @Override
    public void clearMetaKeyState(View view, Editable content, int states) {
    }
}
