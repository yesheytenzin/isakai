// SPDX-License-Identifier: GPL-3.0-or-later
package android.os;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * In-process parcel: primitives, strings, and byte arrays round-trip
 * through an internal byte buffer (big-endian DataOutput/DataInput
 * order); binders, parcelables, and bundles travel by reference with a
 * position marker in the stream. There is no cross-process IPC, so
 * parcelables are NOT flattened (writeToParcel is never called here).
 *
 * <p>Write-then-read discipline: the read snapshot is taken lazily at
 * the first read. Any write after reading discards the snapshot and
 * resets the read position to 0.
 */
public class Parcel {
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final DataOutputStream out = new DataOutputStream(bytes);
    private final ArrayList<Object> objects = new ArrayList<>();

    private byte[] snapshot;
    private DataInputStream in;
    private int readPos;

    private Parcel() {}

    public static Parcel obtain() {
        return new Parcel();
    }

    public void recycle() {
        objects.clear();
        bytes.reset();
        snapshot = null;
        in = null;
        readPos = 0;
    }

    /** Bytes written so far. */
    public int dataSize() {
        return bytes.size();
    }

    /** Current read offset within the snapshot (0 before any read). */
    public int dataPosition() {
        return readPos;
    }

    /**
     * Seek the read cursor. Values outside [0, dataSize] throw
     * IllegalArgumentException (fail-closed: no silent clamp).
     */
    public void setDataPosition(int pos) {
        ensureSnapshot();
        if (pos < 0 || pos > snapshot.length) {
            throw new IllegalArgumentException("bad position " + pos + " of " + snapshot.length);
        }
        try {
            in = new DataInputStream(new ByteArrayInputStream(snapshot));
            long skipped = in.skip(pos);
            if (skipped != pos) throw new IOException("seek short");
            readPos = pos;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Copy of the bytes written so far. */
    public byte[] marshall() {
        discardSnapshot();
        return bytes.toByteArray();
    }

    /** Parcel positioned at 0 over {@code data[off..off+len]}. */
    public static Parcel unmarshall(byte[] data, int off, int len) {
        if (data == null) throw new NullPointerException("data");
        if (off < 0 || len < 0 || off + len > data.length) {
            throw new IllegalArgumentException("bad range");
        }
        Parcel p = new Parcel();
        p.bytes.write(data, off, len);
        return p;
    }

    private void discardSnapshot() {
        snapshot = null;
        in = null;
        readPos = 0;
    }

    private void ensureSnapshot() {
        if (snapshot == null) {
            snapshot = bytes.toByteArray();
            in = new DataInputStream(new ByteArrayInputStream(snapshot));
            readPos = 0;
        }
    }

    private void write(WriteOp op) {
        try {
            op.run();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        discardSnapshot();
    }

    private interface WriteOp {
        void run() throws IOException;
    }

    private <T> T read(ReadOp<T> op, int size) {
        ensureSnapshot();
        try {
            T v = op.run();
            readPos += size;
            return v;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private interface ReadOp<T> {
        T run() throws IOException;
    }

    public void writeInt(int v) { write(() -> out.writeInt(v)); }
    public void writeLong(long v) { write(() -> out.writeLong(v)); }
    public void writeFloat(float v) { write(() -> out.writeFloat(v)); }
    public void writeDouble(double v) { write(() -> out.writeDouble(v)); }
    public void writeByte(byte v) { write(() -> out.writeByte(v)); }

    public void writeBoolean(boolean v) { writeInt(v ? 1 : 0); }

    public void writeString(String v) {
        if (v == null) {
            writeInt(-1);
            return;
        }
        byte[] raw = v.getBytes(StandardCharsets.UTF_8);
        writeInt(raw.length);
        write(() -> out.write(raw));
    }

    public void writeByteArray(byte[] v) {
        if (v == null) {
            writeInt(-1);
            return;
        }
        writeInt(v.length);
        write(() -> out.write(v));
    }

    public void writeIntArray(int[] v) {
        if (v == null) {
            writeInt(-1);
            return;
        }
        writeInt(v.length);
        for (int x : v) writeInt(x);
    }

    public void writeLongArray(long[] v) {
        if (v == null) {
            writeInt(-1);
            return;
        }
        writeInt(v.length);
        for (long x : v) writeLong(x);
    }

    public void writeStringArray(String[] v) {
        if (v == null) {
            writeInt(-1);
            return;
        }
        writeInt(v.length);
        for (String s : v) writeString(s);
    }

    /**
     * Boxed value: common immutable types (String, Integer, Long,
     * Float, Double, Boolean, Byte, byte[]) round-trip inline by tag;
     * anything else travels by reference. Null is preserved.
     */
    public void writeValue(Object v) {
        if (v == null) {
            writeInt(0);
            return;
        }
        if (v instanceof String) { writeInt(1); writeString((String) v); return; }
        if (v instanceof Integer) { writeInt(2); writeInt((Integer) v); return; }
        if (v instanceof Long) { writeInt(3); writeLong((Long) v); return; }
        if (v instanceof Float) { writeInt(4); writeFloat((Float) v); return; }
        if (v instanceof Double) { writeInt(5); writeDouble((Double) v); return; }
        if (v instanceof Boolean) { writeInt(6); writeBoolean((Boolean) v); return; }
        if (v instanceof Byte) { writeInt(7); writeByte((Byte) v); return; }
        if (v instanceof byte[]) { writeInt(8); writeByteArray((byte[]) v); return; }
        writeInt(9);
        int idx = objects.size();
        objects.add(v);
        writeInt(idx);
        discardSnapshot();
    }

    private void writeRef(Object v) {
        if (v == null) {
            writeInt(-1);
            return;
        }
        objects.add(v);
        writeInt(objects.size() - 1);
        discardSnapshot();
    }

    private Object readRef() {
        int idx = readInt();
        if (idx < 0) return null;
        if (idx >= objects.size()) throw new RuntimeException("bad ref " + idx);
        return objects.get(idx);
    }

    public void writeStrongBinder(android.os.IBinder b) { writeRef(b); }
    public void writeParcelable(Parcelable p, int flags) { writeRef(p); }
    public void writeBundle(android.os.Bundle b) { writeRef(b); }

    public int readInt() { return read(() -> in.readInt(), 4); }
    public long readLong() { return read(() -> in.readLong(), 8); }
    public float readFloat() { return read(() -> in.readFloat(), 4); }
    public double readDouble() { return read(() -> in.readDouble(), 8); }
    public byte readByte() { return read(() -> in.readByte(), 1); }
    public boolean readBoolean() { return readInt() != 0; }

    public String readString() {
        int len = readInt();
        if (len < 0) return null;
        ensureSnapshot();
        if (readPos + len > snapshot.length) throw new RuntimeException("string overrun");
        try {
            byte[] raw = new byte[len];
            in.readFully(raw);
            readPos += len;
            return new String(raw, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] createByteArray() {
        int len = readInt();
        if (len < 0) return null;
        ensureSnapshot();
        if (readPos + len > snapshot.length) throw new RuntimeException("byte[] overrun");
        try {
            byte[] v = new byte[len];
            in.readFully(v);
            readPos += len;
            return v;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Fill {@code v} (may be longer than the stored array: prefix fill). */
    public void readByteArray(byte[] v) {
        byte[] stored = createByteArray();
        if (stored == null || v == null) return;
        System.arraycopy(stored, 0, v, 0, Math.min(stored.length, v.length));
    }

    public int[] createIntArray() {
        int len = readInt();
        if (len < 0) return null;
        int[] v = new int[len];
        for (int i = 0; i < len; i++) v[i] = readInt();
        return v;
    }

    public long[] createLongArray() {
        int len = readInt();
        if (len < 0) return null;
        long[] v = new long[len];
        for (int i = 0; i < len; i++) v[i] = readLong();
        return v;
    }

    public String[] createStringArray() {
        int len = readInt();
        if (len < 0) return null;
        String[] v = new String[len];
        for (int i = 0; i < len; i++) v[i] = readString();
        return v;
    }

    public Object readValue(ClassLoader loader) {
        int tag = readInt();
        switch (tag) {
            case 0: return null;
            case 1: return readString();
            case 2: return readInt();
            case 3: return readLong();
            case 4: return readFloat();
            case 5: return readDouble();
            case 6: return readBoolean();
            case 7: return readByte();
            case 8: return createByteArray();
            case 9: {
                int idx = readInt();
                if (idx < 0 || idx >= objects.size()) throw new RuntimeException("bad value ref");
                return objects.get(idx);
            }
            default: throw new RuntimeException("bad value tag " + tag);
        }
    }

    public android.os.IBinder readStrongBinder() {
        return (android.os.IBinder) readRef();
    }

    @SuppressWarnings("unchecked")
    public <T extends Parcelable> T readParcelable(ClassLoader loader) {
        return (T) readRef();
    }

    public android.os.Bundle readBundle() {
        return (android.os.Bundle) readRef();
    }

    public android.os.Bundle readBundle(ClassLoader loader) {
        return readBundle();
    }
}
