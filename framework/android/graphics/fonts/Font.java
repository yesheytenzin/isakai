// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics.fonts;

/** Font record: metadata handle, desktop rendering uses the bitmap font. */
public final class Font {
    private final int weight;
    private final int slant;
    private final String variationSettings;

    private Font(int weight, int slant, String variationSettings) {
        this.weight = weight;
        this.slant = slant;
        this.variationSettings = variationSettings;
    }

    public int getWeight() { return weight; }
    public int getSlant() { return slant; }
    public int getTtcIndex() { return 0; }
    public String getFontVariationSettings() { return variationSettings; }

    public static final class Builder {
        private int weight = 400;
        private int slant;
        private int ttcIndex;
        private String variationSettings;

        public Builder(android.content.res.AssetManager am, int resid) {}

        public Builder(android.content.res.Resources res, int resid) {}

        public Builder(android.os.ParcelFileDescriptor pfd) {}

        public Builder(java.nio.ByteBuffer buffer) {}

        public Builder(java.io.File file) {}

        public Builder setWeight(int weight) {
            this.weight = weight;
            return this;
        }

        public Builder setSlant(int slant) {
            this.slant = slant;
            return this;
        }

        public Builder setTtcIndex(int ttcIndex) {
            this.ttcIndex = ttcIndex;
            return this;
        }

        public Builder setFontVariationSettings(String settings) {
            this.variationSettings = settings;
            return this;
        }

        public Font build() {
            return new Font(weight, slant, variationSettings);
        }
    }
}
