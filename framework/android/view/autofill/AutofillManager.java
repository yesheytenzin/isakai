// SPDX-License-Identifier: GPL-3.0-or-later
package android.view.autofill;

/** Autofill stub: autofill unsupported. */
public final class AutofillManager {
    public AutofillManager() {
    }

    public abstract static class AutofillCallback {
        public void onAutofillEvent(android.view.View view, int event) {}
    }

    public boolean isEnabled() {
        return false;
    }

    public boolean isAutofillSupported() {
        return false;
    }

    public void registerCallback(AutofillCallback callback) {}

    public void unregisterCallback(AutofillCallback callback) {}

    public void cancel() {
    }

    public void commit() {
    }
}
