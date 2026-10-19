// SPDX-License-Identifier: GPL-3.0-or-later
package org.isakai.runtime;

/** Host implementations for Java Object operations unavailable to translated bytecode. */
public final class ObjectSupport {
    private ObjectSupport() {}

    public static Object cloneObject(Object value) {
        if (value == null) throw new NullPointerException("value");
        try {
            java.lang.reflect.Method clone = value.getClass().getMethod("clone");
            return clone.invoke(value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("object is not publicly cloneable", e);
        }
    }
}
