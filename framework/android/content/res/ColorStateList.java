// SPDX-License-Identifier: GPL-3.0-or-later
package android.content.res;

import android.graphics.Color;
import android.util.AttributeSet;
import org.xmlpull.v1.XmlPullParser;

/**
 * State-to-color mapping with default fallback.
 *
 * <p>Parses {@code <selector>} XML: ordered {@code <item>} elements with
 * {@code android:color} plus {@code android:state_*} booleans. Colors may be
 * literals ({@code #rgb/#argb/#rrggbb/#aarrggbb}), named {@code @color} or
 * numeric {@code @0x...} and {@code ?attr} references resolved through {@link Resources};
 * unresolvable references throw {@link Resources.NotFoundException} (fail-closed, never a fallback color).
 */
public class ColorStateList {
    // AOSP android.R.attr ids for state attributes (no R table in this runtime).
    public static final int STATE_ACTIVE = 16842920;
    public static final int STATE_ACTIVATED = 16843518;
    public static final int STATE_CHECKABLE = 16842911;
    public static final int STATE_CHECKED = 16842912;
    public static final int STATE_ENABLED = 16842910;
    public static final int STATE_FOCUSED = 16842908;
    public static final int STATE_HOVERED = 16843623;
    public static final int STATE_PRESSED = 16842919;
    public static final int STATE_SELECTED = 16842913;
    public static final int STATE_WINDOW_FOCUSED = 16842909;

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private final int[][] states;
    private final int[] colors;

    public ColorStateList(int[][] states, int[] colors) {
        this.states = states == null ? new int[0][] : states.clone();
        this.colors = colors == null ? new int[0] : colors.clone();
    }

    public static ColorStateList valueOf(int color) {
        return new ColorStateList(new int[][]{new int[0]}, new int[]{color});
    }

    public boolean isStateful() {
        return states.length > 1;
    }

    public int getDefaultColor() {
        for (int i = 0; i < states.length && i < colors.length; i++) {
            if (states[i].length == 0) return colors[i];
        }
        return colors.length > 0 ? colors[colors.length - 1] : 0xFF000000;
    }

    public int getColorForState(int[] stateSet, int defaultColor) {
        for (int i = 0; i < states.length && i < colors.length; i++) {
            if (matches(states[i], stateSet)) return colors[i];
        }
        return defaultColor;
    }

    private static boolean matches(int[] want, int[] have) {
        if (have == null) have = new int[0];
        for (int w : want) {
            boolean neg = w < 0;
            int attr = neg ? -w : w;
            boolean found = false;
            for (int h : have) {
                if (h == attr) {
                    found = true;
                    break;
                }
            }
            if (found == neg) return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "ColorStateList{states=" + states.length + "}";
    }

    /**
     * Parse a {@code <selector>} from the parser positioned at its start tag.
     * Fail-closed: malformed XML or an unresolvable color reference throws.
     */
    public static ColorStateList createFromXml(Resources r, XmlPullParser parser)
            throws java.io.IOException, org.xmlpull.v1.XmlPullParserException {
        return createFromXml(r, parser, null);
    }

    public static ColorStateList createFromXml(Resources r, XmlPullParser parser,
            Resources.Theme theme)
            throws java.io.IOException, org.xmlpull.v1.XmlPullParserException {
        if (parser == null) throw new org.xmlpull.v1.XmlPullParserException("null parser");
        int type = parser.getEventType();
        while (type != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT) {
            type = parser.next();
        }
        if (type != XmlPullParser.START_TAG || !"selector".equals(parser.getName())) {
            throw new org.xmlpull.v1.XmlPullParserException("expected <selector>");
        }
        java.util.ArrayList<int[]> stateList = new java.util.ArrayList<>();
        java.util.ArrayList<Integer> colorList = new java.util.ArrayList<>();
        while (true) {
            type = parser.next();
            if (type == XmlPullParser.END_DOCUMENT) break;
            if (type != XmlPullParser.START_TAG) {
                if (type == XmlPullParser.END_TAG) break;
                continue;
            }
            if (!"item".equals(parser.getName())) {
                skipElement(parser);
                continue;
            }
            // Re-read attrs here through a live bridge: the StAX parser does
            // not implement AttributeSet, and it now points at the <item> tag.
            AttributeSet itemAttrs = bridgeAttrs(parser);
            stateList.add(extractStateSet(itemAttrs));
            colorList.add(resolveItemColor(r, itemAttrs, theme));
            skipElement(parser);
        }
        int n = colorList.size();
        int[][] states = new int[n][];
        int[] colors = new int[n];
        for (int i = 0; i < n; i++) {
            states[i] = stateList.get(i);
            colors[i] = colorList.get(i);
        }
        return new ColorStateList(states, colors);
    }

    /** State set from {@code android:state_*} booleans on an attribute set; unknown states skipped. */
    public static int[] stateSetFromAttributeSet(AttributeSet attrs) {
        return extractStateSet(attrs);
    }

    private static int[] extractStateSet(AttributeSet attrs) {
        if (attrs == null) return new int[0];
        int[] tmp = new int[10];
        int count = 0;
        for (int i = 0; i < attrs.getAttributeCount(); i++) {
            String name = attrs.getAttributeName(i);
            if (name == null || !name.startsWith("state_")) continue;
            int id = stateAttrId(name);
            if (id == 0) continue;
            if (!"true".equalsIgnoreCase(attrs.getAttributeValue(i))) id = -id;
            if (count >= tmp.length) tmp = java.util.Arrays.copyOf(tmp, tmp.length * 2);
            tmp[count++] = id;
        }
        return java.util.Arrays.copyOf(tmp, count);
    }

    private static int resolveItemColor(Resources r, AttributeSet attrs, Resources.Theme theme) {
        // Unprefixed first: the StAX parser matches on exact namespace URI and
        // reports no namespace for plain "color".
        String raw = attrs == null ? null : attrs.getAttributeValue(null, "color");
        if (raw == null && attrs != null) raw = attrs.getAttributeValue("", "color");
        if (raw == null && attrs != null) raw = attrs.getAttributeValue(ANDROID_NS, "color");
        if (raw == null || raw.isEmpty()) {
            throw new Resources.NotFoundException("selector <item> missing android:color");
        }
        return resolveColor(r, raw.trim(), theme);
    }

    /**
     * Resolve a color literal or {@code @color}/{@code ?attr} reference.
     * References go through the public {@link Resources} lookup and throw
     * NotFoundException when unresolvable (fail-closed, never a fallback).
     */
    public static int resolveColor(Resources r, String raw, Resources.Theme theme) {
        if (raw == null || raw.isEmpty()) {
            throw new Resources.NotFoundException("empty color");
        }
        char c = raw.charAt(0);
        if (c == '#') return Color.parseColor(raw);
        if (r == null) throw new Resources.NotFoundException("no resource table: " + raw);
        if (c == '@') return resolveResourceRef(r, raw, theme);
        if (c == '?') return resolveAttrRef(r, raw, theme);
        return Color.parseColor(raw);
    }

    private static int resolveResourceRef(Resources r, String raw, Resources.Theme theme) {
        String ref = raw.substring(1);
        if (ref.startsWith("+")) ref = ref.substring(1);
        if (isNumericResourceId(ref)) {
            try {
                int id = ref.startsWith("0x") || ref.startsWith("0X")
                        ? (int) Long.parseLong(ref.substring(2), 16)
                        : Integer.parseInt(ref);
                return r.getColorStateList(id).getDefaultColor();
            } catch (NumberFormatException e) {
                throw new Resources.NotFoundException("invalid color resource: " + raw);
            }
        }
        // Forms: @color/name, @+color/name, @package:color/name.
        String pkg = null;
        String rest = ref;
        int colon = ref.indexOf(':');
        if (colon >= 0) {
            pkg = ref.substring(0, colon);
            rest = ref.substring(colon + 1);
        }
        String type = "color";
        String name = rest;
        int slash = rest.indexOf('/');
        if (slash >= 0) {
            type = rest.substring(0, slash);
            name = rest.substring(slash + 1);
        }
        if (!"color".equals(type) || name.isEmpty()) {
            throw new Resources.NotFoundException("unsupported color ref: " + raw);
        }
        int id = r.getIdentifier(name, "color", pkg);
        if (id == 0) throw new Resources.NotFoundException("no resource table: " + raw);
        return r.getColorStateList(id).getDefaultColor();
    }

    private static boolean isNumericResourceId(String ref) {
        return !ref.isEmpty() && Character.isDigit(ref.charAt(0));
    }
    private static int resolveAttrRef(Resources r, String raw, Resources.Theme theme) {
        // Forms: ?attr/name, ?package:attr/name.
        String ref = raw.substring(1);
        String pkg = null;
        String rest = ref;
        int colon = ref.indexOf(':');
        if (colon >= 0) {
            pkg = ref.substring(0, colon);
            rest = ref.substring(colon + 1);
        }
        String name = rest;
        int slash = rest.indexOf('/');
        if (slash >= 0) name = rest.substring(slash + 1);
        if (name.isEmpty()) throw new Resources.NotFoundException("bad attr ref: " + raw);
        int id = r.getIdentifier(name, "attr", pkg);
        android.util.TypedValue tv = new android.util.TypedValue();
        boolean resolved = id != 0 && theme != null && theme.resolveAttribute(id, tv, true);
        if (!resolved) throw new Resources.NotFoundException("no resource table: " + raw);
        if (tv.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT
                && tv.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) {
            return tv.data;
        }
        if (tv.string != null) return resolveColor(r, tv.string.toString(), theme);
        throw new Resources.NotFoundException("no resource table: " + raw);
    }

    private static int stateAttrId(String name) {
        switch (name) {
            case "state_active": return STATE_ACTIVE;
            case "state_activated": return STATE_ACTIVATED;
            case "state_checkable": return STATE_CHECKABLE;
            case "state_checked": return STATE_CHECKED;
            case "state_enabled": return STATE_ENABLED;
            case "state_focused": return STATE_FOCUSED;
            case "state_hovered": return STATE_HOVERED;
            case "state_pressed": return STATE_PRESSED;
            case "state_selected": return STATE_SELECTED;
            case "state_window_focused": return STATE_WINDOW_FOCUSED;
            default: return 0;
        }
    }

    /** Live AttributeSet adapter over a pull parser (Xml.asAttributeSet equivalent). */
    static AttributeSet bridgeAttrs(final XmlPullParser parser) {
        if (parser == null) return null;
        if (parser instanceof AttributeSet) return (AttributeSet) parser;
        return new AttributeSet() {
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

    /** Skip the rest of the current element with a manual depth counter. */
    private static void skipElement(XmlPullParser parser)
            throws java.io.IOException, org.xmlpull.v1.XmlPullParserException {
        if (parser.getEventType() != XmlPullParser.START_TAG) return;
        int depth = 1;
        while (depth > 0) {
            int t = parser.next();
            if (t == XmlPullParser.END_DOCUMENT) return;
            if (t == XmlPullParser.START_TAG) depth++;
            else if (t == XmlPullParser.END_TAG) depth--;
        }
    }
}
