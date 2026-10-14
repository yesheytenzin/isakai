// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

/** Window attachment and resize callbacks must reach custom views. */
public final class ViewLifecycleTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static final class Probe extends View {
        int attached, detached, sizes, layouts;
        int oldWidth, oldHeight;
        protected void onAttachedToWindow() {
            check(isAttachedToWindow(), "attachment state missing inside hook");
            attached++;
        }
        protected void onDetachedFromWindow() { detached++; }
        protected void onSizeChanged(int w, int h, int oldW, int oldH) {
            sizes++; oldWidth = oldW; oldHeight = oldH;
            check(getWidth() == w && getHeight() == h, "bounds must precede size callback");
        }
        protected void onLayout(boolean changed, int l, int t, int r, int b) { layouts++; }
    }
    public static void main(String[] args) {
        ViewGroup root = new ViewGroup(); ViewGroup nested = new ViewGroup(); Probe child = new Probe();
        nested.addView(child); root.addView(nested);
        final int[] notifications = new int[2];
        child.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View view) { notifications[0]++; }
            public void onViewDetachedFromWindow(View view) { notifications[1]++; }
        });
        root.dispatchHostAttached(); root.dispatchHostAttached();
        final int[] insets={0};
        child.setOnApplyWindowInsetsListener((view, value) -> { insets[0]++; return value; });
        child.requestApplyInsets(); child.requestApplyInsets();
        root.dispatchHostInsets(); root.dispatchHostInsets();
        check(insets[0]==1, "inset requests must coalesce and reach descendant listener");
        nested.setOnApplyWindowInsetsListener((view, value) -> WindowInsets.CONSUMED);
        child.requestApplyInsets(); root.dispatchHostInsets();
        check(insets[0]==1, "consumed parent insets must not reach descendants");
        nested.setOnApplyWindowInsetsListener(null);
        check(child.attached == 1 && notifications[0] == 1, "recursive attachment must run once");
        check(root.getWindowToken() != null && root.getWindowToken() == child.getWindowToken(),
                "attached hierarchy must share a non-null window token");
        check(root.getDisplay() != null && child.getDisplay() != null
                && root.getDisplay().getDisplayId() == 0, "attached views must expose host display");
        Probe added = new Probe(); nested.addView(added);
        check(added.attached == 1, "adding to attached parent must attach child");
        nested.removeViewAt(1);
        check(added.detached == 1 && !added.isAttachedToWindow(), "removal must detach child");
        child.layout(0, 0, 100, 50);
        check(child.sizes == 1 && child.oldWidth == 0 && child.oldHeight == 0, "initial size callback");
        child.layout(10, 10, 110, 60);
        check(child.sizes == 1, "position-only change must not trigger size callback");
        child.layout(10, 10, 210, 90);
        check(child.sizes == 2 && child.oldWidth == 100 && child.oldHeight == 50, "resize old dimensions");
        root.dispatchHostDetached(); root.dispatchHostDetached();
        check(child.detached == 1 && notifications[1] == 1 && !child.isAttachedToWindow(), "recursive detach once");
        check(root.getWindowToken() == null && child.getWindowToken() == null, "detachment must clear window token");
        check(root.getDisplay() == null && child.getDisplay() == null, "detached views must clear host display");
        root.dispatchHostAttached();
        check(child.attached == 2, "reattachment must run again");
        nested.removeAllViews();
        check(child.detached == 2 && child.getParent() == null, "removeAllViews must detach descendants");
        root.dispatchHostDetached();
        System.out.println("PASS: recursive window attachment, child removal and size callbacks");
    }
}
