// SPDX-License-Identifier: GPL-3.0-or-later
package android.util;

/** Int-keyed sorted map with binary search. */
public class SparseArray<E> implements Cloneable {
    private int[] keys;
    private Object[] values;
    private int size;

    public SparseArray() {
        this(10);
    }

    public SparseArray(int initialCapacity) {
        if (initialCapacity == 0) {
            keys = new int[0];
            values = new Object[0];
        } else {
            keys = new int[initialCapacity];
            values = new Object[initialCapacity];
        }
        size = 0;
    }

    @SuppressWarnings("unchecked")
    @Override
    public SparseArray<E> clone() {
        try {
            SparseArray<E> c = (SparseArray<E>) super.clone();
            c.keys = keys.clone();
            c.values = values.clone();
            return c;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    private int binarySearch(int key) {
        int lo = 0, hi = size - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int m = keys[mid];
            if (m < key) lo = mid + 1;
            else if (m > key) hi = mid - 1;
            else return mid;
        }
        return ~lo;
    }

    private void grow() {
        int n = size == 0 ? 4 : size * 2;
        int[] nk = new int[n];
        Object[] nv = new Object[n];
        System.arraycopy(keys, 0, nk, 0, size);
        System.arraycopy(values, 0, nv, 0, size);
        keys = nk;
        values = nv;
    }

    @SuppressWarnings("unchecked")
    public E get(int key) {
        return get(key, null);
    }

    @SuppressWarnings("unchecked")
    public E get(int key, E defaultValue) {
        int i = binarySearch(key);
        return i >= 0 ? (E) values[i] : defaultValue;
    }

    public void put(int key, E value) {
        int i = binarySearch(key);
        if (i >= 0) {
            values[i] = value;
            return;
        }
        int at = ~i;
        if (size >= keys.length) grow();
        System.arraycopy(keys, at, keys, at + 1, size - at);
        System.arraycopy(values, at, values, at + 1, size - at);
        keys[at] = key;
        values[at] = value;
        size++;
    }

    public void append(int key, E value) {
        if (size > 0 && key <= keys[size - 1]) {
            put(key, value);
            return;
        }
        if (size >= keys.length) grow();
        keys[size] = key;
        values[size] = value;
        size++;
    }

    public void remove(int key) {
        int i = binarySearch(key);
        if (i >= 0) removeAt(i);
    }

    public void removeAt(int index) {
        if (index < 0 || index >= size) return;
        System.arraycopy(keys, index + 1, keys, index, size - index - 1);
        System.arraycopy(values, index + 1, values, index, size - index - 1);
        values[size - 1] = null;
        size--;
    }

    public int indexOfKey(int key) {
        return binarySearch(key);
    }

    public int indexOfValue(E value) {
        for (int i = 0; i < size; i++) {
            if (values[i] == value || (value != null && value.equals(values[i]))) return i;
        }
        return -1;
    }

    public int size() { return size; }
    public int keyAt(int index) { return keys[index]; }

    @SuppressWarnings("unchecked")
    public E valueAt(int index) { return (E) values[index]; }

    public void setValueAt(int index, E value) { values[index] = value; }

    public void clear() {
        for (int i = 0; i < size; i++) values[i] = null;
        size = 0;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < size; i++) {
            if (i > 0) sb.append(", ");
            sb.append(keyAt(i)).append('=').append(valueAt(i));
        }
        return sb.append('}').toString();
    }
}
