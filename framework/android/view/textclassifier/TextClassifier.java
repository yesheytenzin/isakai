// SPDX-License-Identifier: GPL-3.0-or-later
package android.view.textclassifier;

/** Text classifier stub: no classification available. */
public final class TextClassifier {
    public static final String TYPE_ADDRESS = "address";
    public static final String TYPE_EMAIL = "email";
    public static final String TYPE_PHONE = "phone";
    public static final String TYPE_URL = "url";
    public static final TextClassifier NO_OP = new TextClassifier();

    public TextClassifier() {
    }
}
