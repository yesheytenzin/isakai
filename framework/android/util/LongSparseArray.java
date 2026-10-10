// SPDX-License-Identifier: GPL-3.0-or-later
package android.util;

/** Long-keyed sorted map with binary search. */
public class LongSparseArray<E> implements Cloneable {
    private long[] keys;
    private Object[] values;
    private int size;

    public LongSparseArray() { this(10); }

    public LongSparseArray(int initialCapacity) {
        if (initialCapacity < 0) initialCapacity = 0;
        keys = new long[initialCapacity];
        values = new Object[initialCapacity];
        size = 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    public LongSparseArray<E> clone() {
        LongSparseArray<E> c = new LongSparseArray<>(size);
        System.arraycopy(keys, 0, c.keys, 0, size);
        System.arraycopy(values, 0, c.values, 0, size);
        c.size = size;
        return c;
    }

    private int binarySearch(long key) {
        int lo = 0, hi = size - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            long m = keys[mid];
            if (m < key) lo = mid + 1;
            else if (m > key) hi = mid - 1;
            else return mid;
        }
        return ~lo;
    }

    private void grow() {
        int n = size <= 0 ? 4 : size * 2;
        long[] nk = new long[n];
        Object[] nv = new Object[n];
        System.arraycopy(keys, 0, nk, 0, size);
        System.arraycopy(values, 0, nv, 0, size);
        keys = nk;
        values = nv;
    }

    @SuppressWarnings("unchecked")
    public E get(long key) { return get(key, null); }

    @SuppressWarnings("unchecked")
    public E get(long key, E defaultValue) {
        int i = binarySearch(key);
        return i >= 0 ? (E) values[i] : defaultValue;
    }

    public void put(long key, E value) {
        int i = binarySearch(key);
        if (i >= 0) { values[i] = value; return; }
        i = ~i;
        if (size >= keys.length) grow();
        System.arraycopy(keys, i, keys, i + 1, size - i);
        System.arraycopy(values, i, values, i + 1, size - i);
        keys[i] = key;
        values[i] = value;
        size++;
    }

    public void append(long key, E value) {
        if (size > 0 && key <= keys[size - 1]) { put(key, value); return; }
        if (size >= keys.length) grow();
        keys[size] = key;
        values[size] = value;
        size++;
    }

    public void delete(long key) {
        int i = binarySearch(key);
        if (i >= 0) removeAt(i);
    }

    public void remove(long key) { delete(key); }

    public void removeAt(int index) {
        System.arraycopy(keys, index + 1, keys, index, size - index - 1);
        System.arraycopy(values, index + 1, values, index, size - index - 1);
        values[--size] = null;
    }

    public int indexOfKey(long key) { return binarySearch(key); }

    public int indexOfValue(E value) {
        for (int i = 0; i < size; i++) {
            Object v = values[i];
            if (v == value || (v != null && v.equals(value))) return i;
        }
        return -1;
    }

    public int size() { return size; }
    public long keyAt(int index) { return keys[index]; }

    @SuppressWarnings("unchecked")
    public E valueAt(int index) { return (E) values[index]; }

    public void setValueAt(int index, E value) { values[index] = value; }

    public void clear() {
        for (int i = 0; i < size; i++) values[i] = null;
        size = 0;
    }

    @Override
    public String toString() {
        if (size == 0) return "{}";
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < size; i++) {
            if (i > 0) sb.append(", ");
            sb.append(keyAt(i)).append('=').append(valueAt(i));
        }
        return sb.append('}').toString();
    }
}
