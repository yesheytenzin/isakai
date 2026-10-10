// SPDX-License-Identifier: GPL-3.0-or-later
package android.util;

/** Int-to-long sparse map (sorted keys, binary search). */
public class SparseLongArray implements Cloneable {
    private int[] keys = new int[10];
    private long[] values = new long[10];
    private int size;

    public SparseLongArray() {}

    public SparseLongArray(int initialCapacity) {
        if (initialCapacity > 0) {
            keys = new int[initialCapacity];
            values = new long[initialCapacity];
        }
    }

    private int indexOf(int key) {
        int lo = 0;
        int hi = size - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (keys[mid] < key) lo = mid + 1;
            else if (keys[mid] > key) hi = mid - 1;
            else return mid;
        }
        return ~lo;
    }

    private void grow() {
        int n = size <= 0 ? 10 : size * 2;
        int[] nk = new int[n];
        long[] nv = new long[n];
        System.arraycopy(keys, 0, nk, 0, size);
        System.arraycopy(values, 0, nv, 0, size);
        keys = nk;
        values = nv;
    }

    public long get(int key) {
        return get(key, 0L);
    }

    public long get(int key, long defaultValue) {
        int i = indexOf(key);
        return i >= 0 ? values[i] : defaultValue;
    }

    public void put(int key, long value) {
        int i = indexOf(key);
        if (i >= 0) {
            values[i] = value;
            return;
        }
        i = ~i;
        if (size >= keys.length) grow();
        System.arraycopy(keys, i, keys, i + 1, size - i);
        System.arraycopy(values, i, values, i + 1, size - i);
        keys[i] = key;
        values[i] = value;
        size++;
    }

    public void delete(int key) {
        int i = indexOf(key);
        if (i < 0) return;
        System.arraycopy(keys, i + 1, keys, i, size - i - 1);
        System.arraycopy(values, i + 1, values, i, size - i - 1);
        size--;
    }

    public void removeAt(int index) {
        if (index < 0 || index >= size) return;
        System.arraycopy(keys, index + 1, keys, index, size - index - 1);
        System.arraycopy(values, index + 1, values, index, size - index - 1);
        size--;
    }

    public int size() { return size; }
    public int keyAt(int index) { return keys[index]; }
    public long valueAt(int index) { return values[index]; }
    public int indexOfKey(int key) { return indexOf(key); }

    public int indexOfValue(long value) {
        for (int i = 0; i < size; i++) {
            if (values[i] == value) return i;
        }
        return -1;
    }

    public void clear() { size = 0; }

    public void append(int key, long value) {
        if (size > 0 && key <= keys[size - 1]) {
            put(key, value);
            return;
        }
        if (size >= keys.length) grow();
        keys[size] = key;
        values[size] = value;
        size++;
    }

    @Override
    public SparseLongArray clone() {
        try {
            SparseLongArray c = (SparseLongArray) super.clone();
            c.keys = keys.clone();
            c.values = values.clone();
            return c;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public String toString() {
        if (size == 0) return "{}";
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < size; i++) {
            if (i > 0) sb.append(", ");
            sb.append(keys[i]).append('=').append(values[i]);
        }
        return sb.append('}').toString();
    }
}
