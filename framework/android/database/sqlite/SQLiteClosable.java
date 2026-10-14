// SPDX-License-Identifier: GPL-3.0-or-later
package android.database.sqlite;

/** Reference-counted lifetime shared by databases and compiled programs. */
public abstract class SQLiteClosable implements java.io.Closeable {
    private int references=1;
    public void acquireReference() {
        synchronized(this) {
            if(references<=0)throw new IllegalStateException("SQLite object is closed");
            references++;
        }
    }
    public void releaseReference() {
        boolean released;
        synchronized(this) { released=--references==0; }
        if(released)onAllReferencesReleased();
    }
    public void releaseReferenceFromContainer() {
        boolean released;
        synchronized(this) { released=--references==0; }
        if(released)onAllReferencesReleasedFromContainer();
    }
    protected abstract void onAllReferencesReleased();
    protected void onAllReferencesReleasedFromContainer() { onAllReferencesReleased(); }
    @Override public void close() { releaseReference(); }
}
