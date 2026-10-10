// SPDX-License-Identifier: GPL-3.0-or-later
package android.webkit;

import android.net.Uri;
import java.util.Map;

/** Minimal WebResourceRequest: URL + method + headers + auth flag. */
public class WebResourceRequest {
    private final Uri url;
    private final String method;
    private final Map<String, String> headers;
    private final boolean forMainFrame;
    private final boolean hasGesture;

    public WebResourceRequest(Uri url, String method, Map<String, String> headers, boolean forMainFrame, boolean hasGesture) {
        this.url = url;
        this.method = method;
        this.headers = headers;
        this.forMainFrame = forMainFrame;
        this.hasGesture = hasGesture;
    }

    public Uri getUrl() { return url; }
    public String getMethod() { return method; }
    public Map<String, String> getRequestHeaders() { return headers; }
    public boolean isForMainFrame() { return forMainFrame; }
    public boolean hasGesture() { return hasGesture; }
    public boolean isRedirect() { return false; }
}
