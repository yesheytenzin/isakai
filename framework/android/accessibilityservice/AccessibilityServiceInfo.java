// SPDX-License-Identifier: GPL-3.0-or-later
package android.accessibilityservice;

/** Accessibility service description stub. */
public class AccessibilityServiceInfo {
    public static final int FEEDBACK_SPOKEN = 1;
    public static final int FEEDBACK_HAPTIC = 2;
    public static final int FEEDBACK_AUDIBLE = 4;
    public static final int FEEDBACK_VISUAL = 8;
    public static final int FEEDBACK_GENERIC = 16;
    public static final int FLAG_REPORT_VIEW_IDS = 16;

    public int eventTypes;
    public int feedbackType;
    public long notificationTimeout;

    public AccessibilityServiceInfo() {
    }
}
