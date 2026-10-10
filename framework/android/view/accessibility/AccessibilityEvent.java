// SPDX-License-Identifier: GPL-3.0-or-later
package android.view.accessibility;

import android.graphics.Rect;
import android.view.View;

/** Accessibility event record: type plus text list. Extends the record
 * base like AOSP (translated code calls record methods on events). */
public class AccessibilityEvent extends AccessibilityRecord {
    public static final int TYPE_VIEW_CLICKED = 1;
    public static final int TYPE_VIEW_LONG_CLICKED = 2;
    public static final int TYPE_VIEW_SELECTED = 4;
    public static final int TYPE_VIEW_FOCUSED = 8;
    public static final int TYPE_VIEW_TEXT_CHANGED = 16;
    public static final int TYPE_WINDOW_STATE_CHANGED = 32;
    public static final int TYPE_VIEW_HOVER_ENTER = 128;
    public static final int TYPE_VIEW_HOVER_EXIT = 256;
    public static final int TYPE_TOUCH_EXPLORATION_GESTURE_START = 512;
    public static final int TYPE_TOUCH_EXPLORATION_GESTURE_END = 1024;
    public static final int TYPE_WINDOW_CONTENT_CHANGED = 2048;

    private int eventType;
    private CharSequence packageName;
    private CharSequence className;
    private final java.util.ArrayList<CharSequence> text = new java.util.ArrayList<>();

    public static AccessibilityEvent obtain(int eventType) {
        AccessibilityEvent e = new AccessibilityEvent();
        e.eventType = eventType;
        return e;
    }

    public static AccessibilityEvent obtain(AccessibilityEvent source) {
        AccessibilityEvent e = new AccessibilityEvent();
        if (source != null) {
            e.eventType = source.eventType;
            e.packageName = source.packageName;
            e.className = source.className;
            e.text.addAll(source.text);
        }
        return e;
    }

    public int getEventType() { return eventType; }
    public void setEventType(int type) { this.eventType = type; }
    public CharSequence getPackageName() { return packageName; }
    public void setPackageName(CharSequence name) { this.packageName = name; }
    public CharSequence getClassName() { return className; }
    public void setClassName(CharSequence name) { this.className = name; }
    @Override
    public java.util.List<CharSequence> getText() { return text; }
    @Override
    public void recycle() { super.recycle(); }
}
