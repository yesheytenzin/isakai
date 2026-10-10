// SPDX-License-Identifier: GPL-3.0-or-later
package android.content;

import java.util.Objects;

/** Package/class identity pair. */
public class ComponentName {
    private final String pkg;
    private final String cls;

    public ComponentName(String pkg, String cls) {
        this.pkg = pkg == null ? "" : pkg;
        this.cls = cls == null ? "" : cls;
    }

    public ComponentName(Context context, String cls) {
        this(context == null ? "" : context.getPackageName(), cls);
    }

    public ComponentName(Context context, Class<?> cls) {
        this(context == null ? "" : context.getPackageName(),
                cls == null ? "" : cls.getName());
    }

    public String getPackageName() { return pkg; }
    public String getClassName() { return cls; }
    public String getShortClassName() {
        return cls.startsWith(pkg) ? cls.substring(pkg.length()) : cls;
    }

    public static ComponentName unflattenFromString(String s) {
        if (s == null) return null;
        int i = s.indexOf('/');
        if (i < 0) return null;
        return new ComponentName(s.substring(0, i), s.substring(i + 1));
    }

    public String flattenToString() {
        return pkg + "/" + cls;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ComponentName)) return false;
        ComponentName c = (ComponentName) o;
        return pkg.equals(c.pkg) && cls.equals(c.cls);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pkg, cls);
    }

    @Override
    public String toString() {
        return "ComponentName{" + flattenToString() + "}";
    }
}
