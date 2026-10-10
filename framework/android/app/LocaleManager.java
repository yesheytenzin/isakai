// SPDX-License-Identifier: GPL-3.0-or-later
package android.app;

import android.os.LocaleList;

/** System locale state; empty shell. */
public class LocaleManager {
    public LocaleList getSystemLocales() { return LocaleList.getEmptyLocaleList(); }
    public java.util.Locale getApplicationLocales(String packageName) { return null; }
}
