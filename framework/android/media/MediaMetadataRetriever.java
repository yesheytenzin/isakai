// SPDX-License-Identifier: GPL-3.0-or-later
package android.media;

import android.graphics.Bitmap;
import android.util.Log;
import java.io.File;
import org.isakai.bridge.IsakaiMedia;

/** Metadata retriever backed by host ffprobe/ffmpeg. */
public class MediaMetadataRetriever implements AutoCloseable {
    public static final int METADATA_KEY_TITLE = 7;
    public static final int METADATA_KEY_ARTIST = 2;
    public static final int METADATA_KEY_ALBUM = 1;
    public static final int METADATA_KEY_DURATION = 9;
    public static final int METADATA_KEY_MIMETYPE = 12;
    public static final int METADATA_KEY_VIDEO_WIDTH = 18;
    public static final int METADATA_KEY_VIDEO_HEIGHT = 19;
    public static final int OPTION_PREVIOUS_SYNC = 0;
    public static final int OPTION_NEXT_SYNC = 1;
    public static final int OPTION_CLOSEST_SYNC = 2;
    public static final int OPTION_CLOSEST = 3;

    private String path;

    public void setDataSource(String path) {
        if (path == null) throw new IllegalArgumentException("path == null");
        this.path = path;
    }

    public void setDataSource(java.io.FileDescriptor fd) {
        throw new UnsupportedOperationException("FileDescriptor not supported");
    }

    public String extractMetadata(int keyCode) {
        if (path == null) return null;
        switch (keyCode) {
            case METADATA_KEY_DURATION: {
                long us = IsakaiMedia.probeDurationUs(path);
                if (us < 0) return null;
                return String.valueOf(us / 1000);
            }
            case METADATA_KEY_MIMETYPE:
                return IsakaiMedia.probeMime(path);
            default:
                return null;
        }
    }

    public Bitmap getFrameAtTime(long timeUs) {
        return getFrameAtTime(timeUs, OPTION_CLOSEST_SYNC);
    }

    public Bitmap getFrameAtTime(long timeUs, int option) {
        if (path == null) throw new IllegalStateException("no data source");
        if (!IsakaiMedia.hasFfmpeg()) return null;
        try {
            return IsakaiMedia.getFrameAtTime(path, timeUs);
        } catch (Exception e) {
            Log.w("MediaMetadataRetriever", "getFrameAtTime failed: " + e.getMessage());
            return null;
        }
    }

    public byte[] getEmbeddedPicture() { return null; }
    public void release() { path = null; }
    @Override public void close() { release(); }
}
