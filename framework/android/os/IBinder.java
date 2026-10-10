// SPDX-License-Identifier: GPL-3.0-or-later
package android.os;

/** Binder token: null-object stand-in, no IPC exists. */
public interface IBinder {
    int FIRST_CALL_TRANSACTION = 1;

    String getInterfaceDescriptor();
}
