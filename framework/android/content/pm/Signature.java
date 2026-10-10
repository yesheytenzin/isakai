// SPDX-License-Identifier: GPL-3.0-or-later
package android.content.pm;

/** APK signature bytes. */
public class Signature {
    private final byte[] bytes;

    public Signature(byte[] bytes) {
        this.bytes = bytes == null ? new byte[0] : bytes.clone();
    }

    public Signature(String text) {
        this.bytes = text == null ? new byte[0] : text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public byte[] toByteArray() {
        return bytes.clone();
    }

    public String toCharsString() {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Signature && java.util.Arrays.equals(bytes, ((Signature) o).bytes);
    }

    @Override
    public int hashCode() {
        return java.util.Arrays.hashCode(bytes);
    }
}
