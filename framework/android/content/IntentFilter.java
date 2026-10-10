// SPDX-License-Identifier: GPL-3.0-or-later
package android.content;

import java.util.ArrayList;
import java.util.List;

/** Intent filter: action/category lists, matching by membership. */
public class IntentFilter {
    private final List<String> actions = new ArrayList<>();
    private final List<String> categories = new ArrayList<>();

    public IntentFilter() {}

    public IntentFilter(String action) {
        addAction(action);
    }

    public void addAction(String action) {
        if (action != null && !actions.contains(action)) actions.add(action);
    }

    public int countActions() { return actions.size(); }
    public String getAction(int index) { return actions.get(index); }
    public boolean hasAction(String action) { return actions.contains(action); }

    public void addCategory(String category) {
        if (category != null && !categories.contains(category)) categories.add(category);
    }

    public int countCategories() { return categories.size(); }
    public boolean hasCategory(String category) { return categories.contains(category); }

    public boolean matchAction(String action) {
        return actions.isEmpty() || (action != null && actions.contains(action));
    }
}
