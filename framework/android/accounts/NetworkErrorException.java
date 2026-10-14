// SPDX-License-Identifier: GPL-3.0-or-later
package android.accounts;

/** Authenticator network failure (AOSP shape; required by menu dispatch paths). */
public class NetworkErrorException extends AccountsException {
    public NetworkErrorException() {
        super();
    }

    public NetworkErrorException(String message) {
        super(message);
    }
}
