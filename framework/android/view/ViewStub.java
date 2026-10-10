// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

import android.content.Context;

/** ViewStub: lazily inflated placeholder (inflate resolves via parent). */
public class ViewStub extends View {
    private int layoutResource;
    private int inflatedId = NO_ID;
    private OnInflateListener inflateListener;

    public interface OnInflateListener {
        void onInflate(ViewStub stub, View inflated);
    }

    public ViewStub(Context context) {
        super(context);
    }

    public ViewStub(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    public ViewStub(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public int getLayoutResource() {
        return layoutResource;
    }

    public void setLayoutResource(int layoutResource) {
        this.layoutResource = layoutResource;
    }

    public int getInflatedId() {
        return inflatedId;
    }

    public void setInflatedId(int inflatedId) {
        this.inflatedId = inflatedId;
    }

    public void setOnInflateListener(OnInflateListener listener) {
        this.inflateListener = listener;
    }

    public View inflate() {
        if (layoutResource == 0) return this;
        LayoutInflater inflater = LayoutInflater.from(getContext());
        ViewGroup parent = (ViewGroup) getParent();
        View inflated = inflater.inflate(layoutResource, parent, false);
        if (inflatedId != NO_ID) inflated.setId(inflatedId);
        if (parent != null) {
            int index = parent.indexOfChild(this);
            parent.removeView(this);
            ViewGroup.LayoutParams params = getLayoutParams();
            if (params != null) inflated.setLayoutParams(params);
            parent.addView(inflated, index);
        }
        if (inflateListener != null) inflateListener.onInflate(this, inflated);
        return inflated;
    }

    public void setVisibility(int visibility) {
        if (visibility == VISIBLE || visibility == INVISIBLE) {
            inflate();
        } else {
            super.setVisibility(visibility);
        }
    }
}
