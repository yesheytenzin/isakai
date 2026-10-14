// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

/** Software color transform. Unknown native filter types are explicitly unsupported. */
public class ColorFilter {
    public int filterColor(int color) {
        org.isakai.runtime.Capabilities.unavailable("ColorFilter.softwareTransform");
        return color;
    }
}
