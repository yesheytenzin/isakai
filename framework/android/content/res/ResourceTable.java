// SPDX-License-Identifier: GPL-3.0-or-later
package android.content.res;

import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import java.io.IOException;
import java.io.InputStream;

/**
 * Resource engine below {@link Resources}: {@link AndroidfwTable} (AOSP
 * libandroidfw over the retained base.apk) by default, or the legacy Java
 * parser of the staged resources.arsc ({@link Resources.Table}) with
 * {@code ISAKAI_RESOURCES=legacy} or when the archive is absent.
 *
 * <p>Lookups return null for a missing resource; Resources turns that into
 * NotFoundException.
 */
interface ResourceTable {
    /** Selected value for the current configuration, or null. */
    TypedValue value(int id, boolean resolveRefs);

    String[] stringArray(int id);

    int[] intArray(int id);

    /** {@code package:type/entry}, or null. */
    String resourceName(int id);

    int identifier(String name, String defType, String defPackage);

    /** Parser over the compiled XML file a TYPE_STRING value names. */
    XmlResourceParser openXml(TypedValue file);

    /** Raw bytes of the file a TYPE_STRING value names. */
    InputStream openFile(TypedValue file) throws IOException;

    ThemeImpl newTheme();

    void configure(Configuration config, DisplayMetrics metrics);

    TypedArray obtainAttributes(Resources resources, AttributeSet set, int[] attrs);

    AssetManager assets();

    /** Per-engine theme state behind {@link Resources.Theme}. */
    interface ThemeImpl {
        void applyStyle(int resId, boolean force);

        void setTo(ThemeImpl other);

        /** The theme's own value for an attribute, references unresolved; null if unset. */
        TypedValue attribute(int attrId);

        boolean resolveAttribute(int resId, TypedValue outValue, boolean resolveRefs);

        TypedArray obtainStyledAttributes(Resources resources, AttributeSet set, int[] attrs,
                int defStyleAttr, int defStyleRes);

        int getChangingConfigurations();
    }
}
