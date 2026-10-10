// SPDX-License-Identifier: GPL-3.0-or-later
package android.util;

/** Typed property handle: name plus type. */
public abstract class Property<T, V> {
    private final Class<V> type;
    private final String name;

    public Property(Class<V> type, String name) {
        this.type = type;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Class<V> getType() {
        return type;
    }

    public abstract void set(T object, V value);
    public abstract V get(T object);
}
