// SPDX-License-Identifier: GPL-3.0-or-later
package android.webkit;

import java.util.HashMap;
import java.util.Map;

/**
 * Minimal CookieManager: in-memory cookie jar keyed by URL host.
 * Real webkit2gtk cookie jar lives in the helper process; this jar
 * mirrors the API so apps that only set/get cookies locally still work
 * headless.
 */
public class CookieManager {
    private static CookieManager instance;
    private final Map<String, String> store = new HashMap<>();
    private boolean acceptCookie = true;

    private CookieManager() {}

    public static synchronized CookieManager getInstance() {
        if (instance == null) instance = new CookieManager();
        return instance;
    }

    public void setAcceptCookie(boolean accept) { this.acceptCookie = accept; }
    public boolean acceptCookie() { return acceptCookie; }

    public void setCookie(String url, String value) {
        if (!acceptCookie || url == null || value == null) return;
        synchronized (store) { store.put(url, value); }
    }

    public String getCookie(String url) {
        if (url == null) return null;
        synchronized (store) { return store.get(url); }
    }

    public void removeAllCookies(ValueCallback<Boolean> callback) {
        synchronized (store) { store.clear(); }
        if (callback != null) callback.onReceiveValue(true);
    }

    public void removeAllCookie() { synchronized (store) { store.clear(); } }
    public void removeSessionCookies(ValueCallback<Boolean> callback) { removeAllCookies(callback); }
    public void removeSessionCookie() { removeAllCookie(); }
    public boolean hasCookies() { synchronized (store) { return !store.isEmpty(); } }
    public void flush() {}
}
