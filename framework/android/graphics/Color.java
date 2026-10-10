// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

/** Color constants and packers. Colors are packed ARGB ints. */
public final class Color {
    public static final int TRANSPARENT = 0x00000000;
    public static final int BLACK = 0xFF000000;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int RED = 0xFFFF0000;
    public static final int GREEN = 0xFF00FF00;
    public static final int BLUE = 0xFF0000FF;
    public static final int YELLOW = 0xFFFFFF00;
    public static final int CYAN = 0xFF00FFFF;
    public static final int MAGENTA = 0xFFFF00FF;
    public static final int GRAY = 0xFF888888;
    public static final int LTGRAY = 0xFFCCCCCC;
    public static final int DKGRAY = 0xFF444444;

    private Color() {}

    public static int argb(int a, int r, int g, int b) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    public static int rgb(int r, int g, int b) {
        return argb(0xFF, r, g, b);
    }

    public static int alpha(int color) {
        return (color >>> 24) & 0xFF;
    }

    public static int red(int color) {
        return (color >> 16) & 0xFF;
    }

    public static int green(int color) {
        return (color >> 8) & 0xFF;
    }

    public static int blue(int color) {
        return color & 0xFF;
    }

    /**
     * Parse `#rgb`, `#argb`, `#rrggbb`, `#aarrggbb` or a lowercase color
     * name. Fail-closed: null or malformed input throws
     * IllegalArgumentException, never a default color.
     */
    public static int parseColor(String colorString) {
        if (colorString == null || colorString.isEmpty()) {
            throw new IllegalArgumentException("unknown color");
        }
        if (colorString.charAt(0) == '#') {
            String s = colorString.substring(1);
            int a;
            int r;
            int g;
            int b;
            if (s.length() == 3) {
                r = hexNibble(s.charAt(0));
                g = hexNibble(s.charAt(1));
                b = hexNibble(s.charAt(2));
                return argb(0xFF, r * 17, g * 17, b * 17);
            } else if (s.length() == 4) {
                a = hexNibble(s.charAt(0)) * 17;
                r = hexNibble(s.charAt(1)) * 17;
                g = hexNibble(s.charAt(2)) * 17;
                b = hexNibble(s.charAt(3)) * 17;
                return argb(a, r, g, b);
            } else if (s.length() == 6) {
                r = hexByte(s, 0);
                g = hexByte(s, 2);
                b = hexByte(s, 4);
                return rgb(r, g, b);
            } else if (s.length() == 8) {
                a = hexByte(s, 0);
                r = hexByte(s, 2);
                g = hexByte(s, 4);
                b = hexByte(s, 6);
                return argb(a, r, g, b);
            }
            throw new IllegalArgumentException("unknown color " + colorString);
        }
        switch (colorString.toLowerCase(java.util.Locale.ROOT)) {
            case "transparent": return TRANSPARENT;
            case "black": return BLACK;
            case "white": return WHITE;
            case "red": return RED;
            case "green": return GREEN;
            case "blue": return BLUE;
            case "yellow": return YELLOW;
            case "cyan": return CYAN;
            case "magenta": return MAGENTA;
            case "gray":
            case "grey": return GRAY;
            case "lightgray":
            case "lightgrey": return LTGRAY;
            case "darkgray":
            case "darkgrey": return DKGRAY;
            default: throw new IllegalArgumentException("unknown color " + colorString);
        }
    }

    private static int hexNibble(char c) {
        int d = Character.digit(c, 16);
        if (d < 0) throw new IllegalArgumentException("bad hex color");
        return d;
    }

    private static int hexByte(String s, int off) {
        return (hexNibble(s.charAt(off)) << 4) | hexNibble(s.charAt(off + 1));
    }
}
