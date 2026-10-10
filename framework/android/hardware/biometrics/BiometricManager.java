// SPDX-License-Identifier: GPL-3.0-or-later
package android.hardware.biometrics;

/** Biometric enrollment shell: nothing enrolled. */
public class BiometricManager {
    public static final int BIOMETRIC_SUCCESS = 0;
    public static final int BIOMETRIC_ERROR_NO_HARDWARE = 12;
    public static final int BIOMETRIC_ERROR_HW_UNAVAILABLE = 1;
    public static final int BIOMETRIC_ERROR_NONE_ENROLLED = 11;
    public static final int BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED = 15;
    public static final int BIOMETRIC_ERROR_UNSUPPORTED = 1000;
    public static final int BIOMETRIC_ERROR_STATUS_UNKNOWN = -1;
    public static final int AUTHENTICATORS_EMPTY = 0;
    public static final int AUTHENTICATOR_BIOMETRIC_STRONG = 0x000F;
    public static final int AUTHENTICATOR_BIOMETRIC_WEAK = 0x00FF;
    public static final int AUTHENTICATOR_DEVICE_CREDENTIAL = 1 << 15;
    public int canAuthenticate() { return BIOMETRIC_ERROR_NO_HARDWARE; }
    public int canAuthenticate(int authenticators) { return BIOMETRIC_ERROR_NO_HARDWARE; }
}
