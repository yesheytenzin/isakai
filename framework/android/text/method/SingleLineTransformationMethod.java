// SPDX-License-Identifier: GPL-3.0-or-later
package android.text.method;

import android.graphics.Rect;
import android.view.View;

/** Single-line transformation: strips newline characters. */
public class SingleLineTransformationMethod implements TransformationMethod {
    private static final SingleLineTransformationMethod INSTANCE =
            new SingleLineTransformationMethod();

    public static SingleLineTransformationMethod getInstance() {
        return INSTANCE;
    }

    @Override
    public CharSequence getTransformation(CharSequence source, View view) {
        if (source == null) return "";
        boolean singleLine = true;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '\n' || c == '\r') {
                singleLine = false;
                break;
            }
        }
        if (singleLine) return source;
        StringBuilder sb = new StringBuilder(source.length());
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c != '\n' && c != '\r') sb.append(c);
        }
        return sb.toString();
    }

    @Override
    public void onFocusChanged(View view, CharSequence sourceText, boolean focused, int direction,
            Rect previouslyFocusedRect) {
    }
}
