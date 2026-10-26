// SPDX-License-Identifier: GPL-3.0-or-later
package android.content.res;

import android.util.DisplayMetrics;
import android.util.TypedValue;
import java.io.File;
import java.lang.ref.Cleaner;
import java.util.Locale;

/**
 * libandroidfw (AssetManager2) behind JNI: src/bridge/resources.zig over
 * src/native/res_engine.cpp. Owns one native AssetManager2 over the retained
 * {@code <codeDir>/base.apk}. Every native handle has an explicit close and a
 * Cleaner fallback, so an abandoned theme, parser or asset never leaks.
 *
 * <p>Cookies follow AOSP's Java convention: native cookie + 1, with -1 for
 * values that come from an XML block's own string pool.
 */
final class ResEngine implements AutoCloseable {
    static final Cleaner CLEANER = Cleaner.create();

    static {
        System.loadLibrary("isakai_jni");
    }

    /** Value record words written by nGetValue/nThemeAttr. */
    static final int V_COOKIE = 0, V_TYPE = 1, V_DATA = 2, V_RESID = 3, V_FLAGS = 4, V_DENSITY = 5;
    static final int VALUE_WORDS = 6;

    /** AOSP AttributeResolution STYLE_* layout for applyStyle output. */
    static final int STYLE_NUM_ENTRIES = 7;
    static final int STYLE_TYPE = 0, STYLE_DATA = 1, STYLE_ASSET_COOKIE = 2, STYLE_RESOURCE_ID = 3,
            STYLE_CHANGING_CONFIGURATIONS = 4, STYLE_DENSITY = 5;

    /** Asset access modes (android::Asset::AccessMode). */
    static final int ACCESS_RANDOM = 1, ACCESS_STREAMING = 2;

    static native long nOpen(String[] paths, int systemCount);
    static native void nClose(long res);
    static native void nSetConfiguration(long res, int[] fields, String locale);
    static native boolean nGetValue(long res, int resid, boolean resolve, int density, int[] out);
    static native String nPoolString(long res, int cookie, int index);
    static native int[] nGetBag(long res, int resid);
    static native String nResourceName(long res, int resid);
    static native int nIdentifier(long res, String name, String type, String pkg);
    static native long nThemeNew(long res);
    static native void nThemeDelete(long theme);
    static native boolean nThemeApply(long theme, int style, boolean force);
    static native boolean nThemeSetTo(long dst, long src);
    static native void nThemeClear(long theme);
    static native boolean nThemeAttr(long theme, int attr, boolean resolve, int[] out);
    static native int nThemeChangingConfigurations(long theme);
    static native boolean nApplyStyle(long theme, long xml, int defStyleAttr, int defStyleRes,
            int[] attrs, int[] outValues, int[] outIndices);
    static native boolean nRetrieveAttributes(long res, long xml, int[] attrs, int[] outValues,
            int[] outIndices);
    static native long nXmlOpen(long res, int cookie, String path);
    static native void nXmlClose(long xml);
    static native int nXmlNext(long xml);
    static native int[] nXmlCurrent(long xml);
    static native String nXmlString(long xml, int index);
    static native long nAssetOpen(long res, String path, int mode, int[] outCookie);
    static native int nAssetRead(long asset, byte[] buf, int off, int len);
    static native long nAssetSeek(long asset, long offset, int whence);
    static native long nAssetLength(long asset);
    static native long nAssetRemaining(long asset);
    static native boolean nAssetRange(long asset, long[] out);
    static native void nAssetClose(long asset);
    static native String nAssetList(long res, String dir);

    /**
     * One-shot native release shared by every handle type: the explicit
     * close and the Cleaner run the same action, whichever comes first.
     */
    static final class Handle implements Runnable {
        private final java.util.function.LongConsumer release;
        private volatile long ptr;

        Handle(long ptr, java.util.function.LongConsumer release) {
            this.ptr = ptr;
            this.release = release;
        }

        long get() {
            long p = ptr;
            if (p == 0) throw new IllegalStateException("resource handle closed");
            return p;
        }

        boolean isOpen() {
            return ptr != 0;
        }

        @Override
        public synchronized void run() {
            long p = ptr;
            ptr = 0;
            if (p != 0) release.accept(p);
        }
    }

    private final Handle handle;
    private final Cleaner.Cleanable cleanable;
    /** Loaded archives in cookie order (Java cookie n is archives[n - 1]). */
    private final File[] archives;

    private ResEngine(long ptr, File[] archives) {
        this.handle = new Handle(ptr, ResEngine::nClose);
        this.cleanable = CLEANER.register(this, handle);
        this.archives = archives;
    }

    /**
     * Engine over the framework archive (package 0x01, loaded as system)
     * followed by the app's base and split archives, or null when any is
     * absent or unreadable.
     */
    static ResEngine open(File framework, File... app) {
        File[] archives = new File[app.length + 1];
        archives[0] = framework;
        System.arraycopy(app, 0, archives, 1, app.length);
        String[] paths = new String[archives.length];
        for (int i = 0; i < archives.length; i++) {
            if (archives[i] == null || !archives[i].isFile()) return null;
            paths[i] = archives[i].getPath();
        }
        long ptr = nOpen(paths, 1);
        return ptr == 0 ? null : new ResEngine(ptr, archives);
    }

    /** A second AssetManager2 over the same (cached) archives. */
    ResEngine reopen() {
        return open(archives[0], java.util.Arrays.copyOfRange(archives, 1, archives.length));
    }

    /** framework-res.apk installed beside the framework classes. */
    static File frameworkArchive() {
        try {
            java.security.CodeSource source = ResEngine.class.getProtectionDomain().getCodeSource();
            if (source == null) return null;
            return new File(new File(source.getLocation().toURI()), "framework-res.apk");
        } catch (java.net.URISyntaxException | SecurityException e) {
            return null;
        }
    }

    long ptr() {
        return handle.get();
    }

    @Override
    public void close() {
        cleanable.clean();
    }

    /**
     * Map Configuration + DisplayMetrics to the AOSP ResTable_config fields
     * res_engine.cpp expects (same order as AssetManager.setConfiguration).
     */
    void setConfiguration(Configuration c, DisplayMetrics m, int sdk) {
        int density = m.densityDpi > 0 ? m.densityDpi : DisplayMetrics.DENSITY_DEFAULT;
        float scale = density / (float) DisplayMetrics.DENSITY_DEFAULT;
        int widthDp = c.screenWidthDp != 0 ? c.screenWidthDp : Math.round(m.widthPixels / scale);
        int heightDp = c.screenHeightDp != 0 ? c.screenHeightDp : Math.round(m.heightPixels / scale);
        int smallest = c.smallestScreenWidthDp != 0 ? c.smallestScreenWidthDp : Math.min(widthDp, heightDp);
        int orientation = c.orientation != Configuration.ORIENTATION_UNDEFINED ? c.orientation
                : (m.widthPixels > m.heightPixels ? Configuration.ORIENTATION_LANDSCAPE
                        : Configuration.ORIENTATION_PORTRAIT);
        int uiMode = c.uiMode;
        if ((uiMode & 0x0f) == 0) uiMode |= Configuration.UI_MODE_TYPE_NORMAL;
        if ((uiMode & Configuration.UI_MODE_NIGHT_MASK) == 0) uiMode |= Configuration.UI_MODE_NIGHT_NO;
        int[] fields = {
            c.mcc, c.mnc, orientation, c.touchscreen, density, c.keyboard,
            c.keyboardHidden | (c.navigationHidden << 2), c.navigation,
            Math.max(m.widthPixels, m.heightPixels), Math.min(m.widthPixels, m.heightPixels),
            smallest, widthDp, heightDp, c.screenLayout, uiMode, c.colorMode, sdk,
            // No selector field exists for this in ResTable_config; res_engine.cpp
            // carries it in the free upper inputFlags bits.
            c.hardKeyboardHidden,
        };
        Locale locale = c.locale != null ? c.locale : Locale.getDefault();
        nSetConfiguration(ptr(), fields, locale.toLanguageTag());
    }

    /** Value record → TypedValue with AOSP Java cookie and pooled string. */
    TypedValue toTypedValue(int[] v) {
        TypedValue out = new TypedValue();
        int cookie = v[V_COOKIE];
        out.type = v[V_TYPE];
        out.data = v[V_DATA];
        out.resourceId = v[V_RESID];
        out.changingConfigurations = v[V_FLAGS];
        out.density = v[V_DENSITY];
        out.assetCookie = cookie < 0 ? -1 : cookie + 1;
        out.string = out.type == TypedValue.TYPE_STRING ? poolString(out.assetCookie, out.data) : null;
        return out;
    }

    /** String from the table pool for a Java cookie (null when absent). */
    String poolString(int javaCookie, int index) {
        if (javaCookie <= 0) return null;
        return nPoolString(ptr(), javaCookie - 1, index);
    }

    TypedValue value(int id, boolean resolveRefs, int density) {
        int[] v = new int[VALUE_WORDS];
        if (!nGetValue(ptr(), id, resolveRefs, density, v)) return null;
        return toTypedValue(v);
    }

    /** Resolved bag item values in order, or null when id names no bag. */
    TypedValue[] bag(int id) {
        int[] flat = nGetBag(ptr(), id);
        if (flat == null) return null;
        TypedValue[] out = new TypedValue[flat.length / 7];
        int[] v = new int[VALUE_WORDS];
        for (int i = 0; i < out.length; i++) {
            System.arraycopy(flat, i * 7 + 1, v, 0, VALUE_WORDS);
            out[i] = toTypedValue(v);
        }
        return out;
    }

    /** Native AssetManager2 theme; closed explicitly or by the Cleaner. */
    final class NativeTheme implements AutoCloseable {
        private final Handle handle;
        private final Cleaner.Cleanable cleanable;

        NativeTheme() {
            long ptr = nThemeNew(ResEngine.this.ptr());
            if (ptr == 0) throw new IllegalStateException("theme allocation failed");
            this.handle = new Handle(ptr, ResEngine::nThemeDelete);
            this.cleanable = CLEANER.register(this, handle);
        }

        long ptr() {
            return handle.get();
        }

        void applyStyle(int style, boolean force) {
            nThemeApply(ptr(), style, force);
        }

        void setTo(NativeTheme other) {
            nThemeSetTo(ptr(), other.ptr());
        }

        void clear() {
            nThemeClear(ptr());
        }

        TypedValue attribute(int attr, boolean resolveRefs) {
            int[] v = new int[VALUE_WORDS];
            if (!nThemeAttr(ptr(), attr, resolveRefs, v)) return null;
            return toTypedValue(v);
        }

        int changingConfigurations() {
            return nThemeChangingConfigurations(ptr());
        }

        @Override
        public void close() {
            cleanable.clean();
        }
    }

    /** Compiled XML document opened from the archive. */
    final class XmlBlock implements AutoCloseable {
        private final Handle handle;
        private final Cleaner.Cleanable cleanable;
        private final java.util.HashMap<Integer, String> strings = new java.util.HashMap<>();

        XmlBlock(long ptr) {
            this.handle = new Handle(ptr, ResEngine::nXmlClose);
            this.cleanable = CLEANER.register(this, handle);
        }

        long ptr() {
            return handle.get();
        }

        boolean isOpen() {
            return handle.isOpen();
        }

        ResEngine engine() {
            return ResEngine.this;
        }

        String string(int index) {
            if (index < 0) return null;
            return strings.computeIfAbsent(index, i -> nXmlString(ptr(), i));
        }

        @Override
        public void close() {
            cleanable.clean();
        }
    }

    /** XML block for a file path, or null when missing or malformed. */
    XmlBlock openXml(int javaCookie, String path) {
        long ptr = nXmlOpen(ptr(), javaCookie > 0 ? javaCookie - 1 : -1, path);
        return ptr == 0 ? null : new XmlBlock(ptr);
    }

    /** Native asset handle: stream reads, seeks and uncompressed byte ranges. */
    final class NativeAsset implements AutoCloseable {
        private final Handle handle;
        private final Cleaner.Cleanable cleanable;
        /** Archive this asset was read from. */
        final File archive;

        NativeAsset(long ptr, File archive) {
            this.handle = new Handle(ptr, ResEngine::nAssetClose);
            this.cleanable = CLEANER.register(this, handle);
            this.archive = archive;
        }

        int read(byte[] b, int off, int len) {
            return nAssetRead(handle.get(), b, off, len);
        }

        long seek(long offset, int whence) {
            return nAssetSeek(handle.get(), offset, whence);
        }

        long length() {
            return nAssetLength(handle.get());
        }

        long remaining() {
            return nAssetRemaining(handle.get());
        }

        /** {start, length} inside {@link #archive}, or null when the entry is compressed. */
        long[] range() {
            long[] out = new long[2];
            return nAssetRange(handle.get(), out) ? out : null;
        }

        @Override
        public void close() {
            cleanable.clean();
        }
    }

    /** Asset for an archive path (e.g. assets/x or res/x), or null when missing. */
    NativeAsset openAsset(String path, int mode) {
        int[] cookie = new int[1];
        long ptr = nAssetOpen(ptr(), path, mode, cookie);
        return ptr == 0 ? null : new NativeAsset(ptr, archives[cookie[0] - 1]);
    }

    String[] list(String dir) {
        String joined = nAssetList(ptr(), dir);
        if (joined == null || joined.isEmpty()) return new String[0];
        return joined.substring(0, joined.length() - 1).split("\0", -1);
    }
}
