// SPDX-License-Identifier: GPL-3.0-or-later
package android.webkit;

/**
 * WebViewClient: apps override these 6 methods to intercept navigation,
 * errors, and page events. Returning true from shouldOverrideUrlLoading
 * means the host handled the URL; false lets the WebView load it.
 */
public class WebViewClient {
    public boolean shouldOverrideUrlLoading(WebView view, String url) { return false; }
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        return shouldOverrideUrlLoading(view, request != null ? request.getUrl().toString() : null);
    }
    public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {}
    public void onPageFinished(WebView view, String url) {}
    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {}
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        if (request != null && error != null) onReceivedError(view, error.getErrorCode(), error.getDescription().toString(), request.getUrl().toString());
    }
    public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {}
}
