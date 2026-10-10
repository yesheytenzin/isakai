// SPDX-License-Identifier: GPL-3.0-or-later
package android.os;

/** Cancellation token: flag plus listener, nothing polls it. */
public class CancellationSignal {
    private boolean canceled;
    private OnCancelListener listener;

    public interface OnCancelListener {
        void onCancel();
    }

    public boolean isCanceled() { return canceled; }

    public void cancel() {
        if (canceled) return;
        canceled = true;
        if (listener != null) listener.onCancel();
    }

    public void setOnCancelListener(OnCancelListener listener) {
        this.listener = listener;
        if (canceled && listener != null) listener.onCancel();
    }

    public void throwIfCanceled() {
        if (canceled) throw new OperationCanceledException();
    }

    public static class OperationCanceledException extends RuntimeException {
        public OperationCanceledException() {
            super("canceled");
        }
    }
}
