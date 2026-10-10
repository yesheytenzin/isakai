// SPDX-License-Identifier: GPL-3.0-or-later
package android.net.nsd;

/** Network service discovery; no-op shell. */
public class NsdManager {
    public static final String ACTION_NSD_STATE_CHANGED = "android.net.nsd.STATE_CHANGED";
    public static final int FAILURE_INTERNAL_ERROR = 0;
    public static final int FAILURE_ALREADY_ACTIVE = 3;
    public static final int FAILURE_MAX_LIMIT = 4;
    public void discoverServices(String serviceType, int protocol, DiscoveryListener listener) { if (listener != null) listener.onDiscoveryStarted(serviceType); }
    public void stopServiceDiscovery(DiscoveryListener listener) { }
    public void resolveService(Object serviceInfo, ResolveListener listener) { if (listener != null) listener.onResolveFailed(serviceInfo, FAILURE_INTERNAL_ERROR); }
    public void registerService(Object serviceInfo, int protocol, RegistrationListener listener) { if (listener != null) listener.onRegistrationFailed(serviceInfo, FAILURE_INTERNAL_ERROR); }
    public void unregisterService(RegistrationListener listener) { }
    public interface DiscoveryListener {
        void onDiscoveryStarted(String serviceType);
        void onDiscoveryStopped(String serviceType);
        void onServiceFound(Object serviceInfo);
        void onServiceLost(Object serviceInfo);
        void onStartDiscoveryFailed(String serviceType, int errorCode);
        void onStopDiscoveryFailed(String serviceType, int errorCode);
    }
    public interface ResolveListener {
        void onResolveFailed(Object serviceInfo, int errorCode);
        void onServiceResolved(Object serviceInfo);
    }
    public interface RegistrationListener {
        void onRegistrationFailed(Object serviceInfo, int errorCode);
        void onServiceRegistered(Object serviceInfo);
        void onServiceUnregistered(Object serviceInfo);
        void onUnregistrationFailed(Object serviceInfo, int errorCode);
    }
}
