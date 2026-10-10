// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;

/** Spinner adapter with a themed dropdown view. */
public interface ThemedSpinnerAdapter extends SpinnerAdapter {
    void setDropDownViewTheme(Resources.Theme theme);

    Resources.Theme getDropDownViewTheme();

    @Override
    default View getDropDownView(int position, View convertView, ViewGroup parent) {
        return getView(position, convertView, parent);
    }
}
