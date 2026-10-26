// SPDX-License-Identifier: GPL-3.0-or-later
package android.content.res;

import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import java.io.IOException;
import java.io.InputStream;

/**
 * {@link ResourceTable} on AOSP libandroidfw: configuration best-match
 * selection, reference/theme/style resolution, binary XML and archive
 * assets all come from AssetManager2, as on Android.
 */
final class AndroidfwTable implements ResourceTable {
    final ResEngine engine;

    AndroidfwTable(ResEngine engine) {
        this.engine = engine;
    }

    @Override
    public TypedValue value(int id, boolean resolveRefs) {
        return engine.value(id, resolveRefs, 0);
    }

    @Override
    public String[] stringArray(int id) {
        TypedValue[] bag = engine.bag(id);
        if (bag == null) return null;
        String[] out = new String[bag.length];
        for (int i = 0; i < bag.length; i++) {
            TypedValue v = bag[i];
            out[i] = v.type == TypedValue.TYPE_STRING && v.string != null ? v.string.toString() : null;
        }
        return out;
    }

    @Override
    public int[] intArray(int id) {
        TypedValue[] bag = engine.bag(id);
        if (bag == null) return null;
        int[] out = new int[bag.length];
        for (int i = 0; i < bag.length; i++) {
            TypedValue v = bag[i];
            if (v.type >= TypedValue.TYPE_FIRST_INT && v.type <= TypedValue.TYPE_LAST_INT) out[i] = v.data;
        }
        return out;
    }

    @Override
    public String resourceName(int id) {
        return ResEngine.nResourceName(engine.ptr(), id);
    }

    @Override
    public int identifier(String name, String defType, String defPackage) {
        String ref = name.startsWith("@") ? name.substring(1) : name;
        if (ref.startsWith("+")) ref = ref.substring(1);
        if (ref.isEmpty() || ref.startsWith("?")) return 0;
        try {
            return Integer.parseInt(ref);
        } catch (NumberFormatException notNumeric) {
            return ResEngine.nIdentifier(engine.ptr(), ref, defType, defPackage);
        }
    }

    @Override
    public XmlResourceParser openXml(TypedValue file) {
        String path = file.string == null ? null : file.string.toString();
        if (file.type != TypedValue.TYPE_STRING || path == null) {
            throw new Resources.NotFoundException("not a file resource: 0x" + Integer.toHexString(file.resourceId));
        }
        ResEngine.XmlBlock block = engine.openXml(file.assetCookie, path);
        if (block == null) throw new Resources.NotFoundException("bad xml: " + path);
        return new ResXmlParser(block, path);
    }

    @Override
    public InputStream openFile(TypedValue file) throws IOException {
        String path = file.string == null ? null : file.string.toString();
        if (file.type != TypedValue.TYPE_STRING || path == null) throw new IOException("not a file resource");
        ResEngine.NativeAsset asset = engine.openAsset(path, ResEngine.ACCESS_STREAMING);
        if (asset == null) throw new java.io.FileNotFoundException(path);
        return new AssetManager.AssetInputStream(asset);
    }

    @Override
    public ThemeImpl newTheme() {
        return new NativeThemeImpl(engine.new NativeTheme());
    }

    @Override
    public void configure(Configuration config, DisplayMetrics metrics) {
        engine.setConfiguration(config, metrics, android.os.Build.VERSION.SDK_INT);
    }

    @Override
    public TypedArray obtainAttributes(Resources resources, AttributeSet set, int[] attrs) {
        if (attrs == null) attrs = new int[0];
        ResXmlParser parser = nativeParser(set);
        if (parser == null) return TypedArray.fromTag(resources, set, attrs);
        int[] values = new int[attrs.length * ResEngine.STYLE_NUM_ENTRIES];
        int[] indices = new int[attrs.length + 1];
        if (!ResEngine.nRetrieveAttributes(engine.ptr(), parser.nativeParser(), attrs.clone(), values, indices)) {
            throw new Resources.NotFoundException("attribute retrieval failed: " + set.getPositionDescription());
        }
        return TypedArray.fromStyle(resources, engine, parser.block, values, attrs.length);
    }

    @Override
    public AssetManager assets() {
        return new AssetManager(engine);
    }

    /** The AOSP XmlBlock.Parser case: a parser from this engine, still open. */
    private ResXmlParser nativeParser(AttributeSet set) {
        if (!(set instanceof ResXmlParser)) return null;
        ResXmlParser parser = (ResXmlParser) set;
        return parser.block.engine() == engine && parser.block.isOpen() ? parser : null;
    }

    private final class NativeThemeImpl implements ThemeImpl {
        final ResEngine.NativeTheme theme;

        NativeThemeImpl(ResEngine.NativeTheme theme) {
            this.theme = theme;
        }

        @Override
        public void applyStyle(int resId, boolean force) {
            theme.applyStyle(resId, force);
        }

        @Override
        public void setTo(ThemeImpl other) {
            if (other instanceof NativeThemeImpl) theme.setTo(((NativeThemeImpl) other).theme);
        }

        @Override
        public TypedValue attribute(int attrId) {
            return theme.attribute(attrId, false);
        }

        @Override
        public boolean resolveAttribute(int resId, TypedValue outValue, boolean resolveRefs) {
            TypedValue v = theme.attribute(resId, resolveRefs);
            if (v == null) return false;
            outValue.type = v.type;
            outValue.data = v.data;
            outValue.string = v.string;
            outValue.assetCookie = v.assetCookie;
            outValue.resourceId = v.resourceId;
            outValue.changingConfigurations = v.changingConfigurations;
            outValue.density = v.density;
            return true;
        }

        /**
         * AOSP ThemeImpl.obtainStyledAttributes: ApplyStyle over the tag,
         * its style= attribute, defStyleAttr, defStyleRes and the theme.
         * An AttributeSet that is not one of this engine's parsers carries no
         * binary attributes for ApplyStyle; its tag values are layered on top.
         */
        @Override
        public TypedArray obtainStyledAttributes(Resources resources, AttributeSet set, int[] attrs,
                int defStyleAttr, int defStyleRes) {
            if (attrs == null) attrs = new int[0];
            ResXmlParser parser = nativeParser(set);
            int[] values = new int[attrs.length * ResEngine.STYLE_NUM_ENTRIES];
            int[] indices = new int[attrs.length + 1];
            if (!ResEngine.nApplyStyle(theme.ptr(), parser == null ? 0 : parser.nativeParser(),
                    defStyleAttr, defStyleRes, attrs.clone(), values, indices)) {
                throw new Resources.NotFoundException("style application failed");
            }
            TypedArray styled = TypedArray.fromStyle(resources, engine, parser == null ? null : parser.block,
                    values, attrs.length);
            return parser == null && set != null ? styled.withTagValues(set, attrs) : styled;
        }

        @Override
        public int getChangingConfigurations() {
            return theme.changingConfigurations();
        }
    }
}
