// SPDX-License-Identifier: GPL-3.0-or-later
package android.os;

/** StrictMode shell: notes recorded nowhere. */
public final class StrictMode {
    private StrictMode() { }
    public static final class ThreadPolicy {
        public static final ThreadPolicy LAX = new ThreadPolicy();
        public static final class Builder {
            public Builder() { }
            public Builder(ThreadPolicy policy) { }
            public Builder detectAll() { return this; }
            public Builder detectDiskReads() { return this; }
            public Builder detectDiskWrites() { return this; }
            public Builder detectNetwork() { return this; }
            public Builder penaltyLog() { return this; }
            public Builder penaltyDeath() { return this; }
            public ThreadPolicy build() { return new ThreadPolicy(); }
        }
    }
    public static final class VmPolicy {
        public static final VmPolicy LAX = new VmPolicy();
        public static final class Builder {
            public Builder() { }
            public Builder(VmPolicy policy) { }
            public Builder detectAll() { return this; }
            public Builder detectLeakedClosableObjects() { return this; }
            public Builder penaltyLog() { return this; }
            public Builder penaltyDeath() { return this; }
            public VmPolicy build() { return new VmPolicy(); }
        }
    }
    public static void setThreadPolicy(ThreadPolicy policy) { }
    public static void setVmPolicy(VmPolicy policy) { }
    public static void noteDiskRead() { }
    public static void noteDiskWrite() { }
    public static void noteSlowCall(String name) { }
}
