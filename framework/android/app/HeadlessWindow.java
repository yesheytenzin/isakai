// SPDX-License-Identifier: GPL-3.0-or-later
package android.app;

import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

/**
 * Headless Window: in-memory decor/callback storage, no compositor link.
 * Lets AppCompat install its delegate callback and content view headlessly.
 */
public class HeadlessWindow extends Window {
    /** Framework content id (android.R.id.content): PhoneWindow parity. */
    public static final int ID_ANDROID_CONTENT = 0x01020002;

    private final Activity owner;
    private Callback callback;
    private View content;
    private final ViewGroup decor = new ViewGroup(null) {};
    private final WindowManager.LayoutParams attrs = new WindowManager.LayoutParams();
    private final ViewGroup decorContent;
    /**
     * lifecycle-runtime view_tree_lifecycle_owner key for the pinned Compose
     * app line. The window owns the view-tree owners (ComponentActivity
     * parity): tagging the decor lets the translated owner walk resolve the
     * owning activity, so the app's own path installs the window recomposer.
     */
    private static final int VIEW_TREE_LIFECYCLE_OWNER_KEY = 2131296811;

    public HeadlessWindow() { this(null); }

    public HeadlessWindow(Activity owner) {
        this.owner = owner;
        // PhoneWindow parity: the decor always carries a content child
        // with the framework content id, so AppCompat's content
        // migration (find + move + id swap) behaves identically.
        decorContent = new ViewGroup(null) {};
        decorContent.setId(ID_ANDROID_CONTENT);
        decor.addView(decorContent);
        if (owner != null) decor.setTag(VIEW_TREE_LIFECYCLE_OWNER_KEY, owner);
    }

    @Override
    public void setContentView(View view) {
        setContentView(view, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        this.content = view;
        if (owner != null) owner.setContentViewFromWindow(decorContent);
        // PhoneWindow parity: the content view lives inside the decor
        // content holder, so Window.findViewById sees app views.
        decorContent.removeAllViews();
        if (view != null) {
            if (params != null) view.setLayoutParams(params);
            decorContent.addView(view);
        }
    }

    @Override
    public void addContentView(View view, ViewGroup.LayoutParams params) {
        this.content = view;
        if (owner != null) owner.setContentViewFromWindow(decorContent);
        if (view != null) {
            if (params != null) view.setLayoutParams(params);
            decorContent.addView(view);
        }
    }


    @Override
    public View getDecorView() {
        return decor;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends View> T findViewById(int id) {
        return (T) decor.findViewById(id);
    }


    @Override
    public void addFlags(int flags) {
        attrs.flags |= flags;
    }

    @Override
    public void clearFlags(int flags) {
        attrs.flags &= ~flags;
    }

    @Override
    public void setFlags(int flags, int mask) {
        attrs.flags = (attrs.flags & ~mask) | (flags & mask);
    }

    @Override
    public WindowManager.LayoutParams getAttributes() {
        return attrs;
    }

    @Override
    public void setAttributes(WindowManager.LayoutParams attrs) {
        if (attrs != null) this.attrs.copyFrom(attrs);
    }

    @Override
    public Callback getCallback() {
        return callback;
    }

    @Override
    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    @Override
    public boolean requestFeature(int featureId) {
        return true;
    }

    @Override
    public void setBackgroundDrawable(android.graphics.drawable.Drawable drawable) {}

    public View getContentView() {
        return content;
    }
}
