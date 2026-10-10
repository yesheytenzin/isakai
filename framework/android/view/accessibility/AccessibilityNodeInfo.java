// SPDX-License-Identifier: GPL-3.0-or-later
package android.view.accessibility;

import android.graphics.Rect;
import android.view.View;

/** Accessibility node record: properties plus child list. */
public class AccessibilityNodeInfo {
    public static final int ACTION_FOCUS = 1;
    public static final int ACTION_CLEAR_FOCUS = 2;
    public static final int ACTION_SELECT = 4;
    public static final int ACTION_CLICK = 16;
    public static final int ACTION_LONG_CLICK = 32;
    public static final int ACTION_SCROLL_FORWARD = 4096;
    public static final int ACTION_SCROLL_BACKWARD = 8192;

    public static class AccessibilityAction {
        public static final AccessibilityAction ACTION_SHOW_TOOLTIP =
                new AccessibilityAction(0, null);
        public static final AccessibilityAction ACTION_HIDE_TOOLTIP =
                new AccessibilityAction(0, null);
        public static final AccessibilityAction ACTION_MOVE_WINDOW =
                new AccessibilityAction(0, null);

        private final int id;
        private final CharSequence label;

        public AccessibilityAction(int id, CharSequence label) {
            this.id = id;
            this.label = label;
        }

        public int getId() { return id; }
        public CharSequence getLabel() { return label; }
    }

    public static class CollectionInfo {
        public static CollectionInfo obtain(int rows, int cols, boolean hierarchical, int selectionMode) {
            return new CollectionInfo();
        }
    }

    public static class CollectionItemInfo {
        public static CollectionItemInfo obtain(int row, int rowSpan, int col, int colSpan,
                boolean heading, boolean selected) {
            return new CollectionItemInfo();
        }
    }

    private CharSequence text;
    private CharSequence contentDescription;
    private CharSequence className;
    private CharSequence tooltipText;
    private CharSequence error;
    private final Rect boundsInParent = new Rect();
    private final Rect boundsInScreen = new Rect();
    private boolean checkable;
    private boolean checked;
    private boolean clickable;
    private boolean enabled = true;
    private boolean focusable;
    private boolean focused;
    private boolean scrollable;
    private boolean password;
    private boolean showingHintText;
    private int actions;

    public static AccessibilityNodeInfo obtain() {
        return new AccessibilityNodeInfo();
    }

    public static AccessibilityNodeInfo obtain(View source) {
        return new AccessibilityNodeInfo();
    }

    public static AccessibilityNodeInfo obtain(AccessibilityNodeInfo source) {
        AccessibilityNodeInfo n = new AccessibilityNodeInfo();
        if (source != null) {
            n.text = source.text;
            n.contentDescription = source.contentDescription;
            n.className = source.className;
        }
        return n;
    }

    public CharSequence getText() { return text; }
    public void setText(CharSequence text) { this.text = text; }
    public CharSequence getContentDescription() { return contentDescription; }
    public void setContentDescription(CharSequence desc) { this.contentDescription = desc; }
    public CharSequence getClassName() { return className; }
    public void setClassName(CharSequence name) { this.className = name; }
    public CharSequence getTooltipText() { return tooltipText; }
    public void setTooltipText(CharSequence text) { this.tooltipText = text; }
    public void setError(CharSequence error) { this.error = error; }

    public void getBoundsInParent(Rect out) { if (out != null) out.set(boundsInParent); }
    public void setBoundsInParent(Rect r) { if (r != null) boundsInParent.set(r); }
    public void getBoundsInScreen(Rect out) { if (out != null) out.set(boundsInScreen); }
    public void setBoundsInScreen(Rect r) { if (r != null) boundsInScreen.set(r); }

    public boolean isCheckable() { return checkable; }
    public void setCheckable(boolean checkable) { this.checkable = checkable; }
    public boolean isChecked() { return checked; }
    public void setChecked(boolean checked) { this.checked = checked; }
    public void setClickable(boolean clickable) { this.clickable = clickable; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isFocusable() { return focusable; }
    public void setFocusable(boolean focusable) { this.focusable = focusable; }
    public void setFocused(boolean focused) { this.focused = focused; }
    public boolean isScrollable() { return scrollable; }
    public void setScrollable(boolean scrollable) { this.scrollable = scrollable; }
    public boolean isPassword() { return password; }
    public void setPassword(boolean password) { this.password = password; }
    public boolean isShowingHintText() { return showingHintText; }
    public void setShowingHintText(boolean showing) { this.showingHintText = showing; }
    public void setAccessibilityFocused(boolean focused) {}

    public int getActions() { return actions; }
    public void addAction(int action) { actions |= action; }
    public void addAction(AccessibilityAction action) {}
    public int getChildCount() { return 0; }
    public void addChild(View child) {}
    public void addChild(View child, int virtualDescendantId) {}
    public void setCollectionInfo(CollectionInfo info) {}
    public void setCollectionItemInfo(CollectionItemInfo info) {}
    public void setSource(View source) {}
    public void setParent(View parent) {}
    public void recycle() {}
}
