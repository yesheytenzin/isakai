// SPDX-License-Identifier: GPL-3.0-or-later
package android.database.sqlite;

/** Compiled-statement bind shell. */
public abstract class SQLiteProgram extends SQLiteClosable {
    protected SQLiteProgram() { }
    public void bindNull(int index) { }
    public void bindLong(int index, long value) { }
    public void bindDouble(int index, double value) { }
    public void bindString(int index, String value) { }
    public void bindBlob(int index, byte[] value) { }
    public void clearBindings() { }
    public void bindAllArgsAsStrings(String[] args) {
        if(args!=null)for(int i=args.length;i>0;i--)bindString(i,args[i-1]);
    }
    @Override protected void onAllReferencesReleased() { }
}
