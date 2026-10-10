// SPDX-License-Identifier: GPL-3.0-or-later
package android.content;

import android.os.Bundle;

/** User restrictions; empty shell. */
public class RestrictionsManager {
    public static final String ACTION_REQUEST_PERMISSION = "android.content.action.REQUEST_PERMISSION";
    public static final String EXTRA_REQUEST_TYPE = "android.content.extra.REQUEST_TYPE";
    public Bundle getApplicationRestrictions() { return new Bundle(); }
    public boolean hasRestrictionsProvider() { return false; }
    public void requestPermission(String requestType, String requestId, Bundle request) { }
    public void notifyPermissionResponse(String packageName, Bundle response) { }
}
