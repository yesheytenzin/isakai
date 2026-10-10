// SPDX-License-Identifier: GPL-3.0-or-later
package android.util;

/** Immutable float width/height pair. */
public final class SizeF {
    private final float width;
    private final float height;

    public SizeF(float width, float height) {
        this.width = width;
        this.height = height;
    }

    public SizeF(Size size) {
        this(size == null ? 0 : size.getWidth(), size == null ? 0 : size.getHeight());
    }

    public float getWidth() { return width; }
    public float getHeight() { return height; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SizeF)) return false;
        SizeF other = (SizeF) o;
        return Float.compare(width, other.width) == 0
                && Float.compare(height, other.height) == 0;
    }

    @Override
    public int hashCode() {
        return 31 * Float.hashCode(width) + Float.hashCode(height);
    }

    @Override
    public String toString() {
        return width + "x" + height;
    }
}
