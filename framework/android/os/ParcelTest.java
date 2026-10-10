// SPDX-License-Identifier: GPL-3.0-or-later
package android.os;

/** Host-side unit checks for {@link Parcel} write/read round-trips. Run on the host JVM. */
public final class ParcelTest {
    private ParcelTest() {}

    static void check(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }

    public static void main(String[] args) {
        // Primitives round-trip in order.
        Parcel p = Parcel.obtain();
        p.writeInt(0x12345678);
        p.writeLong(0x1122334455667788L);
        p.writeFloat(1.5f);
        p.writeDouble(-2.25);
        p.writeByte((byte) 0x7F);
        p.writeBoolean(true);
        p.writeBoolean(false);
        check(p.readInt() == 0x12345678, "int");
        check(p.readLong() == 0x1122334455667788L, "long");
        check(p.readFloat() == 1.5f, "float");
        check(p.readDouble() == -2.25, "double");
        check(p.readByte() == (byte) 0x7F, "byte");
        check(p.readBoolean(), "bool true");
        check(!p.readBoolean(), "bool false");

        // Strings: value, empty, null, unicode.
        Parcel s = Parcel.obtain();
        s.writeString("hello");
        s.writeString("");
        s.writeString(null);
        s.writeString("héllo wörld ☃");
        check("hello".equals(s.readString()), "string");
        check("".equals(s.readString()), "empty string");
        check(s.readString() == null, "null string");
        check("héllo wörld ☃".equals(s.readString()), "unicode string");

        // byte[] round-trip + null.
        Parcel b = Parcel.obtain();
        byte[] raw = new byte[] {0x00, 0x01, (byte) 0xFE, (byte) 0xFF, 0x42};
        b.writeByteArray(raw);
        b.writeByteArray(null);
        byte[] back = b.createByteArray();
        check(back != null && back.length == raw.length, "byte[] length");
        for (int i = 0; i < raw.length; i++) check(back[i] == raw[i], "byte[] " + i);
        check(b.createByteArray() == null, "null byte[]");

        // Arrays incl. nulls.
        Parcel a = Parcel.obtain();
        a.writeIntArray(new int[] {1, -2, 0x7FFFFFFF});
        a.writeLongArray(new long[] {0L, -1L});
        a.writeStringArray(new String[] {"a", null, "c"});
        a.writeIntArray(null);
        int[] ia = a.createIntArray();
        check(ia.length == 3 && ia[0] == 1 && ia[1] == -2 && ia[2] == 0x7FFFFFFF, "int[]");
        long[] la = a.createLongArray();
        check(la.length == 2 && la[0] == 0L && la[1] == -1L, "long[]");
        String[] sa = a.createStringArray();
        check(sa.length == 3 && "a".equals(sa[0]) && sa[1] == null && "c".equals(sa[2]), "string[]");
        check(a.createIntArray() == null, "null int[]");

        // writeValue boxed inline types + null.
        Parcel v = Parcel.obtain();
        v.writeValue(null);
        v.writeValue("s");
        v.writeValue(42);
        v.writeValue(43L);
        v.writeValue(4.5f);
        v.writeValue(6.5);
        v.writeValue(Boolean.TRUE);
        v.writeValue((byte) 9);
        v.writeValue(new byte[] {1, 2});
        check(v.readValue(null) == null, "value null");
        check("s".equals(v.readValue(null)), "value string");
        check(Integer.valueOf(42).equals(v.readValue(null)), "value int");
        check(Long.valueOf(43L).equals(v.readValue(null)), "value long");
        check(Float.valueOf(4.5f).equals(v.readValue(null)), "value float");
        check(Double.valueOf(6.5).equals(v.readValue(null)), "value double");
        check(Boolean.TRUE.equals(v.readValue(null)), "value bool");
        check(Byte.valueOf((byte) 9).equals(v.readValue(null)), "value byte");
        byte[] vb = (byte[]) v.readValue(null);
        check(vb.length == 2 && vb[0] == 1 && vb[1] == 2, "value byte[]");

        // setDataPosition rewind re-reads from the start.
        Parcel r = Parcel.obtain();
        r.writeInt(7);
        r.writeString("seven");
        check(r.readInt() == 7, "pre-rewind int");
        r.setDataPosition(0);
        check(r.readInt() == 7, "rewound int");
        check("seven".equals(r.readString()), "rewound string");
        check(r.dataPosition() == r.dataSize(), "position at end");

        // marshall/unmarshall preserves the stream.
        Parcel m = Parcel.obtain();
        m.writeInt(-99);
        m.writeString("packed");
        byte[] packed = m.marshall();
        Parcel u = Parcel.unmarshall(packed, 0, packed.length);
        check(u.readInt() == -99, "unmarshalled int");
        check("packed".equals(u.readString()), "unmarshalled string");

        // Fail-closed: seek out of range throws.
        boolean threw = false;
        try {
            u.setDataPosition(u.dataSize() + 1);
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        check(threw, "bad seek rejected");

        System.out.println("ParcelTest: all PASS");
    }
}
