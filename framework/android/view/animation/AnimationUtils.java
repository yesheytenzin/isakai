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

    /** Instantiate the interpolator named by the parser's current start tag. */
    public static Interpolator createInterpolatorFromXml(Resources res, XmlPullParser parser) {
        AttributeSet attrs = (AttributeSet) parser;
        String tag = parser.getName();
        String android = "http://schemas.android.com/apk/res/android";
        if ("linearInterpolator".equals(tag)) {
            return new LinearInterpolator();
        } else if ("accelerateInterpolator".equals(tag)) {
            return new AccelerateInterpolator(
                    attrs.getAttributeFloatValue(android, "factor", 1.0f));
        } else if ("decelerateInterpolator".equals(tag)) {
            return new DecelerateInterpolator(
                    attrs.getAttributeFloatValue(android, "factor", 1.0f));
        } else if ("accelerateDecelerateInterpolator".equals(tag)) {
            return new AccelerateDecelerateInterpolator();
        } else if ("pathInterpolator".equals(tag)) {
            return new PathInterpolator(
                    attrs.getAttributeFloatValue(android, "controlX1", 0.0f),
                    attrs.getAttributeFloatValue(android, "controlY1", 0.0f),
                    attrs.getAttributeFloatValue(android, "controlX2", 1.0f),
                    attrs.getAttributeFloatValue(android, "controlY2", 1.0f));
        }
        throw new Resources.NotFoundException("unsupported interpolator: " + tag);
    }
}
