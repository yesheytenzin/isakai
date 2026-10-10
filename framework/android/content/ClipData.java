// SPDX-License-Identifier: GPL-3.0-or-later
package android.content;

import android.net.Uri;
import java.util.ArrayList;
import java.util.List;

/**
 * ClipData: text, HTML, and URI-list clips. Items carry at most one of
 * text / HTML / URI; {@link Item#coerceToText} prefers text, then URI
 * string, then HTML stripped of tags (null stays null).
 */
public class ClipData {
    private final ClipDescription description;
    private final List<Item> items;

    public ClipData(ClipDescription description, Item item) {
        if (description == null || item == null) throw new IllegalArgumentException("null description/item");
        this.description = description;
        this.items = new ArrayList<>();
        this.items.add(item);
    }

    public ClipData(CharSequence label, String[] mimeTypes, Item item) {
        this(new ClipDescription(label, mimeTypes), item);
    }

    private ClipData(ClipDescription description, List<Item> items) {
        this.description = description;
        this.items = items;
    }

    /** Single-item plain-text clip. */
    public static ClipData newPlainText(CharSequence label, CharSequence text) {
        return new ClipData(label, new String[] { ClipDescription.MIMETYPE_TEXT_PLAIN }, new Item(text));
    }

    /** Single-item HTML clip ({@code text} + {@code htmlText}). */
    public static ClipData newHtmlText(CharSequence label, CharSequence text, String htmlText) {
        Item item = new Item(text, htmlText, null);
        ClipDescription desc = new ClipDescription(label,
                new String[] { ClipDescription.MIMETYPE_TEXT_HTML, ClipDescription.MIMETYPE_TEXT_PLAIN });
        return new ClipData(desc, item);
    }

    /** Single-item URI clip. */
    public static ClipData newUri(CharSequence label, Uri uri) {
        if (uri == null) throw new NullPointerException("uri");
        Item item = new Item(null, null, uri);
        ClipDescription desc = new ClipDescription(label,
                new String[] { ClipDescription.MIMETYPE_TEXT_URILIST });
        return new ClipData(desc, item);
    }

    /** Multi-item URI-list clip (one Item per URI; empty list rejected). */
    public static ClipData newUriList(CharSequence label, List<Uri> uris) {
        if (uris == null || uris.isEmpty()) throw new IllegalArgumentException("empty uris");
        List<Item> items = new ArrayList<>();
        for (Uri u : uris) {
            if (u == null) throw new IllegalArgumentException("null uri");
            items.add(new Item(null, null, u));
        }
        ClipDescription desc = new ClipDescription(label,
                new String[] { ClipDescription.MIMETYPE_TEXT_URILIST });
        return new ClipData(desc, items);
    }

    public ClipDescription getDescription() {
        return description;
    }

    public int getItemCount() {
        return items.size();
    }

    public Item getItemAt(int index) {
        return items.get(index);
    }

    /** Append an item to this clip (single logical clip, N items). */
    public void addItem(Item item) {
        if (item == null) throw new IllegalArgumentException("null item");
        items.add(item);
    }

    /** Single clip item: text, HTML, and/or URI. */
    public static final class Item {
        private final CharSequence text;
        private final String htmlText;
        private final Uri uri;

        public Item(CharSequence text) {
            this(text, null, null);
        }

        public Item(CharSequence text, String htmlText) {
            this(text, htmlText, null);
        }

        public Item(Uri uri) {
            this(null, null, uri);
        }

        public Item(CharSequence text, String htmlText, Uri uri) {
            this.text = text;
            this.htmlText = htmlText;
            this.uri = uri;
        }

        public CharSequence getText() {
            return text;
        }

        public String getHtmlText() {
            return htmlText;
        }

        public Uri getUri() {
            return uri;
        }

        /**
         * Text coercion: text first, then the URI string, then HTML
         * with tags stripped; null when the item carries nothing.
         */
        public CharSequence coerceToText(android.content.Context context) {
            if (text != null) return text;
            if (uri != null) return uri.toString();
            if (htmlText != null) return htmlText.replaceAll("<[^>]*>", "");
            return null;
        }
    }
}
