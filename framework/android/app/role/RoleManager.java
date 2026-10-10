// SPDX-License-Identifier: GPL-3.0-or-later
package android.app.role;

/** Role availability shell: nothing held. */
public class RoleManager {
    public static final String ROLE_BROWSER = "android.app.role.BROWSER";
    public static final String ROLE_DIALER = "android.app.role.DIALER";
    public static final String ROLE_SMS = "android.app.role.SMS";
    public boolean isRoleAvailable(String roleName) { return false; }
    public boolean isRoleHeld(String roleName) { return false; }
}
