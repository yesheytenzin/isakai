// SPDX-License-Identifier: GPL-3.0-or-later
package android.content.pm;

/** Provider manifest record. */
public class ProviderInfo extends ComponentInfo {
    public String authority;
    public String readPermission;
    public String writePermission;
    public boolean grantUriPermissions;
    public boolean multiprocess;
}
