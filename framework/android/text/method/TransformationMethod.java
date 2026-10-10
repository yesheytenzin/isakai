// SPDX-License-Identifier: GPL-3.0-or-later
package android.text.method;

import android.graphics.Rect;
import android.view.View;

/** Display transformation for password fields and suggestions. */
public interface TransformationMethod {
    CharSequence getTransformation(CharSequence source, View view);
    void onFocusChanged(View view, CharSequence sourceText, boolean focused, int direction,
            Rect previouslyFocusedRect);
}
