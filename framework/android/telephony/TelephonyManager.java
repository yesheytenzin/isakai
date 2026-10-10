// SPDX-License-Identifier: GPL-3.0-or-later
package android.telephony;

/** Telephony query stub: no radio present. */
public class TelephonyManager {
    public static final int PHONE_TYPE_NONE = 0;
    public static final int PHONE_TYPE_GSM = 1;
    public static final int PHONE_TYPE_CDMA = 2;
    public static final int NETWORK_TYPE_UNKNOWN = 0;
    public static final int SIM_STATE_UNKNOWN = 0;
    public static final int SIM_STATE_ABSENT = 1;
    public static final int CALL_STATE_IDLE = 0;
    public static final int CALL_STATE_RINGING = 1;
    public static final int CALL_STATE_OFFHOOK = 2;

    public TelephonyManager() {
    }

    public int getPhoneType() {
        return PHONE_TYPE_NONE;
    }

    public int getCallState() {
        return CALL_STATE_IDLE;
    }

    public int getSimState() {
        return SIM_STATE_ABSENT;
    }

    public String getNetworkOperatorName() {
        return "";
    }

    public String getDeviceId() {
        return null;
    }
}
