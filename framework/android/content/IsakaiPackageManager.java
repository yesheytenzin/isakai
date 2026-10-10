// SPDX-License-Identifier: GPL-3.0-or-later
package android.content;

import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;

/**
 * Single-package PackageManager: answers component/package queries for the
 * bound context's own package from defaults (no manifest activity table is
 * persisted yet, so any same-package component resolves with stock fields).
 * Cross-package queries miss, matching the install snapshot boundary.
 */
public class IsakaiPackageManager extends PackageManager {
    private final Context context;

    public IsakaiPackageManager(Context context) {
        this.context = context;
    }

    private String pkg() {
        return context == null ? "" : context.getPackageName();
    }

    private boolean isOwnPackage(String packageName) {
        return packageName != null && packageName.equals(pkg());
    }

    private boolean isOwnComponent(ComponentName component) {
        return component != null && isOwnPackage(component.getPackageName());
    }

    @Override
    public ActivityInfo getActivityInfo(ComponentName component, int flags)
            throws android.content.pm.PackageManager.NameNotFoundException {
        if (!isOwnComponent(component)) {
            throw new NameNotFoundException(
                    component == null ? "null" : component.flattenToString());
        }
        ActivityInfo ai = new ActivityInfo();
        ai.packageName = pkg();
        ai.name = component.getClassName();
        return ai;
    }

    @Override
    public ServiceInfo getServiceInfo(ComponentName component, int flags)
            throws android.content.pm.PackageManager.NameNotFoundException {
        if (!isOwnComponent(component)) {
            throw new NameNotFoundException(
                    component == null ? "null" : component.flattenToString());
        }
        ServiceInfo si = new ServiceInfo();
        si.packageName = pkg();
        si.name = component.getClassName();
        return si;
    }

    @Override
    public ProviderInfo getProviderInfo(ComponentName component, int flags)
            throws android.content.pm.PackageManager.NameNotFoundException {
        if (!isOwnComponent(component)) {
            throw new NameNotFoundException(
                    component == null ? "null" : component.flattenToString());
        }
        ProviderInfo pi = new ProviderInfo();
        pi.packageName = pkg();
        pi.name = component.getClassName();
        return pi;
    }

    @Override
    public PackageInfo getPackageInfo(String packageName, int flags)
            throws android.content.pm.PackageManager.NameNotFoundException {
        if (!isOwnPackage(packageName)) throw new NameNotFoundException(packageName);
        PackageInfo pi = new PackageInfo();
        pi.packageName = pkg();
        pi.applicationInfo = getApplicationInfo(packageName, flags);
        return pi;
    }

    @Override
    public ApplicationInfo getApplicationInfo(String packageName, int flags)
            throws android.content.pm.PackageManager.NameNotFoundException {
        if (!isOwnPackage(packageName)) throw new NameNotFoundException(packageName);
        ApplicationInfo ai = new ApplicationInfo();
        ai.packageName = pkg();
        return ai;
    }

    @Override
    public android.content.Context getContext() {
        return context;
    }
}
