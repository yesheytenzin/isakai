// SPDX-License-Identifier: GPL-3.0-or-later
package android.media;

/** Media routing; empty shell. */
public class MediaRouter {
    public static final int ROUTE_TYPE_LIVE_AUDIO = 1;
    public static final int ROUTE_TYPE_LIVE_VIDEO = 2;
    public static final int ROUTE_TYPE_REMOTE_DISPLAY = 4;
    public static final int ROUTE_TYPE_USER = 8388608;
    public RouteInfo getDefaultRoute() { return null; }
    public RouteInfo getSelectedRoute(int type) { return null; }
    public int getRouteCount() { return 0; }
    public RouteInfo getRouteAt(int index) { return null; }
    public void addCallback(int types, Callback cb) { }
    public void addCallback(int types, Callback cb, int flags) { }
    public void removeCallback(Callback cb) { }
    public void selectRoute(int type, RouteInfo route) { }
    public static class RouteInfo {
        public CharSequence getName() { return ""; }
        public int getSupportedTypes() { return 0; }
        public boolean isDefault() { return true; }
    }
    public abstract static class Callback {
        public abstract void onRouteSelected(MediaRouter router, int type, RouteInfo info);
        public void onRouteUnselected(MediaRouter router, int type, RouteInfo info) { }
        public void onRouteAdded(MediaRouter router, RouteInfo info) { }
        public void onRouteRemoved(MediaRouter router, RouteInfo info) { }
    }
}
