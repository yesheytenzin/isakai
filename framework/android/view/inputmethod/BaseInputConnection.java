// SPDX-License-Identifier: GPL-3.0-or-later
package android.view.inputmethod;

import android.os.Bundle;
import android.text.Editable;
import android.view.KeyEvent;
import android.view.View;

/** Editable-backed input connection with real buffer plumbing. */
public class BaseInputConnection implements InputConnection {
    protected final View targetView;
    protected final boolean fullEditor;
    private final Editable editable;
    public BaseInputConnection(View targetView, boolean fullEditor) {
        this.targetView = targetView;
        this.fullEditor = fullEditor;
        this.editable = new Editable();
    }
    public Editable getEditable() { return editable; }
    private int cursor() { return editable.length(); }
    @Override public CharSequence getTextBeforeCursor(int length, int flags) {
        int end = cursor(); int start = Math.max(0, end - Math.max(0, length));
        return editable.subSequence(start, end).toString();
    }
    @Override public CharSequence getTextAfterCursor(int length, int flags) { return ""; }
    @Override public CharSequence getSelectedText(int flags) { return null; }
    @Override public boolean setComposingText(CharSequence text, int newCursorPosition) {
        editable.replaceAll(text != null ? text.toString() : "");
        return true;
    }
    @Override public boolean commitText(CharSequence text, int newCursorPosition) {
        if (text != null) editable.append(text);
        return true;
    }
    public boolean finishComposingText() { return true; }
    @Override public boolean deleteSurroundingText(int beforeLength, int afterLength) {
        int end = cursor(); int start = Math.max(0, end - Math.max(0, beforeLength));
        editable.delete(start, end);
        return true;
    }
    @Override public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
        return deleteSurroundingText(beforeLength, afterLength);
    }
    @Override public boolean setSelection(int start, int end) { return true; }
    @Override public boolean beginBatchEdit() { return true; }
    @Override public boolean endBatchEdit() { return true; }
    @Override public boolean performEditorAction(int actionCode) { return false; }
    @Override public boolean performPrivateCommand(String action, Bundle data) { return false; }
    @Override public boolean commitContent(InputContentInfo info, int flags, Bundle opts) { return false; }
    public boolean sendKeyEvent(KeyEvent event) {
        if (event == null) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_DEL) {
            return deleteSurroundingText(1, 0);
        }
        return false;
    }
    public boolean setComposingRegion(int start, int end) { return true; }
}
