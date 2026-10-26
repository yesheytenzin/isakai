// SPDX-License-Identifier: GPL-3.0-or-later
package org.isakai.runtime;

/** Host implementations for Java Object operations unavailable to translated bytecode. */
public final class ObjectSupport {
    private ObjectSupport() {}

    public static Object cloneObject(Object value) {
        if (value == null) throw new NullPointerException("value");
        // Arrays implement Cloneable but expose no reflective clone() method;
        // their clone is a shallow copy with the same runtime component type.
        Class<?> type = value.getClass();
        if (type.isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            Object copy = java.lang.reflect.Array.newInstance(type.getComponentType(), length);
            System.arraycopy(value, 0, copy, 0, length);
            return copy;
        }
        try {
            java.lang.reflect.Method clone = type.getMethod("clone");
            return clone.invoke(value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("object is not publicly cloneable", e);
        }
    }
}
