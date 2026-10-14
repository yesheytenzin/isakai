// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

/**
 * Text-fill state. BitmapFont renders the pixels; this class records the
 * settings calls make so linking succeeds and Canvas can read metrics.
 */
public class Paint {
    public enum Style { FILL, STROKE, FILL_AND_STROKE }
    public enum Cap { BUTT, ROUND, SQUARE }
    public enum Join { MITER, ROUND, BEVEL }
    public enum Align { LEFT, CENTER, RIGHT }

    public static class FontMetrics {
        public float top;
        public float ascent;
        public float descent;
        public float bottom;
        public float leading;
    }

    public static class FontMetricsInt {
        public int top;
        public int ascent;
        public int descent;
        public int bottom;
        public int leading;
    }

    private int color = Color.BLACK;
    private Style style = Style.FILL;
    private Cap cap = Cap.BUTT;
    private Join join = Join.MITER;
    private float strokeWidth;
    private float strokeMiter;
    private float textSize = 12.0f;
    private float textScaleX = 1.0f;
    private float textSkewX;
    private float letterSpacing;
    private int flags;
    private int alpha = 0xFF;
    private boolean antiAlias;
    private boolean fakeBoldText;
    private boolean linearText;
    private boolean filterBitmap;
    private boolean elegantTextHeight;
    private Typeface typeface;
    private Xfermode xfermode;
    public Xfermode setXfermode(Xfermode mode) {
        Xfermode previous=xfermode;xfermode=mode;
        if(mode!=null)org.isakai.runtime.Capabilities.unavailable("android.graphics.Paint.xfermodeRendering");
        return previous;
    }
    public Xfermode getXfermode() { return xfermode; }
    private Shader shader;
    private ColorFilter colorFilter;
    private String fontFeatureSettings;
    private java.util.Locale textLocale = java.util.Locale.getDefault();

    public Paint() {}

    public Paint(int flags) {
        setFlags(flags);
    }

    public Paint(Paint src) {
        if (src != null) {
            this.color = src.color;
            this.style = src.style;
            this.cap = src.cap;
            this.join = src.join;
            this.strokeWidth = src.strokeWidth;
            this.strokeMiter = src.strokeMiter;
            this.textSize = src.textSize;
            this.textScaleX = src.textScaleX;
            this.textSkewX = src.textSkewX;
            this.letterSpacing = src.letterSpacing;
            this.flags = src.flags;
            this.alpha = src.alpha;
            this.antiAlias = src.antiAlias;
            this.fakeBoldText = src.fakeBoldText;
            this.linearText = src.linearText;
            this.filterBitmap = src.filterBitmap;
            this.elegantTextHeight = src.elegantTextHeight;
            this.typeface = src.typeface;
            this.shader = src.shader;
            this.colorFilter = src.colorFilter;
            this.fontFeatureSettings = src.fontFeatureSettings;
            this.textLocale = src.textLocale;
        }
    }

    public void reset() {
        color = Color.BLACK;
        style = Style.FILL;
        cap = Cap.BUTT;
        join = Join.MITER;
        strokeWidth = 0;
        strokeMiter = 0;
        textSize = 12.0f;
        textScaleX = 1.0f;
        textSkewX = 0;
        letterSpacing = 0;
        flags = 0;
        alpha = 0xFF;
        antiAlias = false;
        fakeBoldText = false;
        linearText = false;
        filterBitmap = false;
        elegantTextHeight = false;
        typeface = null;
        shader = null;
        colorFilter = null;
        fontFeatureSettings = null;
    }

    public int getFlags() { return flags; }
    public void setFlags(int flags) {
        this.flags = flags;
        this.antiAlias = (flags & 1) != 0;
    }

    public int getColor() { return color; }
    public void setColor(int color) { this.color = color; }

    public int getAlpha() { return alpha; }
    public void setAlpha(int alpha) { this.alpha = alpha & 0xFF; }

    public Style getStyle() { return style; }
    public void setStyle(Style style) { if (style != null) this.style = style; }

    public float getStrokeWidth() { return strokeWidth; }
    public void setStrokeWidth(float width) { this.strokeWidth = width; }

    public float getStrokeMiter() { return strokeMiter; }
    public void setStrokeMiter(float miter) { this.strokeMiter = miter; }

    public Cap getStrokeCap() { return cap; }
    public void setStrokeCap(Cap cap) { if (cap != null) this.cap = cap; }

    public Join getStrokeJoin() { return join; }
    public void setStrokeJoin(Join join) { if (join != null) this.join = join; }

    public float getTextSize() { return textSize; }
    public void setTextSize(float size) { this.textSize = size; }

    public float getTextScaleX() { return textScaleX; }
    public void setTextScaleX(float scaleX) { this.textScaleX = scaleX; }

    public float getTextSkewX() { return textSkewX; }
    public void setTextSkewX(float skewX) { this.textSkewX = skewX; }

    public float getLetterSpacing() { return letterSpacing; }
    public void setLetterSpacing(float spacing) { this.letterSpacing = spacing; }

    public boolean isAntiAlias() { return antiAlias; }
    public void setAntiAlias(boolean aa) { this.antiAlias = aa; }

    public boolean isFakeBoldText() { return fakeBoldText; }
    public void setFakeBoldText(boolean fake) { this.fakeBoldText = fake; }

    public boolean isLinearText() { return linearText; }
    public void setLinearText(boolean linear) { this.linearText = linear; }

    public boolean isFilterBitmap() { return filterBitmap; }
    public void setFilterBitmap(boolean filter) { this.filterBitmap = filter; }

    public boolean isElegantTextHeight() { return elegantTextHeight; }
    public void setElegantTextHeight(boolean elegant) { this.elegantTextHeight = elegant; }

    public Typeface getTypeface() { return typeface; }
    public Typeface setTypeface(Typeface tf) {
        Typeface prev = this.typeface;
        this.typeface = tf;
        return prev;
    }

    public Shader setShader(Shader shader) {
        Shader prev = this.shader;
        this.shader = shader;
        return prev;
    }
    public Shader getShader() { return shader; }

    public ColorFilter setColorFilter(ColorFilter filter) {
        ColorFilter prev = this.colorFilter;
        this.colorFilter = filter;
        return prev;
    }
    public ColorFilter getColorFilter() { return colorFilter; }

    public String getFontFeatureSettings() { return fontFeatureSettings; }
    public void setFontFeatureSettings(String settings) { this.fontFeatureSettings = settings; }

    public java.util.Locale getTextLocale() { return textLocale; }
    public void setTextLocale(java.util.Locale locale) {
        if (locale != null) this.textLocale = locale;
    }

    public void setShadowLayer(float radius, float dx, float dy, int color) {}

    public Align getTextAlign() { return Align.LEFT; }
    public void setTextAlign(Align align) {}

    private FontMetrics metrics() {
        FontMetrics fm = new FontMetrics();
        fm.top = -0.9f * textSize;
        fm.ascent = -0.8f * textSize;
        fm.descent = 0.2f * textSize;
        fm.bottom = 0.25f * textSize;
        fm.leading = 0;
        return fm;
    }

    public float ascent() { return -0.8f * textSize; }
    public float descent() { return 0.2f * textSize; }

    public FontMetrics getFontMetrics() { return metrics(); }

    public float getFontMetrics(FontMetrics fm) {
        FontMetrics m = metrics();
        if (fm != null) {
            fm.top = m.top;
            fm.ascent = m.ascent;
            fm.descent = m.descent;
            fm.bottom = m.bottom;
            fm.leading = m.leading;
        }
        return m.descent - m.ascent + m.leading;
    }

    public FontMetricsInt getFontMetricsInt() {
        FontMetrics m = metrics();
        FontMetricsInt fmi = new FontMetricsInt();
        fmi.top = (int) m.top;
        fmi.ascent = (int) m.ascent;
        fmi.descent = (int) (m.descent + 0.5f);
        fmi.bottom = (int) (m.bottom + 0.5f);
        fmi.leading = 0;
        return fmi;
    }

    public int getFontMetricsInt(FontMetricsInt fmi) {
        FontMetricsInt m = getFontMetricsInt();
        if (fmi != null) {
            fmi.top = m.top;
            fmi.ascent = m.ascent;
            fmi.descent = m.descent;
            fmi.bottom = m.bottom;
            fmi.leading = m.leading;
        }
        return m.descent - m.ascent + m.leading;
    }

    public float measureText(CharSequence text, int start, int end) {
        if (text == null) return 0;
        return measureText(text.toString(), start, end);
    }

    public float measureText(String text, int start, int end) {
        if (text == null) return 0;
        int s = Math.max(0, start);
        int e = Math.min(text.length(), end);
        if (e <= s) return 0;
        return BitmapFont.measure(text.substring(s, e), textSize);
    }

    public float measureText(String text) {
        return text == null ? 0 : BitmapFont.measure(text, textSize);
    }

    public float measureText(CharSequence text) {
        return text == null ? 0 : BitmapFont.measure(text.toString(), textSize);
    }

    public void getTextBounds(String text, int start, int end, Rect bounds) {
        if (bounds == null) return;
        bounds.setEmpty();
        if (text == null) return;
        int s = Math.max(0, start);
        int e = Math.min(text.length(), end);
        if (e <= s) return;
        bounds.right = (int) (BitmapFont.measure(text.substring(s, e), textSize) + 0.5f);
        bounds.top = (int) (-0.8f * textSize);
        bounds.bottom = (int) (0.2f * textSize + 0.5f);
    }

    public void getTextBounds(CharSequence text, int start, int end, Rect bounds) {
        getTextBounds(text == null ? null : text.toString(), start, end, bounds);
    }
}
