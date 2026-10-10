// SPDX-License-Identifier: GPL-3.0-or-later
package android.view.accessibility;

/**
 * AccessibilityRecord: event/node payload (headless: recorded, never sent).
 */
public class AccessibilityRecord {
    protected AccessibilityRecord() {}

    public static AccessibilityRecord obtain() {
        return new AccessibilityRecord();
    }

    public void recycle() {}

    public void setSource(android.view.View source) {}

    public void setClassName(CharSequence className) {}

    public void setContentDescription(CharSequence contentDescription) {}

    public java.util.List<CharSequence> getText() {
        return new java.util.ArrayList<>();
    }

    public void setItemCount(int itemCount) {}

    public void setFromIndex(int fromIndex) {}

    public void setToIndex(int toIndex) {}

    public void setScrollX(int scrollX) {}

    public void setScrollY(int scrollY) {}

    public void setMaxScrollX(int maxScrollX) {}

    public void setMaxScrollY(int maxScrollY) {}
}
