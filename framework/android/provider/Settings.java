// SPDX-License-Identifier: GPL-3.0-or-later
package android.provider;

/** System settings: fixed defaults, no backing store. */
public final class Settings {
    private Settings() {}

    public static final class Global {
        private Global() {}
        public static final String ANIMATOR_DURATION_SCALE = "animator_duration_scale";
        public static final String TRANSITION_ANIMATION_SCALE = "transition_animation_scale";
        public static final String WINDOW_ANIMATION_SCALE = "window_animation_scale";
        public static final String AIRPLANE_MODE_ON = "airplane_mode_on";
        public static final String ADB_ENABLED = "adb_enabled";

        public static android.net.Uri getUriFor(String name) {
            return android.net.Uri.parse("content://settings/global/" + name);
        }

        public static String getString(android.content.ContentResolver cr, String name) {
            return null;
        }

        public static int getInt(android.content.ContentResolver cr, String name, int def) {
            return def;
        }

        public static int getInt(android.content.ContentResolver cr, String name) throws SettingNotFoundException {
            throw new SettingNotFoundException(name);
        }

        public static long getLong(android.content.ContentResolver cr, String name, long def) {
            return def;
        }

        public static long getLong(android.content.ContentResolver cr, String name) throws SettingNotFoundException {
            throw new SettingNotFoundException(name);
        }

        public static float getFloat(android.content.ContentResolver cr, String name, float def) {
            return def;
        }

        public static float getFloat(android.content.ContentResolver cr, String name) throws SettingNotFoundException {
            throw new SettingNotFoundException(name);
        }

        public static boolean putString(android.content.ContentResolver cr, String name, String value) {
            return false;
        }

        public static boolean putInt(android.content.ContentResolver cr, String name, int value) {
            return false;
        }

        public static boolean putLong(android.content.ContentResolver cr, String name, long value) {
            return false;
        }

        public static boolean putFloat(android.content.ContentResolver cr, String name, float value) {
            return false;
        }
    }

    public static final class System {
        private System() {}

        public static android.net.Uri getUriFor(String name) {
            return android.net.Uri.parse("content://settings/system/" + name);
        }

        public static String getString(android.content.ContentResolver cr, String name) {
            return null;
        }

        public static int getInt(android.content.ContentResolver cr, String name, int def) {
            return def;
        }

        public static float getFloat(android.content.ContentResolver cr, String name, float def) {
            return def;
        }

        public static boolean putString(android.content.ContentResolver cr, String name, String value) {
            return false;
        }
    }

    public static final class Secure {
        private Secure() {}

        public static android.net.Uri getUriFor(String name) {
            return android.net.Uri.parse("content://settings/secure/" + name);
        }

        public static String getString(android.content.ContentResolver cr, String name) {
            return null;
        }

        public static int getInt(android.content.ContentResolver cr, String name, int def) {
            return def;
        }

        public static boolean putString(android.content.ContentResolver cr, String name, String value) {
            return false;
        }
    }

    public static class SettingNotFoundException extends android.util.AndroidException {
        public SettingNotFoundException(String name) {
            super(name);
        }
    }
}
