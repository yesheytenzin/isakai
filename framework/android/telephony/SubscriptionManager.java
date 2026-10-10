// SPDX-License-Identifier: GPL-3.0-or-later
package android.telephony;

/** Subscription query stub: no active subscriptions. */
public class SubscriptionManager {
    public static final int INVALID_SUBSCRIPTION_ID = -1;
    public static final int DEFAULT_SUBSCRIPTION_ID = Integer.MAX_VALUE;

    public SubscriptionManager() {
    }

    public static int getDefaultSubscriptionId() {
        return INVALID_SUBSCRIPTION_ID;
    }

    public static int getDefaultDataSubscriptionId() {
        return INVALID_SUBSCRIPTION_ID;
    }

    public java.util.List getActiveSubscriptionInfoList() {
        return new java.util.ArrayList();
    }
}
