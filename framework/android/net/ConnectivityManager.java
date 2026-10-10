// SPDX-License-Identifier: GPL-3.0-or-later
package android.net;

/** Network state; offline shell. */
public class ConnectivityManager {
    public static final int TYPE_MOBILE = 0;
    public static final int TYPE_WIFI = 1;
    public static final int TYPE_MOBILE_MMS = 2;
    public static final int TYPE_MOBILE_SUPL = 3;
    public static final int TYPE_BLUETOOTH = 7;
    public static final int TYPE_ETHERNET = 9;
    public static final int TYPE_VPN = 17;
    public static final String CONNECTIVITY_ACTION = "android.net.conn.CONNECTIVITY_CHANGE";
    public static final String EXTRA_NETWORK_INFO = "networkInfo";
    public static final String EXTRA_NO_CONNECTIVITY = "noConnectivity";
    public Object getActiveNetworkInfo() { return null; }
    public Object getNetworkInfo(int networkType) { return null; }
    public Object getNetworkInfo(Object network) { return null; }
    public Object[] getAllNetworkInfo() { return new Object[0]; }
    public Object[] getAllNetworks() { return new Object[0]; }
    public Object getActiveNetwork() { return null; }
    public Object getNetworkCapabilities(Object network) { return null; }
    public Object getLinkProperties(Object network) { return null; }
    public boolean isActiveNetworkMetered() { return false; }
    public boolean getBackgroundDataSetting() { return false; }
    public void registerNetworkCallback(Object request, ConnectivityManager.NetworkCallback callback) { }
    public void unregisterNetworkCallback(ConnectivityManager.NetworkCallback callback) { }
    public abstract static class NetworkCallback {
        public void onAvailable(Object network) { }
        public void onLost(Object network) { }
        public void onCapabilitiesChanged(Object network, Object caps) { }
    }
}
