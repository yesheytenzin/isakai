// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.Context;
import android.view.View;

/** Image button: clickable ImageView with button styling. */
public class ImageButton extends ImageView {
    public ImageButton(Context context) {
        super(context);
        init();
    }

    public ImageButton(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ImageButton(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setClickable(true);
        setFocusable(true);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
