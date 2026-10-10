// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

/**
 * Typeface handle. Desktop rendering uses the bitmap font; this class
 * records style/weight so text configuration calls link and round-trip.
 */
public class Typeface {
    public static final int NORMAL = 0;
    public static final int BOLD = 1;
    public static final int ITALIC = 2;
    public static final int BOLD_ITALIC = 3;

    public static final Typeface DEFAULT = new Typeface(NORMAL, 400, false);
    public static final Typeface DEFAULT_BOLD = new Typeface(BOLD, 700, false);
    public static final Typeface SANS_SERIF = new Typeface(NORMAL, 400, false);
    public static final Typeface SERIF = new Typeface(NORMAL, 400, false);
    public static final Typeface MONOSPACE = new Typeface(NORMAL, 400, false);

    private final int style;
    private final int weight;
    private final boolean italic;

    private Typeface(int style, int weight, boolean italic) {
        this.style = style;
        this.weight = weight;
        this.italic = italic;
    }

    public int getStyle() { return style; }
    public int getWeight() { return weight; }
    public boolean isBold() { return (style & BOLD) != 0 || weight >= 600; }
    public boolean isItalic() { return italic || (style & ITALIC) != 0; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Typeface)) return false;
        Typeface t = (Typeface) o;
        return style == t.style && weight == t.weight && italic == t.italic;
    }

    @Override
    public int hashCode() {
        return style * 31 * 31 + weight * 31 + (italic ? 1 : 0);
    }

    public static Typeface defaultFromStyle(int style) {
        return new Typeface(style, (style & BOLD) != 0 ? 700 : 400, (style & ITALIC) != 0);
    }

    public static Typeface create(String familyName, int style) {
        return defaultFromStyle(style);
    }

    public static Typeface create(Typeface family, int style) {
        if (family == null) return defaultFromStyle(style);
        return new Typeface(style, family.weight, family.italic || (style & ITALIC) != 0);
    }

    public static Typeface create(Typeface family, int weight, boolean italic) {
        int style = (weight >= 600 ? BOLD : NORMAL) | (italic ? ITALIC : NORMAL);
        return new Typeface(style, weight, italic);
    }

    public static Typeface createFromFile(String path) {
        return DEFAULT;
    }

    public static Typeface createFromFile(java.io.File file) {
        return DEFAULT;
    }

    public static Typeface createFromAsset(android.content.res.AssetManager mgr, String path) {
        return DEFAULT;
    }

    public static final class Builder {
        private int weight = 400;
        private boolean italic;

        public Builder(java.io.FileDescriptor fd) {}
        public Builder(String path) {}

        public Builder setWeight(int weight) {
            this.weight = weight;
            return this;
        }

        public Builder setItalic(boolean italic) {
            this.italic = italic;
            return this;
        }

        public Typeface build() {
            return create(null, weight, italic);
        }
    }

    public static final class CustomFallbackBuilder {
        private android.graphics.fonts.FontStyle style;

        public CustomFallbackBuilder(android.graphics.fonts.FontFamily family) {}

        public CustomFallbackBuilder setStyle(android.graphics.fonts.FontStyle style) {
            this.style = style;
            return this;
        }

        public Typeface build() {
            if (style == null) return DEFAULT;
            return create(null, style.getWeight(), style.getSlant() != 0);
        }
    }
}
