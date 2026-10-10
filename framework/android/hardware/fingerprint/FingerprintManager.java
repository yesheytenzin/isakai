// SPDX-License-Identifier: GPL-3.0-or-later
package android.hardware.fingerprint;

import android.os.CancellationSignal;

/** Fingerprint shell: no hardware. */
public class FingerprintManager {
    public static final int FINGERPRINT_ERROR_CANCELED = 5;
    public abstract static class AuthenticationCallback {
        public void onAuthenticationError(int errMsgId, CharSequence errString) { }
        public void onAuthenticationSucceeded(AuthenticationResult result) { }
        public void onAuthenticationFailed() { }
    }
    public static class AuthenticationResult { public AuthenticationResult() { } }
    public static class CryptoObject { public CryptoObject(javax.crypto.Cipher cipher) { } }
    public boolean hasEnrolledFingerprints() { return false; }
    public boolean isHardwareDetected() { return false; }
    public void authenticate(CryptoObject crypto, CancellationSignal cancel, int flags, AuthenticationCallback callback, android.os.Handler handler) {
        if (callback != null) callback.onAuthenticationError(FINGERPRINT_ERROR_CANCELED, "unsupported");
    }
}
