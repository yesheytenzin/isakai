// SPDX-License-Identifier: GPL-3.0-or-later
package android.os;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Ordered immutable locale list (headless: default locale only unless set). */
public final class LocaleList {
    private final List<Locale> locales;

    public LocaleList(Locale... locales) {
        List<Locale> items = new ArrayList<>();
        if (locales != null) {
            for (Locale l : locales) {
                if (l != null && !items.contains(l)) items.add((Locale) l.clone());
            }
        }
        this.locales = Collections.unmodifiableList(items);
    }

    public Locale get(int index) {
        return locales.get(index);
    }

    public boolean isEmpty() {
        return locales.isEmpty();
    }

    public int size() {
        return locales.size();
    }

    public String toLanguageTags() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < locales.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(locales.get(i).toLanguageTag());
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return locales.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LocaleList)) return false;
        return locales.equals(((LocaleList) o).locales);
    }

    @Override
    public int hashCode() {
        return locales.hashCode();
    }

    public static LocaleList getEmptyLocaleList() {
        return new LocaleList();
    }

    public static LocaleList forLanguageTags(String tags) {
        if (tags == null || tags.isEmpty()) return getEmptyLocaleList();
        String[] parts = tags.split(",");
        Locale[] items = new Locale[parts.length];
        for (int i = 0; i < parts.length; i++) {
            items[i] = Locale.forLanguageTag(parts[i].trim());
        }
        return new LocaleList(items);
    }

    public static LocaleList getDefault() {
        return new LocaleList(Locale.getDefault());
    }

    public static void setDefault(LocaleList locales) {
        if (locales != null && !locales.isEmpty()) {
            Locale.setDefault(locales.get(0));
        }
    }

    public static LocaleList getAdjustedDefault() {
        return getDefault();
    }
}
