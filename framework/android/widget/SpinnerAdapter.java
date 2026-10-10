// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

/** Spinner adapter with dropdown views. */
public interface SpinnerAdapter extends Adapter {
    android.view.View getDropDownView(int position, android.view.View convertView,
            android.view.ViewGroup parent);
}
