// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

/** Menu contract: item list with add/remove. */
public interface Menu {
    MenuItem add(CharSequence title);
    MenuItem add(int titleRes);
    MenuItem add(int groupId, int itemId, int order, CharSequence title);
    SubMenu addSubMenu(CharSequence title);
    SubMenu addSubMenu(int groupId, int itemId, int order, CharSequence title);
    MenuItem findItem(int id);
    MenuItem getItem(int index);
    int size();
    void removeItem(int id);
    void clear();
    void close();
    boolean hasVisibleItems();
}
