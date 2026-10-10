// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

/** Menu item contract: recorded state, no rendering. */
public interface MenuItem {
    int SHOW_AS_ACTION_NEVER = 0;
    int SHOW_AS_ACTION_IF_ROOM = 1;
    int SHOW_AS_ACTION_ALWAYS = 2;
    int SHOW_AS_ACTION_WITH_TEXT = 4;
    int SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW = 8;

    interface OnMenuItemClickListener {
        boolean onMenuItemClick(MenuItem item);
    }

    interface OnActionExpandListener {
        boolean onMenuItemActionExpand(MenuItem item);
        boolean onMenuItemActionCollapse(MenuItem item);
    }

    int getItemId();
    int getGroupId();
    int getOrder();
    CharSequence getTitle();
    CharSequence getTitleCondensed();
    android.graphics.drawable.Drawable getIcon();
    android.content.Intent getIntent();
    char getAlphabeticShortcut();
    char getNumericShortcut();
    boolean isCheckable();
    boolean isChecked();
    boolean isEnabled();
    boolean isVisible();
    boolean hasSubMenu();
    SubMenu getSubMenu();
    ContextMenu.ContextMenuInfo getMenuInfo();
    android.view.View getActionView();
    boolean isActionViewExpanded();

    MenuItem setTitle(CharSequence title);
    MenuItem setTitle(int title);
    MenuItem setTitleCondensed(CharSequence title);
    MenuItem setIcon(android.graphics.drawable.Drawable icon);
    MenuItem setIcon(int iconRes);
    MenuItem setIntent(android.content.Intent intent);
    MenuItem setCheckable(boolean checkable);
    MenuItem setChecked(boolean checked);
    MenuItem setEnabled(boolean enabled);
    MenuItem setVisible(boolean visible);
    MenuItem setOnMenuItemClickListener(OnMenuItemClickListener listener);
    MenuItem setActionView(android.view.View view);
    MenuItem setActionView(int resId);
    void setShowAsAction(int action);
    boolean expandActionView();
    boolean collapseActionView();
    MenuItem setOnActionExpandListener(OnActionExpandListener listener);
}
