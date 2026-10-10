// SPDX-License-Identifier: GPL-3.0-or-later
package android.content;

/**
 * Minimal ClipDescription: a label plus the offered MIME types.
 * Text-clip MIME only for now (URIs deferred with ClipData).
 */
public class ClipDescription {
    public static final String MIMETYPE_TEXT_PLAIN = "text/plain";
    public static final String MIMETYPE_TEXT_HTML = "text/html";
    public static final String MIMETYPE_TEXT_URILIST = "text/uri-list";

    private final CharSequence label;
    private final String[] mimeTypes;

    public ClipDescription(CharSequence label, String[] mimeTypes) {
        this.label = label;
        this.mimeTypes = mimeTypes != null ? mimeTypes.clone() : new String[0];
    }

    public CharSequence getLabel() {
        return label;
    }

    public int getMimeTypeCount() {
        return mimeTypes.length;
    }

    public String getMimeType(int index) {
        return mimeTypes[index];
    }

    /**
     * True when {@code mimeType} is offered. A bare type also matches
     * parameterized offers (e.g. {@code text/plain} matches
     * {@code text/plain;charset=utf-8}).
     */
    public boolean hasMimeType(String mimeType) {
        if (mimeType == null) return false;
        for (String m : mimeTypes) {
            if (m == null) continue;
            if (mimeType.equals(m) || m.startsWith(mimeType + ";")) return true;
        }
        return false;
    }
}
