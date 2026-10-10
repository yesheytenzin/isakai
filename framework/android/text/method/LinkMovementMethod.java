// SPDX-License-Identifier: GPL-3.0-or-later
package android.text.method;

/** Link movement: taps activate URLSpans (headless: no tap dispatch). */
public class LinkMovementMethod extends ScrollingMovementMethod {
    private static LinkMovementMethod instance;

    public static MovementMethod getInstance() {
        if (instance == null) instance = new LinkMovementMethod();
        return instance;
    }
}
