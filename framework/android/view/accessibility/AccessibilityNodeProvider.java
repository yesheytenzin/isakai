// SPDX-License-Identifier: GPL-3.0-or-later
package android.view.accessibility;

import android.view.View;

/** Custom accessibility bridge: no virtual nodes. */
public class AccessibilityNodeProvider {
    public AccessibilityNodeInfo createAccessibilityNodeInfo(int virtualViewId) {
        return null;
    }

    public boolean performAction(int virtualViewId, int action, android.os.Bundle arguments) {
        return false;
    }

    public java.util.List<AccessibilityNodeInfo> findAccessibilityNodeInfosByText(String text, int virtualViewId) {
        return java.util.Collections.emptyList();
    }
}
