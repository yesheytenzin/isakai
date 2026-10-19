// SPDX-License-Identifier: GPL-3.0-or-later
package android.view.inputmethod;

/**
 * Editor bounds info: IME-visible regions (headless: recorded only).
 * AOSP API 34 shape: the regions are float rects.
 */
public final class EditorBoundsInfo {
    private final android.graphics.RectF editorBounds;
    private final android.graphics.RectF handwritingBounds;

    private EditorBoundsInfo(android.graphics.RectF editorBounds,
            android.graphics.RectF handwritingBounds) {
        this.editorBounds = editorBounds;
        this.handwritingBounds = handwritingBounds;
    }

    public android.graphics.RectF getEditorBounds() { return editorBounds; }
    public android.graphics.RectF getHandwritingBounds() { return handwritingBounds; }

    public static final class Builder {
        private android.graphics.RectF editorBounds = new android.graphics.RectF();
        private android.graphics.RectF handwritingBounds = new android.graphics.RectF();

        public Builder setEditorBounds(android.graphics.RectF bounds) {
            if (bounds != null) this.editorBounds = bounds;
            return this;
        }

        public Builder setEditorBounds(android.graphics.Rect bounds) {
            if (bounds != null) this.editorBounds = new android.graphics.RectF(bounds);
            return this;
        }

        public Builder setHandwritingBounds(android.graphics.RectF bounds) {
            if (bounds != null) this.handwritingBounds = bounds;
            return this;
        }

        public Builder setHandwritingBounds(android.graphics.Rect bounds) {
            if (bounds != null) this.handwritingBounds = new android.graphics.RectF(bounds);
            return this;
        }

        public EditorBoundsInfo build() {
            return new EditorBoundsInfo(editorBounds, handwritingBounds);
        }
    }
}
