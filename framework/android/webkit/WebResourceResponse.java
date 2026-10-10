// SPDX-License-Identifier: GPL-3.0-or-later
package android.webkit;

import java.io.InputStream;
import java.util.Map;

/** Minimal WebResourceResponse: MIME + encoding + data stream + headers. */
public class WebResourceResponse {
    private final String mimeType;
    private final String encoding;
    private final InputStream data;
    private final int statusCode;
    private final String reasonPhrase;
    private final Map<String, String> headers;

    public WebResourceResponse(String mimeType, String encoding, InputStream data) {
        this(mimeType, encoding, 200, "OK", null, data);
    }

    public WebResourceResponse(String mimeType, String encoding, int statusCode, String reasonPhrase, Map<String, String> headers, InputStream data) {
        this.mimeType = mimeType;
        this.encoding = encoding;
        this.statusCode = statusCode;
        this.reasonPhrase = reasonPhrase;
        this.headers = headers;
        this.data = data;
    }

    public String getMimeType() { return mimeType; }
    public String getEncoding() { return encoding; }
    public InputStream getData() { return data; }
    public int getStatusCode() { return statusCode; }
    public String getReasonPhrase() { return reasonPhrase; }
    public Map<String, String> getResponseHeaders() { return headers; }
}
