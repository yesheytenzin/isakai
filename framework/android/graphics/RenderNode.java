// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

import java.util.List;

/**
 * Render node: transform bookkeeping plus a deferred {@link DisplayList}.
 * The software draw path records layer content through
 * {@link #beginRecording()} and replays it via
 * {@link Canvas#drawRenderNode}.
 */
public final class RenderNode {
    private final String name;
    private final DisplayList list = new DisplayList();

    private float tx, ty, tz, rx, ry, rz, sx = 1f, sy = 1f, alpha = 1f;
    private float pivotX, pivotY, elevation, cameraDistance;
    private int left, top, right, bottom;
    private boolean clipToBounds = true;
    private boolean clipToOutline;
    private boolean overlappingRendering = true;

    public RenderNode(String name) {
        this.name = name;
    }

    public RenderNode() {
        this(null);
    }

    public boolean hasDisplayList() {
        return !list.isEmpty();
    }

    public boolean hasIdentityMatrix() {
        return tx == 0 && ty == 0 && tz == 0 && rx == 0 && ry == 0 && rz == 0 && sx == 1f && sy == 1f
                && pivotX == 0 && pivotY == 0 && left == 0 && top == 0;
    }

    public boolean setPosition(int l, int t, int r, int b) {
        left = l;
        top = t;
        right = r;
        bottom = b;
        return true;
    }

    public int getLeft() {
        return left;
    }

    public int getTop() {
        return top;
    }

    public int getRight() {
        return right;
    }

    public int getBottom() {
        return bottom;
    }

    public int getWidth() {
        return right - left;
    }

    public int getHeight() {
        return bottom - top;
    }

    public boolean setTranslationX(float v) {
        tx = v;
        return true;
    }

    public float getTranslationX() {
        return tx;
    }

    public boolean setTranslationY(float v) {
        ty = v;
        return true;
    }

    public float getTranslationY() {
        return ty;
    }

    public boolean setTranslationZ(float v) {
        tz = v;
        return true;
    }

    public float getTranslationZ() {
        return tz;
    }

    public boolean setRotationX(float v) {
        rx = v;
        return true;
    }

    public float getRotationX() {
        return rx;
    }

    public boolean setRotationY(float v) {
        ry = v;
        return true;
    }

    public float getRotationY() {
        return ry;
    }

    public boolean setRotationZ(float v) {
        rz = v;
        return true;
    }

    public float getRotationZ() {
        return rz;
    }

    public boolean setRotation(float v) {
        rz = v;
        return true;
    }

    public float getRotation() {
        return rz;
    }

    public boolean setScaleX(float v) {
        sx = v;
        return true;
    }

    public float getScaleX() {
        return sx;
    }

    public boolean setScaleY(float v) {
        sy = v;
        return true;
    }

    public float getScaleY() {
        return sy;
    }

    public boolean setAlpha(float v) {
        alpha = v;
        return true;
    }

    public float getAlpha() {
        return alpha;
    }

    public boolean setPivotX(float v) {
        pivotX = v;
        return true;
    }

    public float getPivotX() {
        return pivotX;
    }

    public boolean setPivotY(float v) {
        pivotY = v;
        return true;
    }

    public float getPivotY() {
        return pivotY;
    }

    public boolean setElevation(float v) {
        elevation = v;
        return true;
    }

    public float getElevation() {
        return elevation;
    }

    public boolean setCameraDistance(float v) {
        cameraDistance = v;
        return true;
    }

    public float getCameraDistance() {
        return cameraDistance;
    }

    public boolean setClipToBounds(boolean v) {
        clipToBounds = v;
        return true;
    }

    public boolean getClipToBounds() {
        return clipToBounds;
    }

    public boolean setClipToOutline(boolean v) {
        clipToOutline = v;
        return true;
    }

    public boolean getClipToOutline() {
        return clipToOutline;
    }

    public boolean setHasOverlappingRendering(boolean v) {
        overlappingRendering = v;
        return true;
    }

    public boolean hasOverlappingRendering() {
        return overlappingRendering;
    }

    public boolean setOutline(android.graphics.Outline outline) {
        return true;
    }

    /**
     * Forced compositing layers carry an alpha-bearing paint; the recorded
     * list replays with {@link #setAlpha} instead, so this is state only.
     */
    public boolean setUseCompositingLayer(boolean use, android.graphics.Paint paint) {
        return true;
    }

    public boolean setRenderEffect(android.graphics.RenderEffect effect) {
        return true;
    }

    public boolean setAmbientShadowColor(int color) {
        return true;
    }

    public boolean setSpotShadowColor(int color) {
        return true;
    }

    public boolean offsetLeftAndRight(int offset) {
        left += offset;
        right += offset;
        return true;
    }

    public boolean offsetTopAndBottom(int offset) {
        top += offset;
        bottom += offset;
        return true;
    }

    public boolean getMatrix(Matrix out) {
        if (out == null) return false;
        out.setValues(renderMatrix());
        return true;
    }

    public boolean getInverseMatrix(Matrix out) {
        return out != null && out.invert(out);
    }

    /** Node transform as a 9-float affine matrix (position, translation, scale, rotation). */
    float[] renderMatrix() {
        Matrix m = new Matrix();
        m.setTranslate(left + tx, top + ty);
        float px = left + tx + pivotX;
        float py = top + ty + pivotY;
        if (sx != 1f || sy != 1f) m.postScale(sx, sy, px, py);
        if (rz != 0) m.postRotate(rz, px, py);
        float[] v = new float[9];
        m.getValues(v);
        return v;
    }

    List<DisplayList.Op> displayListOps() {
        return list.ops();
    }

    DisplayList displayList() {
        return list;
    }

    /** Begin a fresh display list sized to the node's current position. */
    public RecordingCanvas beginRecording() {
        list.clear();
        RecordingCanvas canvas = new RecordingCanvas(list, right - left, bottom - top);
        return canvas;
    }

    public RecordingCanvas beginRecording(int width, int height) {
        list.clear();
        return new RecordingCanvas(list, width, height);
    }

    public void endRecording() {
    }

    public String getName() {
        return name;
    }

    public void discardDisplayList() {
        list.clear();
    }
}
