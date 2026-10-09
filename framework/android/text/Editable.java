// SPDX-License-Identifier: GPL-3.0-or-later
package android.text;

/**
 * Minimal Editable: a plain mutable character buffer with insert/delete.
 * No spans or watchers in M3.
 */
public class Editable implements CharSequence {
    private final StringBuilder buffer;

    public Editable() {
        this.buffer = new StringBuilder();
    }

    public Editable(CharSequence text) {
        this.buffer = new StringBuilder(text != null ? text.toString() : "");
    }

    @Override
    public int length() {
        return buffer.length();
    }

    @Override
    public char charAt(int index) {
        return buffer.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return buffer.subSequence(start, end);
    }

    @Override
    public String toString() {
        return buffer.toString();
    }

    /** Insert text at offset, clamped into range. Returns this. */
    public Editable insert(int offset, CharSequence text) {
        int at = Math.max(0, Math.min(offset, buffer.length()));
        buffer.insert(at, text != null ? text.toString() : "");
        return this;
    }

    /** Delete [start, end), clamped into range. Returns this. */
    public Editable delete(int start, int end) {
        int s = Math.max(0, Math.min(start, buffer.length()));
        int e = Math.max(0, Math.min(end, buffer.length()));
        if (s < e) buffer.delete(s, e);
        return this;
    }

    /** Replace the whole buffer. Returns this. */
    public Editable replaceAll(CharSequence text) {
        buffer.setLength(0);
        if (text != null) buffer.append(text.toString());
        return this;
    }

    /** Append text at the end. Returns this. */
    public Editable append(CharSequence text) {
        if (text != null) buffer.append(text.toString());
        return this;
    }

    public void clear() {
        buffer.setLength(0);
    }
}
