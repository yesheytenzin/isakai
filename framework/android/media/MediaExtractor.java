// SPDX-License-Identifier: GPL-3.0-or-later
package android.media;

/** Media extraction shell: no tracks. */
public final class MediaExtractor implements AutoCloseable {
    public static final int SEEK_TO_PREVIOUS_SYNC = 0;
    public static final int SEEK_TO_NEXT_SYNC = 1;
    public static final int SEEK_TO_CLOSEST_SYNC = 2;
    public static final int SAMPLE_FLAG_SYNC = 1;
    public static final int SAMPLE_FLAG_PARTIAL_FRAME = 4;
    public void setDataSource(String path) { }
    public int getTrackCount() { return 0; }
    public MediaFormat getTrackFormat(int index) { throw new IllegalArgumentException("no track " + index); }
    public void selectTrack(int index) { }
    public void unselectTrack(int index) { }
    public void seekTo(long timeUs, int mode) { }
    public void release() { }
    @Override public void close() { release(); }
}
