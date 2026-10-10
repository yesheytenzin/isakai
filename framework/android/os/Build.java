// SPDX-License-Identifier: GPL-3.0-or-later
package android.os;

/** Build identity: host Linux masquerading at API 33. */
public final class Build {
    public static final String MANUFACTURER = "isakai";
    public static final String MODEL = "Desktop";
    public static final String FINGERPRINT = "isakai/desktop/1.0";
    public static final String BRAND = "isakai";
    public static final String DEVICE = "desktop";
    public static final String PRODUCT = "desktop";
    public static final String BOARD = "desktop";
    public static final String HARDWARE = "desktop";
    public static final String ID = "1.0";
    public static final String DISPLAY = "isakai 1.0";
    public static final String TYPE = "user";
    public static final String TAGS = "release-keys";
    public static final String USER = "isakai";
    public static final long TIME = 0L;

    private Build() {}

    public static final class VERSION {
        public static final int SDK_INT = 33;
        public static final int PREVIEW_SDK_INT = 0;
        public static final String RELEASE = "13";
        public static final String CODENAME = "REL";
        public static final String INCREMENTAL = "1";
        public static final String SECURITY_PATCH = "2026-10-01";

        private VERSION() {}
    }

    public static String getRadioVersion() {
        return "";
    }
}
