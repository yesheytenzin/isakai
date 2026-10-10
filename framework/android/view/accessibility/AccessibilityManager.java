// SPDX-License-Identifier: GPL-3.0-or-later
package android.view.accessibility;

/** Accessibility service registry: disabled, empty lists. */
public class AccessibilityManager {
    public interface TouchExplorationStateChangeListener {
        void onTouchExplorationStateChanged(boolean enabled);
    }

    public boolean isEnabled() { return false; }
    public boolean isTouchExplorationEnabled() { return false; }

    public java.util.List<android.content.pm.ResolveInfo> getEnabledAccessibilityServiceList(int feedbackFlags) {
        return java.util.Collections.emptyList();
    }

    public boolean addTouchExplorationStateChangeListener(TouchExplorationStateChangeListener listener) {
        return false;
    }

    public boolean removeTouchExplorationStateChangeListener(TouchExplorationStateChangeListener listener) {
        return false;
    }
}
