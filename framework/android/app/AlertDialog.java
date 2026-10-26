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
        return button(whichButton);
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
}
