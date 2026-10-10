// SPDX-License-Identifier: GPL-3.0-or-later
package android.content.pm;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import java.util.Collections;
import java.util.List;

/** Launcher queries; empty shell. */
public class LauncherApps {
    public static class ShortcutQuery {
        public static final int FLAG_MATCH_DYNAMIC = 1;
        public static final int FLAG_MATCH_PINNED = 2;
        public static final int FLAG_MATCH_MANIFEST = 8;
        public void setQueryFlags(int flags) { }
        public void setPackage(String packageName) { }
    }
    public List getActivityList(String packageName, Object user) { return Collections.emptyList(); }
    public Object resolveActivity(android.content.Intent intent, Object user) { return null; }
    public void startMainActivity(android.content.ComponentName component, Object user, android.graphics.Rect sourceBounds, Bundle opts) { }
    public boolean isPackageEnabled(String packageName, Object user) { return false; }
    public boolean isActivityEnabled(android.content.ComponentName component, Object user) { return false; }
    public Drawable getShortcutIconDrawable(Object shortcut, int densityDpi) { return null; }
    public List getShortcuts(ShortcutQuery query, Object user) { return Collections.emptyList(); }
    public boolean hasShortcutHostPermission() { return false; }
    public void registerCallback(Callback callback) { }
    public void unregisterCallback(Callback callback) { }
    public abstract static class Callback {
        public abstract void onPackageRemoved(String packageName, Object user);
        public abstract void onPackageAdded(String packageName, Object user);
        public abstract void onPackageChanged(String packageName, Object user);
        public abstract void onPackagesAvailable(String[] packageNames, Object user, boolean replacing);
        public abstract void onPackagesUnavailable(String[] packageNames, Object user, boolean replacing);
    }
}
