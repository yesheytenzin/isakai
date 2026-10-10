// SPDX-License-Identifier: GPL-3.0-or-later
package android.content.pm;

import android.os.Bundle;

/** Shared component fields: name, package, metadata. */
public class ComponentInfo {
    public String name;
    public String packageName;
    public Bundle metaData;
    public int icon;
    public int logo;
    public CharSequence nonLocalizedLabel;
    public int labelRes;
    public boolean exported;
    public boolean enabled = true;

    public CharSequence loadLabel(android.content.pm.PackageManager pm) {
        return nonLocalizedLabel == null ? "" : nonLocalizedLabel;
    }

    public android.graphics.drawable.Drawable loadIcon(android.content.pm.PackageManager pm) {
        return new android.graphics.drawable.ColorDrawable(0);
    }
}
