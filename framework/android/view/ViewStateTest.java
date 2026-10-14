// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

public final class ViewStateTest {
    private static final class Probe extends View {
        int saves, restores;
        protected android.os.Parcelable onSaveInstanceState() { saves++; return super.onSaveInstanceState(); }
        protected void onRestoreInstanceState(android.os.Parcelable state) { restores++; }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        ViewGroup root=new ViewGroup(); ViewGroup managed=new ViewGroup(); Probe child=new Probe();
        child.setId(42); managed.addView(child); root.addView(managed);
        android.util.SparseArray<android.os.Parcelable> state=new android.util.SparseArray<>();
        check(managed.isSaveFromParentEnabled(), "default parent save flag");
        managed.setSaveFromParentEnabled(false);
        root.saveHierarchyState(state);
        check(child.saves==0 && state.get(42)==null, "parent must skip managed subtree");
        managed.saveHierarchyState(state);
        check(child.saves==1 && state.get(42)!=null, "direct subtree save must remain available");
        root.restoreHierarchyState(state);
        check(child.restores==0, "parent restore must skip managed subtree");
        managed.restoreHierarchyState(state);
        check(child.restores==1, "direct subtree restore must remain available");
        managed.setSaveFromParentEnabled(true);
        managed.setSaveEnabled(false);
        root.saveHierarchyState(state);
        check(child.saves==2, "saveEnabled=false must not suppress descendants");
        System.out.println("PASS: parent-controlled hierarchy state save/restore");
    }
}
