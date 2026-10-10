// SPDX-License-Identifier: GPL-3.0-or-later
package android.hardware.biometrics;

import android.content.Context;
import android.os.CancellationSignal;
import java.util.concurrent.Executor;

/** Biometric auth shell: immediately fails closed. */
public class BiometricPrompt {
    public static final int BIOMETRIC_ERROR_CANCELED = 3;
    public static class AuthenticationResult {
        private final int authType;
        public AuthenticationResult(int authType) { this.authType = authType; }
        public int getAuthenticationType() { return authType; }
    }
    public abstract static class AuthenticationCallback {
        public void onAuthenticationError(int errorCode, CharSequence errString) { }
        public void onAuthenticationSucceeded(AuthenticationResult result) { }
        public void onAuthenticationFailed() { }
    }
    public static class Builder {
        private final Context context;
        private CharSequence title;
        private CharSequence subtitle;
        private CharSequence description;
        private CharSequence negative;
        public Builder(Context context) { this.context = context; }
        public Builder setTitle(CharSequence title) { this.title = title; return this; }
        public Builder setSubtitle(CharSequence subtitle) { this.subtitle = subtitle; return this; }
        public Builder setDescription(CharSequence description) { this.description = description; return this; }
        public Builder setNegativeButton(CharSequence text, Executor exec, android.content.DialogInterface.OnClickListener listener) { negative = text; return this; }
        public BiometricPrompt build() { return new BiometricPrompt(); }
    }
    public void authenticate(CancellationSignal cancel, Executor executor, AuthenticationCallback callback) {
        if (callback != null) callback.onAuthenticationError(BIOMETRIC_ERROR_CANCELED, "unsupported");
    }
    public void cancelAuthentication() { }
}
