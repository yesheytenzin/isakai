package android.os;
import java.io.File;
/** Storage facade: shared host storage is unavailable; legacy paths are app-scoped. */
public final class Environment {
    private Environment() {}
    public static final String MEDIA_MOUNTED="mounted";
    public static final String MEDIA_UNMOUNTED="unmounted";
    public static final String DIRECTORY_DOWNLOADS="Download";
    public static final String DIRECTORY_DOCUMENTS="Documents";
    public static File getExternalStorageDirectory() {
        org.isakai.runtime.Capabilities.unavailable("android.os.Environment.sharedExternalStorage");
        String data=System.getProperty("isakai.dataDir");
        if(data==null)throw new IllegalStateException("App storage is not attached");
        File root=new File(data,"external");
        if(!root.isDirectory() && !root.mkdirs())throw new IllegalStateException("Cannot create app-scoped external storage");
        return root;
    }
    public static String getExternalStorageState() { return getExternalStorageDirectory().canWrite()?MEDIA_MOUNTED:MEDIA_UNMOUNTED; }
    public static boolean isExternalStorageLegacy() { return false; }
    public static boolean isExternalStorageManager() { return false; }
}
