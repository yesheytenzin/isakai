// SPDX-License-Identifier: GPL-3.0-or-later
package android.util;

/** Base Android exception (checked). */
public class AndroidException extends Exception {
    public AndroidException() {
        super();
    }

    public AndroidException(String name) {
        super(name);
    }

    public AndroidException(String name, Throwable cause) {
        super(name, cause);
    }

    public AndroidException(Exception cause) {
        super(cause);
    }
}
