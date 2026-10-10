// SPDX-License-Identifier: GPL-3.0-or-later
package android.view.autofill;

/** Autofill node id: opaque long wrapper. */
public final class AutofillId {
    private final long id;
    public AutofillId(long id) { this.id = id; }
    public long getValue() { return id; }
    @Override public boolean equals(Object o) { return o instanceof AutofillId && ((AutofillId) o).id == id; }
    @Override public int hashCode() { return Long.hashCode(id); }
    @Override public String toString() { return "AutofillId{" + id + "}"; }
}
