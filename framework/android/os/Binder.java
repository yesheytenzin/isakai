// SPDX-License-Identifier: GPL-3.0-or-later
package android.os;

/** Binder identity shell: alive, same-process only. */
public class Binder implements IBinder {
    public static long clearCallingIdentity() { return 0L; }
    public static void restoreCallingIdentity(long token) { }
    public static int getCallingUid() { return 0; }
    public static int getCallingPid() { return 0; }
    @Override public String getInterfaceDescriptor() { return ""; }
    public boolean pingBinder() { return true; }
    public boolean isBinderAlive() { return true; }
    public void linkToDeath(DeathRecipient recipient, int flags) { }
    public boolean unlinkToDeath(DeathRecipient recipient, int flags) { return true; }
    public interface DeathRecipient { void binderDied(); }
}
