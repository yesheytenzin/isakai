// SPDX-License-Identifier: GPL-3.0-or-later
package android.app;

/** Desktop notification channel: id + name + importance (drives urgency). */
public class NotificationChannel {
    /** No notifications; urgency 0. */
    public static final int IMPORTANCE_NONE = 0;
    /** Minimal; urgency 0. */
    public static final int IMPORTANCE_MIN = 1;
    /** Low; urgency 0. */
    public static final int IMPORTANCE_LOW = 2;
    /** Default; urgency 1. */
    public static final int IMPORTANCE_DEFAULT = 3;
    /** High; urgency 2. */
    public static final int IMPORTANCE_HIGH = 4;
    /** Max; urgency 2. */
    public static final int IMPORTANCE_MAX = 5;
    /** Unspecified; treated as default. */
    public static final int IMPORTANCE_UNSPECIFIED = -1000;

    private final String id;
    private final CharSequence name;
    private final int importance;

    /** Channel with {@code id}, {@code name}, and importance level. */
    public NotificationChannel(String id, CharSequence name, int importance) {
        this.id = id;
        this.name = name;
        this.importance = importance;
    }

    /** Channel id. */
    public String getId() {
        return id;
    }

    /** Human-readable channel name. */
    public CharSequence getName() {
        return name;
    }

    /** Importance level (one of the IMPORTANCE_* constants). */
    public int getImportance() {
        return importance;
    }
}
