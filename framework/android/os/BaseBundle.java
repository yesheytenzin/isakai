// SPDX-License-Identifier: GPL-3.0-or-later
package android.os;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

/** String-keyed value map base; Bundle adds parceling. */
public class BaseBundle {
    protected final HashMap<String, Object> map = new HashMap<>();

    public BaseBundle() {}

    public BaseBundle(BaseBundle src) {
        if (src != null) map.putAll(src.map);
    }

    public int size() { return map.size(); }
    public boolean isEmpty() { return map.isEmpty(); }
    public void clear() { map.clear(); }
    public boolean containsKey(String key) { return map.containsKey(key); }
    public Set<String> keySet() { return map.keySet(); }
    public Object get(String key) { return map.get(key); }
    public void remove(String key) { map.remove(key); }

    public void putBoolean(String k, boolean v) { map.put(k, v); }
    public void putByte(String k, byte v) { map.put(k, v); }
    public void putChar(String k, char v) { map.put(k, v); }
    public void putShort(String k, short v) { map.put(k, v); }
    public void putInt(String k, int v) { map.put(k, v); }
    public void putLong(String k, long v) { map.put(k, v); }
    public void putFloat(String k, float v) { map.put(k, v); }
    public void putDouble(String k, double v) { map.put(k, v); }
    public void putString(String k, String v) { map.put(k, v); }
    public void putCharSequence(String k, CharSequence v) { map.put(k, v); }
    public void putParcelable(String k, Parcelable v) { map.put(k, v); }
    public void putParcelableArray(String k, Parcelable[] v) { map.put(k, v); }
    public void putParcelableArrayList(String k, ArrayList<? extends Parcelable> v) { map.put(k, v); }
    public void putIntegerArrayList(String k, ArrayList<Integer> v) { map.put(k, v); }
    public void putStringArrayList(String k, ArrayList<String> v) { map.put(k, v); }
    public void putIntArray(String k, int[] v) { map.put(k, v); }
    public void putLongArray(String k, long[] v) { map.put(k, v); }
    public void putStringArray(String k, String[] v) { map.put(k, v); }
    public void putBooleanArray(String k, boolean[] v) { map.put(k, v); }
    public void putBinder(String k, IBinder v) { map.put(k, v); }

    protected <T> T cast(String k, T def, Class<T> type) {
        Object o = map.get(k);
        return type.isInstance(o) ? type.cast(o) : def;
    }

    public boolean getBoolean(String k) { return getBoolean(k, false); }
    public boolean getBoolean(String k, boolean def) { return cast(k, def, Boolean.class); }
    public byte getByte(String k) { return getByte(k, (byte) 0); }
    public byte getByte(String k, byte def) { return cast(k, def, Byte.class); }
    public char getChar(String k) { return getChar(k, (char) 0); }
    public char getChar(String k, char def) { return cast(k, def, Character.class); }
    public short getShort(String k) { return getShort(k, (short) 0); }
    public short getShort(String k, short def) { return cast(k, def, Short.class); }
    public int getInt(String k) { return getInt(k, 0); }
    public int getInt(String k, int def) { return cast(k, def, Integer.class); }
    public long getLong(String k) { return getLong(k, 0L); }
    public long getLong(String k, long def) { return cast(k, def, Long.class); }
    public float getFloat(String k) { return getFloat(k, 0f); }
    public float getFloat(String k, float def) { return cast(k, def, Float.class); }
    public double getDouble(String k) { return getDouble(k, 0d); }
    public double getDouble(String k, double def) { return cast(k, def, Double.class); }
    public String getString(String k) { return getString(k, null); }
    public String getString(String k, String def) { return cast(k, def, String.class); }
    public CharSequence getCharSequence(String k) { return cast(k, null, CharSequence.class); }
    public int[] getIntArray(String k) { return cast(k, null, int[].class); }
    public String[] getStringArray(String k) { return cast(k, null, String[].class); }
    public IBinder getBinder(String k) { return cast(k, null, IBinder.class); }
    @SuppressWarnings("unchecked")
    public ArrayList<String> getStringArrayList(String k) { return (ArrayList<String>) map.get(k); }

    @Override
    public String toString() {
        return "Bundle" + map.toString();
    }
}
