// SPDX-License-Identifier: GPL-3.0-or-later
package android.content;

import java.util.Map;
import java.util.Set;

/**
 * Minimal SharedPreferences: typed getters, contains, and edit/commit/apply.
 * Implemented by {@link SharedPreferencesImpl}. All values are immutable
 * snapshots; string sets are unmodifiable defensive copies.
 */
public interface SharedPreferences {
    Map<String, ?> getAll();

    String getString(String key, String defValue);

    Set<String> getStringSet(String key, Set<String> defValues);

    int getInt(String key, int defValue);

    long getLong(String key, long defValue);

    float getFloat(String key, float defValue);

    boolean getBoolean(String key, boolean defValue);

    boolean contains(String key);

    Editor edit();

    void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener);

    void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener);

    /** Listener hook for key changes; fired synchronously on commit/apply callers. */
    interface OnSharedPreferenceChangeListener {
        void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key);
    }

    /** Staged mutation batch; {@link #commit} is synchronous, {@link #apply} queued. */
    interface Editor {
        Editor putString(String key, String value);

        Editor putStringSet(String key, Set<String> values);

        Editor putInt(String key, int value);

        Editor putLong(String key, long value);

        Editor putFloat(String key, float value);

        Editor putBoolean(String key, boolean value);

        Editor remove(String key);

        Editor clear();

        boolean commit();

        void apply();
    }
}
