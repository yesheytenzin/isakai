// SPDX-License-Identifier: GPL-3.0-or-later
package android.view.inputmethod;

import android.os.Bundle;

/** Delegating input connection wrapper. */
public class InputConnectionWrapper implements InputConnection {
    private InputConnection target;
    private final boolean mutable;

    public InputConnectionWrapper(InputConnection target, boolean mutable) {
        this.target = target;
        this.mutable = mutable;
    }

    public void setTarget(InputConnection target) {
        this.target = target;
    }

    @Override
    public CharSequence getTextBeforeCursor(int length, int flags) {
        return target == null ? "" : target.getTextBeforeCursor(length, flags);
    }

    @Override
    public CharSequence getTextAfterCursor(int length, int flags) {
        return target == null ? "" : target.getTextAfterCursor(length, flags);
    }

    @Override
    public CharSequence getSelectedText(int flags) {
        return target == null ? "" : target.getSelectedText(flags);
    }

    @Override
    public boolean setComposingText(CharSequence text, int newCursorPosition) {
        return target != null && target.setComposingText(text, newCursorPosition);
    }

    @Override
    public boolean commitText(CharSequence text, int newCursorPosition) {
        return target != null && target.commitText(text, newCursorPosition);
    }

    @Override
    public boolean deleteSurroundingText(int beforeLength, int afterLength) {
        return target != null && target.deleteSurroundingText(beforeLength, afterLength);
    }

    @Override
    public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
        return target != null && target.deleteSurroundingTextInCodePoints(beforeLength, afterLength);
    }

    @Override
    public boolean setSelection(int start, int end) {
        return target != null && target.setSelection(start, end);
    }

    @Override
    public boolean beginBatchEdit() {
        return target != null && target.beginBatchEdit();
    }

    @Override
    public boolean endBatchEdit() {
        return target != null && target.endBatchEdit();
    }

    @Override
    public boolean performEditorAction(int actionCode) {
        return target != null && target.performEditorAction(actionCode);
    }

    @Override
    public boolean performPrivateCommand(String action, Bundle data) {
        return target != null && target.performPrivateCommand(action, data);
    }

    @Override
    public boolean commitContent(InputContentInfo inputContentInfo, int flags, Bundle opts) {
        return target != null && target.commitContent(inputContentInfo, flags, opts);
    }
}
