// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

/** Render-node shell: transform bookkeeping, no display list. */
public final class RenderNode {
    private final String name;
    private float tx, ty, tz, rx, ry, rz, sx = 1f, sy = 1f, alpha = 1f;
    private int left, top, right, bottom;
    private boolean clipToBounds = true;
    public RenderNode(String name) { this.name = name; }
    public RenderNode() { this(null); }
    public boolean hasDisplayList() { return false; }
    public boolean hasIdentityMatrix() { return tx == 0 && ty == 0 && tz == 0 && rx == 0 && ry == 0 && rz == 0 && sx == 1f && sy == 1f; }
    public void setPosition(int l, int t, int r, int b) { left = l; top = t; right = r; bottom = b; }
    public void setTranslationX(float v) { tx = v; } public float getTranslationX() { return tx; }
    public void setTranslationY(float v) { ty = v; } public float getTranslationY() { return ty; }
    public void setTranslationZ(float v) { tz = v; } public float getTranslationZ() { return tz; }
    public void setRotationX(float v) { rx = v; } public float getRotationX() { return rx; }
    public void setRotationY(float v) { ry = v; } public float getRotationY() { return ry; }
    public void setRotationZ(float v) { rz = v; } public float getRotationZ() { return rz; }
    public void setScaleX(float v) { sx = v; } public float getScaleX() { return sx; }
    public void setScaleY(float v) { sy = v; } public float getScaleY() { return sy; }
    public void setAlpha(float v) { alpha = v; } public float getAlpha() { return alpha; }
    public void setClipToBounds(boolean v) { clipToBounds = v; }
    public String getName() { return name; }
    public void discardDisplayList() { }
}
