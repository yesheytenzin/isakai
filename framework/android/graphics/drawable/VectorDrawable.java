// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics.drawable;

import android.content.res.Resources;
import android.util.AttributeSet;

/**
 * Vector drawable: parses viewport/dimensions/path data, renders nothing
 * (software raster has no path fill yet; presence + bounds are honored).
 */
public class VectorDrawable extends Drawable {
    private int viewportWidth;
    private int viewportHeight;
    private float baseWidth;
    private float baseHeight;
    private int alpha = 255;

    public VectorDrawable() {}

    public static VectorDrawable create(Resources res, int resId) {
        if (res == null) return null;
        try {
            Drawable d = res.getDrawable(resId);
            return d instanceof VectorDrawable ? (VectorDrawable) d : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    void inflate(Resources r, AttributeSet attrs,
            org.xmlpull.v1.XmlPullParser parser) {
        if (attrs != null) {
            viewportWidth = attrFloat(attrs, "viewportWidth", 0);
            viewportHeight = attrFloat(attrs, "viewportHeight", 0);
            baseWidth = dimenFloat(r, attrs, "width", 0);
            baseHeight = dimenFloat(r, attrs, "height", 0);
            String alphaStr = attr(attrs, "alpha");
            if (alphaStr != null) {
                try {
                    alpha = Math.round(Float.parseFloat(alphaStr.trim()) * 255);
                } catch (NumberFormatException e) {
                    alpha = 255;
                }
            }
        }
        // Consume nested group/path/clip-path elements (data recorded
        // nowhere: rendering is out of scope; malformed XML fails closed).
        if (parser != null) {
            try {
                int depth = parser.getDepth();
                int type;
                while (((type = parser.next()) != org.xmlpull.v1.XmlPullParser.END_TAG
                        || parser.getDepth() > depth)
                        && type != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                }
            } catch (Exception e) {
                // Malformed vector: keep the shell.
            }
        }
    }

    @Override
    public void draw(android.graphics.Canvas canvas) {}

    @Override
    public int getIntrinsicWidth() {
        return Math.round(baseWidth);
    }

    @Override
    public int getIntrinsicHeight() {
        return Math.round(baseHeight);
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
    }

    @Override
    public int getAlpha() {
        return alpha;
    }

    @Override
    public int getOpacity() {
        return android.graphics.PixelFormat.TRANSLUCENT;
    }

    public float getViewportWidth() { return viewportWidth; }
    public float getViewportHeight() { return viewportHeight; }

    private static String attr(AttributeSet attrs, String name) {
        if (attrs == null) return null;
        String v = attrs.getAttributeValue(null, name);
        if (v != null) return v;
        v = attrs.getAttributeValue("", name);
        if (v != null) return v;
        return attrs.getAttributeValue(
                "http://schemas.android.com/apk/res/android", name);
    }

    private static int attrFloat(AttributeSet attrs, String name, int def) {
        String v = attr(attrs, name);
        if (v == null) return def;
        try {
            return Math.round(Float.parseFloat(v.trim()));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static float dimenFloat(Resources r, AttributeSet attrs, String name, float def) {
        String v = attr(attrs, name);
        if (v == null) return def;
        v = v.trim();
        try {
            if (v.endsWith("dp") || v.endsWith("dip")) {
                float dp = Float.parseFloat(v.substring(0, v.length() - 2).trim());
                float density = r == null ? 1.0f
                        : r.getDisplayMetrics().density;
                return dp * density;
            }
            if (v.endsWith("px")) {
                return Float.parseFloat(v.substring(0, v.length() - 2).trim());
            }
            return Float.parseFloat(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
