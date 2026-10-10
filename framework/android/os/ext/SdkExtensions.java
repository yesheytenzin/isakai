// SPDX-License-Identifier: GPL-3.0-or-later
package android.os.ext;

/** SDK extension versions (headless: no extensions). */
public final class SdkExtensions {
    private SdkExtensions() {}

    public static int getExtensionVersion(int sdkVersion) {
        return 0;
    }
}
