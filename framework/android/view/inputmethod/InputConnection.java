// SPDX-License-Identifier: GPL-3.0-or-later
package android.view.inputmethod;

import android.os.Bundle;
import android.view.KeyEvent;

/** IME-to-editor bridge contract. */
public interface InputConnection {
    CharSequence getTextBeforeCursor(int length, int flags);
    CharSequence getTextAfterCursor(int length, int flags);
    CharSequence getSelectedText(int flags);
    boolean setComposingText(CharSequence text, int newCursorPosition);
    boolean commitText(CharSequence text, int newCursorPosition);
    boolean deleteSurroundingText(int beforeLength, int afterLength);
    boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength);
    boolean setSelection(int start, int end);
    boolean beginBatchEdit();
    boolean endBatchEdit();
    boolean performEditorAction(int actionCode);
    boolean performPrivateCommand(String action, Bundle data);
    boolean commitContent(InputContentInfo inputContentInfo, int flags, Bundle opts);
}
