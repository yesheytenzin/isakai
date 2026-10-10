// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

/** Checkable contract for toggle widgets. */
public interface Checkable {
    boolean isChecked();
    void setChecked(boolean checked);
    void toggle();
}
