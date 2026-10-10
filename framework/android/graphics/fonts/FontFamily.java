// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics.fonts;

/** Font-family record: metadata handle. */
public final class FontFamily {
    private final Font[] fonts;

    private FontFamily(Font[] fonts) {
        this.fonts = fonts;
    }

    public Font getFont(int index) {
        return index >= 0 && index < fonts.length ? fonts[index] : null;
    }

    public int getSize() { return fonts.length; }

    public static final class Builder {
        private final java.util.ArrayList<Font> fonts = new java.util.ArrayList<>();

        public Builder(Font font) {
            if (font != null) fonts.add(font);
        }

        public Builder addFont(Font font) {
            if (font != null) fonts.add(font);
            return this;
        }

        public FontFamily build() {
            return new FontFamily(fonts.toArray(new Font[0]));
        }
    }
}
