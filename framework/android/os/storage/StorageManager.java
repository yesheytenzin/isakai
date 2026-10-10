// SPDX-License-Identifier: GPL-3.0-or-later
package android.os.storage;

/** Storage volume query stub: no volumes mounted. */
public class StorageManager {
    public StorageManager() {
    }

    public java.util.List getStorageVolumes() {
        return new java.util.ArrayList();
    }

    public boolean isEncrypted(java.io.File file) {
        return false;
    }
}
