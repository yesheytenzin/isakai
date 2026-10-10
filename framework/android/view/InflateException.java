// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

/** Inflate failure: view hierarchy could not be constructed. */
public class InflateException extends RuntimeException {
    public InflateException(String detailMessage) {
        super(detailMessage);
    }

    public InflateException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public InflateException(Throwable cause) {
        super(cause);
    }
}
