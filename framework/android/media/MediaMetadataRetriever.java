// SPDX-License-Identifier: GPL-3.0-or-later
package android.media;

/** Metadata shell: no keys populated. */
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
    public void setDataSource(String path) { }
    public String extractMetadata(int keyCode) { return null; }
    public void release() { }
    @Override public void close() { release(); }
}
