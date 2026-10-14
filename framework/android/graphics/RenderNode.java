// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

/** Render-node shell: transform bookkeeping, no display list. */
public final class RenderNode {
    private final String name;
    private float tx, ty, tz, rx, ry, rz, sx = 1f, sy = 1f, alpha = 1f;
    private float pivotX, pivotY, elevation, cameraDistance;
    private int left, top, right, bottom;
    private boolean clipToBounds = true;
    private boolean clipToOutline;
    private boolean overlappingRendering = true;
    public RenderNode(String name) { this.name = name; }
    public RenderNode() { this(null); }
    public boolean hasDisplayList() { return false; }
    public boolean hasIdentityMatrix() { return tx == 0 && ty == 0 && tz == 0 && rx == 0 && ry == 0 && rz == 0 && sx == 1f && sy == 1f; }
    public boolean setPosition(int l, int t, int r, int b) { left = l; top = t; right = r; bottom = b; return true; }
    public int getLeft() { return left; }
    public int getTop() { return top; }
    public int getRight() { return right; }
    public int getBottom() { return bottom; }
    public int getWidth() { return right - left; }
    public int getHeight() { return bottom - top; }
    public boolean setTranslationX(float v) { tx = v; return true; } public float getTranslationX() { return tx; }
    public boolean setTranslationY(float v) { ty = v; return true; } public float getTranslationY() { return ty; }
    public boolean setTranslationZ(float v) { tz = v; return true; } public float getTranslationZ() { return tz; }
    public boolean setRotationX(float v) { rx = v; return true; } public float getRotationX() { return rx; }
    public boolean setRotationY(float v) { ry = v; return true; } public float getRotationY() { return ry; }
    public boolean setRotationZ(float v) { rz = v; return true; } public float getRotationZ() { return rz; }
    public boolean setRotation(float v) { rz = v; return true; } public float getRotation() { return rz; }
    public boolean setScaleX(float v) { sx = v; return true; } public float getScaleX() { return sx; }
    public boolean setScaleY(float v) { sy = v; return true; } public float getScaleY() { return sy; }
    public boolean setAlpha(float v) { alpha = v; return true; } public float getAlpha() { return alpha; }
    public boolean setPivotX(float v) { pivotX = v; return true; } public float getPivotX() { return pivotX; }
    public boolean setPivotY(float v) { pivotY = v; return true; } public float getPivotY() { return pivotY; }
    public boolean setElevation(float v) { elevation = v; return true; } public float getElevation() { return elevation; }
    public boolean setCameraDistance(float v) { cameraDistance = v; return true; } public float getCameraDistance() { return cameraDistance; }
    public boolean setClipToBounds(boolean v) { clipToBounds = v; return true; } public boolean getClipToBounds() { return clipToBounds; }
    public boolean setClipToOutline(boolean v) { clipToOutline = v; return true; } public boolean getClipToOutline() { return clipToOutline; }
    public boolean setHasOverlappingRendering(boolean v) { overlappingRendering = v; return true; } public boolean hasOverlappingRendering() { return overlappingRendering; }
    public boolean setOutline(android.graphics.Outline outline) { return true; }
    public boolean setUseCompositingLayer(boolean use, android.graphics.Paint paint) { return true; }
    public boolean setRenderEffect(android.graphics.RenderEffect effect) { return true; }
    public boolean setAmbientShadowColor(int color) { return true; }
    public boolean setSpotShadowColor(int color) { return true; }
    public boolean offsetLeftAndRight(int offset) { left += offset; right += offset; return true; }
    public boolean offsetTopAndBottom(int offset) { top += offset; bottom += offset; return true; }
    public boolean getMatrix(android.graphics.Matrix matrix) { return false; }
    public boolean getInverseMatrix(android.graphics.Matrix matrix) { return false; }
    public android.graphics.RecordingCanvas beginRecording() { return new android.graphics.RecordingCanvas(); }
    public android.graphics.RecordingCanvas beginRecording(int width, int height) { return new android.graphics.RecordingCanvas(); }
    public void endRecording() { }
    public String getName() { return name; }
    public void discardDisplayList() { }
}
