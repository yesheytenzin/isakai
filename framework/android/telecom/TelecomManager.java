// SPDX-License-Identifier: GPL-3.0-or-later
package android.telecom;

/** Telecom stub: no active calls. */
public class TelecomManager {
    public TelecomManager() {
    }

    public boolean isInCall() {
        return false;
    }

    public boolean isRinging() {
        return false;
    }

    public void cancelMissedCallsNotification() {
    }
}
