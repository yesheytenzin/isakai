// SPDX-License-Identifier: GPL-3.0-or-later
package android.view.animation;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import org.xmlpull.v1.XmlPullParser;

/** Loads animations and interpolators from XML resources. */
public final class AnimationUtils {
    private AnimationUtils() {}

    public static long currentAnimationTimeMillis() {
        return System.currentTimeMillis();
    }

    /**
     * Load an {@link Interpolator} from an {@code R.interpolator} (or
     * {@code R.anim}) resource id. Only interpolators the framework can
     * construct are supported; anything else fails loudly rather than
     * silently easing wrong.
     */
    public static Interpolator loadInterpolator(Context context, int id) {
        Resources res = context.getResources();
        android.content.res.XmlResourceParser parser = res.getAnimation(id);
        try {
            int type;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                    && type != XmlPullParser.START_TAG) {
            }
            if (type != XmlPullParser.START_TAG) {
                throw new Resources.NotFoundException("no interpolator tag: " + id);
            }
            return createInterpolatorFromXml(res, parser);
        } catch (java.io.IOException | org.xmlpull.v1.XmlPullParserException e) {
            throw new Resources.NotFoundException("bad interpolator: " + id);
        } finally {
            parser.close();
        }
    }

    private static final int ATTR_FACTOR = 0x010101d3;
    private static final int ATTR_CONTROL_X1 = 0x010103fc;
    private static final int ATTR_CONTROL_Y1 = 0x010103fd;
    private static final int ATTR_CONTROL_X2 = 0x010103fe;
    private static final int ATTR_CONTROL_Y2 = 0x010103ff;

    /**
     * Instantiate the interpolator named by the parser's current start tag.
     * Attributes resolve through {@link Resources#obtainAttributes}, as in
     * AOSP, so references and any numeric type read as floats.
     */
    public static Interpolator createInterpolatorFromXml(Resources res, XmlPullParser parser) {
        AttributeSet attrs = (AttributeSet) parser;
        String tag = parser.getName();
        if ("linearInterpolator".equals(tag)) {
            return new LinearInterpolator();
        } else if ("accelerateInterpolator".equals(tag)) {
            return new AccelerateInterpolator(factor(res, attrs));
        } else if ("decelerateInterpolator".equals(tag)) {
            return new DecelerateInterpolator(factor(res, attrs));
        } else if ("accelerateDecelerateInterpolator".equals(tag)) {
            return new AccelerateDecelerateInterpolator();
        } else if ("pathInterpolator".equals(tag)) {
            android.content.res.TypedArray a = res.obtainAttributes(attrs,
                    new int[] {ATTR_CONTROL_X1, ATTR_CONTROL_Y1, ATTR_CONTROL_X2, ATTR_CONTROL_Y2});
            float x1 = a.getFloat(0, 0.0f);
            float y1 = a.getFloat(1, 0.0f);
            boolean cubic = a.hasValue(2) || a.hasValue(3);
            float x2 = a.getFloat(2, 1.0f);
            float y2 = a.getFloat(3, 1.0f);
            a.recycle();
            if (cubic) return new PathInterpolator(x1, y1, x2, y2);
            // Quadratic curve through (x1, y1), raised to the equivalent cubic.
            return new PathInterpolator(2 * x1 / 3, 2 * y1 / 3,
                    (1 + 2 * x1) / 3, (1 + 2 * y1) / 3);
        }
        throw new Resources.NotFoundException("unsupported interpolator: " + tag);
    }

    private static float factor(Resources res, AttributeSet attrs) {
        android.content.res.TypedArray a = res.obtainAttributes(attrs, new int[] {ATTR_FACTOR});
        float factor = a.getFloat(0, 1.0f);
        a.recycle();
        return factor;
    }
}
