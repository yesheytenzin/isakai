// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics.drawable;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Region;
import android.util.LayoutDirection;

/**
 * Drawable base: bounds/state/level/visibility bookkeeping with abstract
 * draw. Subclasses render into the software Canvas.
 */
public abstract class Drawable {
    private int[] state = new int[0];
    private int level;
    private int changingConfigurations;
    private Rect bounds = new Rect();
    private Callback callback;
    private boolean visible = true;
    private boolean autoMirrored;

    public interface Callback {
        void invalidateDrawable(Drawable who);
        void scheduleDrawable(Drawable who, Runnable what, long when);
        void unscheduleDrawable(Drawable who, Runnable what);
    }

    public abstract static class ConstantState {
        public abstract Drawable newDrawable();
        public Drawable newDrawable(Resources res) { return newDrawable(); }
        public Drawable newDrawable(Resources res, Resources.Theme theme) { return newDrawable(res); }
        public int getChangingConfigurations() { return 0; }
        public boolean canApplyTheme() { return false; }
    }

    public abstract void draw(Canvas canvas);

    public void setAlpha(int alpha) {}
    public void setColorFilter(ColorFilter colorFilter) {}
    public void setColorFilter(int color, PorterDuff.Mode mode) {
        setColorFilter(new android.graphics.PorterDuffColorFilter(color, mode));
    }
    public void clearColorFilter() { setColorFilter(null); }
    public int getAlpha() { return 0xFF; }
    public void setFilterBitmap(boolean filter) {}

    public void setBounds(int left, int top, int right, int bottom) {
        Rect old = new Rect(bounds);
        bounds.set(left, top, right, bottom);
        onBoundsChange(bounds);
    }

    public void setBounds(Rect bounds) {
        if (bounds != null) setBounds(bounds.left, bounds.top, bounds.right, bounds.bottom);
    }

    public Rect getBounds() { return bounds; }
    public void copyBounds(Rect out) { if (out != null) out.set(bounds); }

    public void setChangingConfigurations(int configs) { this.changingConfigurations = configs; }
    public int getChangingConfigurations() { return changingConfigurations; }

    public void setCallback(Callback cb) { this.callback = cb; }
    public Callback getCallback() { return callback; }

    public void invalidateSelf() {
        if (callback != null) callback.invalidateDrawable(this);
    }

    public void scheduleSelf(Runnable what, long when) {
        if (callback != null) callback.scheduleDrawable(this, what, when);
    }

    public void unscheduleSelf(Runnable what) {
        if (callback != null) callback.unscheduleDrawable(this, what);
    }

    public boolean setState(int[] stateSet) {
        this.state = stateSet == null ? new int[0] : stateSet.clone();
        return onStateChange(this.state);
    }

    public int[] getState() { return state.clone(); }
    public boolean isStateful() { return false; }
    protected boolean onStateChange(int[] state) { return false; }
    public void jumpToCurrentState() {}

    public boolean setLevel(int level) {
        if (this.level == level) return false;
        this.level = level;
        return onLevelChange(level);
    }

    public int getLevel() { return level; }
    protected boolean onLevelChange(int level) { return false; }

    public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = this.visible != visible;
        this.visible = visible;
        return changed;
    }

    public boolean isVisible() { return visible; }

    public void setAutoMirrored(boolean mirrored) { this.autoMirrored = mirrored; }
    public boolean isAutoMirrored() { return autoMirrored; }

    public int getLayoutDirection() { return LayoutDirection.LTR; }

    public Drawable getCurrent() { return this; }
    public boolean selectDrawable(int index) { return false; }

    public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    public Region getTransparentRegion() { return null; }

    public int getIntrinsicWidth() { return -1; }
    public int getIntrinsicHeight() { return -1; }
    public int getMinimumWidth() { return 0; }
    public int getMinimumHeight() { return 0; }

    public boolean getPadding(Rect padding) {
        if (padding != null) padding.setEmpty();
        return false;
    }

    public Drawable mutate() { return this; }
    public ConstantState getConstantState() { return null; }

    public void inflate(Resources r, org.xmlpull.v1.XmlPullParser parser,
            android.util.AttributeSet attrs) {}

    public void inflate(Resources r, org.xmlpull.v1.XmlPullParser parser,
            android.util.AttributeSet attrs, Resources.Theme theme) {
        inflate(r, parser, attrs);
    }

    public void applyTheme(Resources.Theme theme) {}
    public boolean canApplyTheme() { return false; }

    protected void onBoundsChange(Rect bounds) {}

    public static Drawable createFromXmlInner(Resources r, org.xmlpull.v1.XmlPullParser parser,
            android.util.AttributeSet attrs, Resources.Theme theme) {
        if (parser == null) return null;
        try {
            int type = parser.getEventType();
            while (type != org.xmlpull.v1.XmlPullParser.START_TAG
                    && type != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                type = parser.next();
            }
            if (type != org.xmlpull.v1.XmlPullParser.START_TAG) return null;
            if (attrs == null) attrs = bridgeAttrs(parser);
            return inflateTag(r, parser, attrs, theme);
        } catch (java.io.IOException | org.xmlpull.v1.XmlPullParserException e) {
            return null;
        }
    }

    public static Drawable createFromXml(Resources r, org.xmlpull.v1.XmlPullParser parser) {
        return createFromXmlInner(r, parser, bridgeAttrs(parser), null);
    }

    /**
     * Bridge a pull parser to AttributeSet. The StAX parser does not implement
     * AttributeSet, so attribute reads go through this live adapter (AOSP's
     * Xml.asAttributeSet equivalent). The adapter delegates every call to the
     * parser's current position; callers must query it while the parser is on
     * the element's start tag.
     */
    static android.util.AttributeSet bridgeAttrs(
            final org.xmlpull.v1.XmlPullParser parser) {
        if (parser == null) return null;
        if (parser instanceof android.util.AttributeSet) {
            return (android.util.AttributeSet) parser;
        }
        return new android.util.AttributeSet() {
            @Override
            public int getAttributeCount() { return parser.getAttributeCount(); }
            @Override
            public String getAttributeName(int index) { return parser.getAttributeName(index); }
            @Override
            public String getAttributeValue(int index) { return parser.getAttributeValue(index); }
            @Override
            public String getAttributeValue(String namespace, String name) {
                return parser.getAttributeValue(namespace, name);
            }
            @Override
            public String getPositionDescription() { return parser.getPositionDescription(); }
            @Override
            public int getAttributeNameResource(int index) { return 0; }
            @Override
            public int getAttributeListValue(String namespace, String attribute,
                    String[] options, int defaultValue) {
                return defaultValue;
            }
            @Override
            public boolean getAttributeBooleanValue(String namespace, String attribute,
                    boolean defaultValue) {
                String v = getAttributeValue(namespace, attribute);
                return v == null ? defaultValue : Boolean.parseBoolean(v);
            }
            @Override
            public int getAttributeResourceValue(String namespace, String attribute,
                    int defaultValue) {
                return defaultValue;
            }
            @Override
            public int getAttributeIntValue(String namespace, String attribute,
                    int defaultValue) {
                String v = getAttributeValue(namespace, attribute);
                if (v == null) return defaultValue;
                try {
                    return Integer.parseInt(v.trim());
                } catch (NumberFormatException e) {
                    return defaultValue;
                }
            }
            @Override
            public int getAttributeUnsignedIntValue(String namespace, String attribute,
                    int defaultValue) {
                return getAttributeIntValue(namespace, attribute, defaultValue);
            }
            @Override
            public float getAttributeFloatValue(String namespace, String attribute,
                    float defaultValue) {
                String v = getAttributeValue(namespace, attribute);
                if (v == null) return defaultValue;
                try {
                    return Float.parseFloat(v.trim());
                } catch (NumberFormatException e) {
                    return defaultValue;
                }
            }
            @Override
            public int getStyleAttribute() { return 0; }
            @Override
            public void recycle() {}
        };
    }

    /**
     * Inflate the element at the parser's current start tag. Supports the
     * software-rasterized subset: {@code color}, {@code shape} (solid color),
     * {@code bitmap}, {@code selector}, {@code layer-list}, {@code inset},
     * {@code clip}, {@code animation-list}. Anything else (vector, ripple,
     * nine-patch, transition, level-list) is fail-closed to {@code null}:
     * the software rasterizer cannot render it faithfully.
     */
    private static Drawable inflateTag(Resources r, org.xmlpull.v1.XmlPullParser parser,
            android.util.AttributeSet attrs, Resources.Theme theme) {
        String tag = parser.getName();
        if (tag == null) return null;
        switch (tag) {
            case "color": return inflateColor(r, attrs, theme);
            case "shape": return inflateShape(r, parser, attrs, theme);
            case "bitmap": return inflateBitmap(r, attrs);
            case "selector": return inflateSelector(r, parser, attrs, theme);
            case "layer-list": return inflateLayerList(r, parser, attrs, theme);
            case "inset": return inflateInset(r, parser, attrs, theme);
            case "clip": return inflateClip(r, parser, attrs, theme);
            case "animation-list": return inflateAnimationList(r, parser, attrs, theme);
            case "vector": return inflateVector(r, parser, attrs);
            default: return null;
        }
    }

    /** inflate() entry: position the parser then delegate to the tag switch. */
    public static Drawable createFromPath(String pathName) {
        if (pathName == null) return null;
        String lower = pathName.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".xml")) {
            try (java.io.InputStream in = new java.io.FileInputStream(pathName)) {
                org.xmlpull.v1.XmlPullParser parser = android.util.Xml.newPullParser();
                parser.setInput(in, "UTF-8");
                return createFromXmlInner(null, parser, null, null);
            } catch (java.io.IOException | org.xmlpull.v1.XmlPullParserException e) {
                return null;
            }
        }
        android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(pathName);
        return bitmap == null ? null : new BitmapDrawable(bitmap);
    }

    private static String attr(android.util.AttributeSet attrs, String name) {
        if (attrs == null) return null;
        // The StAX parser matches on the exact namespace URI: unprefixed
        // attributes carry no namespace, so try NO_NAMESPACE first.
        String v = attrs.getAttributeValue(null, name);
        if (v != null) return v;
        v = attrs.getAttributeValue("", name);
        if (v != null) return v;
        return attrs.getAttributeValue(
                "http://schemas.android.com/apk/res/android", name);
    }

    private static int attrInt(android.util.AttributeSet attrs, String name, int def) {
        String v = attr(attrs, name);
        if (v == null || v.isEmpty()) return def;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int resolveColorAttr(Resources r, android.util.AttributeSet attrs,
            String name, Resources.Theme theme, int def) {
        String v = attr(attrs, name);
        if (v == null || v.isEmpty()) return def;
        try {
            return ColorStateList.resolveColor(r, v.trim(), theme);
        } catch (RuntimeException e) {
            return def;
        }
    }

    private static Drawable inflateVector(Resources r, org.xmlpull.v1.XmlPullParser parser,
            android.util.AttributeSet attrs) {
        VectorDrawable v = new VectorDrawable();
        v.inflate(r, attrs, parser);
        return v;
    }

    private static Drawable inflateColor(Resources r, android.util.AttributeSet attrs,
            Resources.Theme theme) {
        return new ColorDrawable(resolveColorAttr(r, attrs, "color", theme, 0));
    }


    private static Drawable inflateShape(Resources r, org.xmlpull.v1.XmlPullParser parser,
            android.util.AttributeSet attrs, Resources.Theme theme) {
        // Basics: outer android:shape + nested <solid android:color>.
        // Gradient/stroke/corners/padding/size render as a solid fill (documented
        // in GradientDrawable); nested <gradient> startColor backs <solid>.
        GradientDrawable d = new GradientDrawable();
        String shape = attr(attrs, "shape");
        if ("oval".equals(shape)) d.setShape(GradientDrawable.OVAL);
        else if ("line".equals(shape)) d.setShape(GradientDrawable.LINE);
        else if ("ring".equals(shape)) d.setShape(GradientDrawable.RING);
        else d.setShape(GradientDrawable.RECTANGLE);
        int color = resolveColorAttr(r, attrs, "color", theme, 0);
        boolean haveColor = hasAttr(attrs, "color");
        try {
            while (true) {
                int t = parser.next();
                if (t == org.xmlpull.v1.XmlPullParser.END_DOCUMENT) break;
                if (t != org.xmlpull.v1.XmlPullParser.START_TAG) {
                    if (t == org.xmlpull.v1.XmlPullParser.END_TAG) break;
                    continue;
                }
                String child = parser.getName();
                android.util.AttributeSet childAttrs = bridgeAttrs(parser);
                if ("solid".equals(child) && !haveColor) {
                    color = resolveColorAttr(r, childAttrs, "color", theme, color);
                    haveColor = hasAttr(childAttrs, "color");
                } else if ("gradient".equals(child) && !haveColor) {
                    color = resolveColorAttr(r, childAttrs, "startColor", theme, color);
                    haveColor = hasAttr(childAttrs, "startColor");
                } else if ("stroke".equals(child)) {
                    d.setStroke(attrInt(childAttrs, "width", 0),
                            resolveColorAttr(r, childAttrs, "color", theme, 0));
                } else if ("corners".equals(child)) {
                    String radius = attr(childAttrs, "radius");
                    if (radius != null) {
                        try {
                            d.setCornerRadius(Float.parseFloat(radius.trim()));
                        } catch (NumberFormatException e) {
                            // Keep square corners on malformed radius.
                        }
                    }
                }
                skipTag(parser);
            }
        } catch (java.io.IOException | org.xmlpull.v1.XmlPullParserException e) {
            return null;
        }
        d.setColor(color);
        return d;
    }

    private static boolean hasAttr(android.util.AttributeSet attrs, String name) {
        String v = attr(attrs, name);
        return v != null && !v.isEmpty();
    }

    private static Drawable inflateBitmap(Resources r, android.util.AttributeSet attrs) {
        String src = attr(attrs, "src");
        if (src == null || src.isEmpty()) return null;
        String path = src.trim();
        android.graphics.Bitmap bitmap;
        if (path.startsWith("@")) {
            // Resource id with no table in this runtime: fail closed.
            return null;
        }
        bitmap = android.graphics.BitmapFactory.decodeFile(path);
        if (bitmap == null && r != null) {
            try (java.io.InputStream in = r.getAssets().open(path)) {
                bitmap = android.graphics.BitmapFactory.decodeStream(in);
            } catch (java.io.IOException | RuntimeException e) {
                bitmap = null;
            }
        }
        return bitmap == null ? null : new BitmapDrawable(r, bitmap);
    }

    private static Drawable inflateSelector(Resources r, org.xmlpull.v1.XmlPullParser parser,
            android.util.AttributeSet attrs, Resources.Theme theme) {
        StateListDrawable d = new StateListDrawable();
        try {
            while (true) {
                int t = parser.next();
                if (t == org.xmlpull.v1.XmlPullParser.END_DOCUMENT) break;
                if (t == org.xmlpull.v1.XmlPullParser.START_TAG) {
                    if (!"item".equals(parser.getName())) {
                        skipTag(parser);
                        continue;
                    }
                    android.util.AttributeSet itemAttrs = bridgeAttrs(parser);
                    int[] states = ColorStateList.stateSetFromAttributeSet(itemAttrs);
                    Drawable child = inflateChildFromItem(r, parser, itemAttrs, theme);
                    if (child != null) d.addState(states, child);
                    else d.addState(states, new ColorDrawable(
                            resolveColorAttr(r, itemAttrs, "color", theme, 0)));
                } else if (t == org.xmlpull.v1.XmlPullParser.END_TAG) {
                    break;
                }
            }
        } catch (java.io.IOException | org.xmlpull.v1.XmlPullParserException e) {
            return null;
        }
        return d;
    }

    private static Drawable inflateLayerList(Resources r, org.xmlpull.v1.XmlPullParser parser,
            android.util.AttributeSet attrs, Resources.Theme theme) {
        java.util.ArrayList<Drawable> layers = new java.util.ArrayList<>();
        try {
            while (true) {
                int t = parser.next();
                if (t == org.xmlpull.v1.XmlPullParser.END_DOCUMENT) break;
                if (t != org.xmlpull.v1.XmlPullParser.START_TAG) {
                    if (t == org.xmlpull.v1.XmlPullParser.END_TAG) break;
                    continue;
                }
                if (!"item".equals(parser.getName())) {
                    skipTag(parser);
                    continue;
                }
                android.util.AttributeSet itemAttrs = bridgeAttrs(parser);
                Drawable child = inflateChildFromItem(r, parser, itemAttrs, theme);
                if (child != null) layers.add(child);
                skipTag(parser);
            }
        } catch (java.io.IOException | org.xmlpull.v1.XmlPullParserException e) {
            return null;
        }
        return new LayerDrawable(layers.toArray(new Drawable[0]));
    }

    private static Drawable inflateInset(Resources r, org.xmlpull.v1.XmlPullParser parser,
            android.util.AttributeSet attrs, Resources.Theme theme) {
        int inset = attrInt(attrs, "inset", 0);
        Drawable inner = inflateFirstChild(r, parser, theme, "inset");
        if (inner == null) inner = new ColorDrawable(0);
        return new InsetDrawable(inner,
                attrInt(attrs, "insetLeft", inset), attrInt(attrs, "insetTop", inset),
                attrInt(attrs, "insetRight", inset), attrInt(attrs, "insetBottom", inset));
    }

    private static Drawable inflateClip(Resources r, org.xmlpull.v1.XmlPullParser parser,
            android.util.AttributeSet attrs, Resources.Theme theme) {
        Drawable inner = inflateFirstChild(r, parser, theme, "clip");
        if (inner == null) inner = new ColorDrawable(0);
        String orientation = attr(attrs, "clipOrientation");
        int o = "vertical".equalsIgnoreCase(orientation)
                ? ClipDrawable.VERTICAL : ClipDrawable.HORIZONTAL;
        return new ClipDrawable(inner, 0, o);
    }

    private static Drawable inflateAnimationList(Resources r,
            org.xmlpull.v1.XmlPullParser parser, android.util.AttributeSet attrs,
            Resources.Theme theme) {
        AnimationDrawable d = new AnimationDrawable();
        d.setOneShot("true".equalsIgnoreCase(attr(attrs, "oneshot")));
        try {
            while (true) {
                int t = parser.next();
                if (t == org.xmlpull.v1.XmlPullParser.END_DOCUMENT) break;
                if (t != org.xmlpull.v1.XmlPullParser.START_TAG) {
                    if (t == org.xmlpull.v1.XmlPullParser.END_TAG) break;
                    continue;
                }
                if (!"item".equals(parser.getName())) {
                    skipTag(parser);
                    continue;
                }
                android.util.AttributeSet itemAttrs = bridgeAttrs(parser);
                int duration = attrInt(itemAttrs, "duration", 0);
                Drawable frame = inflateChildFromItem(r, parser, itemAttrs, theme);
                if (frame == null) {
                    frame = new ColorDrawable(
                            resolveColorAttr(r, itemAttrs, "color", theme, 0));
                }
                d.addFrame(frame, duration);
                skipTag(parser);
            }
        } catch (java.io.IOException | org.xmlpull.v1.XmlPullParserException e) {
            return null;
        }
        return d;
    }

    /** Nested drawable inside an <item>: inline tag or android:drawable literal/ref. */
    private static Drawable inflateChildFromItem(Resources r,
            org.xmlpull.v1.XmlPullParser parser, android.util.AttributeSet itemAttrs,
            Resources.Theme theme) {
        try {
            int t = parser.next();
            while (t == org.xmlpull.v1.XmlPullParser.TEXT
                    || t == org.xmlpull.v1.XmlPullParser.IGNORABLE_WHITESPACE
                    || t == org.xmlpull.v1.XmlPullParser.COMMENT) {
                t = parser.next();
            }
            if (t == org.xmlpull.v1.XmlPullParser.START_TAG) {
                android.util.AttributeSet childAttrs = bridgeAttrs(parser);
                Drawable inline = inflateTag(r, parser, childAttrs, theme);
                if (inline != null) return inline;
                skipTag(parser);
                return null;
            }
            // No nested tag (whitespace then the item's END_TAG): fall back to
            // the android:drawable color literal. Refs without a table fail closed.
            if (t == org.xmlpull.v1.XmlPullParser.END_TAG) {
                String ref = attr(itemAttrs, "drawable");
                if (ref == null || ref.isEmpty()) return null;
                ref = ref.trim();
                if (ref.charAt(0) == '#') {
                    try {
                        return new ColorDrawable(
                                ColorStateList.resolveColor(r, ref, theme));
                    } catch (RuntimeException e) {
                        return null;
                    }
                }
                return null;
            }
            return null;
        } catch (java.io.IOException | org.xmlpull.v1.XmlPullParserException e) {
            return null;
        }
    }

    /** First nested drawable tag; leaves the parser at the child's end tag. */
    private static Drawable inflateFirstChild(Resources r,
            org.xmlpull.v1.XmlPullParser parser, Resources.Theme theme, String parent) {
        try {
            while (true) {
                int t = parser.next();
                if (t == org.xmlpull.v1.XmlPullParser.END_DOCUMENT) return null;
                if (t != org.xmlpull.v1.XmlPullParser.START_TAG) {
                    if (t == org.xmlpull.v1.XmlPullParser.END_TAG) return null;
                    continue;
                }
                android.util.AttributeSet childAttrs = bridgeAttrs(parser);
                Drawable d = inflateTag(r, parser, childAttrs, theme);
                if (d != null) {
                    skipTag(parser);
                    return d;
                }
                skipTag(parser);
            }
        } catch (java.io.IOException | org.xmlpull.v1.XmlPullParserException e) {
            return null;
        }
    }

    /**
     * Skip the rest of the current element. The StAX parser reports depth 0
     * always, so a manual counter tracks nesting instead of getDepth().
     */
    private static void skipTag(org.xmlpull.v1.XmlPullParser parser) {
        try {
            if (parser.getEventType() != org.xmlpull.v1.XmlPullParser.START_TAG) return;
            int depth = 1;
            while (depth > 0) {
                int t = parser.next();
                if (t == org.xmlpull.v1.XmlPullParser.END_DOCUMENT) return;
                if (t == org.xmlpull.v1.XmlPullParser.START_TAG) depth++;
                else if (t == org.xmlpull.v1.XmlPullParser.END_TAG) depth--;
            }
        } catch (java.io.IOException | org.xmlpull.v1.XmlPullParserException e) {
            // Parser already failed closed; nothing to skip.
        }
    }
}
