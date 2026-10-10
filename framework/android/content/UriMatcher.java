// SPDX-License-Identifier: GPL-3.0-or-later
package android.content;

import android.net.Uri;
import java.util.ArrayList;
import java.util.List;

/** URI matcher: exact segment match with # (number) and * (text) wildcards. */
public class UriMatcher {
    public static final int NO_MATCH = -1;
    private final int noMatch;
    private final List<Entry> entries = new ArrayList<>();
    private static final class Entry { String auth; List<String> segs; int code; Entry(String a, List<String> s, int c) { auth = a; segs = s; code = c; } }
    public UriMatcher(int code) { noMatch = code; }
    public void addURI(String authority, String path, int code) {
        List<String> segs = new ArrayList<>();
        if (path != null && !path.isEmpty()) { for (String p : path.split("/")) { if (!p.isEmpty()) segs.add(p); } }
        entries.add(new Entry(authority, segs, code));
    }
    public int match(Uri uri) {
        if (uri == null) return noMatch;
        String auth = authorityOf(uri.toString());
        List<String> got = pathSegmentsOf(uri.toString());
        for (Entry e : entries) {
            if (e.auth == null ? auth != null : !e.auth.equals(auth)) continue;
            if (e.segs.size() != got.size()) continue;
            boolean ok = true;
            for (int i = 0; i < e.segs.size(); i++) {
                String want = e.segs.get(i); String have = got.get(i);
                if (want.equals("#")) { if (!isNumber(have)) { ok = false; break; } }
                else if (!want.equals("*") && !want.equals(have)) { ok = false; break; }
            }
            if (ok) return e.code;
        }
        return noMatch;
    }
    private static String authorityOf(String s) {
        int c = s.indexOf(':');
        String rest = c >= 0 ? s.substring(c + 1) : s;
        if (rest.startsWith("//")) { rest = rest.substring(2); int e = rest.indexOf('/'); return e < 0 ? rest : rest.substring(0, e); }
        return null;
    }
    private static List<String> pathSegmentsOf(String s) {
        int c = s.indexOf(':');
        String rest = c >= 0 ? s.substring(c + 1) : s;
        if (rest.startsWith("//")) { int slash = rest.indexOf('/', 2); rest = slash < 0 ? "" : rest.substring(slash + 1); }
        else if (rest.startsWith("/")) rest = rest.substring(1);
        List<String> out = new ArrayList<>();
        for (String p : rest.split("/")) if (!p.isEmpty()) out.add(p);
        return out;
    }
    private static boolean isNumber(String s) { if (s == null || s.isEmpty()) return false; for (int i = 0; i < s.length(); i++) { if (!Character.isDigit(s.charAt(i))) return false; } return true; }
}
