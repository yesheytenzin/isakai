// SPDX-License-Identifier: GPL-3.0-or-later
package android.text.method;

import android.text.Editable;
import android.view.KeyEvent;
import android.view.View;

/** Key-event to Editable translation contract. */
public interface KeyListener {
    int getInputType();
    boolean onKeyDown(View view, Editable text, int keyCode, KeyEvent event);
    boolean onKeyUp(View view, Editable text, int keyCode, KeyEvent event);
    boolean onKeyOther(View view, Editable text, KeyEvent event);
    void clearMetaKeyState(View view, Editable content, int states);
}
