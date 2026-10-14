// SPDX-License-Identifier: GPL-3.0-or-later
package android.media;
import android.content.Context;
import android.net.Uri;
/** No host MediaStore scanner: completion reports a null indexed URI. */
public final class MediaScannerConnection {
    public interface OnScanCompletedListener { void onScanCompleted(String path,Uri uri); }
    private MediaScannerConnection() {}
    public static void scanFile(Context context,String[] paths,String[] mimeTypes,OnScanCompletedListener callback) {
        if(paths==null)throw new NullPointerException("paths");
        org.isakai.runtime.Capabilities.unavailable("android.media.MediaScannerConnection.mediaStoreIndexing");
        if(callback!=null) {
            android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
            for(String path:paths)handler.post(() -> callback.onScanCompleted(path,null));
        }
    }
}
