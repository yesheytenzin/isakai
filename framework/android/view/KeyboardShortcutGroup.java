// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

/** Keyboard-shortcut group record. */
public class KeyboardShortcutGroup {
    private final CharSequence label;

    public KeyboardShortcutGroup(CharSequence label) {
        this.label = label;
    }

    public CharSequence getLabel() {
        return label;
    }
}
