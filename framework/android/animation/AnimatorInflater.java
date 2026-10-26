// SPDX-License-Identifier: GPL-3.0-or-later
package android.animation;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.util.AttributeSet;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;

/** Loads the synchronous subset of Android animator XML supported by Isakai. */
public final class AnimatorInflater {
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private AnimatorInflater() {}

    public static Animator loadAnimator(Context context, int id) {
        if (context == null) throw new NullPointerException("context");
        return loadAnimator(context.getResources(), null, id);
    }

    public static Animator loadAnimator(Resources resources, int id) {
        return loadAnimator(resources, null, id);
    }

    /**
     * State-list animators are recorded by {@link StateListAnimator} but not
     * played back yet, so the resource is validated only by loading.
     * shortcut: no state matching/playback; parse items when playback lands.
     */
    public static StateListAnimator loadStateListAnimator(Context context, int id) {
        if (context == null) throw new NullPointerException("context");
        context.getResources().getAnimation(id).close();
        return new StateListAnimator();
    }

    public static Animator loadAnimator(Resources resources, Resources.Theme theme, int id) {
        if (resources == null) throw new NullPointerException("resources");
        XmlResourceParser parser = resources.getAnimation(id);
        try {
            AttributeSet attrs = Xml.asAttributeSet(parser);
            int event;
            do {
                event = parser.next();
            } while (event != XmlPullParser.START_TAG && event != XmlPullParser.END_DOCUMENT);
            if (event != XmlPullParser.START_TAG) throw failure(id, "empty animator document", null);
            Animator animator = parseNode(parser, attrs, id);
            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    throw failure(id, "multiple root animator elements", null);
                }
            }
            return animator;
        } catch (Resources.NotFoundException e) {
            throw e;
        } catch (java.io.IOException | org.xmlpull.v1.XmlPullParserException e) {
            throw failure(id, "invalid animator XML", e);
        } finally {
            parser.close();
        }
    }

    private static Animator parseNode(XmlPullParser parser, AttributeSet attrs, int id)
            throws java.io.IOException, org.xmlpull.v1.XmlPullParserException {
        String name = parser.getName();
        if ("animator".equals(name)) return parseValueAnimator(attrs, id, false);
        if ("objectAnimator".equals(name)) return parseValueAnimator(attrs, id, true);
        if ("set".equals(name)) return parseSet(parser, attrs, id);
        throw failure(id, "unsupported animator element " + name, null);
    }

    private static Animator parseValueAnimator(AttributeSet attrs, int id, boolean object) {
        String from = required(attrs, "valueFrom", id);
        String to = required(attrs, "valueTo", id);
        String type = value(attrs, "valueType");
        boolean integer = "intType".equals(type) || (type == null && isInteger(from) && isInteger(to));
        if (type != null && !integer && !"floatType".equals(type)) {
            throw failure(id, "unsupported valueType " + type, null);
        }
        Animator animator;
        String property = value(attrs, "propertyName");
        if (object) {
            if (property == null || property.isEmpty()) throw failure(id, "missing propertyName", null);
            animator = integer
                    ? ObjectAnimator.ofInt(null, property, parseInt(from, id), parseInt(to, id))
                    : ObjectAnimator.ofFloat(null, property, parseFloat(from, id), parseFloat(to, id));
        } else {
            animator = integer
                    ? ValueAnimator.ofInt(parseInt(from, id), parseInt(to, id))
                    : ValueAnimator.ofFloat(parseFloat(from, id), parseFloat(to, id));
        }
        configure(animator, attrs, id);
        return animator;
    }

    private static Animator parseSet(XmlPullParser parser, AttributeSet attrs, int id)
            throws java.io.IOException, org.xmlpull.v1.XmlPullParserException {
        String ordering = value(attrs, "ordering");
        boolean sequential = "sequentially".equals(ordering);
        if (ordering != null && !sequential && !"together".equals(ordering)) {
            throw failure(id, "unsupported set ordering " + ordering, null);
        }
        java.util.ArrayList<Animator> children = new java.util.ArrayList<>();
        int depth = parser.getDepth();
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.END_TAG && parser.getDepth() == depth) break;
            if (event == XmlPullParser.START_TAG) {
                children.add(parseNode(parser, Xml.asAttributeSet(parser), id));
            } else if (event == XmlPullParser.TEXT && parser.getText().trim().length() != 0) {
                throw failure(id, "text inside animator set", null);
            }
        }
        if (children.isEmpty()) throw failure(id, "empty animator set", null);
        AnimatorSet set = new AnimatorSet();
        if (sequential) set.playSequentially(children.toArray(new Animator[0]));
        else set.playTogether(children.toArray(new Animator[0]));
        configure(set, attrs, id);
        return set;
    }

    private static void configure(Animator animator, AttributeSet attrs, int id) {
        String duration = value(attrs, "duration");
        String delay = value(attrs, "startOffset");
        String repeat = value(attrs, "repeatCount");
        String mode = value(attrs, "repeatMode");
        if (duration != null) animator.setDuration(nonNegativeLong(duration, id, "duration"));
        if (delay != null) animator.setStartDelay(nonNegativeLong(delay, id, "startOffset"));
        if (animator instanceof ValueAnimator) {
            ValueAnimator valueAnimator = (ValueAnimator) animator;
            if (repeat != null) {
                int count = "infinite".equals(repeat) ? ValueAnimator.INFINITE : parseInt(repeat, id);
                if (count < 0 && count != ValueAnimator.INFINITE) throw failure(id, "invalid repeatCount", null);
                valueAnimator.setRepeatCount(count);
            }
            if (mode != null) {
                if ("restart".equals(mode)) valueAnimator.setRepeatMode(ValueAnimator.RESTART);
                else if ("reverse".equals(mode)) valueAnimator.setRepeatMode(ValueAnimator.REVERSE);
                else throw failure(id, "invalid repeatMode", null);
            }
        }
    }

    private static String required(AttributeSet attrs, String name, int id) {
        String value = value(attrs, name);
        if (value == null || value.isEmpty()) throw failure(id, "missing " + name, null);
        return value;
    }

    private static String value(AttributeSet attrs, String name) {
        return attrs.getAttributeValue(ANDROID_NS, name);
    }

    private static boolean isInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static int parseInt(String value, int id) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw failure(id, "invalid integer value", e);
        }
    }

    private static float parseFloat(String value, int id) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            throw failure(id, "invalid float value", e);
        }
    }

    private static long nonNegativeLong(String value, int id, String name) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 0) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException e) {
            throw failure(id, "invalid " + name, e);
        }
    }

    private static Resources.NotFoundException failure(int id, String message, Throwable cause) {
        Resources.NotFoundException error =
                new Resources.NotFoundException(message + " for 0x" + Integer.toHexString(id));
        if (cause != null) error.initCause(cause);
        return error;
    }
}
