// SPDX-License-Identifier: GPL-3.0-or-later
package android.net.wifi;

import java.util.Collections;
import java.util.List;

/** Wi-Fi state; disabled shell. */
public class WifiManager {
    public static final String WIFI_SERVICE = "wifi";
    public static final int WIFI_STATE_DISABLED = 1;
    public static final int WIFI_STATE_DISABLING = 0;
    public static final int WIFI_STATE_ENABLED = 3;
    public static final int WIFI_STATE_ENABLING = 2;
    public static final int WIFI_STATE_UNKNOWN = 4;
    public static final String WIFI_STATE_CHANGED_ACTION = "android.net.wifi.WIFI_STATE_CHANGED";
    public static final String SCAN_RESULTS_AVAILABLE_ACTION = "android.net.wifi.SCAN_RESULTS";
    public static final String NETWORK_STATE_CHANGED_ACTION = "android.net.wifi.STATE_CHANGE";
    public static final String EXTRA_WIFI_STATE = "wifi_state";
    public boolean isWifiEnabled() { return false; }
    public int getWifiState() { return WIFI_STATE_DISABLED; }
    public boolean setWifiEnabled(boolean enabled) { return false; }
    public List getScanResults() { return Collections.emptyList(); }
    public boolean startScan() { return false; }
    public Object getConnectionInfo() { return null; }
    public List getConfiguredNetworks() { return Collections.emptyList(); }
    public int addNetwork(Object config) { return -1; }
    public boolean removeNetwork(int netId) { return false; }
    public boolean enableNetwork(int netId, boolean disableOthers) { return false; }
    public boolean disconnect() { return false; }
    public boolean reconnect() { return false; }
    public WifiLock createWifiLock(String tag) { return null; }
    public static class WifiLock {
        public void acquire() { }
        public void release() { }
        public boolean isHeld() { return false; }
        public void setReferenceCounted(boolean refCounted) { }
    }
}
