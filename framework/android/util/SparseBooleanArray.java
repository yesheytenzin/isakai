// SPDX-License-Identifier: GPL-3.0-or-later
package android.util;

/** Int-keyed boolean-valued sorted map. */
public class SparseBooleanArray implements Cloneable {
    private int[] keys;
    private boolean[] values;
    private int size;

    public SparseBooleanArray() { this(10); }

    public SparseBooleanArray(int initialCapacity) {
        if (initialCapacity < 0) initialCapacity = 0;
        keys = new int[initialCapacity];
        values = new boolean[initialCapacity];
        size = 0;
    }

    @Override
    public SparseBooleanArray clone() {
        SparseBooleanArray c = new SparseBooleanArray(size);
        System.arraycopy(keys, 0, c.keys, 0, size);
        System.arraycopy(values, 0, c.values, 0, size);
        c.size = size;
        return c;
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
        int n = size <= 0 ? 4 : size * 2;
        int[] nk = new int[n];
        boolean[] nv = new boolean[n];
        System.arraycopy(keys, 0, nk, 0, size);
        System.arraycopy(values, 0, nv, 0, size);
        keys = nk;
        values = nv;
    }

    public boolean get(int key) { return get(key, false); }

    public boolean get(int key, boolean defaultValue) {
        int i = binarySearch(key);
        return i >= 0 ? values[i] : defaultValue;
    }

    public void put(int key, boolean value) {
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

    public void append(int key, boolean value) {
        if (size > 0 && key <= keys[size - 1]) { put(key, value); return; }
        if (size >= keys.length) grow();
        keys[size] = key;
        values[size] = value;
        size++;
    }

    public void delete(int key) {
        int i = binarySearch(key);
        if (i >= 0) removeAt(i);
    }

    public void remove(int key) { delete(key); }

    public void removeAt(int index) {
        System.arraycopy(keys, index + 1, keys, index, size - index - 1);
        System.arraycopy(values, index + 1, values, index, size - index - 1);
        size--;
    }

    public int indexOfKey(int key) { return binarySearch(key); }

    public int indexOfValue(boolean value) {
        for (int i = 0; i < size; i++) if (values[i] == value) return i;
        return -1;
    }

    public int size() { return size; }
    public int keyAt(int index) { return keys[index]; }
    public boolean valueAt(int index) { return values[index]; }
    public void setValueAt(int index, boolean value) { values[index] = value; }
    public void clear() { size = 0; }

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
