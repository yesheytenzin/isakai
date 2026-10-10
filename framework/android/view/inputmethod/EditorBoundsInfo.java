// SPDX-License-Identifier: GPL-3.0-or-later
package android.view.inputmethod;

/**
 * Editor bounds info: IME-visible regions (headless: recorded only).
 */
public final class EditorBoundsInfo {
    private final android.graphics.Rect editorBounds;
    private final android.graphics.Rect handwritingBounds;

    private EditorBoundsInfo(android.graphics.Rect editorBounds,
            android.graphics.Rect handwritingBounds) {
        this.editorBounds = editorBounds;
        this.handwritingBounds = handwritingBounds;
    }

    public android.graphics.Rect getEditorBounds() { return editorBounds; }
    public android.graphics.Rect getHandwritingBounds() { return handwritingBounds; }

    public static final class Builder {
        private android.graphics.Rect editorBounds = new android.graphics.Rect();
        private android.graphics.Rect handwritingBounds = new android.graphics.Rect();

        public Builder setEditorBounds(android.graphics.Rect bounds) {
            this.editorBounds = bounds;
            return this;
        }

        public Builder setHandwritingBounds(android.graphics.Rect bounds) {
            this.handwritingBounds = bounds;
            return this;
        }

        public EditorBoundsInfo build() {
            return new EditorBoundsInfo(editorBounds, handwritingBounds);
        }
    }
}
