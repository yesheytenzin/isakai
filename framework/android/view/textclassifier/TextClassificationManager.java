// SPDX-License-Identifier: GPL-3.0-or-later
package android.view.textclassifier;

/** Text classification service stub: returns the no-op classifier. */
public final class TextClassificationManager {
    public TextClassificationManager() {
    }

    public TextClassifier getTextClassifier() {
        return TextClassifier.NO_OP;
    }

    public TextClassifier getTextClassifier(int type) {
        return TextClassifier.NO_OP;
    }
}
