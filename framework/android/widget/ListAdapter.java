// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

/** Adapter with stable-id and dropdown-view hooks. */
public interface ListAdapter extends Adapter {
    boolean areAllItemsEnabled();
    boolean isEnabled(int position);
}
