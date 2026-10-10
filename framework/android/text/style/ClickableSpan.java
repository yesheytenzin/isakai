// SPDX-License-Identifier: GPL-3.0-or-later
package android.text.style;

import android.text.TextPaint;
import android.view.View;

/** Clickable span: click dispatch is a no-op, draw state underlines. */
public abstract class ClickableSpan extends CharacterStyle {
    public abstract void onClick(View widget);

    @Override
    public void updateDrawState(TextPaint paint) {
        if (paint != null) paint.setFakeBoldText(false);
    }
}
