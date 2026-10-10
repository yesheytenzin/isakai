// SPDX-License-Identifier: GPL-3.0-or-later
package android.os;

/** Cross-process failure marker (no IPC exists; never thrown). */
public class RemoteException extends Exception {
    public RemoteException() {
        super();
    }

    public RemoteException(String message) {
        super(message);
    }
}
