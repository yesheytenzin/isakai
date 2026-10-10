// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

import android.content.Context;
import java.io.IOException;
import java.lang.reflect.Constructor;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Layout inflater: walks the binary-XML {@link
 * android.content.res.XmlResourceParser} for a layout id, builds the view
 * tree through the Factory/Factory2 chain then a framework widget factory,
 * applies a small set of attributes (id/text/hint/orientation/layout_*,
 * visibility, onClick reflection), and attaches per attachToRoot.
 *
 * <p>Unknown tags (androidx/custom views) resolve by reflection against the
 * app class loader; when that fails a {@link android.widget.FrameLayout}
 * placeholder keeps the subtree position so nested known ids stay findable.
 * IO/parse failures are fail-closed via {@link
 * android.util.AndroidRuntimeException}.
 */
public abstract class LayoutInflater {
    public interface Factory {
        View onCreateView(String name, Context context, android.util.AttributeSet attrs);
    }

    public interface Factory2 extends Factory {
        View onCreateView(View parent, String name, Context context, android.util.AttributeSet attrs);
    }

    protected final Context mContext;
    private Factory mFactory;
    private Factory2 mFactory2;
    private Factory2 mPrivateFactory;

    protected LayoutInflater(Context context) {
        if (context == null) throw new IllegalArgumentException("context is null");
        mContext = context;
    }

    /** Compatibility no-arg shape; assigns no context (inflation requires one). */
    protected LayoutInflater() {
        mContext = null;
    }

    private static final class BasicInflater extends LayoutInflater {
        BasicInflater(Context context) {
            super(context);
        }
    }

    public static LayoutInflater from(Context context) {
        return new BasicInflater(context);
    }

    public LayoutInflater cloneInContext(Context newContext) {
        BasicInflater copy = new BasicInflater(newContext);
        copy.setFactory(mFactory);
        if (mFactory2 != null) copy.setFactory2(mFactory2);
        copy.setPrivateFactory(mPrivateFactory);
        return copy;
    }

    public Factory getFactory() { return mFactory; }
    public Factory2 getFactory2() { return mFactory2; }
    /** @hide private factory installed by the framework/AppCompat delegate. */
    public Factory2 getPrivateFactory() { return mPrivateFactory; }
    public void setFactory(Factory factory) { mFactory = factory; }
    public void setFactory2(Factory2 factory) {
        mFactory2 = factory;
        mFactory = factory;
    }
    /** @hide */
    public void setPrivateFactory(Factory2 factory) { mPrivateFactory = factory; }
    public Context getContext() { return mContext; }

    /** Hook for subclasses/delegates; default honors the factory chain. */
    protected View onCreateView(View parent, String name, Context context,
            android.util.AttributeSet attrs) {
        return onCreateView(name, context, attrs);
    }

    protected View onCreateView(String name, Context context,
            android.util.AttributeSet attrs) {
        return createView(name, null, attrs);
    }
    // (abstract in AOSP; concrete here: resource-backed via Resources.getLayout)
    public View inflate(int resource, ViewGroup root, boolean attachToRoot) {
        if (mContext == null) {
            throw new android.util.AndroidRuntimeException("inflate: no context");
        }
        android.content.res.XmlResourceParser parser = null;
        try {
            parser = mContext.getResources().getLayout(resource);
        } catch (RuntimeException e) {
            throw new android.util.AndroidRuntimeException(
                    "inflate: bad layout id 0x" + Integer.toHexString(resource), e);
        }
        try {
            return inflate(parser, root, attachToRoot);
        } finally {
            if (parser != null) {
                try {
                    parser.close();
                } catch (Exception e) {
                }
            }
        }
    }


    public View inflate(int resource, ViewGroup root) {
        return inflate(resource, root, root != null);
    }

    public View inflate(XmlPullParser parser, ViewGroup root) {
        return inflate(parser, root, root != null);
    }

    public View inflate(XmlPullParser parser, ViewGroup root, boolean attachToRoot) {
        if (parser == null) {
            throw new android.util.AndroidRuntimeException("inflate: parser is null");
        }
        android.util.AttributeSet attrs = attrsOf(parser);
        try {
            int type;
            while ((type = parser.next()) != XmlPullParser.START_TAG
                    && type != XmlPullParser.END_DOCUMENT) {
            }
            if (type != XmlPullParser.START_TAG) {
                throw new android.util.AndroidRuntimeException("inflate: no start tag found");
            }
            String name = parser.getName();
            if ("merge".equals(name)) {
                if (root == null || !attachToRoot) {
                    throw new android.util.AndroidRuntimeException(
                            "inflate: <merge> requires a non-null root and attachToRoot=true");
                }
                rInflate(parser, root, attrsOf(parser), false);
                root.onFinishInflate();
                return root;
            }
            View temp = createViewFromTag(root, name, attrs, false);
            ViewGroup.LayoutParams params = null;
            if (root != null) {
                params = layoutParamsOf(temp);
            }
            rInflateChildren(parser, temp, attrsOf(parser));
            temp.onFinishInflate();
            if (root != null && attachToRoot) {
                if (params != null) temp.setLayoutParams(params);
                root.addView(temp);
                return root;
            }
            if (root != null && params != null) {
                temp.setLayoutParams(params);
            }
            return temp;
        } catch (XmlPullParserException | IOException e) {
            throw new android.util.AndroidRuntimeException("inflate: " + e.getMessage(), e);
        }
    }

    private void rInflate(XmlPullParser parser, ViewGroup parent,
            android.util.AttributeSet attrs, boolean finishParent)
            throws XmlPullParserException, IOException {
        int depth = parser.getDepth();
        int type;
        while (((type = parser.next()) != XmlPullParser.END_TAG
                || parser.getDepth() > depth)
                && type != XmlPullParser.END_DOCUMENT) {
            if (type != XmlPullParser.START_TAG) continue;
            String name = parser.getName();
            android.util.AttributeSet childAttrs = attrsOf(parser);
            if ("requestFocus".equals(name)) {
                skipSubtree(parser);
                continue;
            }
            if ("include".equals(name)) {
                int layout = attrRes(childAttrs, "layout", 0);
                if (layout != 0) {
                    inflate(layout, parent, true);
                } else {
                    android.util.Log.w("LayoutInflater",
                            "skip <include> without layout");
                }
                skipSubtree(parser);
                continue;
            }
            if ("merge".equals(name)) {
                rInflate(parser, parent, childAttrs, false);
                continue;
            }
            View child = createViewFromTag(parent, name, childAttrs, false);
            ViewGroup.LayoutParams params = layoutParamsOf(child);
            rInflateChildren(parser, child, attrsOf(parser));
            child.onFinishInflate();
            if (params != null) child.setLayoutParams(params);
            parent.addView(child);
        }
    }

    /** Consume the rest of the current element including nested children. */
    private void skipSubtree(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        int depth = parser.getDepth();
        int type;
        while (((type = parser.next()) != XmlPullParser.END_TAG
                || parser.getDepth() > depth)
                && type != XmlPullParser.END_DOCUMENT) {
        }
    }

    private void rInflateChildren(XmlPullParser parser, View parent,
            android.util.AttributeSet attrs)
            throws XmlPullParserException, IOException {
        if (!(parent instanceof ViewGroup)) {
            skipSubtree(parser);
            return;
        }
        rInflate(parser, (ViewGroup) parent, attrsOf(parser), true);
    }

    private android.util.AttributeSet attrsOf(XmlPullParser parser) {
        if (parser instanceof android.util.AttributeSet) {
            return (android.util.AttributeSet) parser;
        }
        return null;
    }

    protected View createViewFromTag(View parent, String name,
            android.util.AttributeSet attrs, boolean ignoreThemeAttr) {
        View view = null;
        if (mPrivateFactory != null) {
            try {
                view = mPrivateFactory.onCreateView(parent, name, mContext, attrs);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                android.util.Log.w("LayoutInflater", "private factory failed for " + name);
            }
        }
        if (view == null && mFactory2 != null) {
            try {
                view = mFactory2.onCreateView(parent, name, mContext, attrs);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                android.util.Log.w("LayoutInflater", "factory2 failed for " + name);
            }
        } else if (view == null && mFactory != null) {
            try {
                view = mFactory.onCreateView(name, mContext, attrs);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                android.util.Log.w("LayoutInflater", "factory failed for " + name);
            }
        }
        if (view == null && mContext instanceof android.app.Activity) {
            android.app.Activity act = (android.app.Activity) mContext;
            try {
                view = act.onCreateView(parent, name, mContext, attrs);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                view = null;
            }
            if (view == null) {
                try {
                    view = act.onCreateView(name, mContext, attrs);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    view = null;
                }
            }
        }
        if (view == null) {
            try {
                view = onCreateView(parent, name, mContext, attrs);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                view = null;
            }
        }
        if (view == null) {
            view = placeholder(name);
        }
        applyAttributes(view, attrs);
        return view;
    }

    protected View createView(String name, String prefix, android.util.AttributeSet attrs) {
        if (name == null) return null;
        if ("view".equals(name)) {
            String cls = attrs == null ? null : attrRaw(attrs, "class");
            if (cls == null) return null;
            return createView(cls, null, attrs);
        }
        String full = prefix != null ? prefix + name : name;
        View v = frameworkView(full, attrs);
        if (v != null) return v;
        if (full.indexOf('.') < 0) {
            v = frameworkView("android.widget." + full, attrs);
            if (v != null) return v;
            v = frameworkView("android.view." + full, attrs);
            if (v != null) return v;
        }
        v = reflectView(full, attrs);
        return v;
    }

    private View frameworkView(String full, android.util.AttributeSet attrs) {
        Context c = mContext;
        if ("android.view.View".equals(full) || "View".equals(full)) {
            return new View(c, attrs);
        }
        if ("android.view.ViewGroup".equals(full) || "ViewGroup".equals(full)) {
            return new ViewGroup(c, attrs) {};
        }
        if ("android.view.SurfaceView".equals(full) || "SurfaceView".equals(full)) {
            return new View(c, attrs);
        }
        String cls = full.startsWith("android.widget.") ? full.substring(15) : full;
        if (full.startsWith("android.widget.") || full.indexOf('.') < 0) {
            if ("TextView".equals(cls)) return new android.widget.TextView(c, attrs);
            if ("Button".equals(cls)) return new android.widget.Button(c, attrs);
            if ("EditText".equals(cls)) return new android.widget.EditText(c, attrs);
            if ("ImageView".equals(cls)) return new android.widget.ImageView(c, attrs);
            if ("ImageButton".equals(cls)) return new android.widget.ImageButton(c, attrs);
            if ("LinearLayout".equals(cls)) return new android.widget.LinearLayout(c, attrs);
            if ("FrameLayout".equals(cls)) return new android.widget.FrameLayout(c, attrs);
            if ("TableLayout".equals(cls)) return new android.widget.TableLayout(c, attrs);
            if ("TableRow".equals(cls)) return new android.widget.TableRow(c, attrs);
            if ("HorizontalScrollView".equals(cls)) {
                return new android.widget.HorizontalScrollView(c, attrs);
            }
            if ("ScrollView".equals(cls)) {
                return new android.widget.ScrollView(c, attrs);
            }
            if ("RelativeLayout".equals(cls)) {
                return new android.widget.RelativeLayout(c, attrs);
            }
            if ("Switch".equals(cls)) return new android.widget.Switch(c, attrs);
            if ("Chronometer".equals(cls)) {
                return new android.widget.Chronometer(c, attrs);
            }
            if ("DateTimeView".equals(cls)) {
                return new android.widget.DateTimeView(c, attrs);
            }
            if ("android.widget.Space".equals(full)) return new android.widget.Space(c, attrs);
            if ("Space".equals(cls)) return new android.widget.Space(c, attrs);
            if ("ViewStub".equals(cls)) {
                return new ViewStub(c, attrs);
            }
            if ("ProgressBar".equals(cls)) return new android.widget.ProgressBar(c, attrs);
            if ("SeekBar".equals(cls)) return new android.widget.SeekBar(c, attrs);
            if ("RatingBar".equals(cls)) return new android.widget.RatingBar(c, attrs);
            if ("CheckBox".equals(cls)) return new android.widget.CheckBox(c, attrs);
            if ("RadioButton".equals(cls)) return new android.widget.RadioButton(c, attrs);
            if ("CompoundButton".equals(cls)) return new android.widget.CompoundButton(c, attrs);
            if ("ToggleButton".equals(cls)) return new android.widget.ToggleButton(c, attrs);
            if ("CheckedTextView".equals(cls)) {
                return new android.widget.CheckedTextView(c, attrs);
            }
            if ("AutoCompleteTextView".equals(cls)) {
                return new android.widget.AutoCompleteTextView(c, attrs);
            }
            if ("MultiAutoCompleteTextView".equals(cls)) {
                return new android.widget.MultiAutoCompleteTextView(c, attrs);
            }
            if ("Spinner".equals(cls)) return new android.widget.Spinner(c, attrs);
            if ("ListView".equals(cls)) return new android.widget.ListView(c, attrs);
            if ("GridView".equals(cls)) return new android.widget.GridView(c, attrs);
            if (full.indexOf('.') < 0) return null;
            return null;
        }
        return null;
    }

    private View reflectView(String full, android.util.AttributeSet attrs) {
        ClassLoader loader = mContext != null ? mContext.getClassLoader()
                : LayoutInflater.class.getClassLoader();
        try {
            Class<?> clazz = Class.forName(full, false, loader);
            if (!View.class.isAssignableFrom(clazz)) return null;
            try {
                Constructor<?> ctor = clazz.getConstructor(
                        Context.class, android.util.AttributeSet.class);
                return (View) ctor.newInstance(mContext, attrs);
            } catch (NoSuchMethodException e) {
                try {
                    Constructor<?> ctor = clazz.getConstructor(Context.class);
                    View v = (View) ctor.newInstance(mContext);
                    return v;
                } catch (NoSuchMethodException e2) {
                    Constructor<?> ctor = clazz.getConstructor();
                    return (View) ctor.newInstance();
                }
            }
        } catch (Exception e) {
            return null;
        }
    }

    private View placeholder(String name) {
        android.util.Log.w("LayoutInflater", "unknown view " + name + "; using FrameLayout");
        return new android.widget.FrameLayout(mContext);
    }

    private ViewGroup.LayoutParams layoutParamsOf(View child) {
        int w = ViewGroup.LayoutParams.WRAP_CONTENT;
        int h = ViewGroup.LayoutParams.WRAP_CONTENT;
        android.util.AttributeSet attrs = null;
        return new ViewGroup.LayoutParams(w, h);
    }

    private void applyAttributes(View view, android.util.AttributeSet attrs) {
        if (view == null || attrs == null) return;
        int id = attrRes(attrs, "id", View.NO_ID);
        if (id != View.NO_ID) {
            view.setId(id);
        } else {
            String rawId = attrRaw(attrs, "id");
            int parsed = parseIdString(rawId);
            if (parsed != View.NO_ID) view.setId(parsed);
        }
        String vis = attrRaw(attrs, "visibility");
        if (vis != null) {
            if ("gone".equalsIgnoreCase(vis)) view.setVisibility(View.GONE);
            else if ("invisible".equalsIgnoreCase(vis)
                    || "hidden".equalsIgnoreCase(vis)) view.setVisibility(View.INVISIBLE);
            else if ("visible".equalsIgnoreCase(vis)) view.setVisibility(View.VISIBLE);
        }
        String desc = attrRaw(attrs, "contentDescription");
        if (desc != null) view.setContentDescription(desc);
        int enabled = attrBool(attrs, "enabled", -1);
        if (enabled >= 0) view.setEnabled(enabled != 0);
        int clickable = attrBool(attrs, "clickable", -1);
        if (clickable >= 0) view.setClickable(clickable != 0);
        int focusable = attrBool(attrs, "focusable", -1);
        if (focusable >= 0) view.setFocusable(focusable != 0);
        if (view instanceof android.widget.LinearLayout) {
            String o = attrRaw(attrs, "orientation");
            if ("horizontal".equalsIgnoreCase(o)) {
                ((android.widget.LinearLayout) view).setOrientation(
                        android.widget.LinearLayout.HORIZONTAL);
            } else if ("vertical".equalsIgnoreCase(o)) {
                ((android.widget.LinearLayout) view).setOrientation(
                        android.widget.LinearLayout.VERTICAL);
            } else if (o != null) {
                try {
                    ((android.widget.LinearLayout) view).setOrientation(Integer.parseInt(o));
                } catch (NumberFormatException e) {
                }
            }
        }
        if (view instanceof android.widget.TextView) {
            android.widget.TextView tv = (android.widget.TextView) view;
            int textRes = attrRes(attrs, "text", 0);
            String raw = attrRaw(attrs, "text");
            if (textRes != 0) {
                try {
                    tv.setText(mContext.getResources().getText(textRes));
                    raw = null;
                } catch (RuntimeException e) {
                }
            }
            if (raw != null) tv.setText(raw);
            int hintRes = attrRes(attrs, "hint", 0);
            String rawHint = attrRaw(attrs, "hint");
            if (hintRes != 0) {
                try {
                    tv.setHint(mContext.getResources().getText(hintRes));
                    rawHint = null;
                } catch (RuntimeException e) {
                }
            }
            if (rawHint != null) tv.setHint(rawHint);
        }
        String click = attrRaw(attrs, "onClick");
        if (click != null && !click.isEmpty()) {
            bindOnClick(view, click);
        }
        int w = parseLayoutDim(attrRaw(attrs, "layout_width"),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        int h = parseLayoutDim(attrRaw(attrs, "layout_height"),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        view.setLayoutParams(new ViewGroup.LayoutParams(w, h));
    }

    private void bindOnClick(View view, final String method) {
        final Context target = mContext;
        if (target == null) return;
        view.setClickable(true);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    java.lang.reflect.Method m = target.getClass().getMethod(
                            method, View.class);
                    m.invoke(target, v);
                } catch (Exception e) {
                    android.util.Log.w("LayoutInflater",
                            "onClick " + method + " failed: " + e.getMessage());
                }
            }
        });
    }

    private static String attrRaw(android.util.AttributeSet attrs, String name) {
        if (attrs == null || name == null) return null;
        String v = safeValue(attrs, null, name);
        if (v != null) return v;
        v = safeValue(attrs, "http://schemas.android.com/apk/res/android", name);
        if (v != null) return v;
        try {
            int n = attrs.getAttributeCount();
            for (int i = 0; i < n; i++) {
                String an;
                try {
                    an = attrs.getAttributeName(i);
                } catch (RuntimeException e) {
                    continue;
                }
                if (an == null) continue;
                if (an.equals(name) || an.endsWith(":" + name)) {
                    try {
                        return attrs.getAttributeValue(i);
                    } catch (RuntimeException e) {
                        return null;
                    }
                }
            }
        } catch (RuntimeException e) {
        }
        return null;
    }

    private static String safeValue(android.util.AttributeSet attrs,
            String ns, String name) {
        try {
            return attrs.getAttributeValue(ns, name);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static int attrRes(android.util.AttributeSet attrs, String name, int def) {
        if (attrs == null) return def;
        try {
            int v = attrs.getAttributeResourceValue(null, name, def);
            if (v != def) return v;
        } catch (RuntimeException e) {
        }
        try {
            return attrs.getAttributeResourceValue(
                    "http://schemas.android.com/apk/res/android", name, def);
        } catch (RuntimeException e) {
            return def;
        }
    }

    private static int attrBool(android.util.AttributeSet attrs, String name, int def) {
        if (attrs == null) return def;
        try {
            boolean d = def != 0;
            boolean v = attrs.getAttributeBooleanValue(null, name, d);
            String raw = attrRaw(attrs, name);
            if (raw == null && v == d) {
                v = attrs.getAttributeBooleanValue(
                        "http://schemas.android.com/apk/res/android", name, d);
                raw = "ns";
            }
            if (raw == null && v == d) return def;
            return v ? 1 : 0;
        } catch (RuntimeException e) {
            return def;
        }
    }

    private static int parseIdString(String raw) {
        if (raw == null) return View.NO_ID;
        raw = raw.trim();
        if (raw.startsWith("0x") || raw.startsWith("0X")) {
            try {
                return (int) Long.parseLong(raw.substring(2), 16);
            } catch (NumberFormatException e) {
                return View.NO_ID;
            }
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return View.NO_ID;
        }
    }

    private static int parseLayoutDim(String raw, int def) {
        if (raw == null) return def;
        raw = raw.trim();
        if ("match_parent".equalsIgnoreCase(raw) || "fill_parent".equalsIgnoreCase(raw)
                || "match_constraint".equalsIgnoreCase(raw)) {
            return ViewGroup.LayoutParams.MATCH_PARENT;
        }
        if ("wrap_content".equalsIgnoreCase(raw)) {
            return ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
        }
        int i = 0;
        while (i < raw.length()
                && (Character.isDigit(raw.charAt(i)) || raw.charAt(i) == '-')) {
            i++;
        }
        if (i > 0) {
            try {
                return Integer.parseInt(raw.substring(0, i));
            } catch (NumberFormatException e) {
            }
        }
        return def;
    }
}
