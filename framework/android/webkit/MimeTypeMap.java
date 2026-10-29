package android.webkit;

import java.net.URLConnection;
import java.util.Locale;
import java.util.Map;

/** Maps common file extensions to MIME types. */
public final class MimeTypeMap {
    private static final MimeTypeMap INSTANCE = new MimeTypeMap();
    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("txt", "text/plain"), Map.entry("md", "text/markdown"),
            Map.entry("html", "text/html"), Map.entry("htm", "text/html"),
            Map.entry("css", "text/css"), Map.entry("csv", "text/csv"),
            Map.entry("json", "application/json"), Map.entry("xml", "application/xml"),
            Map.entry("pdf", "application/pdf"), Map.entry("zip", "application/zip"),
            Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"), Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"), Map.entry("svg", "image/svg+xml"),
            Map.entry("mp3", "audio/mpeg"), Map.entry("mp4", "video/mp4"));

    private MimeTypeMap() {}

    public static MimeTypeMap getSingleton() { return INSTANCE; }

    public static String getFileExtensionFromUrl(String url) {
        if (url == null || url.isEmpty()) return "";
        int fragment = url.indexOf('#');
        if (fragment >= 0) url = url.substring(0, fragment);
        int query = url.indexOf('?');
        if (query >= 0) url = url.substring(0, query);
        String filename = url.substring(url.lastIndexOf('/') + 1);
        if (!filename.matches("[a-zA-Z_0-9.()%\\-]+")) return "";
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1);
    }

    public boolean hasMimeType(String mimeType) { return getExtensionFromMimeType(mimeType) != null; }
    public boolean hasExtension(String extension) { return getMimeTypeFromExtension(extension) != null; }

    public String getMimeTypeFromExtension(String extension) {
        if (extension == null || extension.isEmpty()) return null;
        String normalized = extension.toLowerCase(Locale.ROOT);
        String type = TYPES.get(normalized);
        return type != null ? type : URLConnection.getFileNameMap().getContentTypeFor("file." + normalized);
    }

    public String getExtensionFromMimeType(String mimeType) {
        if (mimeType == null || mimeType.isEmpty()) return null;
        String normalized = mimeType.toLowerCase(Locale.ROOT);
        if (normalized.equals("text/html")) return "html";
        if (normalized.equals("image/jpeg")) return "jpg";
        for (Map.Entry<String, String> entry : TYPES.entrySet()) {
            if (entry.getValue().equals(normalized)) return entry.getKey();
        }
        return null;
    }
}
