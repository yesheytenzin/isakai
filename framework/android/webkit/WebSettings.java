// SPDX-License-Identifier: GPL-3.0-or-later
package android.webkit;

import java.util.HashMap;
import java.util.Map;

/** Minimal WebSettings: all getters/setters stored, no engine coupling. */
public class WebSettings {
    private boolean javaScriptEnabled = false;
    private boolean domStorageEnabled = false;
    private boolean allowFileAccess = true;
    private boolean allowContentAccess = true;
    private boolean loadsImagesAutomatically = true;
    private boolean blockNetworkLoads = false;
    private boolean javaScriptCanOpenWindowsAutomatically = false;
    private boolean supportZoom = true;
    private boolean builtInZoomControls = false;
    private boolean displayZoomControls = true;
    private int cacheMode = 0;
    private String userAgentString = "Isakai/1.0";
    private int mixedContentMode = 0;
    private final Map<String, Object> extras = new HashMap<>();

    public boolean getJavaScriptEnabled() { return javaScriptEnabled; }
    public void setJavaScriptEnabled(boolean flag) { this.javaScriptEnabled = flag; }
    public boolean getDomStorageEnabled() { return domStorageEnabled; }
    public void setDomStorageEnabled(boolean flag) { this.domStorageEnabled = flag; }
    public boolean getAllowFileAccess() { return allowFileAccess; }
    public void setAllowFileAccess(boolean allow) { this.allowFileAccess = allow; }
    public boolean getAllowContentAccess() { return allowContentAccess; }
    public void setAllowContentAccess(boolean allow) { this.allowContentAccess = allow; }
    public boolean getLoadsImagesAutomatically() { return loadsImagesAutomatically; }
    public void setLoadsImagesAutomatically(boolean flag) { this.loadsImagesAutomatically = flag; }
    public boolean getBlockNetworkLoads() { return blockNetworkLoads; }
    public void setBlockNetworkLoads(boolean flag) { this.blockNetworkLoads = flag; }
    public boolean getJavaScriptCanOpenWindowsAutomatically() { return javaScriptCanOpenWindowsAutomatically; }
    public void setJavaScriptCanOpenWindowsAutomatically(boolean flag) { this.javaScriptCanOpenWindowsAutomatically = flag; }
    public boolean supportZoom() { return supportZoom; }
    public void setSupportZoom(boolean support) { this.supportZoom = support; }
    public boolean getBuiltInZoomControls() { return builtInZoomControls; }
    public void setBuiltInZoomControls(boolean enabled) { this.builtInZoomControls = enabled; }
    public boolean getDisplayZoomControls() { return displayZoomControls; }
    public void setDisplayZoomControls(boolean enabled) { this.displayZoomControls = enabled; }
    public int getCacheMode() { return cacheMode; }
    public void setCacheMode(int mode) { this.cacheMode = mode; }
    public String getUserAgentString() { return userAgentString; }
    public void setUserAgentString(String ua) { this.userAgentString = ua; }
    public int getMixedContentMode() { return mixedContentMode; }
    public void setMixedContentMode(int mode) { this.mixedContentMode = mode; }
}
