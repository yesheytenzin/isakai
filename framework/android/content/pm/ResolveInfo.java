// SPDX-License-Identifier: GPL-3.0-or-later
package android.content.pm;

/** Intent-resolution result. */
public class ResolveInfo {
    public ActivityInfo activityInfo;
    public ServiceInfo serviceInfo;
    public ProviderInfo providerInfo;
    public int specificIndex = -1;
    public int priority;
    public int preferredOrder;
    public int match;
    public boolean system;
    public boolean handleAllWebDataURI;

    public CharSequence loadLabel(PackageManager pm) {
        if (activityInfo != null) return activityInfo.loadLabel(pm);
        if (serviceInfo != null) return serviceInfo.loadLabel(pm);
        if (providerInfo != null) return providerInfo.loadLabel(pm);
        return "";
    }

    public android.graphics.drawable.Drawable loadIcon(PackageManager pm) {
        if (activityInfo != null) return activityInfo.loadIcon(pm);
        if (serviceInfo != null) return serviceInfo.loadIcon(pm);
        if (providerInfo != null) return providerInfo.loadIcon(pm);
        return new android.graphics.drawable.ColorDrawable(0);
    }
}
