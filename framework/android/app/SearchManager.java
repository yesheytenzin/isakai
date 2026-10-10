// SPDX-License-Identifier: GPL-3.0-or-later
package android.app;

import android.content.ComponentName;
import android.os.Bundle;

/** Search UI hooks; no-op shell. */
public class SearchManager {
    public static final String QUERY = "query";
    public static final String ACTION_KEY = "action_key";
    public static final String EXTRA_SELECT_QUERY = "select_query";
    public void startSearch(String initialQuery, boolean selectInitialQuery, ComponentName launchActivity, Bundle appSearchData, boolean globalSearch) { }
    public void stopSearch() { }
    public boolean isVisible() { return false; }
    public android.content.ComponentName getGlobalSearchActivity() { return null; }
    public void setOnCancelListener(SearchManager.OnCancelListener listener) { }
    public void setOnDismissListener(SearchManager.OnDismissListener listener) { }
    public interface OnCancelListener { void onCancel(); }
    public interface OnDismissListener { void onDismiss(); }
}
