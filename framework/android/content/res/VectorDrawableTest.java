// SPDX-License-Identifier: GPL-3.0-or-later
package android.content.res;

import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import java.nio.charset.StandardCharsets;

/** Behavioral checks for vector path inflation, transforms, clipping and tint. */
public final class VectorDrawableTest {
    private VectorDrawableTest() {}

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static VectorDrawable parse(String xml) throws Exception {
        try (android.content.res.XmlResourceParser parser = Resources.decodeBinaryXml(
                xml.getBytes(StandardCharsets.UTF_8), "vector.xml")) {
            Drawable drawable = Drawable.createFromXml(new Resources(), parser);
            if (!(drawable instanceof VectorDrawable)) throw new AssertionError("vector XML was not inflated");
            return (VectorDrawable) drawable;
        }
    }

    private static String vector(String contents) {
        return "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\""
                + " android:width=\"4px\" android:height=\"4px\""
                + " android:viewportWidth=\"4\" android:viewportHeight=\"4\">"
                + contents + "</vector>";
    }
    /** Assert the fill's RGB with meaningful coverage (2x2 sampler). */
    private static void checkFilled(int pixel, int rgb, String what) {
        check((pixel & 0x00ffffff) == rgb && (pixel >>> 24) >= 0x40,
                what + " (pixel " + Integer.toHexString(pixel) + ")");
    }

    private static int renderCenter(String pathData) throws Exception {
        VectorDrawable drawable = parse(vector(
                "<path android:fillColor=\"#ff456789\" android:pathData=\"" + pathData + "\"/>"));
        drawable.setBounds(0, 0, 4, 4);
        Canvas canvas = new Canvas(4, 4);
        drawable.draw(canvas);
        return canvas.getPixel(2, 2);
    }

    public static void main(String[] args) throws Exception {
        checkFilled(renderCenter("M0,0 L4,0 l0,4 h-4 Z"), 0x456789,
                "absolute and relative line commands did not render");
        checkFilled(renderCenter("M0,2 C0,0 4,0 4,2 S0,4 0,2 Z"), 0x456789,
                "cubic and smooth cubic commands did not render");
        checkFilled(renderCenter("M0,2 Q2,0 4,2 T0,2 Z"), 0x456789,
                "quadratic and smooth quadratic commands did not render");
        VectorDrawable transformed = parse(vector(
                "<clip-path android:pathData=\"M0,0 H3 V4 H0 Z\"/>"
                + "<group android:translateX=\"1\" android:translateY=\"1\" android:alpha=\"0.5\">"
                + "<path android:fillColor=\"#ffffffff\" android:pathData=\"M0,0 H4 V4 H0 Z\"/>"
                + "</group>"));
        transformed.setBounds(0, 0, 4, 4);
        transformed.setTintList(new ColorStateList(
                new int[][]{{ColorStateList.STATE_PRESSED}, {}},
                new int[]{0xffff0000, 0xff0000ff}));
        Canvas canvas = new Canvas(4, 4);
        canvas.drawColor(0xff000000);
        transformed.draw(canvas);
        int normal = canvas.getPixel(2, 2);
        check(normal == 0xff000080, "group translation, clipping, alpha or default tint was lost: "
                + Integer.toHexString(normal));
        check(canvas.getPixel(3, 2) == 0xff000000, "clip path did not exclude outside pixels");
        check(canvas.getPixel(0, 0) == 0xff000000, "path escaped its translated bounds");

        transformed.setState(new int[]{ColorStateList.STATE_PRESSED});
        Canvas pressed = new Canvas(4, 4);
        pressed.drawColor(0xff000000);
        transformed.draw(pressed);
        check(pressed.getPixel(2, 2) == 0xff800000, "stateful vector tint was not selected");

        VectorDrawable arc = parse(vector(
                "<path android:fillColor=\"@0x0106000b\""
                + " android:pathData=\"M1,2 A1,1 0 0,1 3,2 A1,1 0 0,1 1,2 Z\"/>"));
        arc.setBounds(0, 0, 4, 4);
        Canvas arcCanvas = new Canvas(4, 4);
        arc.draw(arcCanvas);
        checkFilled(arcCanvas.getPixel(2, 2), 0xffffff,
                "numeric system color or elliptical arc path did not render");
        check(arcCanvas.getPixel(0, 0) == 0, "arc path painted outside its geometry");
        boolean missingColorRejected = false;
        try {
            ColorStateList.resolveColor(new Resources(), "@0x0106ffff", null);
        } catch (Resources.NotFoundException expected) {
            missingColorRejected = true;
        }
        check(missingColorRejected, "unknown numeric color resource fell back silently");

        // Stroke: a path with strokeColor/strokeWidth paints its outline even
        // where the fill is absent, and honours the cap style.
        VectorDrawable stroked = parse(vector(
                "<path android:strokeColor=\"#ff00ff00\" android:strokeWidth=\"1\""
                + " android:strokeLineCap=\"round\" android:pathData=\"M0,2 L4,2\"/>"));
        stroked.setBounds(0, 0, 4, 4);
        Canvas strokeCanvas = new Canvas(4, 4);
        strokeCanvas.drawColor(0xff000000);
        stroked.draw(strokeCanvas);
        // A 1px stroke on the pixel boundary covers half of each sampled row,
        // so the coverage sampler reports partial alpha: assert the hue.
        int strokeOnLine = strokeCanvas.getPixel(2, 2);
        check((strokeOnLine >>> 8 & 0xff) > 0 && (strokeOnLine & 0xff) == 0 && (strokeOnLine >>> 16 & 0xff) == 0,
                "stroke did not paint green on the line: " + Integer.toHexString(strokeOnLine));
        check(strokeCanvas.getPixel(2, 0) == 0xff000000, "stroke painted above its width");
        check(strokeCanvas.getPixel(2, 3) == 0xff000000, "stroke painted below its width");
        check((strokeCanvas.getPixel(1, 2) >>> 8 & 0xff) > 0, "stroke did not run along the line");

        // Fill + stroke: both survive.
        VectorDrawable both = parse(vector(
                "<path android:fillColor=\"#ff0000ff\" android:strokeColor=\"#ffff0000\""
                + " android:strokeWidth=\"2\" android:pathData=\"M0,0 H4 V4 H0 Z\"/>"));
        both.setBounds(0, 0, 4, 4);
        Canvas bothCanvas = new Canvas(4, 4);
        bothCanvas.drawColor(0xff000000);
        both.draw(bothCanvas);
        check(bothCanvas.getPixel(2, 2) == 0xff0000ff,
                "fill inside a stroke was lost: " + Integer.toHexString(bothCanvas.getPixel(2, 2)));
        int border = bothCanvas.getPixel(0, 2);
        check((border >>> 16 & 0xff) > 0 && (border & 0xff) == 0,
                "stroke color was lost on the border: " + Integer.toHexString(border));

        // Trim path: keeping only the first half must drop the far end.
        VectorDrawable trimmed = parse(vector(
                "<path android:fillColor=\"#ffffffff\" android:trimPathStart=\"0\""
                + " android:trimPathEnd=\"0.5\" android:pathData=\"M0,0 H4 V4 H0 Z\"/>"));
        trimmed.setBounds(0, 0, 4, 4);
        Canvas trimCanvas = new Canvas(4, 4);
        trimmed.draw(trimCanvas);
        int kept = 0;
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) if (trimCanvas.getPixel(x, y) != 0) kept++;
        }
        check(kept > 0 && kept < 16, "trimPathEnd did not cut the geometry (painted " + kept + " px)");

        // Full trim keeps everything; an empty trim paints nothing.
        VectorDrawable untrimmed = parse(vector(
                "<path android:fillColor=\"#ffffffff\" android:trimPathStart=\"0\""
                + " android:trimPathEnd=\"1\" android:pathData=\"M0,0 H4 V4 H0 Z\"/>"));
        untrimmed.setBounds(0, 0, 4, 4);
        Canvas fullCanvas = new Canvas(4, 4);
        untrimmed.draw(fullCanvas);
        check(fullCanvas.getPixel(2, 2) != 0, "trimPathEnd=1 must keep the geometry");
        VectorDrawable empty = parse(vector(
                "<path android:fillColor=\"#ffffffff\" android:trimPathStart=\"0.5\""
                + " android:trimPathEnd=\"0.5\" android:pathData=\"M0,0 H4 V4 H0 Z\"/>"));
        empty.setBounds(0, 0, 4, 4);
        Canvas emptyCanvas = new Canvas(4, 4);
        empty.draw(emptyCanvas);
        check(emptyCanvas.getPixel(2, 2) == 0, "a zero-length trim must paint nothing");

        System.out.println("PASS: vector path fills, strokes, trim paths, group transforms, clipping, alpha and stateful tint");
    }
}
