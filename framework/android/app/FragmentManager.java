// SPDX-License-Identifier: GPL-3.0-or-later
package android.app;

import java.util.HashMap;
import java.util.Map;

/** Fragment registry with tag lookup. */
public class FragmentManager {
    private final Map<String, Fragment> fragments = new HashMap<>();

    public FragmentTransaction beginTransaction() {
        return new FragmentTransaction(this);
    }

    public Fragment findFragmentByTag(String tag) {
        return fragments.get(tag);
    }

    public boolean executePendingTransactions() {
        return false;
    }

    void putFragment(String tag, Fragment fragment) {
        if (tag != null && fragment != null) fragments.put(tag, fragment);
    }

    void removeFragment(Fragment fragment) {
        fragments.values().remove(fragment);
    }
}
