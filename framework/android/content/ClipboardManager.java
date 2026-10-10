// SPDX-License-Identifier: GPL-3.0-or-later
package android.content;

import android.net.Uri;
import java.util.ArrayList;
import java.util.List;
import org.isakai.bridge.IsakaiWindow;

/**
 * Window-bound clipboard manager: text clips travel through the Wayland
 * data device, with a process-local copy as fallback (and as the whole
 * store before any compositor selection exists).
 *
 * <p>URI lists travel as {@code text/uri-list} over the same channel
 * (newline-joined); HTML clips degrade to their plain-text coercion on
 * the wire and keep full HTML only in the local copy. Reads prefer the
 * compositor selection, then the local copy.
 *
 * <p>A null native window means local-only mode: every method still works
 * against the local copy. This covers Activity use before runWindowed
 * opens the toplevel.
 */
public class ClipboardManager {
    /** Service name for Activity.getSystemService. */
    public static final String CLIPBOARD_SERVICE = "clipboard";

    private final IsakaiWindow.Window window;
    private ClipData localClip;
    private int selectionStatus;

    public ClipboardManager(IsakaiWindow.Window window) {
        this.window = window;
    }

    /** For tests: which native window this manager is bound to (may be null). */
    public IsakaiWindow.Window getWindow() {
        return window;
    }

    public void setPrimaryClip(ClipData clip) {
        localClip = clip;
        if (window != null) window.clipboardSet(clipText(clip));
    }

    public ClipData getPrimaryClip() {
        String wire = null;
        if (window != null) wire = window.clipboardGet();
        if (wire != null) {
            ClipData uris = tryParseUriList(wire);
            if (uris != null) return uris;
            return ClipData.newPlainText("", wire);
        }
        if (localClip != null) return localClip;
        return null;
    }

    public CharSequence getText() {
        ClipData clip = getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return null;
        return clip.getItemAt(0).coerceToText(null);
    }

    public boolean hasPrimaryClip() {
        if (window != null && window.clipboardHasText()) return true;
        if (localClip != null && localClip.getItemCount() > 0) return true;
        return selectionStatus == 1;
    }

    public boolean hasText() {
        return hasPrimaryClip();
    }

    public ClipDescription getPrimaryClipDescription() {
        ClipData clip = getPrimaryClip();
        return clip == null ? null : clip.getDescription();
    }

    /**
     * Kind-7 selection hint from the input drain: 1 = remote text offered,
     * 2 = remote non-text offer, 0 = selection empty. Advisory only; the
     * native flags remain the source of truth.
     */
    public void onSelectionChanged(int status) {
        selectionStatus = status;
    }

    /** Wire form: URIs newline-joined, else text coercion (HTML stripped). */
    private static String clipText(ClipData clip) {
        if (clip == null || clip.getItemCount() == 0) return "";
        List<String> uris = new ArrayList<>();
        for (int i = 0; i < clip.getItemCount(); i++) {
            Uri u = clip.getItemAt(i).getUri();
            if (u != null) uris.add(u.toString());
        }
        if (!uris.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < uris.size(); i++) {
                if (i > 0) sb.append('\n');
                sb.append(uris.get(i));
            }
            return sb.toString();
        }
        CharSequence t = clip.getItemAt(0).coerceToText(null);
        return t == null ? "" : t.toString();
    }

    private static ClipData tryParseUriList(String wire) {
        String[] lines = wire.split("\n");
        List<Uri> uris = new ArrayList<>();
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            if (!t.contains("://")) return null;
            uris.add(Uri.parse(t));
        }
        if (uris.isEmpty()) return null;
        return ClipData.newUriList("", uris);
    }
}
