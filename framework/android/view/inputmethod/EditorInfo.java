// SPDX-License-Identifier: GPL-3.0-or-later
package android.view.inputmethod;

import android.os.Bundle;

/** Editor capabilities record. */
public class EditorInfo {
    public int inputType;
    public int imeOptions;
    public String privateImeOptions;
    public CharSequence actionLabel;
    public int actionId;
    public CharSequence hintText;
    public CharSequence label;
    public int initialSelStart = -1;
    public int initialSelEnd = -1;
    public int initialCapsMode;
    public String[] contentMimeTypes;
    public Bundle extras;
    public int targetInputMethodSubtypeId;

    public void setInitialSurroundingSubText(CharSequence subText, int subTextStart) {}

    public void makeCompatible(int targetSdkVersion) {}
}
