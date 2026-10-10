// SPDX-License-Identifier: GPL-3.0-or-later
package android.text;

/**
 * HTML to spanned text (headless: tag-stripping with bold/italic/underline
 * spans; entities resolved; images/lists unsupported).
 */
public class Html {
    public static final int FROM_HTML_MODE_COMPACT = 63;
    public static final int FROM_HTML_MODE_LEGACY = 0;
    public static final int FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH = 1;
    public static final int FROM_HTML_SEPARATOR_LINE_BREAK_DIV = 4;
    public static final int FROM_HTML_SEPARATOR_LINE_BREAK_LIST_ITEM = 8;
    public static final int FROM_HTML_SEPARATOR_LINE_BREAK_HEADING = 2;
    public static final int FROM_HTML_SEPARATOR_LINE_BREAK_BLOCKQUOTE = 16;
    public static final int TO_HTML_PARAGRAPH_LINES_CONSECUTIVE = 0;
    public static final int TO_HTML_PARAGRAPH_LINES_INDIVIDUAL = 1;

    public interface ImageGetter {
        android.graphics.drawable.Drawable getDrawable(String source);
    }

    public interface TagHandler {
        void handleTag(boolean opening, String tag, android.text.Editable output,
                org.xml.sax.Attributes attributes);
    }

    private Html() {}

    public static Spanned fromHtml(String source) {
        return fromHtml(source, FROM_HTML_MODE_COMPACT, null, null);
    }

    public static Spanned fromHtml(String source, int flags) {
        return fromHtml(source, flags, null, null);
    }

    public static Spanned fromHtml(String source, int flags, ImageGetter imageGetter,
            TagHandler tagHandler) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        if (source == null) return out;
        String s = source.replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&");
        int i = 0;
        java.util.ArrayDeque<int[]> boldStack = new java.util.ArrayDeque<>();
        java.util.ArrayDeque<int[]> italicStack = new java.util.ArrayDeque<>();
        java.util.ArrayDeque<int[]> underlineStack = new java.util.ArrayDeque<>();
        StringBuilder text = new StringBuilder();
        java.util.ArrayList<Object[]> spans = new java.util.ArrayList<>();
        int[] boldStart = {-1};
        int[] italicStart = {-1};
        int[] underlineStart = {-1};
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '<') {
                int end = s.indexOf('>', i + 1);
                if (end < 0) {
                    text.append(c);
                    i++;
                    continue;
                }
                String tag = s.substring(i + 1, end).trim().toLowerCase(java.util.Locale.ROOT);
                boolean closing = tag.startsWith("/");
                String name = closing ? tag.substring(1).trim() : tag.split("\\s")[0];
                if (name.endsWith("/")) name = name.substring(0, name.length() - 1);
                switch (name) {
                    case "b":
                    case "strong":
                        if (closing) {
                            if (boldStart[0] >= 0) {
                                spans.add(new Object[] {
                                        new android.text.style.StyleSpan(
                                                android.graphics.Typeface.BOLD),
                                        boldStart[0], text.length()});
                                boldStart[0] = -1;
                            }
                        } else {
                            boldStart[0] = text.length();
                        }
                        break;
                    case "i":
                    case "em":
                    case "cite":
                    case "dfn":
                        if (closing) {
                            if (italicStart[0] >= 0) {
                                spans.add(new Object[] {
                                        new android.text.style.StyleSpan(
                                                android.graphics.Typeface.ITALIC),
                                        italicStart[0], text.length()});
                                italicStart[0] = -1;
                            }
                        } else {
                            italicStart[0] = text.length();
                        }
                        break;
                    case "u":
                        if (closing) {
                            if (underlineStart[0] >= 0) {
                                spans.add(new Object[] {
                                        new android.text.style.UnderlineSpan(),
                                        underlineStart[0], text.length()});
                                underlineStart[0] = -1;
                            }
                        } else {
                            underlineStart[0] = text.length();
                        }
                        break;
                    case "br":
                    case "p":
                    case "div":
                    case "li":
                    case "h1":
                    case "h2":
                    case "h3":
                    case "h4":
                    case "h5":
                    case "h6":
                        if (text.length() > 0
                                && text.charAt(text.length() - 1) != '\n') {
                            text.append('\n');
                        }
                        break;
                    default:
                        break;
                }
                i = end + 1;
            } else {
                text.append(c);
                i++;
            }
        }
        out.append(text.toString());
        for (Object[] sp : spans) {
            out.setSpan(sp[0], (Integer) sp[1], (Integer) sp[2],
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return out;
    }

    public static String toHtml(Spanned text) {
        return text == null ? "" : text.toString();
    }

    public static String toHtml(Spanned text, int option) {
        return toHtml(text);
    }

    public static String escapeHtml(CharSequence text) {
        if (text == null) return "";
        return text.toString().replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
