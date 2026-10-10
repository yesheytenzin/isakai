// SPDX-License-Identifier: GPL-3.0-or-later
package android.content;

import android.net.Uri;
import java.util.Arrays;

/** Host-side unit checks for {@link ClipData} + local {@link ClipboardManager}. Run on the host JVM. */
public final class ClipboardTest {
    private ClipboardTest() {}

    static void check(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }

    public static void main(String[] args) {
        // Local-only manager (null window): text round-trips.
        ClipboardManager cm = new ClipboardManager(null);
        check(!cm.hasPrimaryClip(), "starts empty");
        check(cm.getPrimaryClip() == null, "null clip");
        cm.setPrimaryClip(ClipData.newPlainText("label", "hello"));
        check(cm.hasPrimaryClip(), "has clip");
        ClipData got = cm.getPrimaryClip();
        check(got != null && got.getItemCount() == 1, "one item");
        check("hello".contentEquals(got.getItemAt(0).getText()), "text value");
        check("hello".contentEquals(cm.getText()), "getText");
        check(ClipDescription.MIMETYPE_TEXT_PLAIN.equals(
                got.getDescription().getMimeType(0)), "mime");

        // URI-list round-trips through the local copy.
        ClipboardManager cu = new ClipboardManager(null);
        ClipData uris = ClipData.newUriList("files", Arrays.asList(
                Uri.parse("file:///tmp/a.txt"), Uri.parse("content://test.table/rows/1")));
        cu.setPrimaryClip(uris);
        ClipData back = cu.getPrimaryClip();
        check(back != null && back.getItemCount() == 2, "uri count");
        check("file:///tmp/a.txt".equals(back.getItemAt(0).getUri().toString()), "uri 0");
        check(back.getItemAt(0).coerceToText(null) != null, "uri coerce");
        check(cu.hasPrimaryClip(), "uri has clip");

        // HTML falls back to plain text on the wire form.
        ClipData html = ClipData.newHtmlText("h", "plain words", "<b>plain</b> words");
        check("plain words".contentEquals(html.getItemAt(0).getText()), "html text");
        check("<b>plain</b> words".equals(html.getItemAt(0).getHtmlText()), "html raw");
        ClipboardManager ch = new ClipboardManager(null);
        ch.setPrimaryClip(html);
        check("plain words".contentEquals(ch.getText()), "html fallback text");

        // Multi-item addItem.
        ClipData multi = ClipData.newPlainText("m", "one");
        multi.addItem(new ClipData.Item("two"));
        check(multi.getItemCount() == 2, "addItem count");
        check("two".contentEquals(multi.getItemAt(1).getText()), "addItem value");

        System.out.println("ClipboardTest: all PASS");
    }
}
