// SPDX-License-Identifier: GPL-3.0-or-later
package android.content;

/** Service bind/unbind callbacks. */
public interface ServiceConnection {
    void onServiceConnected(ComponentName name, android.os.IBinder service);
    void onServiceDisconnected(ComponentName name);
}
