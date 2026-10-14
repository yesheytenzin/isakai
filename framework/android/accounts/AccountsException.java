// SPDX-License-Identifier: GPL-3.0-or-later
package android.accounts;

/** Base authenticator exception (AOSP shape; thrown by account operations). */
public class AccountsException extends Exception {
    public AccountsException() {
        super();
    }

    public AccountsException(String message) {
        super(message);
    }

    public AccountsException(String message, Throwable cause) {
        super(message, cause);
    }
}
