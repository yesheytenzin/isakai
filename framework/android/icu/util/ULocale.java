// SPDX-License-Identifier: GPL-3.0-or-later
package android.icu.util;

/** Unicode locale stub: wraps a language tag string. */
public final class ULocale {
    public static final ULocale ROOT = new ULocale("root");
    public static final ULocale ENGLISH = new ULocale("en");

    private final String tag;

    public ULocale(String locale) {
        this.tag = locale == null ? "" : locale;
    }

    public static ULocale getDefault() {
        return new ULocale("en-US");
    }

    public static ULocale forLanguageTag(String tag) {
        return new ULocale(tag);
    }

    public String toLanguageTag() {
        return tag;
    }

    public String getLanguage() {
        int i = tag.indexOf('-');
        return i < 0 ? tag : tag.substring(0, i);
    }

    public String getDisplayName() {
        return tag;
    }

    @Override
    public String toString() {
        return tag;
    }
}
