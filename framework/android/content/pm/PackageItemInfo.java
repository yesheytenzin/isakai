// SPDX-License-Identifier: GPL-3.0-or-later
package android.content.pm;

/** Named-item base for package records. */
public class PackageItemInfo {
    public String name;
    public String packageName;
    public int icon;
    public int logo;
    public CharSequence nonLocalizedLabel;
    public int labelRes;

    public CharSequence loadLabel(PackageManager pm) {
        if (labelRes != 0 && pm != null) {
            try {
                android.content.Context ctx = pm.getContext();
                if (ctx != null) return ctx.getResources().getText(labelRes);
            } catch (RuntimeException e) {
                // Fall through to the literal below (fail-closed).
            }
        }
        return nonLocalizedLabel == null ? "" : nonLocalizedLabel;
    }
}
