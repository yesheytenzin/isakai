// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

import android.content.Context;

/** Pointer icon handle: type id only, no bitmap loading. */
public final class PointerIcon {
    public static final int TYPE_NULL = 0;
    public static final int TYPE_ARROW = 1000;
    public static final int TYPE_SPOT_HOVER = 2000;
    public static final int TYPE_SPOT_TOUCH = 2002;
    public static final int TYPE_SPOT_ANCHOR = 2001;
    private final int type;
    private PointerIcon(int type) { this.type = type; }
    public static PointerIcon getSystemIcon(Context context, int type) { return new PointerIcon(type); }
    public static PointerIcon load(android.content.res.Resources resources, int resourceId) { return new PointerIcon(TYPE_NULL); }
    public int getType() { return type; }
}
