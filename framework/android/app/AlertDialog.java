// SPDX-License-Identifier: GPL-3.0-or-later
package android.app;

import android.content.Context;
import android.content.DialogInterface;
import android.widget.Button;

/**
 * Minimal AlertDialog: retains a message and up to three buttons. Subclassed
 * by ProgressDialog, which is how Android apps reach the button surface.
 */
public class AlertDialog extends Dialog {
    private CharSequence message;
    private final Button[] buttons = new Button[3];

    public AlertDialog(Context context) {
        super(context);
    }

    public AlertDialog(Context context, int themeResId) {
        super(context, themeResId);
    }

    public void setMessage(CharSequence message) {
        this.message = message;
    }

    public CharSequence getMessage() {
        return message;
    }

    public void setButton(int whichButton, CharSequence text,
            DialogInterface.OnClickListener listener) {
        Button button = button(whichButton);
        button.setText(text);
        button.setOnClickListener(v -> {
            if (listener != null) listener.onClick(AlertDialog.this, whichButton);
            dismiss();
        });
    }

    public Button getButton(int whichButton) {
        int index = -whichButton - 1;
        if (index < 0 || index >= buttons.length) {
            throw new IllegalArgumentException("whichButton");
        }
        return buttons[index];
    }

    private Button button(int whichButton) {
        int index = -whichButton - 1;
        if (index < 0 || index >= buttons.length) {
            throw new IllegalArgumentException("whichButton");
        }
        Button button = buttons[index];
        if (button == null) {
            button = new Button(getContext());
            buttons[index] = button;
        }
        return button;
    }

    /** Minimal builder over {@link #setMessage} and {@link #setButton}. */
    public static class Builder {
        private final AlertDialog dialog;

        public Builder(Context context) {
            dialog = new AlertDialog(context);
        }

        public Builder setTitle(CharSequence title) {
            dialog.setTitle(title);
            return this;
        }

        public Builder setMessage(CharSequence message) {
            dialog.setMessage(message);
            return this;
        }

        public Builder setMessage(int messageId) {
            dialog.setMessage(dialog.getContext().getText(messageId));
            return this;
        }

        public Builder setPositiveButton(CharSequence text, DialogInterface.OnClickListener listener) {
            dialog.setButton(DialogInterface.BUTTON_POSITIVE, text, listener);
            return this;
        }

        public Builder setPositiveButton(int textId, DialogInterface.OnClickListener listener) {
            return setPositiveButton(dialog.getContext().getText(textId), listener);
        }

        public Builder setNegativeButton(CharSequence text, DialogInterface.OnClickListener listener) {
            dialog.setButton(DialogInterface.BUTTON_NEGATIVE, text, listener);
            return this;
        }

        public Builder setNegativeButton(int textId, DialogInterface.OnClickListener listener) {
            return setNegativeButton(dialog.getContext().getText(textId), listener);
        }

        public Builder setNeutralButton(CharSequence text, DialogInterface.OnClickListener listener) {
            dialog.setButton(DialogInterface.BUTTON_NEUTRAL, text, listener);
            return this;
        }

        public Builder setNeutralButton(int textId, DialogInterface.OnClickListener listener) {
            return setNeutralButton(dialog.getContext().getText(textId), listener);
        }

        public Builder setCancelable(boolean cancelable) {
            dialog.setCancelable(cancelable);
            return this;
        }

        public AlertDialog create() {
            return dialog;
        }

        public AlertDialog show() {
            dialog.show();
            return dialog;
        }
    }
}
