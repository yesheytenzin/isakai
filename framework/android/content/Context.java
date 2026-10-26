// SPDX-License-Identifier: GPL-3.0-or-later
package android.content;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal abstract Context: file/prefs/database storage surface (M4).
 *
 * <p>Storage layout under the app data dir (lazy 0700 mkdir handled by the
 * host helper at install; runtime mkdirs defensively re-assert 0700):
 * <pre>
 *   files/  cache/  code_cache/  no_backup/  shared_prefs/  databases/  app_&lt;name&gt;/
 * </pre>
 * {@link #getDir} rejects any name containing a separator; {@link #getDatabasePath}
 * appends {@code .db} when no extension is present.
 *
 * <p>Prefs: one {@link SharedPreferences} instance per name, cached in a map;
 * world-readable/world-writeable modes throw {@link SecurityException}.
 */
public abstract class Context {
    public static final int MODE_PRIVATE = 0;
    public static final int MODE_WORLD_READABLE = 1;
    public static final int MODE_WORLD_WRITEABLE = 2;
    public static final int MODE_APPEND = 32768;
    public static final int BIND_AUTO_CREATE = 1;

    /** Service name for notifications; Application.getSystemService override lands with desktop. */
    public static final String NOTIFICATION_SERVICE = "notification";

    /** Service name for layout inflation; resolved by the default getSystemService branch. */
    public static final String LAYOUT_INFLATER_SERVICE = "layout_inflater";

    /** Service name for the input-method manager stub; never null. */
    public static final String INPUT_METHOD_SERVICE = "input_method";

    public static final String ACCESSIBILITY_SERVICE = "accessibility";
    public static final String AUTOFILL_MANAGER_SERVICE = "autofill";
    public static final String ACTIVITY_SERVICE = "activity";
    private String dataDir;
    private String packageName = "";
    private final Map<String, SharedPreferences> prefsCache = new ConcurrentHashMap<>();

    /** Called by IsakaiMain before any component runs. */
    public void attach(String dataDir) {
        this.dataDir = dataDir;
    }

    /** Called by IsakaiMain right after attach; argv carries the package. */
    public void attachPackage(String packageName) {
        this.packageName = packageName != null ? packageName : "";
    }

    public String getPackageName() {
        return packageName;
    }

    public String getDataDirString() {
        return dataDir;
    }

    protected File dataRoot() {
        return new File(dataDir);
    }

    private File subdir(String name) {
        File d = new File(dataRoot(), name);
        if (!d.isDirectory()) {
            d.mkdirs();
            chmod0700(d);
        }
        return d;
    }

    private static void chmod0700(File f) {
        f.setReadable(false, false);
        f.setWritable(false, false);
        f.setExecutable(false, false);
        f.setReadable(true, true);
        f.setWritable(true, true);
        f.setExecutable(true, true);
    }

    public File getFilesDir() {
        return subdir("files");
    }

    public File getExternalFilesDir(String type) {
        File root=subdir("external/Android/data/"+getPackageName()+"/files");
        if(type==null)return root;
        File dir=new File(root,type);
        try {
            if(!dir.getCanonicalPath().startsWith(root.getCanonicalPath()+File.separator))
                throw new IllegalArgumentException("external directory type escapes app storage");
        } catch(IOException e) { return null; }
        if(!dir.isDirectory() && !dir.mkdirs())return null;
        chmod0700(dir);return dir;
    }
    public File[] getExternalFilesDirs(String type) { return new File[]{getExternalFilesDir(type)}; }
    public File getExternalCacheDir() { return subdir("external/Android/data/"+getPackageName()+"/cache"); }
    public File[] getExternalCacheDirs() { return new File[]{getExternalCacheDir()}; }
    public File getCacheDir() {
        return subdir("cache");
    }

    public File getCodeCacheDir() {
        return subdir("code_cache");
    }

    public File getNoBackupFilesDir() {
        return subdir("no_backup");
    }

    public File getDataDir() {
        return dataRoot();
    }

    /**
     * App-private directory {@code app_<name>}; names containing a file
     * separator are rejected with IllegalArgumentException.
     */
    public File getDir(String name, int mode) {
        checkPrivateMode(mode);
        if (name == null || name.indexOf('/') >= 0 || name.indexOf(File.separatorChar) >= 0) {
            throw new IllegalArgumentException("getDir rejects separators: " + name);
        }
        File d = new File(dataRoot(), "app_" + name);
        if (!d.isDirectory()) {
            d.mkdirs();
            chmod0700(d);
        }
        return d;
    }

    public File getFileStreamPath(String name) {
        return new File(getFilesDir(), name);
    }

    public File getSharedPrefsFile(String name) {
        return new File(subdir("shared_prefs"), name + ".xml");
    }

    public File getDatabasePath(String name) {
        String file = name.indexOf('.') >= 0 ? name : name + ".db";
        return new File(subdir("databases"), file);
    }

    public String[] fileList() {
        String[] list = getFilesDir().list();
        return list != null ? list : new String[0];
    }

    public String[] databaseList() {
        String[] list = subdir("databases").list();
        return list != null ? list : new String[0];
    }

    public boolean deleteFile(String name) {
        File f = getFileStreamPath(name);
        return f.exists() && f.delete();
    }

    public boolean deleteDatabase(String name) {
        File f = getDatabasePath(name);
        boolean ok = !f.exists() || f.delete();
        new File(f.getParentFile(), f.getName() + "-journal").delete();
        new File(f.getParentFile(), f.getName() + "-wal").delete();
        new File(f.getParentFile(), f.getName() + "-shm").delete();
        return ok;
    }

    public FileInputStream openFileInput(String name) throws IOException {
        return new FileInputStream(getFileStreamPath(name));
    }

    public FileOutputStream openFileOutput(String name, int mode) throws IOException {
        boolean append = (mode & MODE_APPEND) != 0;
        checkPrivateMode(mode & ~MODE_APPEND);
        File f = getFileStreamPath(name);
        FileOutputStream out = new FileOutputStream(f, append);
        chmod0600(f);
        return out;
    }

    private static void chmod0600(File f) {
        f.setReadable(false, false);
        f.setWritable(false, false);
        f.setExecutable(false, false);
        f.setReadable(true, true);
        f.setWritable(true, true);
    }

    /** Legacy M1 helper: read a private file as UTF-8, fallback when missing/unreadable. */
    public String readFile(String name, String fallback) {
        try {
            Path p = getFileStreamPath(name).toPath();
            if (!Files.exists(p)) return fallback;
            return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return fallback;
        }
    }

    /** Legacy M1 helper: atomic tmp+rename write of a private UTF-8 file. */
    public void writeFile(String name, String content) {
        try {
            File f = getFileStreamPath(name);
            File parent = f.getParentFile();
            if (parent != null) parent.mkdirs();
            File tmp = new File(parent, name + ".tmp");
            Files.write(tmp.toPath(), content.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(tmp.toPath(), f.toPath(),
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), f.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RuntimeException("write " + name + ": " + e.getMessage(), e);
        }
    }

    private static void checkPrivateMode(int mode) {
        if ((mode & (MODE_WORLD_READABLE | MODE_WORLD_WRITEABLE)) != 0) {
            throw new SecurityException("world modes are not supported");
        }
    }

    public SharedPreferences getSharedPreferences(String name, int mode) {
        checkPrivateMode(mode);
        SharedPreferences existing = prefsCache.get(name);
        if (existing != null) return existing;
        SharedPreferences created = new SharedPreferencesImpl(getSharedPrefsFile(name));
        SharedPreferences raced = prefsCache.putIfAbsent(name, created);
        return raced != null ? raced : created;
    }

    /** Framework hook: drain queued disk writes (prefs apply()). No-op unless overridden. */
    public void drainQueuedWork() {}

    private final java.util.concurrent.CopyOnWriteArrayList<ComponentCallbacks> componentCallbacks =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * Component-callback registration (Compose's recomposer hooks
     * configuration changes). The desktop runtime raises no configuration
     * changes, so registered callbacks are retained but never invoked.
     */
    public void registerComponentCallbacks(ComponentCallbacks callback) {
        if (callback != null && !componentCallbacks.contains(callback)) componentCallbacks.add(callback);
    }

    public void unregisterComponentCallbacks(ComponentCallbacks callback) {
        componentCallbacks.remove(callback);
    }

    public abstract android.content.res.Resources getResources();

    private int baseTheme;

    /** Manifest application theme; applied under activity/setTheme layers. */
    public void attachTheme(int appTheme) {
        baseTheme = appTheme;
    }
    /** Manifest application theme id for handoff to navigated activities. */
    public int baseThemeForNav() {
        return baseTheme;
    }


    public android.content.res.Resources.Theme getTheme() {
        android.content.res.Resources.Theme theme = getResources().newTheme();
        if (baseTheme != 0) theme.applyStyle(baseTheme, true);
        return theme;
    }


    public String getString(int resId) {
        return getResources().getString(resId);
    }

    public String getString(int resId, Object... formatArgs) {
        return getResources().getString(resId, formatArgs);
    }

    public android.os.Looper getMainLooper() {
        return android.os.Looper.getMainLooper();
    }

    public CharSequence getText(int resId) {
        return getResources().getText(resId);
    }

    public android.content.Context getApplicationContext() {
        return this;
    }

    /**
     * Config-override context: resources over the same archives selected for
     * this context's configuration updated from {@code overrideConfiguration}
     * (AppCompat carries its night mode this way). Everything else delegates
     * to this context.
     */
    public android.content.Context createConfigurationContext(android.content.res.Configuration overrideConfiguration) {
        if (overrideConfiguration == null) {
            throw new IllegalArgumentException("overrideConfiguration must not be null");
        }
        android.content.res.Resources base = getResources();
        android.content.res.Configuration config = base.getConfiguration();
        config.updateFrom(overrideConfiguration);
        android.content.res.Resources resources =
                new android.content.res.Resources(base.getAssets(), base.getDisplayMetrics(), config);
        return new ContextWrapper(this) {
            @Override
            public android.content.res.Resources getResources() {
                return resources;
            }
        };
    }

    /**
     * Service lookup: {@code LAYOUT_INFLATER_SERVICE} returns a
     * {@link android.view.LayoutInflater} bound to this context; other names
     * return null (overrides add their services then fall through here).
     */
    public <T> T getSystemService(Class<T> type) {
        if(type == android.app.ActivityManager.class) return type.cast(new android.app.ActivityManager());
        if(type == android.content.pm.ShortcutManager.class) return type.cast(new android.content.pm.ShortcutManager());
        if(type == android.view.inputmethod.InputMethodManager.class) return type.cast(new android.view.inputmethod.InputMethodManager());
        if(type == android.view.accessibility.AccessibilityManager.class) return type.cast(new android.view.accessibility.AccessibilityManager());
        if(type == android.view.autofill.AutofillManager.class) return type.cast(new android.view.autofill.AutofillManager());
        return null;
    }

    public Object getSystemService(String name) {
        if (ACTIVITY_SERVICE.equals(name)) return new android.app.ActivityManager();
        if ("shortcut".equals(name)) return new android.content.pm.ShortcutManager();
        if ("window".equals(name)) return new WindowManagerImpl();
        if (LAYOUT_INFLATER_SERVICE.equals(name)) {
            return android.view.LayoutInflater.from(this);
        }
        if (INPUT_METHOD_SERVICE.equals(name)) {
            return new android.view.inputmethod.InputMethodManager();
        }
        if (ACCESSIBILITY_SERVICE.equals(name)) {
            return new android.view.accessibility.AccessibilityManager();
        }
        if (AUTOFILL_MANAGER_SERVICE.equals(name)) {
            return new android.view.autofill.AutofillManager();
        }
        return null;
    }
    public android.content.pm.ApplicationInfo getApplicationInfo() {
        try { return getPackageManager().getApplicationInfo(getPackageName(), 0); }
        catch (android.content.pm.PackageManager.NameNotFoundException e) { throw new IllegalStateException(e); }
    }

    public android.content.pm.PackageManager getPackageManager() {
        return new IsakaiPackageManager(this);
    }


    private final android.content.ContentResolver contentResolver =
            new android.content.ContentResolver() {};

    public android.content.ContentResolver getContentResolver() {
        return contentResolver;
    }

    public ClassLoader getClassLoader() {
        return getClass().getClassLoader();
    }

    public boolean isRestricted() {
        return false;
    }

    public android.content.res.TypedArray obtainStyledAttributes(android.util.AttributeSet set, int[] attrs) {
        return getTheme().obtainStyledAttributes(set, attrs, 0, 0);
    }

    public android.content.res.TypedArray obtainStyledAttributes(int resid, int[] attrs) {
        return getTheme().obtainStyledAttributes(resid, attrs);
    }

    public android.content.res.TypedArray obtainStyledAttributes(int[] attrs) {
        return getTheme().obtainStyledAttributes(attrs);
    }

    public android.content.res.TypedArray obtainStyledAttributes(android.util.AttributeSet set, int[] attrs, int defStyleAttr, int defStyleRes) {
        if (System.getProperty("isakai.diagAttrs") != null && defStyleAttr == 2130968618) {
            StringBuilder sb = new StringBuilder();
            sb.append("DIAG obtainStyled attrs=[");
            if (attrs != null) for (int i = 0; i < Math.min(attrs.length, 10); i++) sb.append(Integer.toHexString(attrs[i])).append(',');
            sb.append("] defStyleRes=0x").append(Integer.toHexString(defStyleRes));
            sb.append(" theme=").append(getTheme());
            System.err.println(sb);
        }
        return getTheme().obtainStyledAttributes(set, attrs, defStyleAttr, defStyleRes);
    }

    public final int getColor(int id) { return getResources().getColor(id); }

    public android.graphics.drawable.Drawable getDrawable(int id) {
        return getResources().getDrawable(id);
    }

    public android.content.res.ColorStateList getColorStateList(int id) {
        return getResources().getColorStateList(id);
    }


    public void startActivity(android.content.Intent intent) {}

    public void startActivity(android.content.Intent intent, android.os.Bundle options) {
        startActivity(intent);
    }

    public void startActivities(android.content.Intent[] intents, android.os.Bundle options) {
        if (intents == null) return;
        for (android.content.Intent intent : intents) startActivity(intent, options);
    }

    public boolean bindService(Intent intent, ServiceConnection connection, int flags) {
        return false;
    }

    public void unbindService(ServiceConnection connection) {}

    /** Copy a stream; shared by prefs backup/restore paths. */
    protected static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) {
            out.write(buf, 0, n);
        }
    }

    /**
     * Minimal window service: default display only. add/update/remove are
     * unsupported (windows are owned by activities, not added ad hoc).
     */
    private static final class WindowManagerImpl implements android.view.WindowManager {
        @Override
        public android.view.Display getDefaultDisplay() {
            return android.view.Display.getDefault();
        }

        @Override
        public void addView(android.view.View view, android.view.ViewGroup.LayoutParams params) {
            throw new UnsupportedOperationException("ad-hoc window views unsupported");
        }

        @Override
        public void updateViewLayout(android.view.View view, android.view.ViewGroup.LayoutParams params) {
            throw new UnsupportedOperationException("ad-hoc window views unsupported");
        }

        @Override
        public void removeView(android.view.View view) {}
    }
}
