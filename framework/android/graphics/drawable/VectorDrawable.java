// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics.drawable;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.util.AttributeSet;
import java.awt.geom.AffineTransform;
import java.awt.BasicStroke;
import java.awt.geom.FlatteningPathIterator;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/** Software renderer for filled Android vector paths and their tint. */
public class VectorDrawable extends Drawable {
    private final List<VectorPath> paths = new ArrayList<>();
    private static final Path2D.Float[] NO_CLIPS = new Path2D.Float[0];
    private float viewportWidth;
    private float viewportHeight;
    private float baseWidth;
    private float baseHeight;
    private int alpha = 255;
    private int[] pathState = new int[0];

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

    void inflate(Resources r, AttributeSet attrs, XmlPullParser parser) {
        if (attrs != null) {
            viewportWidth = attrFloat(attrs, "viewportWidth", 0);
            viewportHeight = attrFloat(attrs, "viewportHeight", 0);
            baseWidth = dimenFloat(r, attrs, "width", 0);
            baseHeight = dimenFloat(r, attrs, "height", 0);
            alpha = Math.round(clamp(attrFloat(attrs, "alpha", 1), 0, 1) * 255);
        }
        if (parser == null || viewportWidth <= 0 || viewportHeight <= 0) return;

        int rootDepth = parser.getDepth();
        AffineTransform transform = new AffineTransform();
        float groupAlpha = 1;
        ArrayList<GroupFrame> groups = new ArrayList<>();
        ArrayList<Path2D.Float> clips = new ArrayList<>();
        try {
            while (true) {
                int type = parser.next();
                if (type == XmlPullParser.END_DOCUMENT) break;
                if (type == XmlPullParser.END_TAG) {
                    if (parser.getDepth() == rootDepth) break;
                    if ("group".equals(parser.getName()) && !groups.isEmpty()) {
                        GroupFrame frame = groups.remove(groups.size() - 1);
                        transform = frame.transform;
                        groupAlpha = frame.alpha;
                        while (clips.size() > frame.clipCount) clips.remove(clips.size() - 1);
                    }
                    continue;
                }
                if (type != XmlPullParser.START_TAG) continue;

                String tag = parser.getName();
                AttributeSet child = attributes(parser);
                if ("group".equals(tag)) {
                    groups.add(new GroupFrame(transform, groupAlpha, clips.size()));
                    AffineTransform local = groupTransform(child);
                    transform = new AffineTransform(transform);
                    transform.concatenate(local);
                    groupAlpha *= clamp(attrFloat(child, "alpha", 1), 0, 1);
                } else if ("path".equals(tag)) {
                    inflatePath(r, child, transform, groupAlpha, clips, paths);
                } else if ("clip-path".equals(tag)) {
                    Path2D.Float path = parsePath(child, transform);
                    if (path != null) clips.add(path);
                } else {
                    skipElement(parser);
                }
            }
        } catch (java.io.IOException | XmlPullParserException | RuntimeException e) {
            org.isakai.runtime.Capabilities.unavailable("VectorDrawable.xmlParsing");
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (canvas == null || viewportWidth <= 0 || viewportHeight <= 0 || alpha <= 0) return;
        Rect bounds = getBounds();
        if (bounds.width() <= 0 || bounds.height() <= 0) return;
        float scaleX = bounds.width() / viewportWidth;
        float scaleY = bounds.height() / viewportHeight;
        float rootAlpha = alpha / 255.0f;

        for (VectorPath vectorPath : paths) {
            Rectangle2D pathBounds = vectorPath.bounds;
            int left = Math.max(0, bounds.left + (int) Math.floor(pathBounds.getMinX() * scaleX));
            int top = Math.max(0, bounds.top + (int) Math.floor(pathBounds.getMinY() * scaleY));
            int right = Math.min(canvas.getWidth(),
                    bounds.left + (int) Math.ceil(pathBounds.getMaxX() * scaleX));
            int bottom = Math.min(canvas.getHeight(),
                    bounds.top + (int) Math.ceil(pathBounds.getMaxY() * scaleY));
            left = Math.max(left, bounds.left);
            top = Math.max(top, bounds.top);
            right = Math.min(right, bounds.right);
            bottom = Math.min(bottom, bounds.bottom);
            if (right <= left || bottom <= top) continue;

            ColorStateList paintList = vectorPath.strokeColor != null
                    ? vectorPath.strokeColor : vectorPath.fillColor;
            float paintAlpha = vectorPath.strokeColor != null ? vectorPath.strokeAlpha : vectorPath.alpha;
            int color = paintList.getColorForState(pathState, paintList.getDefaultColor());
            color = tintedColor(color);
            int drawAlpha = Math.round((color >>> 24) * paintAlpha * rootAlpha);
            if (drawAlpha <= 0) continue;
            color = (color & 0x00ffffff) | (drawAlpha << 24);

            // 2x2 coverage sampling with binary coverage: a single center
            // sample misses thin geometry (a 1px stroke landing exactly on a
            // pixel boundary), so four sub-samples decide membership. Coverage
            // stays binary because the established pixel expectations (and the
            // rest of this rasterizer) are not antialiased.
            for (int y = top; y < bottom; y++) {
                for (int x = left; x < right; x++) {
                    int covered = 0;
                    for (int sy = 0; sy < 2; sy++) {
                        for (int sx = 0; sx < 2; sx++) {
                            double vectorX = (x + 0.25 + sx * 0.5 - bounds.left) / scaleX;
                            double vectorY = (y + 0.25 + sy * 0.5 - bounds.top) / scaleY;
                            if (!vectorPath.path.contains(vectorX, vectorY)) continue;
                            boolean insideClips = true;
                            for (Path2D.Float clip : vectorPath.clips) {
                                if (!clip.contains(vectorX, vectorY)) {
                                    insideClips = false;
                                    break;
                                }
                            }
                            if (insideClips) covered++;
                        }
                    }
                    if (covered == 0) continue;
                    int coveredColor = covered == 4 ? color
                            : (color & 0x00ffffff) | (((color >>> 24) * covered / 4) << 24);
                    canvas.setPixel(x, y, sourceOver(coveredColor, canvas.getPixel(x, y)));
                }
            }
        }
    }
    private static int sourceOver(int source, int destination) {
        int sourceAlpha = source >>> 24;
        if (sourceAlpha == 255) return source;
        int destinationAlpha = destination >>> 24;
        float sourceFactor = sourceAlpha / 255.0f;
        float destinationFactor = destinationAlpha / 255.0f * (1 - sourceFactor);
        float outputAlpha = sourceFactor + destinationFactor;
        if (outputAlpha <= 0) return 0;
        int a = Math.round(outputAlpha * 255);
        int r = Math.round((((source >>> 16) & 0xff) * sourceFactor
                + ((destination >>> 16) & 0xff) * destinationFactor) / outputAlpha);
        int g = Math.round((((source >>> 8) & 0xff) * sourceFactor
                + ((destination >>> 8) & 0xff) * destinationFactor) / outputAlpha);
        int b = Math.round(((source & 0xff) * sourceFactor
                + (destination & 0xff) * destinationFactor) / outputAlpha);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }


    @Override
    public int getIntrinsicWidth() { return Math.round(baseWidth); }

    @Override
    public int getIntrinsicHeight() { return Math.round(baseHeight); }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = Math.max(0, Math.min(255, alpha));
        invalidateSelf();
    }

    @Override
    public int getAlpha() { return alpha; }

    @Override
    public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }

    @Override
    public boolean isStateful() {
        if (super.isStateful()) return true;
        for (VectorPath path : paths) if (path.fillColor.isStateful()) return true;
        return false;
    }

    @Override
    protected boolean onStateChange(int[] state) {
        pathState = state; // Drawable owns this copy; avoid cloning it on every draw.
        for (VectorPath path : paths) if (path.fillColor.isStateful()) return true;
        return false;
    }

    public float getViewportWidth() { return viewportWidth; }
    public float getViewportHeight() { return viewportHeight; }

    /**
     * Inflate one `&lt;path&gt;`: trim first (geometry), then emit the fill entry
     * (original geometry, fill color) and, when a stroke is configured, the
     * stroked-outline entry (outline geometry, stroke color). AOSP paints the
     * fill and the stroke as separate passes; keeping them as separate
     * entries preserves that without a second rasterizer.
     */
    private static void inflatePath(Resources resources, AttributeSet attrs, AffineTransform transform,
            float groupAlpha, ArrayList<Path2D.Float> clips, List<VectorPath> out) {
        Path2D.Float path = parsePath(attrs, transform);
        if (path == null) return;
        ColorStateList fillColor = colorList(resources, attr(attrs, "fillColor"));
        if (fillColor == null) return;

        if (hasAttr(attrs, "trimPathStart") || hasAttr(attrs, "trimPathEnd")
                || hasAttr(attrs, "trimPathOffset")) {
            float start = attrFloat(attrs, "trimPathStart", 0);
            float end = attrFloat(attrs, "trimPathEnd", 1);
            float offset = attrFloat(attrs, "trimPathOffset", 0);
            path = TrimPath.apply(path, start, end, offset);
            if (path == null || path.getBounds2D().isEmpty()) return;
        }

        ColorStateList strokeColor = hasAttr(attrs, "strokeColor")
                ? colorList(resources, attr(attrs, "strokeColor")) : null;
        float strokeWidth = dimenFloat(resources, attrs, "strokeWidth", 0);
        Path2D.Float[] pathClips = clips.isEmpty()
                ? NO_CLIPS : clips.toArray(new Path2D.Float[clips.size()]);

        // Fill pass: only when a fill color was declared (a stroke-only path
        // must not paint a black silhouette).
        if (hasAttr(attrs, "fillColor")) {
            Rectangle2D fillBounds = path.getBounds2D();
            if (!fillBounds.isEmpty()) {
                float fillAlpha = clamp(attrFloat(attrs, "fillAlpha", 1), 0, 1) * groupAlpha;
                out.add(new VectorPath(path, fillBounds, fillColor, fillAlpha, pathClips));
            }
        }

        // Stroke pass: outline the geometry with java2d's stroker so the same
        // point-in-shape rasterizer covers strokes as well as fills.
        if (strokeColor != null && strokeWidth > 0) {
            BasicStroke stroke = new BasicStroke(strokeWidth,
                    strokeCap(attr(attrs, "strokeLineCap")),
                    strokeJoin(attr(attrs, "strokeLineJoin")),
                    Math.max(1, attrFloat(attrs, "strokeMiterLimit", 4)));
            Path2D.Float outline = new Path2D.Float(stroke.createStrokedShape(path));
            Rectangle2D outlineBounds = outline.getBounds2D();
            if (!outlineBounds.isEmpty()) {
                float alpha = clamp(attrFloat(attrs, "strokeAlpha", 1), 0, 1) * groupAlpha;
                out.add(new VectorPath(outline, outlineBounds, strokeColor, alpha, pathClips));
            }
        }
    }

    private static Path2D.Float parsePath(AttributeSet attrs, AffineTransform transform) {
        String data = attr(attrs, "pathData");
        if (data == null || data.isEmpty()) return null;
        boolean evenOdd = "evenOdd".equals(attr(attrs, "fillType"));
        try {
            Path2D.Float path = VectorPathParser.parse(data, evenOdd);
            if (!transform.isIdentity()) path.transform(transform);
            return path;
        } catch (IllegalArgumentException e) {
            org.isakai.runtime.Capabilities.unavailable("VectorDrawable.pathData");
            return null;
        }
    }

    private static ColorStateList colorList(Resources resources, String raw) {
        if (raw == null || raw.isEmpty()) return ColorStateList.valueOf(Color.BLACK);
        try {
            return ColorStateList.valueOf(ColorStateList.resolveColor(resources, raw.trim(), null));
        } catch (RuntimeException e) {
            org.isakai.runtime.Capabilities.unavailable("VectorDrawable.fillColor: unsupported " + raw.trim());
            return null;
        }
    }

    private static AffineTransform groupTransform(AttributeSet attrs) {
        float pivotX = attrFloat(attrs, "pivotX", 0);
        float pivotY = attrFloat(attrs, "pivotY", 0);
        AffineTransform transform = new AffineTransform();
        transform.translate(attrFloat(attrs, "translateX", 0) + pivotX,
                attrFloat(attrs, "translateY", 0) + pivotY);
        transform.rotate(Math.toRadians(attrFloat(attrs, "rotation", 0)));
        transform.scale(attrFloat(attrs, "scaleX", 1), attrFloat(attrs, "scaleY", 1));
        transform.translate(-pivotX, -pivotY);
        return transform;
    }

    private static AttributeSet attributes(XmlPullParser parser) {
        if (parser instanceof AttributeSet) return (AttributeSet) parser;
        return new AttributeSet() {
            @Override public int getAttributeCount() { return parser.getAttributeCount(); }
            @Override public String getAttributeName(int index) { return parser.getAttributeName(index); }
            @Override public String getAttributeValue(int index) { return parser.getAttributeValue(index); }
            @Override public String getAttributeValue(String namespace, String name) {
                return parser.getAttributeValue(namespace, name);
            }
            @Override public String getPositionDescription() { return parser.getPositionDescription(); }
            @Override public int getAttributeNameResource(int index) { return 0; }
            @Override public int getAttributeListValue(String namespace, String attribute,
                    String[] options, int defaultValue) { return defaultValue; }
            @Override public boolean getAttributeBooleanValue(String namespace, String attribute,
                    boolean defaultValue) {
                String value = getAttributeValue(namespace, attribute);
                return value == null ? defaultValue : "true".equalsIgnoreCase(value);
            }
            @Override public int getAttributeResourceValue(String namespace, String attribute,
                    int defaultValue) { return defaultValue; }
            @Override public int getAttributeIntValue(String namespace, String attribute, int defaultValue) {
                return parseInt(getAttributeValue(namespace, attribute), defaultValue);
            }
            @Override public int getAttributeUnsignedIntValue(String namespace, String attribute,
                    int defaultValue) { return getAttributeIntValue(namespace, attribute, defaultValue); }
            @Override public float getAttributeFloatValue(String namespace, String attribute,
                    float defaultValue) { return parseFloat(getAttributeValue(namespace, attribute), defaultValue); }
            @Override public int getStyleAttribute() { return 0; }
            @Override public void recycle() {}
        };
    }

    private static String attr(AttributeSet attrs, String name) {
        if (attrs == null) return null;
        String value = attrs.getAttributeValue(null, name);
        if (value == null) value = attrs.getAttributeValue("", name);
        if (value == null) value = attrs.getAttributeValue(
                "http://schemas.android.com/apk/res/android", name);
        return value;
    }

    private static boolean hasAttr(AttributeSet attrs, String name) {
        String value = attr(attrs, name);
        return value != null && !value.isEmpty();
    }

    private static int strokeCap(String raw) {
        if (raw == null) return BasicStroke.CAP_BUTT;
        switch (raw.trim()) {
            case "round": return BasicStroke.CAP_ROUND;
            case "square": return BasicStroke.CAP_SQUARE;
            default: return BasicStroke.CAP_BUTT;
        }
    }

    private static int strokeJoin(String raw) {
        if (raw == null) return BasicStroke.JOIN_MITER;
        switch (raw.trim()) {
            case "round": return BasicStroke.JOIN_ROUND;
            case "bevel": return BasicStroke.JOIN_BEVEL;
            default: return BasicStroke.JOIN_MITER;
        }
    }

    /**
     * Trim path: flatten the geometry, cut the cumulative-length interval
     * [start+offset, end+offset] (wrapping past 1), and rebuild the sub-path.
     * AOSP interpolates within the segment that straddles an endpoint; this
     * keeps whole segments at the cut and drops everything outside, which is
     * what the rasterized result needs.
     */
    static final class TrimPath {
        private TrimPath() {}

        static Path2D.Float apply(Path2D.Float path, float start, float end, float offset) {
            if (path == null) return null;
            float delta = end - start;
            if (delta == 0f) return null;              // zero-length trim: nothing
            if (Math.abs(delta) >= 1f) return path;    // full path
            float from = wrap(start + offset);
            float to = wrap(from + Math.abs(delta));
            if (from == to) return null;
            FlatteningPathIterator it = new FlatteningPathIterator(path.getPathIterator(null), 0.25);
            double[] current = new double[6];
            Path2D.Float out = new Path2D.Float(path.getWindingRule());
            double[] previous = new double[2];
            boolean hasPrevious = false;
            // Total length first (wrap-around needs two passes).
            double total = 0;
            double[] segmentLengths = new double[4096];
            double[][] segmentPoints = new double[4096][8];
            int count = 0;
            while (!it.isDone() && count < segmentLengths.length) {
                int type = it.currentSegment(current);
                if (type == PathIterator.SEG_MOVETO) {
                    previous[0] = current[0];
                    previous[1] = current[1];
                    hasPrevious = true;
                } else {
                    if (!hasPrevious) {
                        previous[0] = current[0];
                        previous[1] = current[1];
                        hasPrevious = true;
                    } else {
                        double length = Math.hypot(current[0] - previous[0], current[1] - previous[1]);
                        segmentLengths[count] = length;
                        segmentPoints[count][0] = previous[0];
                        segmentPoints[count][1] = previous[1];
                        segmentPoints[count][2] = current[0];
                        segmentPoints[count][3] = current[1];
                        count++;
                        total += length;
                        previous[0] = current[0];
                        previous[1] = current[1];
                    }
                }
                it.next();
            }
            if (total <= 0) return path;
            boolean wrapAround = from > to;
            double position = 0;
            boolean started = false;
            for (int i = 0; i < count; i++) {
                double segStart = position / total;
                double segEnd = (position + segmentLengths[i]) / total;
                position += segmentLengths[i];
                boolean keep = wrapAround
                        ? (segEnd >= from || segStart <= to)
                        : (segEnd >= from && segStart <= to);
                if (!keep) {
                    if (started) {
                        started = false;
                    }
                    continue;
                }
                if (!started) {
                    out.moveTo(segmentPoints[i][0], segmentPoints[i][1]);
                    started = true;
                }
                out.lineTo(segmentPoints[i][2], segmentPoints[i][3]);
            }
            return out.getCurrentPoint() == null ? null : out;
        }

        private static float wrap(float value) {
            float wrapped = value % 1f;
            return wrapped < 0 ? wrapped + 1f : wrapped;
        }
    }

    private static float attrFloat(AttributeSet attrs, String name, float fallback) {
        return parseFloat(attr(attrs, name), fallback);
    }

    private static float parseFloat(String raw, float fallback) {
        if (raw == null || raw.isEmpty()) return fallback;
        try {
            float value = Float.parseFloat(raw.trim());
            return Float.isFinite(value) ? value : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null || raw.isEmpty()) return fallback;
        try {
            return Integer.decode(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static float dimenFloat(Resources resources, AttributeSet attrs, String name, float fallback) {
        String value = attr(attrs, name);
        if (value == null) return fallback;
        value = value.trim();
        try {
            if (value.endsWith("dip")) {
                float density = resources == null ? 1 : resources.getDisplayMetrics().density;
                return Float.parseFloat(value.substring(0, value.length() - 3).trim()) * density;
            }
            if (value.endsWith("dp")) {
                float density = resources == null ? 1 : resources.getDisplayMetrics().density;
                return Float.parseFloat(value.substring(0, value.length() - 2).trim()) * density;
            }
            if (value.endsWith("px")) return Float.parseFloat(value.substring(0, value.length() - 2).trim());
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void skipElement(XmlPullParser parser)
            throws java.io.IOException, XmlPullParserException {
        int depth = parser.getDepth();
        while (true) {
            int type = parser.next();
            if (type == XmlPullParser.END_DOCUMENT) return;
            if (type == XmlPullParser.END_TAG && parser.getDepth() == depth) return;
        }
    }

    private static final class GroupFrame {
        final AffineTransform transform;
        final float alpha;
        final int clipCount;

        GroupFrame(AffineTransform transform, float alpha, int clipCount) {
            this.transform = transform;
            this.alpha = alpha;
            this.clipCount = clipCount;
        }
    }

    private static final class VectorPath {
        final Path2D.Float path;
        final Rectangle2D bounds;
        final ColorStateList fillColor;
        final float alpha;
        final Path2D.Float[] clips;
        /** Non-null when the geometry was already stroked into {@link #path}. */
        final ColorStateList strokeColor;
        final float strokeAlpha;

        VectorPath(Path2D.Float path, Rectangle2D bounds, ColorStateList fillColor,
                float alpha, Path2D.Float[] clips) {
            this(path, bounds, fillColor, alpha, clips, null, 1f);
        }

        VectorPath(Path2D.Float path, Rectangle2D bounds, ColorStateList fillColor,
                float alpha, Path2D.Float[] clips, ColorStateList strokeColor, float strokeAlpha) {
            this.path = path;
            this.bounds = bounds;
            this.fillColor = fillColor;
            this.alpha = alpha;
            this.clips = clips;
            this.strokeColor = strokeColor;
            this.strokeAlpha = strokeAlpha;
        }
    }
}
