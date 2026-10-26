// SPDX-License-Identifier: GPL-3.0-or-later
package android.content.res;

import android.util.AttributeSet;
import android.util.TypedValue;

/**
 * Styled-attribute array. With libandroidfw the slots are AOSP ApplyStyle /
 * RetrieveAttributes output, already resolved through tag, style, defaults
 * and theme ({@link #fromStyle}). The legacy engine resolves each attr id
 * here: tag literal, then theme, then table, with the caller default on any
 * miss (fail-closed, never a guessed value).
 */
public class TypedArray {
    private final Resources resources;
    /** Legacy ?attr chain resolution; null when the slots are final values. */
    private final ResourceTable.ThemeImpl theme;
    private final AttributeSet set;
    private final TypedValue[] values;
    private boolean recycled;

    TypedArray(Resources resources, ResourceTable.ThemeImpl theme, AttributeSet set, int[] attrs) {
        this.resources = resources;
        this.theme = theme;
        this.set = set;
        int[] ids = attrs == null ? new int[0] : attrs;
        this.values = new TypedValue[ids.length];
        for (int i = 0; i < ids.length; i++) this.values[i] = lookup(ids[i]);
    }

    private TypedArray(Resources resources, TypedValue[] values) {
        this.resources = resources;
        this.theme = null;
        this.set = null;
        this.values = values;
    }

    /** Tag literals only (an AttributeSet outside any XML block). */
    static TypedArray fromTag(Resources resources, AttributeSet set, int[] attrs) {
        return new TypedArray(resources, null, set, attrs);
    }

    /**
     * AOSP STYLE_* records for {@code count} attrs. Strings come from the
     * XML block's pool for cookie -1 and from the table pool otherwise.
     */
    static TypedArray fromStyle(Resources resources, ResEngine engine, ResEngine.XmlBlock xml,
            int[] data, int count) {
        TypedValue[] values = new TypedValue[count];
        for (int i = 0; i < count; i++) {
            int at = i * ResEngine.STYLE_NUM_ENTRIES;
            int type = data[at + ResEngine.STYLE_TYPE];
            if (type == TypedValue.TYPE_NULL) continue;
            TypedValue v = new TypedValue();
            v.type = type;
            v.data = data[at + ResEngine.STYLE_DATA];
            v.assetCookie = data[at + ResEngine.STYLE_ASSET_COOKIE];
            v.resourceId = data[at + ResEngine.STYLE_RESOURCE_ID];
            v.changingConfigurations = data[at + ResEngine.STYLE_CHANGING_CONFIGURATIONS];
            v.density = data[at + ResEngine.STYLE_DENSITY];
            if (type == TypedValue.TYPE_STRING) {
                v.string = v.assetCookie < 0 ? (xml == null ? null : xml.string(v.data))
                        : engine.poolString(v.assetCookie, v.data);
            }
            values[i] = v;
        }
        return new TypedArray(resources, values);
    }

    /** Overlay tag literals from a non-binary AttributeSet onto styled slots. */
    TypedArray withTagValues(AttributeSet tag, int[] attrs) {
        TypedArray literal = new TypedArray(resources, null, tag, attrs);
        TypedValue[] merged = values.clone();
        for (int i = 0; i < merged.length; i++) {
            if (literal.values[i] != null) merged[i] = literal.values[i];
        }
        return new TypedArray(resources, merged);
    }

    private TypedValue lookup(int attrId) {
        if (attrId != 0 && set != null) {
            // Tag literal wins: match by recorded binary-XML attr id and
            // reuse the raw type/data (references resolve through the table).
            for (int i = 0; i < set.getAttributeCount(); i++) {
                if (set.getAttributeNameResource(i) != attrId) continue;
                TypedValue v = rawValue(set, i);
                if (v != null) return v;
            }
        }
        if (theme != null) {
            TypedValue t = theme.attribute(attrId);
            if (t != null) return t;
        }
        if (resources == null) return null;
        return resources.attributeValue(attrId);
    }

    /** TypedValue from the set's raw binary-XML type/data at index. */
    private TypedValue rawValue(AttributeSet set, int index) {
        if (!(set instanceof XmlResourceParser)) return null;
        int[] raw = ((XmlResourceParser) set).getAttributeRawValue(index);
        int typ = raw[0];
        int data = raw[1];
        if (typ == 0 && data == 0) return null;
        TypedValue v = new TypedValue();
        if (typ == 1) {
            // Reference: resolve through the table (fail-closed null).
            TypedValue r = resources == null ? null : resources.attributeValue(data);
            if (r == null) {
                String name = null;
                try {
                    name = resources == null ? null : resources.getResourceEntryName(data);
                } catch (RuntimeException e) {
                    name = null;
                }
                if (name == null) return null;
            }
            if (r != null) {
                v.type = r.type;
                v.string = r.string;
                v.data = r.data;
                v.assetCookie = r.assetCookie;
                v.resourceId = data;
                v.changingConfigurations = r.changingConfigurations;
                v.density = r.density;
                return v;
            }
            return null;
        }
        v.type = typ;
        v.string = null;
        v.data = data;
        if (typ == 3) {
            v.string = set.getAttributeValue(index);
        }
        return v;
    }

    private TypedValue at(int index) {
        if (index < 0 || index >= values.length) return null;
        return values[index];
    }

    /** Value with ?attr/ references resolved through the theme (cap 8). */
    private TypedValue resolved(int index) {
        TypedValue v = at(index);
        if (v == null || v.type != TypedValue.TYPE_ATTRIBUTE || v.data == 0) return v;
        if (theme == null) return v;
        int id = v.data;
        for (int depth = 0; depth < 8; depth++) {
            TypedValue t = theme.attribute(id);
            if (t == null && resources != null) t = resources.attributeValue(id);
            if (t == null) return v;
            if (t.type != TypedValue.TYPE_ATTRIBUTE || t.data == 0 || t.data == id) return t;
            id = t.data;
        }
        return v;
    }

    public int length() { return values.length; }

    public int getIndexCount() {
        int n = 0;
        for (TypedValue v : values) if (v != null) n++;
        return n;
    }

    public int getIndex(int at) {
        int seen = -1;
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null) {
                seen++;
                if (seen == at) return i;
            }
        }
        return -1;
    }

    public Resources getResources() { return resources; }

    public boolean hasValue(int index) { return at(index) != null; }

    public boolean getBoolean(int index, boolean defValue) {
        TypedValue v = resolved(index);
        if (v == null) return defValue;
        if (v.type == TypedValue.TYPE_INT_BOOLEAN) return v.data != 0;
        if (v.type >= TypedValue.TYPE_FIRST_INT && v.type <= TypedValue.TYPE_LAST_INT) return v.data != 0;
        return defValue;
    }

    public int getInt(int index, int defValue) {
        TypedValue v = resolved(index);
        if (v == null) return defValue;
        if (v.type >= TypedValue.TYPE_FIRST_INT && v.type <= TypedValue.TYPE_LAST_INT) return v.data;
        return defValue;
    }

    public int getInteger(int index,int defValue) {
        TypedValue value=resolved(index);
        if(value==null)return defValue;
        if(value.type>=TypedValue.TYPE_FIRST_INT && value.type<=TypedValue.TYPE_LAST_INT)return value.data;
        throw new UnsupportedOperationException("Attribute is not an integer");
    }

    public float getFloat(int index, float defValue) {
        TypedValue v = resolved(index);
        if (v == null) return defValue;
        if (v.type == TypedValue.TYPE_FLOAT) return Float.intBitsToFloat(v.data);
        if (v.type >= TypedValue.TYPE_FIRST_INT && v.type <= TypedValue.TYPE_LAST_INT) return v.data;
        if (v.type == TypedValue.TYPE_STRING && v.string != null) {
            try {
                return Float.parseFloat(v.string.toString());
            } catch (NumberFormatException e) {
                return defValue;
            }
        }
        return defValue;
    }

    public int getColor(int index, int defValue) {
        TypedValue v = resolved(index);
        if (v == null) return defValue;
        if (v.type >= TypedValue.TYPE_FIRST_COLOR_INT && v.type <= TypedValue.TYPE_LAST_COLOR_INT) return v.data;
        if (v.type == TypedValue.TYPE_STRING && v.resourceId != 0 && resources != null) {
            // A color state list file: its default color, as on Android.
            try {
                return resources.getColorStateList(v.resourceId).getDefaultColor();
            } catch (Resources.NotFoundException e) {
                return defValue;
            }
        }
        if (v.type == TypedValue.TYPE_STRING && v.string != null) {
            try {
                return ColorStateList.resolveColor(resources, v.string.toString().trim(), null);
            } catch (RuntimeException e) {
                return defValue;
            }
        }
        return defValue;
    }

    public android.graphics.drawable.Drawable getDrawable(int index) {
        TypedValue value=resolved(index);
        if(value==null)return null;
        if(value.type>=TypedValue.TYPE_FIRST_COLOR_INT && value.type<=TypedValue.TYPE_LAST_COLOR_INT)
            return new android.graphics.drawable.ColorDrawable(value.data);
        if(value.resourceId!=0 && resources!=null)return resources.getDrawable(value.resourceId);
        // A reference the theme cannot finish (dangling style/?attr link)
        // degrades to null like a missing value; anything else stays loud.
        if(value.type==TypedValue.TYPE_REFERENCE || value.type==TypedValue.TYPE_ATTRIBUTE)return null;
        throw new UnsupportedOperationException("Drawable attribute is not a color or resource reference");
    }

    public ColorStateList getColorStateList(int index) {
        TypedValue v = resolved(index);
        if (v == null) return null;
        if (v.type >= TypedValue.TYPE_FIRST_COLOR_INT && v.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return ColorStateList.valueOf(v.data);
        }
        if (v.resourceId != 0 && resources != null) {
            try {
                return resources.getColorStateList(v.resourceId);
            } catch (Resources.NotFoundException e) {
                return null;
            }
        }
        return null;
    }

    public float getDimension(int index, float defValue) {
        TypedValue v = resolved(index);
        if (v == null || resources == null) return defValue;
        if (v.type == TypedValue.TYPE_DIMENSION) {
            return TypedValue.complexToDimension(v.data, resources.getDisplayMetrics());
        }
        return defValue;
    }

    public int getDimensionPixelOffset(int index, int defValue) {
        TypedValue v = resolved(index);
        if (v == null || resources == null) return defValue;
        if (v.type == TypedValue.TYPE_DIMENSION) {
            return TypedValue.complexToDimensionPixelOffset(v.data, resources.getDisplayMetrics());
        }
        return defValue;
    }

    public int getLayoutDimension(int index, int defValue) {
        TypedValue v = resolved(index);
        if (v == null) return defValue;
        if (v.type >= 16 && v.type <= 31) return v.data;
        return getDimensionPixelSize(index, defValue);
    }
    public int getLayoutDimension(int index, String name) {
        if (!hasValue(index)) throw new IllegalArgumentException("Missing layout dimension " + name);
        return getLayoutDimension(index, 0);
    }
    public int getDimensionPixelSize(int index, int defValue) {
        TypedValue v = resolved(index);
        if (v == null || resources == null) return defValue;
        if (v.type == TypedValue.TYPE_DIMENSION) {
            return TypedValue.complexToDimensionPixelSize(v.data, resources.getDisplayMetrics());
        }
        return defValue;
    }

    public int getResourceId(int index, int defValue) {
        TypedValue v = resolved(index);
        if (v == null) return defValue;
        if (v.type == TypedValue.TYPE_REFERENCE) return v.data;
        if (v.resourceId != 0) return v.resourceId;
        return defValue;
    }

    public String getString(int index) {
        TypedValue v = resolved(index);
        if (v == null) return null;
        if (v.type == TypedValue.TYPE_STRING && v.string != null) return v.string.toString();
        return null;
    }

    public CharSequence getText(int index) {
        return getString(index);
    }

    public CharSequence[] getTextArray(int index) {
        TypedValue v = resolved(index);
        if (v == null || resources == null || v.resourceId == 0) return null;
        try {
            return resources.getTextArray(v.resourceId);
        } catch (Resources.NotFoundException e) {
            return null;
        }
    }

    public boolean getValue(int index, TypedValue outValue) {
        TypedValue v = resolved(index);
        if (v == null || outValue == null) return false;
        outValue.type = v.type;
        outValue.string = v.string;
        outValue.data = v.data;
        outValue.assetCookie = v.assetCookie;
        outValue.resourceId = v.resourceId;
        outValue.changingConfigurations = v.changingConfigurations;
        outValue.density = v.density;
        return true;
    }

    public TypedValue peekValue(int index) {
        return at(index);
    }

    public int getChangingConfigurations() { return 0; }
    public String getPositionDescription() { return "<unknown>"; }

    public void recycle() { recycled = true; }
}
