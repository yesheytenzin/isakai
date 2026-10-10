// SPDX-License-Identifier: GPL-3.0-or-later
package android.app;

/** Fragment transaction: add/remove batch, commit applies. */
public class FragmentTransaction {
    private final FragmentManager manager;
    private final java.util.ArrayList<Runnable> ops = new java.util.ArrayList<>();

    FragmentTransaction(FragmentManager manager) {
        this.manager = manager;
    }

    public FragmentTransaction add(Fragment fragment, String tag) {
        ops.add(() -> manager.putFragment(tag, fragment));
        return this;
    }

    public FragmentTransaction add(int containerViewId, Fragment fragment, String tag) {
        return add(fragment, tag);
    }

    public FragmentTransaction remove(Fragment fragment) {
        ops.add(() -> manager.removeFragment(fragment));
        return this;
    }

    public int commit() {
        for (Runnable op : ops) op.run();
        return 0;
    }
}
