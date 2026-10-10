// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

/** Menu-item action view provider base. */
public abstract class ActionProvider {
    public interface VisibilityListener {
        void onActionProviderVisibilityChanged(boolean isVisible);
    }

    private VisibilityListener visibilityListener;

    public ActionProvider(android.content.Context context) {}

    public abstract View onCreateActionView(MenuItem item);

    public boolean onPerformDefaultAction() {
        return false;
    }

    public boolean hasSubMenu() {
        return false;
    }

    public void onPrepareSubMenu(SubMenu subMenu) {}

    public boolean overridesItemVisibility() {
        return false;
    }

    public boolean isVisible() {
        return true;
    }

    public void setVisibilityListener(VisibilityListener listener) {
        this.visibilityListener = listener;
    }
}
