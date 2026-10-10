// SPDX-License-Identifier: GPL-3.0-or-later
package android.webkit;

/** Minimal WebResourceError: code + description. */
public class WebResourceError {
    private final int code;
    private final CharSequence desc;
    public WebResourceError(int code, CharSequence desc) { this.code = code; this.desc = desc; }
    public int getErrorCode() { return code; }
    public CharSequence getDescription() { return desc; }
}
