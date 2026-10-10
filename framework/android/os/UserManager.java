// SPDX-License-Identifier: GPL-3.0-or-later
package android.os;

/** User management stub: single user, no restrictions. */
public class UserManager {
    public UserManager() {
    }

    public boolean isUserUnlocked() {
        return true;
    }

    public boolean hasUserRestriction(String restriction) {
        return false;
    }

    public long getSerialNumberForUser(int userId) {
        return 0L;
    }
}
