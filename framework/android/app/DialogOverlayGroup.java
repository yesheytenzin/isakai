// SPDX-License-Identifier: GPL-3.0-or-later
package android.app;

import android.content.Context;
import android.graphics.Canvas;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

/**
 * Overlay installed as the activity's root while a Dialog is showing.
 *
 * <p>{@code dialogPanel} is a real ViewGroup child so it participates in
 * host-attach/detach, dirty propagation, and touch routing normally.
 *
 * <p>{@code savedContent} is held as a reference only, never added as a child.
 * The activity's AppCompat delegate routes {@code setContentView} through
 * HeadlessWindow, which would call {@code decorContent.addView(overlay)},
 * making savedContent the parent of the overlay — an immediate cycle.
 * Bypassing that via {@code setContentViewFromWindow} leaves savedContent's
 * parent unchanged, so it is safe to draw manually here.
 */
final class DialogOverlayGroup extends ViewGroup {

    private final View savedContent; // drawn in onDraw; NOT a child
    private final Dialog dialog;
    private int panelLeft;
    private int panelTop;

    DialogOverlayGroup(Context context, View savedContent, View dialogPanel, Dialog dialog) {
        super(context);
        this.savedContent = savedContent;
        this.dialog = dialog;
        if (dialogPanel != null) {
            addView(dialogPanel, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int h = MeasureSpec.getSize(heightSpec);
        if (savedContent != null) {
            savedContent.measure(
                    MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        }
        if (getChildCount() > 0) {
            int dw = Math.max(1, (int) (w * 0.9f));
            int dh = Math.max(1, (int) (h * 0.9f));
            getChildAt(0).measure(
                    MeasureSpec.makeMeasureSpec(dw, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(dh, MeasureSpec.AT_MOST));
        }
        setMeasuredDimension(resolveSize(w, widthSpec), resolveSize(h, heightSpec));
    }

    @Override
    protected void onLayout(int left, int top, int right, int bottom) {
        int w = right - left;
        int h = bottom - top;
        if (savedContent != null) {
            savedContent.layout(0, 0, w, h);
        }
        if (getChildCount() > 0) {
            View panel = getChildAt(0);
            int pw = panel.getMeasuredWidth();
            int ph = panel.getMeasuredHeight();
            panelLeft = Math.max(0, (w - pw) / 2);
            panelTop  = Math.max(0, (h - ph) / 2);
            panel.layout(panelLeft, panelTop, panelLeft + pw, panelTop + ph);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // savedContent is detached from the host (run loop detached it when
        // the root switched to this overlay), but draw() does not require
        // attachment, so the frozen background renders correctly.
        if (savedContent != null) {
            savedContent.drawAt(canvas, 0, 0);
        }
        canvas.drawColor(0x88000000);
        // dispatchDrawAt (super) draws dialogPanel on top at the centered position.
    }

    @Override
    public boolean isDirtyAndClear() {
        // savedContent's invalidate() walks to HeadlessWindow.decor, not here;
        // check it explicitly so background updates trigger a redraw.
        boolean d = super.isDirtyAndClear();
        if (savedContent != null) d |= savedContent.isDirtyAndClear();
        return d;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (dialog.isCanceledOnTouchOutside()) dialog.cancel();
            return true; // always consume: frozen background must not respond
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            dialog.onBackPressed();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
