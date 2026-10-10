// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

/** Contextual action bar: finish hooks only. */
public class ActionMode {
    public interface Callback {
        boolean onCreateActionMode(ActionMode mode, Menu menu);
        boolean onPrepareActionMode(ActionMode mode, Menu menu);
        boolean onActionItemClicked(ActionMode mode, MenuItem item);
        void onDestroyActionMode(ActionMode mode);
    }

    public void setTitle(CharSequence title) {}
    public void setSubtitle(CharSequence subtitle) {}
    public void invalidate() {}
    public void finish() {}
    public Menu getMenu() { return null; }
}
