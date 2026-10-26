// SPDX-License-Identifier: GPL-3.0-or-later
package android.content;

/** Context delegating context-owned operations to a base context. */
public class ContextWrapper extends Context {
    private Context base;

    public ContextWrapper(Context base) {
        this.base = base;
    }

    protected void attachBaseContext(Context base) {
        if (this.base != null) throw new IllegalStateException("already attached");
        this.base = base;
    }

    public Context getBaseContext() {
        return base;
    }

    @Override
    public android.content.res.Resources getResources() {
        return base.getResources();
    }

    @Override
    public Context createConfigurationContext(android.content.res.Configuration overrideConfiguration) {
        return base.createConfigurationContext(overrideConfiguration);
    }

    @Override
    public android.content.res.Resources.Theme getTheme() {
        return base.getTheme();
    }

    @Override
    public Object getSystemService(String name) {
        return base.getSystemService(name);
    }

    @Override
    public String getPackageName() {
        return base.getPackageName();
    }

    @Override
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return base.getSharedPreferences(name, mode);
    }

    @Override
    public String getDataDirString() {
        return base.getDataDirString();
    }

    @Override
    public java.io.File getDataDir() {
        return base.getDataDir();
    }

    @Override
    public java.io.File getFilesDir() {
        return base.getFilesDir();
    }

    @Override
    public java.io.File getCacheDir() {
        return base.getCacheDir();
    }

    @Override
    public java.io.File getCodeCacheDir() {
        return base.getCodeCacheDir();
    }

    @Override
    public java.io.File getNoBackupFilesDir() {
        return base.getNoBackupFilesDir();
    }

    @Override
    public java.io.File getExternalFilesDir(String type) {
        return base.getExternalFilesDir(type);
    }

    @Override
    public java.io.File[] getExternalFilesDirs(String type) {
        return base.getExternalFilesDirs(type);
    }

    @Override
    public java.io.File getExternalCacheDir() {
        return base.getExternalCacheDir();
    }

    @Override
    public java.io.File[] getExternalCacheDirs() {
        return base.getExternalCacheDirs();
    }

    @Override
    public java.io.File getDir(String name, int mode) {
        return base.getDir(name, mode);
    }

    @Override
    public java.io.File getFileStreamPath(String name) {
        return base.getFileStreamPath(name);
    }

    @Override
    public java.io.File getSharedPrefsFile(String name) {
        return base.getSharedPrefsFile(name);
    }

    @Override
    public java.io.File getDatabasePath(String name) {
        return base.getDatabasePath(name);
    }

    @Override
    public String[] fileList() {
        return base.fileList();
    }

    @Override
    public String[] databaseList() {
        return base.databaseList();
    }

    @Override
    public boolean deleteFile(String name) {
        return base.deleteFile(name);
    }

    @Override
    public boolean deleteDatabase(String name) {
        return base.deleteDatabase(name);
    }

    @Override
    public java.io.FileInputStream openFileInput(String name) throws java.io.IOException {
        return base.openFileInput(name);
    }

    @Override
    public java.io.FileOutputStream openFileOutput(String name, int mode) throws java.io.IOException {
        return base.openFileOutput(name, mode);
    }

    @Override
    public String readFile(String name, String fallback) {
        return base.readFile(name, fallback);
    }

    @Override
    public void writeFile(String name, String content) {
        base.writeFile(name, content);
    }

    @Override
    public void drainQueuedWork() {
        base.drainQueuedWork();
    }

    @Override
    public Context getApplicationContext() {
        return base.getApplicationContext();
    }

    @Override
    public android.content.pm.ApplicationInfo getApplicationInfo() {
        return base.getApplicationInfo();
    }

    @Override
    public android.content.pm.PackageManager getPackageManager() {
        return base.getPackageManager();
    }

    @Override
    public ContentResolver getContentResolver() {
        return base.getContentResolver();
    }

    @Override
    public ClassLoader getClassLoader() {
        return base.getClassLoader();
    }

    @Override
    public android.os.Looper getMainLooper() {
        return base.getMainLooper();
    }

    @Override
    public boolean isRestricted() {
        return base.isRestricted();
    }

    @Override
    public <T> T getSystemService(Class<T> type) {
        return base.getSystemService(type);
    }

    @Override
    public void registerComponentCallbacks(ComponentCallbacks callback) {
        base.registerComponentCallbacks(callback);
    }

    @Override
    public void unregisterComponentCallbacks(ComponentCallbacks callback) {
        base.unregisterComponentCallbacks(callback);
    }

    @Override
    public void startActivity(Intent intent) {
        base.startActivity(intent);
    }

    @Override
    public void startActivity(Intent intent, android.os.Bundle options) {
        base.startActivity(intent, options);
    }

    @Override
    public void startActivities(Intent[] intents, android.os.Bundle options) {
        base.startActivities(intents, options);
    }

    @Override
    public boolean bindService(Intent intent, ServiceConnection connection, int flags) {
        return base.bindService(intent, connection, flags);
    }

    @Override
    public void unbindService(ServiceConnection connection) {
        base.unbindService(connection);
    }
}
