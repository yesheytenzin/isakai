// SPDX-License-Identifier: GPL-3.0-or-later
package org.isakai.runtime;

/**
 * JDK interface-default helpers for translated invoke-super calls.
 *
 * <p>DEX invoke-super to an interface default (e.g. Map.getOrDefault) has no
 * V49 JVM encoding: invokespecial+Methodref fails ("Illegal use of nonvirtual
 * function call") and invokespecial+InterfaceMethodref fails ("Illegal type
 * in constant pool"), while invokeinterface would dispatch virtually to the
 * override (wrong). These helpers implement the JDK default semantics via
 * virtual/interface calls, and the translator rewrites matching super calls
 * to them.
 */
public final class InterfaceDefaults {
    private InterfaceDefaults() {}

    /** JDK Map.getOrDefault default: get, else the default value. */
    public static <K, V> V mapGetOrDefault(java.util.Map<K, V> self, Object key, V defaultValue) {
        V v;
        try {
            @SuppressWarnings("unchecked")
            K k = (K) key;
            v = self.get(k);
        } catch (ClassCastException e) {
            return defaultValue;
        }
        return v != null ? v : defaultValue;
    }
}
