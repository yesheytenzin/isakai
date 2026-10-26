// SPDX-License-Identifier: GPL-3.0-or-later
package android.net;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hierarchical URI: scheme/authority/path/query splits over the raw string,
 * plus a Builder. Encoding is preserved verbatim (no escaping).
 */
public final class Uri {
    private final String value;

    private Uri(String value) {
        this.value = value;
    }

    public static Uri parse(String s) {
        if (s == null) throw new NullPointerException("uri");
        return new Uri(s);
    }

    public static Uri fromParts(String scheme, String ssp, String fragment) {
        StringBuilder sb = new StringBuilder();
        if (scheme != null) sb.append(scheme).append(':');
        if (ssp != null) sb.append(ssp);
        if (fragment != null) sb.append('#').append(fragment);
        return new Uri(sb.toString());
    }

    public static Uri withAppendedId(Uri contentUri, long id) {
        return parse(contentUri == null ? String.valueOf(id)
                : contentUri.value + "/" + id);
    }

    public Builder buildUpon() {
        return new Builder().parse(value);
    }

    public String getScheme() {
        int i = value.indexOf(':');
        if (i <= 0) return null;
        return value.substring(0, i).toLowerCase(java.util.Locale.ROOT);
    }

    public String getAuthority() {
        int c = value.indexOf(':');
        if (c < 0 || !value.startsWith("//", c + 1)) return null;
        int start = c + 3;
        int end = value.length();
        for (int i = start; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '/' || ch == '?' || ch == '#') {
                end = i;
                break;
            }
        }
        return value.substring(start, end);
    }

    public String getHost() {
        String auth = getAuthority();
        if (auth == null) return null;
        int at = auth.lastIndexOf('@');
        String hostPort = at >= 0 ? auth.substring(at + 1) : auth;
        int colon = hostPort.lastIndexOf(':');
        if (colon >= 0 && colon < hostPort.length() - 1
                && hostPort.substring(colon + 1).matches("[0-9]+")) {
            return hostPort.substring(0, colon);
        }
        return hostPort;
    }

    public int getPort() {
        String auth = getAuthority();
        if (auth == null) return -1;
        int at = auth.lastIndexOf('@');
        String hostPort = at >= 0 ? auth.substring(at + 1) : auth;
        int colon = hostPort.lastIndexOf(':');
        if (colon >= 0 && colon < hostPort.length() - 1) {
            try {
                return Integer.parseInt(hostPort.substring(colon + 1));
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    public String getPath() {
        int c = value.indexOf(':');
        if (c >= 0 && value.startsWith("//", c + 1)) {
            int slash = value.indexOf('/', c + 3);
            if (slash < 0) return "";
            return stripQueryFragment(value.substring(slash));
        }
        if (value.startsWith("/")) return stripQueryFragment(value);
        if (c >= 0) return stripQueryFragment(value.substring(c + 1));
        return stripQueryFragment(value);
    }

    private static String stripQueryFragment(String s) {
        int q = s.indexOf('?');
        int h = s.indexOf('#');
        int end = s.length();
        if (q >= 0) end = Math.min(end, q);
        if (h >= 0) end = Math.min(end, h);
        return s.substring(0, end);
    }

    public List<String> getPathSegments() {
        String path = getPath();
        List<String> out = new ArrayList<>();
        for (String seg : path.split("/")) {
            if (!seg.isEmpty()) out.add(seg);
        }
        return out;
    }

    public String getLastPathSegment() {
        List<String> segs = getPathSegments();
        return segs.isEmpty() ? null : segs.get(segs.size() - 1);
    }

    public String getQuery() {
        int q = value.indexOf('?');
        if (q < 0) return null;
        int h = value.indexOf('#', q + 1);
        return h < 0 ? value.substring(q + 1) : value.substring(q + 1, h);
    }

    public String getQueryParameter(String key) {
        String query = getQuery();
        if (query == null || key == null) return null;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String k = eq < 0 ? pair : pair.substring(0, eq);
            if (k.equals(key)) return eq < 0 ? "" : pair.substring(eq + 1);
        }
        return null;
    }

    public String getFragment() {
        int h = value.indexOf('#');
        return h < 0 ? null : value.substring(h + 1);
    }

    /** The part after the scheme (and including a hierarchical '//'), minus the fragment. */
    public String getSchemeSpecificPart() {
        int c = value.indexOf(':');
        String ssp = c < 0 ? value : value.substring(c + 1);
        int h = ssp.indexOf('#');
        return h < 0 ? ssp : ssp.substring(0, h);
    }

    public String getEncodedSchemeSpecificPart() {
        return getSchemeSpecificPart();
    }

    public boolean isHierarchical() {
        int c = value.indexOf(':');
        return c < 0 || value.startsWith("/", c + 1);
    }

    public boolean isOpaque() {
        return !isHierarchical();
    }

    public boolean isRelative() {
        return getScheme() == null;
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Uri && value.equals(((Uri) o).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    /** Builder: assembles scheme/authority/path/query/fragment parts. */
    public static final class Builder {
        private String scheme;
        private String authority;
        private final StringBuilder path = new StringBuilder();
        private final Map<String, String> query = new LinkedHashMap<>();
        private String fragment;
        private String opaque;

        Builder parse(String s) {
            if (s == null) return this;
            Uri u = Uri.parse(s);
            scheme = u.getScheme();
            authority = u.getAuthority();
            String p = u.getPath();
            if (p != null) path.append(p);
            String q = u.getQuery();
            if (q != null) {
                for (String pair : q.split("&")) {
                    int eq = pair.indexOf('=');
                    if (eq < 0) query.put(pair, "");
                    else query.put(pair.substring(0, eq), pair.substring(eq + 1));
                }
            }
            fragment = u.getFragment();
            return this;
        }

        public Builder scheme(String scheme) {
            this.scheme = scheme;
            return this;
        }

        public Builder authority(String authority) {
            this.authority = authority;
            return this;
        }

        public Builder path(String path) {
            this.path.setLength(0);
            if (path != null) this.path.append(path);
            return this;
        }

        public Builder appendPath(String segment) {
            if (segment == null) return this;
            if (path.length() > 0 && path.charAt(path.length() - 1) != '/') path.append('/');
            path.append(segment);
            return this;
        }

        public Builder appendQueryParameter(String key, String value) {
            if (key != null) query.put(key, value == null ? "" : value);
            return this;
        }

        public Builder clearQuery() {
            query.clear();
            return this;
        }

        public Builder fragment(String fragment) {
            this.fragment = fragment;
            return this;
        }

        public Builder opaquePart(String opaque) {
            this.opaque = opaque;
            return this;
        }

        public Uri build() {
            StringBuilder sb = new StringBuilder();
            if (scheme != null) sb.append(scheme).append(':');
            if (opaque != null) {
                sb.append(opaque);
            } else {
                if (authority != null) sb.append("//").append(authority);
                sb.append(path);
                boolean first = true;
                for (Map.Entry<String, String> e : query.entrySet()) {
                    sb.append(first ? '?' : '&');
                    first = false;
                    sb.append(e.getKey()).append('=').append(e.getValue());
                }
            }
            if (fragment != null) sb.append('#').append(fragment);
            return new Uri(sb.toString());
        }

        @Override
        public String toString() {
            return build().toString();
        }
    }
}
