// SPDX-License-Identifier: GPL-3.0-or-later
package android.view.autofill;

/** Autofill value union: text, list index, or toggle. */
public final class AutofillValue {
    private final CharSequence textValue;
    private final int listIndex;
    private final boolean toggle;
    private final boolean isText;
    private final boolean isList;
    private final boolean isToggle;
    private AutofillValue(CharSequence text, int listIndex, boolean toggle, boolean isText, boolean isList, boolean isToggle) {
        this.textValue = text; this.listIndex = listIndex; this.toggle = toggle;
        this.isText = isText; this.isList = isList; this.isToggle = isToggle;
    }
    public static AutofillValue forText(CharSequence value) { return new AutofillValue(value, -1, false, true, false, false); }
    public static AutofillValue forList(int value) { return new AutofillValue(null, value, false, false, true, false); }
    public static AutofillValue forToggle(boolean value) { return new AutofillValue(null, -1, value, false, false, true); }
    public boolean isText() { return isText; }
    public boolean isList() { return isList; }
    public boolean isToggle() { return isToggle; }
    public CharSequence getTextValue() { return textValue; }
    public int getListValue() { return listIndex; }
    public boolean getToggleValue() { return toggle; }
}
