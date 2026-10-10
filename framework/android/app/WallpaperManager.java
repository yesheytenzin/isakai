// SPDX-License-Identifier: GPL-3.0-or-later
package android.app;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.IBinder;
import java.io.InputStream;

/** Wallpaper access; null shell. */
public class WallpaperManager {
    public static final String WALLPAPER_SERVICE = "wallpaper";
    public Drawable getDrawable() { return null; }
    public Drawable peekDrawable() { return null; }
    public Bitmap getBitmap() { return null; }
    public InputStream openStream() { return null; }
    public int getDesiredMinimumWidth() { return 0; }
    public int getDesiredMinimumHeight() { return 0; }
    public void setBitmap(Bitmap bitmap) { }
    public void clear() { }
    public void suggestDesiredDimensions(int minimumWidth, int minimumHeight) { }
    public void setWallpaperOffsets(IBinder windowToken, float xOffset, float yOffset) { }
    public boolean isWallpaperSupported() { return false; }
}
