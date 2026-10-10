// SPDX-License-Identifier: GPL-3.0-or-later
package android.content;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Mutable name/value map for database rows, ported from the AOSP value-holder
 * API. Null keys are rejected; null values are stored as Java null (bound as
 * SQL NULL) and reported by {@link #containsKey}/{@link #isNull}.
 */
public final class ContentValues {
    private final HashMap<String, Object> values;

    public ContentValues() {
        values = new HashMap<>();
    }

    public ContentValues(int size) {
        values = new HashMap<>((int) Math.ceil(size / 0.75f) + 1);
    }

    public ContentValues(ContentValues from) {
        values = new HashMap<>(from.values);
    }

    private static String checkKey(String key) {
        if (key == null) throw new IllegalArgumentException("null key");
        return key;
    }

    public void put(String key, String value) {
        values.put(checkKey(key), value);
    }

    public void put(String key, Integer value) {
        values.put(checkKey(key), value);
    }

    public void put(String key, Long value) {
        values.put(checkKey(key), value);
    }

    public void put(String key, Float value) {
        values.put(checkKey(key), value);
    }

    public void put(String key, Double value) {
        values.put(checkKey(key), value);
    }

    public void put(String key, Boolean value) {
        values.put(checkKey(key), value);
    }

    public void put(String key, byte[] value) {
        values.put(checkKey(key), value != null ? value.clone() : null);
    }

    public void putNull(String key) {
        values.put(checkKey(key), null);
    }

    public Object get(String key) {
        return values.get(key);
    }

    public String getAsString(String key) {
        Object v = values.get(key);
        return v != null ? v.toString() : null;
    }

    public Integer getAsInteger(String key) {
        Object v = values.get(key);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof Boolean) return (Boolean) v ? 1 : 0;
        return Integer.parseInt(v.toString());
    }

    public Long getAsLong(String key) {
        Object v = values.get(key);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        if (v instanceof Boolean) return (Boolean) v ? 1L : 0L;
        return Long.parseLong(v.toString());
    }

    public Float getAsFloat(String key) {
        Object v = values.get(key);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).floatValue();
        return Float.parseFloat(v.toString());
    }

    public Double getAsDouble(String key) {
        Object v = values.get(key);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).doubleValue();
        return Double.parseDouble(v.toString());
    }

    public Boolean getAsBoolean(String key) {
        Object v = values.get(key);
        if (v == null) return null;
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number) return ((Number) v).intValue() != 0;
        return Boolean.parseBoolean(v.toString());
    }

    public byte[] getAsByteArray(String key) {
        Object v = values.get(key);
        if (v == null) return null;
        if (v instanceof byte[]) return ((byte[]) v).clone();
        throw new ClassCastException("not a byte[]: " + key);
    }

    public boolean containsKey(String key) {
        return values.containsKey(key);
    }

    public boolean isNull(String key) {
        return values.containsKey(key) && values.get(key) == null;
    }

    public int size() {
        return values.size();
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public void remove(String key) {
        values.remove(key);
    }

    public void clear() {
        values.clear();
    }

    public Set<Map.Entry<String, Object>> valueSet() {
        return values.entrySet();
    }

    public Set<String> keySet() {
        return values.keySet();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ContentValues)) return false;
        return values.equals(((ContentValues) o).values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return "ContentValues" + values;
    }
}
