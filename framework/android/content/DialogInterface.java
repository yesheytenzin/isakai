// SPDX-License-Identifier: GPL-3.0-or-later
package android.content;

/** Dialog cancel/dismiss/click/key contracts. */
public interface DialogInterface {
    int BUTTON_POSITIVE = -1;
    int BUTTON_NEGATIVE = -2;
    int BUTTON_NEUTRAL = -3;

    void cancel();
    void dismiss();

    interface OnCancelListener {
        void onCancel(DialogInterface dialog);
    }

    interface OnDismissListener {
        void onDismiss(DialogInterface dialog);
    }

    interface OnClickListener {
        void onClick(DialogInterface dialog, int which);
    }

    interface OnMultiChoiceClickListener {
        void onClick(DialogInterface dialog, int which, boolean isChecked);
    }

    interface OnKeyListener {
        boolean onKey(DialogInterface dialog, int keyCode, android.view.KeyEvent event);
    }

    interface OnShowListener {
        void onShow(DialogInterface dialog);
    }
}
