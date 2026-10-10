// SPDX-License-Identifier: GPL-3.0-or-later
package android.content;

import android.net.Uri;
import java.util.HashMap;
import java.util.Map;

/** Plain intent POJO: action + data/type + string/int extras. */
public class Intent {
    /** Action: view data in the desktop handler. */
    public static final String ACTION_VIEW = "android.intent.action.VIEW";
    /** Action: pick a file, result via onActivityResult. */
    public static final String ACTION_GET_CONTENT = "android.intent.action.GET_CONTENT";
    /** Action: open a document, result via onActivityResult. */
    public static final String ACTION_OPEN_DOCUMENT = "android.intent.action.OPEN_DOCUMENT";
    /** Action: unsupported on desktop (no save-stream sink), treated as no-op. */
    public static final String ACTION_CREATE_DOCUMENT = "android.intent.action.CREATE_DOCUMENT";
    /** Extra: shared text payload. */
    public static final String EXTRA_TEXT = "android.intent.extra.TEXT";
    /** Extra: subject line for shared text. */
    public static final String EXTRA_SUBJECT = "android.intent.extra.SUBJECT";
    /** Extra: stream Uri for shared content. */
    public static final String EXTRA_STREAM = "android.intent.extra.STREAM";
    /** Extra: file-picker dialog title. */
    public static final String EXTRA_TITLE = "android.intent.extra.TITLE";

    private String action;
    private Uri data;
    private String type;
    private final Map<String, Object> extras = new HashMap<>();

    /** Empty intent with no action, data, or extras. */
    public Intent() {}

    private ComponentName component;
    private int flags;

    public Intent(Context packageContext, Class<?> cls) {
        String pkg = packageContext == null ? "" : packageContext.getPackageName();
        this.component = new ComponentName(pkg, cls == null ? "" : cls.getName());
    }

    public Intent(String action) {
        this.action = action;
    }

    public Intent(String action, Uri uri) {
        this.action = action;
        this.data = uri;
    }

    public Intent(Context packageContext, Class<?> cls, String action, Uri uri) {
        this(packageContext, cls);
        this.action = action;
        this.data = uri;
    }

    public Intent(Intent other) {
        if (other != null) {
            this.action = other.action;
            this.data = other.data;
            this.type = other.type;
            this.component = other.component;
            this.flags = other.flags;
            this.extras.putAll(other.extras);
        }
    }

    public ComponentName getComponent() {
        return component;
    }

    public Intent setComponent(ComponentName component) {
        this.component = component;
        return this;
    }

    public Intent setClass(Context packageContext, Class<?> cls) {
        String pkg = packageContext == null ? "" : packageContext.getPackageName();
        this.component = new ComponentName(pkg, cls == null ? "" : cls.getName());
        return this;
    }

    public Intent setClassName(Context packageContext, String className) {
        String pkg = packageContext == null ? "" : packageContext.getPackageName();
        this.component = new ComponentName(pkg, className == null ? "" : className);
        return this;
    }

    public Intent setClassName(String packageName, String className) {
        this.component = new ComponentName(packageName, className);
        return this;
    }

    public Intent addFlags(int flags) {
        this.flags |= flags;
        return this;
    }

    public Intent setFlags(int flags) {
        this.flags = flags;
        return this;
    }

    public int getFlags() {
        return flags;
    }


    /** Set the action and return this. */
    public Intent setAction(String action) {
        this.action = action;
        return this;
    }

    /** Set the data Uri and return this. */
    public Intent setData(Uri data) {
        this.data = data;
        return this;
    }

    /** Set the MIME type and return this. */
    public Intent setType(String type) {
        this.type = type;
        return this;
    }

    /** Set data and type together and return this. */
    public Intent setDataAndType(Uri data, String type) {
        this.data = data;
        this.type = type;
        return this;
    }

    /** Store a String extra and return this. */
    public Intent putExtra(String name, String value) {
        extras.put(name, value);
        return this;
    }

    /** Store an int extra and return this. */
    public Intent putExtra(String name, int value) {
        extras.put(name, Integer.valueOf(value));
        return this;
    }

    /** Current action, or null when unset. */
    public String getAction() {
        return action;
    }

    /** Current data Uri, or null when unset. */
    public Uri getData() {
        return data;
    }

    /** Current MIME type, or null when unset. */
    public String getType() {
        return type;
    }

    /** String extra value, or null when absent or not a String. */
    public String getStringExtra(String name) {
        Object v = extras.get(name);
        return v instanceof String ? (String) v : null;
    }

    /** Int extra value, or {@code defaultValue} when absent or not an int. */
    public int getIntExtra(String name, int defaultValue) {
        Object v = extras.get(name);
        return v instanceof Integer ? ((Integer) v).intValue() : defaultValue;
    }

    public long getLongExtra(String name, long defaultValue) {
        Object v = extras.get(name);
        return v instanceof Long ? ((Long) v).longValue() : defaultValue;
    }

    public boolean getBooleanExtra(String name, boolean defaultValue) {
        Object v = extras.get(name);
        return v instanceof Boolean ? ((Boolean) v).booleanValue() : defaultValue;
    }

    public float getFloatExtra(String name, float defaultValue) {
        Object v = extras.get(name);
        return v instanceof Float ? ((Float) v).floatValue() : defaultValue;
    }

    public double getDoubleExtra(String name, double defaultValue) {
        Object v = extras.get(name);
        return v instanceof Double ? ((Double) v).doubleValue() : defaultValue;
    }

    public android.os.Bundle getExtras() {
        if (extras.isEmpty()) return null;
        android.os.Bundle b = new android.os.Bundle();
        for (Map.Entry<String, Object> e : extras.entrySet()) {
            Object v = e.getValue();
            if (v instanceof String) b.putString(e.getKey(), (String) v);
            else if (v instanceof Integer) b.putInt(e.getKey(), (Integer) v);
            else if (v instanceof Long) b.putLong(e.getKey(), (Long) v);
            else if (v instanceof Boolean) b.putBoolean(e.getKey(), (Boolean) v);
            else if (v instanceof Float) b.putFloat(e.getKey(), (Float) v);
            else if (v instanceof Double) b.putDouble(e.getKey(), (Double) v);
        }
        return b;
    }

    public android.os.Bundle getBundleExtra(String name) {
        Object v = extras.get(name);
        return v instanceof android.os.Bundle ? (android.os.Bundle) v : null;
    }

    public Intent putExtras(android.os.Bundle bundle) {
        if (bundle != null) {
            for (String key : bundle.keySet()) {
                extras.put(key, bundle.get(key));
            }
        }
        return this;
    }

    public Intent putExtras(Intent other) {
        if (other != null) extras.putAll(other.extras);
        return this;
    }

    public Intent putExtra(String name, long value) {
        extras.put(name, Long.valueOf(value));
        return this;
    }

    public Intent putExtra(String name, boolean value) {
        extras.put(name, Boolean.valueOf(value));
        return this;
    }

    public Intent putExtra(String name, float value) {
        extras.put(name, Float.valueOf(value));
        return this;
    }

    public Intent putExtra(String name, double value) {
        extras.put(name, Double.valueOf(value));
        return this;
    }

    public Intent putExtra(String name, android.os.Bundle value) {
        extras.put(name, value);
        return this;
    }

    public Intent removeExtra(String name) {
        extras.remove(name);
        return this;
    }

    /** True when an extra under {@code name} is present. */
    public boolean hasExtra(String name) {
        return extras.containsKey(name);
    }
}
