// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.Context;
import android.view.View;

/** Magnifier loupe shell: visibility flag only. */
public class Magnifier {
    private final View view;
    private boolean showing;
    public Magnifier(View view) { this.view = view; }
    public Magnifier(Context context) { this.view = null; }
    public void show(float x, float y) { showing = true; }
    public void show(float x, float y, float width, float height) { showing = true; }
    public void update() {}
    public void dismiss() { showing = false; }
    public boolean isShowing() { return showing; }
}
