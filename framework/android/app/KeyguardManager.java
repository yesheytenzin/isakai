// SPDX-License-Identifier: GPL-3.0-or-later
package android.app;

/** Keyguard/lock state; unlocked shell. */
public class KeyguardManager {
    public boolean isKeyguardLocked() { return false; }
    public boolean isKeyguardSecure() { return false; }
    public boolean isDeviceLocked() { return false; }
    public boolean isDeviceSecure() { return false; }
    public boolean inKeyguardRestrictedInputMode() { return false; }
    public void requestDismissKeyguard(android.app.Activity activity, KeyguardDismissCallback callback) { if (callback != null) callback.onDismissSucceeded(); }
    public static class KeyguardDismissCallback {
        public void onDismissError() { }
        public void onDismissSucceeded() { }
        public void onDismissCancelled() { }
    }
    public static abstract class KeyguardLock {
        public abstract void disableKeyguard();
        public abstract void reenableKeyguard();
    }
    public KeyguardLock newKeyguardLock(String tag) { return null; }
}
