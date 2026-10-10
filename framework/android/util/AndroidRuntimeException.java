// SPDX-License-Identifier: GPL-3.0-or-later
package android.util;

/** Framework runtime failure. */
public class AndroidRuntimeException extends RuntimeException {
    public AndroidRuntimeException() {
        super();
    }

    public AndroidRuntimeException(String message) {
        super(message);
    }

    public AndroidRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
