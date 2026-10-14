// SPDX-License-Identifier: GPL-3.0-or-later
package android.database;

/** Change observer: stub delivery, self observer marker. */
public abstract class ContentObserver {
    private final Object lock = new Object();

    public ContentObserver() {}

    public ContentObserver(android.os.Handler handler) {}
    public boolean deliverSelfNotifications() {
        return false;
    }

    public void onChange(boolean selfChange) {}

    public void onChange(boolean selfChange, android.net.Uri uri) {
        onChange(selfChange);
    }
}
