// SPDX-License-Identifier: GPL-3.0-or-later
package android.util;

/** Immutable pair of two values. */
public class Pair<F, S> {
    public final F first;
    public final S second;

    public Pair(F first, S second) {
        this.first = first;
        this.second = second;
    }

    public static <A, B> Pair<A, B> create(A a, B b) {
        return new Pair<>(a, b);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Pair)) return false;
        Pair<?, ?> p = (Pair<?, ?>) o;
        return eq(first, p.first) && eq(second, p.second);
    }

    private static boolean eq(Object a, Object b) {
        return a == b || (a != null && a.equals(b));
    }

    @Override
    public int hashCode() {
        int h1 = first == null ? 0 : first.hashCode();
        int h2 = second == null ? 0 : second.hashCode();
        return h1 ^ h2;
    }

    @Override
    public String toString() {
        return "Pair{" + first + " " + second + "}";
    }
}
