// SPDX-License-Identifier: GPL-3.0-or-later
package android.util;

/** Immutable width/height pair (pixels). */
public final class Size {
    private final int width;
    private final int height;

    public Size(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public static Size parseSize(String s) {
        if (s == null) throw new NumberFormatException("null");
        int x = s.indexOf('*');
        if (x < 0) x = s.indexOf('x');
        if (x < 0) throw new NumberFormatException(s);
        return new Size(Integer.parseInt(s.substring(0, x).trim()),
                Integer.parseInt(s.substring(x + 1).trim()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Size)) return false;
        Size other = (Size) o;
        return width == other.width && height == other.height;
    }

    @Override
    public int hashCode() {
        return 31 * width + height;
    }

    @Override
    public String toString() {
        return width + "x" + height;
    }
}
