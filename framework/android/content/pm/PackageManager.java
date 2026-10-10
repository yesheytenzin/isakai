// SPDX-License-Identifier: GPL-3.0-or-later
package android.content.pm;

import android.content.ComponentName;
import android.content.Intent;
import java.util.Collections;
import java.util.List;

/**
 * PackageManager: answers from the install snapshot (single package);
 * cross-package queries miss or return empty lists.
 */
public abstract class PackageManager {
    public static final int GET_ACTIVITIES = 1;
    public static final int GET_SERVICES = 8;
    public static final int GET_PROVIDERS = 0x1000;
    public static final int GET_META_DATA = 0x80;
    public static final int MATCH_DEFAULT_ONLY = 0x10000;

    public static final int COMPONENT_ENABLED_STATE_DEFAULT = 0;
    public static final int COMPONENT_ENABLED_STATE_ENABLED = 1;
    public static final int COMPONENT_ENABLED_STATE_DISABLED = 2;

    public static final int DONT_KILL_APP = 1;

    public PackageInfo getPackageInfo(String packageName, int flags) throws NameNotFoundException {
        throw new NameNotFoundException(packageName);
    }

    public PackageInfo getPackageInfo(String packageName, PackageInfoFlags flags)
            throws NameNotFoundException {
        return getPackageInfo(packageName, 0);
    }

    public ApplicationInfo getApplicationInfo(String packageName, int flags) throws NameNotFoundException {
        throw new NameNotFoundException(packageName);
    }

    public ActivityInfo getActivityInfo(ComponentName component, int flags) throws NameNotFoundException {
        throw new NameNotFoundException(component == null ? "null" : component.flattenToString());
    }

    public ServiceInfo getServiceInfo(ComponentName component, int flags) throws NameNotFoundException {
        throw new NameNotFoundException(component == null ? "null" : component.flattenToString());
    }

    public ProviderInfo getProviderInfo(ComponentName component, int flags) throws NameNotFoundException {
        throw new NameNotFoundException(component == null ? "null" : component.flattenToString());
    }

    public ProviderInfo resolveContentProvider(String name, int flags) {
        return null;
    }

    public List<ResolveInfo> queryIntentActivities(Intent intent, int flags) {
        return Collections.emptyList();
    }

    public List<ResolveInfo> queryIntentActivityOptions(ComponentName caller, Intent[] specifics,
            Intent intent, int flags) {
        return Collections.emptyList();
    }

    public List<ResolveInfo> queryIntentContentProviders(Intent intent, int flags) {
        return Collections.emptyList();
    }

    public List<ResolveInfo> queryIntentServices(Intent intent, int flags) {
        return Collections.emptyList();
    }

    public String[] getPackagesForUid(int uid) {
        return new String[0];
    }

    public boolean hasSystemFeature(String name) {
        return false;
    }

    public int getComponentEnabledSetting(ComponentName component) {
        return COMPONENT_ENABLED_STATE_DEFAULT;
    }

    /** Bound context for resource-backed lookups; null unless overridden. */
    public android.content.Context getContext() {
        return null;
    }

    public static class NameNotFoundException extends Exception {
        public NameNotFoundException(String message) {
            super(message);
        }
    }

    public static final class PackageInfoFlags {
        private final long value;

        private PackageInfoFlags(long value) {
            this.value = value;
        }

        public static PackageInfoFlags of(long value) {
            return new PackageInfoFlags(value);
        }

        public long getValue() {
            return value;
        }
    }
}
