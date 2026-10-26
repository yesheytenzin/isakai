// SPDX-License-Identifier: GPL-3.0-or-later
package android.inputmethodservice;

import android.content.Context;
import android.graphics.drawable.Drawable;
import java.util.Collections;
import java.util.List;

@Deprecated
public class Keyboard {
    public Keyboard(Context context, int xmlLayoutResId) {}

    public List<Key> getKeys() { return Collections.emptyList(); }
    public int getHeight() { return 0; }
    public int getMinWidth() { return 0; }
    public int[] getNearestKeys(int x, int y) { return new int[0]; }

    public static class Row {
        public int defaultWidth;
        public int defaultHeight;
        public int defaultHorizontalGap;
        public int verticalGap;

        public Row(Keyboard parent) {}
    }

    public static class Key {
        public int[] codes;
        public CharSequence label;
        public Drawable icon;
        public Drawable iconPreview;
        public int width;
        public int height;
        public int gap;
        public int x;
        public int y;
        public boolean pressed;
        public boolean on;
        public boolean sticky;
        public boolean repeatable;
        public int edgeFlags;

        public Key(Row parent) {}

        public boolean isInside(int px, int py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }

        public void onPressed() { pressed = true; }
        public void onReleased(boolean inside) {
            pressed = false;
            if (sticky && inside) on = !on;
        }
    }
}
